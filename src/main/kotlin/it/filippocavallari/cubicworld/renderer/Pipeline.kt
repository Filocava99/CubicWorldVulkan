package it.filippocavallari.cubicworld.renderer

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.*
import java.nio.LongBuffer

/**
 * Represents a pipeline for rendering.
 * Encapsulates the Vulkan graphics pipeline creation and management.
 */
class Pipeline(private val device: VkDevice) {
    
    private var pipelineLayout: Long = VK_NULL_HANDLE
    private var graphicsPipeline: Long = VK_NULL_HANDLE
    
    /**
     * Creates a graphics pipeline for rendering.
     * 
     * @param renderPass The render pass to use
     * @param shader The shader program to use
     * @param extent The rendering extent
     * @param descriptorSetLayout The descriptor set layout
     * @param pushConstantRanges The push constant ranges
     */
    fun create(
        renderPass: Long,
        shader: Shader,
        extent: VkExtent2D,
        descriptorSetLayout: LongBuffer? = null,
        pushConstantRanges: VkPushConstantRange.Buffer? = null
    ) {
        MemoryStack.stackPush().use { stack ->
            // Pipeline layout
            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            
            if (descriptorSetLayout != null) {
                pipelineLayoutInfo.pSetLayouts(descriptorSetLayout)
            }
            
            if (pushConstantRanges != null) {
                pipelineLayoutInfo.pPushConstantRanges(pushConstantRanges)
            }
            
            val pPipelineLayout = stack.mallocLong(1)
            if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw RuntimeException("Failed to create pipeline layout")
            }
            pipelineLayout = pPipelineLayout.get(0)
            
            // Vertex input state
            val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(null)
                .pVertexAttributeDescriptions(null)
            
            // Input assembly state
            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false)
            
            // Viewport state
            val viewport = VkViewport.calloc(1, stack)
                .x(0.0f)
                .y(0.0f)
                .width(extent.width().toFloat())
                .height(extent.height().toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)
            
            val scissor = VkRect2D.calloc(1, stack)
                .offset(VkOffset2D.calloc(stack).set(0, 0))
                .extent(extent)
            
            val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .pViewports(viewport)
                .pScissors(scissor)
            
            // Rasterization state
            val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_BACK_BIT)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .depthBiasEnable(false)
            
            // Multisample state
            val multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
            
            // Depth stencil state
            val depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(true)
                .depthWriteEnable(true)
                .depthCompareOp(VK_COMPARE_OP_LESS)
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false)
            
            // Color blend state
            val colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
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
            
            val colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .pAttachments(colorBlendAttachment)
            
            // Dynamic state
            val dynamicStates = stack.ints(
                VK_DYNAMIC_STATE_VIEWPORT,
                VK_DYNAMIC_STATE_SCISSOR
            )
            
            val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(dynamicStates)
            
            // Pipeline creation
            val pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shader.getShaderStages())
                .pVertexInputState(vertexInputInfo)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pDepthStencilState(depthStencil)
                .pColorBlendState(colorBlending)
                .pDynamicState(dynamicState)
                .layout(pipelineLayout)
                .renderPass(renderPass)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1)
            
            val pGraphicsPipeline = stack.mallocLong(1)
            if (vkCreateGraphicsPipelines(
                    device,
                    VK_NULL_HANDLE,
                    pipelineInfo,
                    null,
                    pGraphicsPipeline
                ) != VK_SUCCESS
            ) {
                throw RuntimeException("Failed to create graphics pipeline")
            }
            graphicsPipeline = pGraphicsPipeline.get(0)
        }
    }
    
    /**
     * Binds the pipeline for rendering.
     * 
     * @param commandBuffer The command buffer to record the bind command to
     */
    fun bind(commandBuffer: VkCommandBuffer) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline)
    }
    
    /**
     * Gets the pipeline layout.
     * 
     * @return The pipeline layout handle
     */
    fun getPipelineLayout(): Long {
        return pipelineLayout
    }
    
    /**
     * Gets the graphics pipeline.
     * 
     * @return The graphics pipeline handle
     */
    fun getGraphicsPipeline(): Long {
        return graphicsPipeline
    }
    
    /**
     * Cleans up resources.
     */
    fun cleanup() {
        vkDestroyPipeline(device, graphicsPipeline, null)
        vkDestroyPipelineLayout(device, pipelineLayout, null)
    }
}