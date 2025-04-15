package it.filippocavallari.cubicworld.utils

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPhysicalDevice
import java.nio.LongBuffer

/**
 * Utility class for shader management.
 * Handles shader compilation, loading, and pipeline creation.
 */
object ShaderManager {
    
    // Map of loaded shaders by name
    private val shaders = mutableMapOf<String, ShaderProgram>()
    
    /**
     * Loads a shader program from vertex and fragment shader files.
     * 
     * @param name The name to identify the shader program
     * @param device The Vulkan logical device
     * @param vertexShaderPath The path to the vertex shader file
     * @param fragmentShaderPath The path to the fragment shader file
     */
    fun loadShader(
        name: String,
        device: VkDevice,
        vertexShaderPath: String,
        fragmentShaderPath: String
    ) {
        if (shaders.containsKey(name)) {
            println("Shader program '$name' already loaded")
            return
        }
        
        try {
            val shader = ShaderProgram(device, vertexShaderPath, fragmentShaderPath)
            shaders[name] = shader
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to load shader program: $name")
        }
    }
    
    /**
     * Gets a loaded shader program by name.
     * 
     * @param name The name of the shader program
     * @return The shader program, or null if not found
     */
    fun getShader(name: String): ShaderProgram? {
        return shaders[name]
    }
    
    /**
     * Creates a pipeline for a shader program.
     * 
     * @param name The name of the shader program
     * @param device The Vulkan logical device
     * @param renderPass The render pass to use
     * @param width The width of the rendering surface
     * @param height The height of the rendering surface
     * @param descriptorSetLayouts The descriptor set layouts
     * @return The created pipeline, or null if failed
     */
    fun createPipeline(
        name: String,
        device: VkDevice,
        renderPass: Long,
        width: Int,
        height: Int,
        descriptorSetLayouts: LongBuffer? = null
    ): Long {
        val shader = shaders[name] ?: return VK_NULL_HANDLE
        
        MemoryStack.stackPush().use { stack ->
            // Create pipeline layout
            val pipelineLayoutInfo = org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(descriptorSetLayouts)
                .pPushConstantRanges(null)
            
            val pPipelineLayout = stack.mallocLong(1)
            if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                println("Failed to create pipeline layout")
                return VK_NULL_HANDLE
            }
            
            val pipelineLayout = pPipelineLayout.get(0)
            
            // Create graphics pipeline
            val pipeline = shader.createPipeline(device, renderPass, width, height, pipelineLayout)
            
            return pipeline
        }
    }
    
    /**
     * Cleans up all shader programs.
     * 
     * @param device The Vulkan logical device
     */
    fun cleanup(device: VkDevice) {
        for (shader in shaders.values) {
            shader.cleanup(device)
        }
        shaders.clear()
    }
    
    /**
     * Represents a shader program with vertex and fragment shaders.
     */
    class ShaderProgram(
        device: VkDevice,
        vertexShaderPath: String,
        fragmentShaderPath: String
    ) {
        private var vertexShaderModule: Long = VK_NULL_HANDLE
        private var fragmentShaderModule: Long = VK_NULL_HANDLE
        
        init {
            // Load shader modules
            vertexShaderModule = loadShaderModule(device, vertexShaderPath)
            fragmentShaderModule = loadShaderModule(device, fragmentShaderPath)
        }
        
        /**
         * Creates a pipeline for this shader program.
         * 
         * @param device The Vulkan logical device
         * @param renderPass The render pass to use
         * @param width The width of the rendering surface
         * @param height The height of the rendering surface
         * @param pipelineLayout The pipeline layout to use
         * @return The created pipeline
         */
        fun createPipeline(
            device: VkDevice,
            renderPass: Long,
            width: Int,
            height: Int,
            pipelineLayout: Long
        ): Long {
            MemoryStack.stackPush().use { stack ->
                // Shader stages
                val shaderStages = org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.calloc(2, stack)
                
                // Vertex shader
                shaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertexShaderModule)
                    .pName(stack.UTF8("main"))
                
                // Fragment shader
                shaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragmentShaderModule)
                    .pName(stack.UTF8("main"))
                
                // Vertex input state
                val vertexInputInfo = org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(null)
                    .pVertexAttributeDescriptions(null)
                
                // Input assembly state
                val inputAssembly = org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false)
                
                // Viewport and scissor
                val viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                    .x(0.0f)
                    .y(0.0f)
                    .width(width.toFloat())
                    .height(height.toFloat())
                    .minDepth(0.0f)
                    .maxDepth(1.0f)
                
                val scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
                    .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                    .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(width, height))
                
                val viewportState = org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .pViewports(viewport)
                    .scissorCount(1)
                    .pScissors(scissor)
                
                // Rasterization state
                val rasterizer = org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .depthBiasEnable(false)
                
                // Multisample state
                val multisampling = org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                
                // Color blend state
                val colorBlendAttachment = org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(
                        VK_COLOR_COMPONENT_R_BIT or
                        VK_COLOR_COMPONENT_G_BIT or
                        VK_COLOR_COMPONENT_B_BIT or
                        VK_COLOR_COMPONENT_A_BIT
                    )
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
                
                val colorBlending = org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .logicOp(VK_LOGIC_OP_COPY)
                    .attachmentCount(1)
                    .pAttachments(colorBlendAttachment)
                
                // Dynamic state
                val dynamicStates = stack.ints(
                    VK_DYNAMIC_STATE_VIEWPORT,
                    VK_DYNAMIC_STATE_SCISSOR
                )
                
                val dynamicState = org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(dynamicStates)
                
                // Create the pipeline
                val pipelineInfo = org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .stageCount(2)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(null)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1)
                
                val pPipelines = stack.mallocLong(1)
                if (vkCreateGraphicsPipelines(
                        device,
                        VK_NULL_HANDLE,
                        pipelineInfo,
                        null,
                        pPipelines
                    ) != VK_SUCCESS
                ) {
                    throw RuntimeException("Failed to create graphics pipeline")
                }
                
                return pPipelines.get(0)
            }
        }
        
        /**
         * Loads a shader module from a file.
         * 
         * @param device The Vulkan logical device
         * @param shaderPath The path to the shader file
         * @return The shader module handle
         */
        private fun loadShaderModule(device: VkDevice, shaderPath: String): Long {
            val shaderCode = readShaderFile(shaderPath)
            
            MemoryStack.stackPush().use { stack ->
                val createInfo = org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(shaderCode)
                
                val pShaderModule = stack.mallocLong(1)
                if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                    throw RuntimeException("Failed to create shader module")
                }
                
                return pShaderModule.get(0)
            }
        }
        
        /**
         * Reads a shader file from the resources.
         * 
         * @param shaderPath The path to the shader file
         * @return The shader code as a ByteBuffer
         */
        private fun readShaderFile(shaderPath: String): java.nio.ByteBuffer {
            val classLoader = javaClass.classLoader
            val url = classLoader.getResource(shaderPath)
                ?: throw RuntimeException("Shader file not found: $shaderPath")
            
            try {
                val bytes = url.openStream().readBytes()
                val buffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.size)
                buffer.put(bytes)
                buffer.flip()
                return buffer
            } catch (e: Exception) {
                throw RuntimeException("Failed to read shader file: $shaderPath", e)
            }
        }
        
        /**
         * Cleans up shader modules.
         * 
         * @param device The Vulkan logical device
         */
        fun cleanup(device: VkDevice) {
            if (vertexShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertexShaderModule, null)
                vertexShaderModule = VK_NULL_HANDLE
            }
            
            if (fragmentShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragmentShaderModule, null)
                fragmentShaderModule = VK_NULL_HANDLE
            }
        }
    }
}