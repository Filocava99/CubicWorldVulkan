package it.filippocavallari.cubicworld.renderer

import it.filippocavallari.cubicworld.utils.VulkanUtils
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.models.ModelManager
import it.filippocavallari.textures.TextureAtlasLoader
import it.filippocavallari.textures.TextureStitcher
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.LongBuffer

/**
 * Handles rendering of chunks using Vulkan.
 * This is the core of the rendering pipeline for voxel chunks.
 */
class ChunkRenderer(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    private val renderPass: Long,
    private val commandPool: Long,
    private val graphicsQueue: VkQueue,
    private val swapChainExtent: VkExtent2D,
    private val modelManager: ModelManager,
    private val textureStitcher: TextureStitcher
) {
    // Pipeline objects
    private var pipelineLayout: Long = VK_NULL_HANDLE
    private var pipeline: Long = VK_NULL_HANDLE
    
    // Descriptor set layout and pool
    private var descriptorSetLayout: Long = VK_NULL_HANDLE
    private var descriptorPool: Long = VK_NULL_HANDLE
    
    // Texture handles
    private var textureDescriptorSets: LongArray? = null
    
    // UBO (Uniform Buffer Object) for transformation matrices
    private var uniformBuffer: Long = VK_NULL_HANDLE
    private var uniformBufferMemory: Long = VK_NULL_HANDLE
    private var uniformDescriptorSet: Long = VK_NULL_HANDLE
    
    // Chunk meshes (handle multiple chunks)
    private val chunkMeshes = HashMap<Long, ChunkMesh>()
    private val chunkMeshBuilder = ChunkMeshBuilder(modelManager, textureStitcher)
    
    // Light properties buffer
    private var lightBuffer: Long = VK_NULL_HANDLE
    private var lightBufferMemory: Long = VK_NULL_HANDLE
    private var lightDescriptorSet: Long = VK_NULL_HANDLE
    
    // View matrix for the camera
    private val viewMatrix = Matrix4f()
    private val projMatrix = Matrix4f()
    
    /**
     * Initialize the chunk renderer
     */
    fun init() {
        createDescriptorSetLayout()
        createPipeline()
        createUniformBuffers()
        createDescriptorPool()
        allocateDescriptorSets()
        loadTextures()
        updateLightBuffer()
    }
    
    /**
     * Create the descriptor set layout for uniforms and textures
     */
    private fun createDescriptorSetLayout() {
        MemoryStack.stackPush().use { stack ->
            // UBO binding (for transformation matrices)
            val uboBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
            uboBinding.get(0)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
            
            // Texture sampler binding
            val samplerBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
            samplerBinding.get(0)
                .binding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
            
            // Light properties UBO binding
            val lightBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
            lightBinding.get(0)
                .binding(2)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
            
            // Create bindings array
            val bindings = VkDescriptorSetLayoutBinding.calloc(3, stack)
            bindings.put(0, uboBinding.get(0))
            bindings.put(1, samplerBinding.get(0))
            bindings.put(2, lightBinding.get(0))
            
            // Create the descriptor set layout
            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings)
            
            val pDescriptorSetLayout = stack.mallocLong(1)
            if (vkCreateDescriptorSetLayout(device, layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw RuntimeException("Failed to create descriptor set layout")
            }
            
            descriptorSetLayout = pDescriptorSetLayout.get(0)
        }
    }
    
    /**
     * Create the pipeline for rendering chunks
     */
    private fun createPipeline() {
        MemoryStack.stackPush().use { stack ->
            // Load shaders
            val vertShaderModule = createShaderModule("shaders/basic.vert.spv", stack)
            val fragShaderModule = createShaderModule("shaders/basic.frag.spv", stack)
            
            // Setup shader stages
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack)
            
            shaderStages.get(0)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertShaderModule)
                .pName(stack.UTF8("main"))
            
            shaderStages.get(1)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragShaderModule)
                .pName(stack.UTF8("main"))
            
            // Vertex input
            val bindingDescription = VkVertexInputBindingDescription.calloc(1, stack)
            bindingDescription.get(0)
                .binding(0)
                .stride(8 * 4) // 3 position + 3 normal + 2 texcoord
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
            
            val attributeDescriptions = VkVertexInputAttributeDescription.calloc(3, stack)
            
            // Position attribute
            attributeDescriptions.get(0)
                .binding(0)
                .location(0)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0)
            
            // Normal attribute
            attributeDescriptions.get(1)
                .binding(0)
                .location(1)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(3 * 4)
            
            // Texture coordinate attribute
            attributeDescriptions.get(2)
                .binding(0)
                .location(2)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(6 * 4)
            
            // Vertex input state
            val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDescription)
                .pVertexAttributeDescriptions(attributeDescriptions)
            
            // Input assembly state
            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false)
            
            // Viewport state
            val viewport = VkViewport.calloc(1, stack)
                .x(0.0f)
                .y(0.0f)
                .width(swapChainExtent.width().toFloat())
                .height(swapChainExtent.height().toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)
            
            val scissor = VkRect2D.calloc(1, stack)
                .offset(VkOffset2D.calloc(stack).set(0, 0))
                .extent(swapChainExtent)
            
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
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false)
            
            // Multisample state
            val multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
            
            // Depth and stencil state
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
            
            // Pipeline layout
            val layouts = stack.longs(descriptorSetLayout)
            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(layouts)
            
            val pPipelineLayout = stack.mallocLong(1)
            if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw RuntimeException("Failed to create pipeline layout")
            }
            pipelineLayout = pPipelineLayout.get(0)
            
            // Create the graphics pipeline
            val pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(vertexInputInfo)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pDepthStencilState(depthStencil)
                .pColorBlendState(colorBlending)
                .layout(pipelineLayout)
                .renderPass(renderPass)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1)
            
            val pPipeline = stack.mallocLong(1)
            if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline) != VK_SUCCESS) {
                throw RuntimeException("Failed to create graphics pipeline")
            }
            pipeline = pPipeline.get(0)
            
            // Clean up shader modules
            vkDestroyShaderModule(device, vertShaderModule, null)
            vkDestroyShaderModule(device, fragShaderModule, null)
        }
    }
    
    /**
     * Create the necessary uniform buffers
     */
    private fun createUniformBuffers() {
        // Create a uniform buffer for transformation matrices
        val uboSize = 3 * 16 * 4 // 3 matrices (model, view, projection) of 4x4 floats
        
        val uboBuffer = createBuffer(
            uboSize.toLong(),
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        
        uniformBuffer = uboBuffer.first
        uniformBufferMemory = uboBuffer.second
        
        // Create a uniform buffer for light properties
        val lightSize = 3 * 4 + 3 * 4 + 3 * 4 + 4 * 4 // vec3 lightPos + vec3 lightColor + vec3 viewPos + 4 floats
        
        val lightBuffer = createBuffer(
            lightSize.toLong(),
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        
        this.lightBuffer = lightBuffer.first
        this.lightBufferMemory = lightBuffer.second
    }
    
    /**
     * Create a Vulkan buffer with memory allocation
     */
    private fun createBuffer(
        size: Long,
        usage: Int,
        properties: Int
    ): Pair<Long, Long> {
        MemoryStack.stackPush().use { stack ->
            // Create buffer
            val bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            
            val pBuffer = stack.mallocLong(1)
            if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                throw RuntimeException("Failed to create buffer")
            }
            val buffer = pBuffer.get(0)
            
            // Get memory requirements
            val memReqs = VkMemoryRequirements.calloc(stack)
            vkGetBufferMemoryRequirements(device, buffer, memReqs)
            
            // Find memory type
            val memoryTypeIndex = VulkanUtils.findMemoryType(
                physicalDevice,
                memReqs.memoryTypeBits(),
                properties
            )
            
            // Allocate memory
            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(memoryTypeIndex)
            
            val pMemory = stack.mallocLong(1)
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate buffer memory")
            }
            val bufferMemory = pMemory.get(0)
            
            // Bind memory to buffer
            vkBindBufferMemory(device, buffer, bufferMemory, 0)
            
            return Pair(buffer, bufferMemory)
        }
    }
    
    /**
     * Create a descriptor pool for allocating descriptor sets
     */
    private fun createDescriptorPool() {
        MemoryStack.stackPush().use { stack ->
            // We need to allocate descriptor sets for:
            // 1. Uniform buffer (matrices)
            // 2. Combined image sampler (texture)
            // 3. Uniform buffer (light properties)
            
            val poolSizes = VkDescriptorPoolSize.calloc(2, stack)
            
            // Uniform buffer pool size
            poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(2) // One for matrices, one for lights
            
            // Combined image sampler pool size
            poolSizes.get(1)
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(3) // For diffuse, normal, and specular textures
            
            // Create the descriptor pool
            val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pPoolSizes(poolSizes)
                .maxSets(5) // Total number of sets we need to allocate
            
            val pDescriptorPool = stack.mallocLong(1)
            if (vkCreateDescriptorPool(device, poolInfo, null, pDescriptorPool) != VK_SUCCESS) {
                throw RuntimeException("Failed to create descriptor pool")
            }
            
            descriptorPool = pDescriptorPool.get(0)
        }
    }
    
    /**
     * Allocate and update descriptor sets for uniforms and textures
     */
    private fun allocateDescriptorSets() {
        MemoryStack.stackPush().use { stack ->
            // Allocate uniform buffer descriptor set
            val layouts = stack.mallocLong(1)
            layouts.put(0, descriptorSetLayout)
            
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts)
            
            val pDescriptorSet = stack.mallocLong(1)
            if (vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate descriptor set")
            }
            
            uniformDescriptorSet = pDescriptorSet.get(0)
            
            // Update the uniform buffer descriptor
            val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(uniformBuffer)
                .offset(0)
                .range(3 * 16 * 4)
            
            val descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(uniformDescriptorSet)
                .dstBinding(0)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(bufferInfo)
            
            vkUpdateDescriptorSets(device, descriptorWrite, null)
            
            // Allocate light buffer descriptor set
            val lightAllocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts)
            
            val pLightDescriptorSet = stack.mallocLong(1)
            if (vkAllocateDescriptorSets(device, lightAllocInfo, pLightDescriptorSet) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate light descriptor set")
            }
            
            lightDescriptorSet = pLightDescriptorSet.get(0)
            
            // Update the light buffer descriptor
            val lightBufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(lightBuffer)
                .offset(0)
                .range(3 * 4 + 3 * 4 + 3 * 4 + 4 * 4)
            
            val lightDescriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(lightDescriptorSet)
                .dstBinding(2)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(lightBufferInfo)
            
            vkUpdateDescriptorSets(device, lightDescriptorWrite, null)
        }
    }
    
    /**
     * Load textures from the stitcher to the GPU
     */
    private fun loadTextures() {
        // Load the texture atlas to Vulkan
        val vkInstance = physicalDevice.instance
        
        textureDescriptorSets = TextureAtlasLoader.loadAtlasesToVulkan(
            textureStitcher,
            renderPass,
            device,
            physicalDevice.address(),
            vkInstance,
            commandPool,
            graphicsQueue
        )
    }
    
    /**
     * Update the light properties uniform buffer
     */
    private fun updateLightBuffer() {
        MemoryStack.stackPush().use { stack ->
            // Map memory
            val data = stack.mallocPointer(1)
            vkMapMemory(device, lightBufferMemory, 0, 3 * 4 + 3 * 4 + 3 * 4 + 4 * 4, 0, data)
            
            // Write light position (vec3)
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memFloatBuffer(mappedMemory, 13)
            
            // Light position (vec3)
            buffer.put(0, 1000.0f) // x
            buffer.put(1, 1000.0f) // y
            buffer.put(2, 1000.0f) // z
            
            // Light color (vec3)
            buffer.put(3, 1.0f) // r
            buffer.put(4, 1.0f) // g
            buffer.put(5, 0.9f) // b
            
            // View position (vec3) - will be updated per frame
            buffer.put(6, 0.0f) // x
            buffer.put(7, 0.0f) // y
            buffer.put(8, 0.0f) // z
            
            // Light properties (4 floats)
            buffer.put(9, 0.2f)  // ambient strength
            buffer.put(10, 0.5f) // specular strength
            buffer.put(11, 32.0f) // shininess
            buffer.put(12, 0.0f) // padding
            
            // Unmap
            vkUnmapMemory(device, lightBufferMemory)
        }
    }
    
    /**
     * Create a shader module from a SPIR-V file
     */
    private fun createShaderModule(filename: String, stack: MemoryStack): Long {
        val shaderCode = loadShaderCode(filename)
        
        val createInfo = VkShaderModuleCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pCode(shaderCode)
        
        val pShaderModule = stack.mallocLong(1)
        if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
            throw RuntimeException("Failed to create shader module: $filename")
        }
        
        return pShaderModule.get(0)
    }
    
    /**
     * Load the shader code from a SPIR-V file
     */
    private fun loadShaderCode(filename: String): ByteBuffer {
        val classLoader = Thread.currentThread().contextClassLoader
        val inputStream = classLoader.getResourceAsStream(filename)
            ?: throw RuntimeException("Failed to load shader file: $filename")
        
        val bytes = inputStream.readBytes()
        val buffer = MemoryUtil.memAlloc(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        
        return buffer
    }
    
    /**
     * Set the view and projection matrices
     */
    fun setViewProjection(viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        this.viewMatrix.set(viewMatrix)
        this.projMatrix.set(projMatrix)
    }
    
    /**
     * Update the camera position for lighting calculations
     */
    fun setCameraPosition(position: Vector3f) {
        MemoryStack.stackPush().use { stack ->
            // Map memory
            val data = stack.mallocPointer(1)
            vkMapMemory(device, lightBufferMemory, 6 * 4, 3 * 4, 0, data)
            
            // Update view position
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memFloatBuffer(mappedMemory, 3)
            buffer.put(0, position.x)
            buffer.put(1, position.y)
            buffer.put(2, position.z)
            
            // Unmap
            vkUnmapMemory(device, lightBufferMemory)
        }
    }
    
    /**
     * Add a chunk to be rendered
     */
    fun addChunk(chunk: Chunk) {
        val chunkKey = getChunkKey(chunk.position.x, chunk.position.y)
        
        // Check if we already have a mesh for this chunk
        if (chunkMeshes.containsKey(chunkKey) && !chunk.isDirty()) {
            return // Chunk mesh already exists and is up to date
        }
        
        // Build the mesh data for this chunk (execute synchronously for simplicity)
        val meshData = chunkMeshBuilder.generateMeshData(chunk)
        
        // Create a chunk mesh if needed or update existing one
        if (chunkMeshes.containsKey(chunkKey)) {
            chunkMeshes[chunkKey]?.update(meshData, device, commandPool, graphicsQueue)
        } else {
            val chunkMesh = ChunkMesh(device, physicalDevice, commandPool, graphicsQueue)
            chunkMesh.init(meshData)
            chunkMeshes[chunkKey] = chunkMesh
        }
        
        // Mark chunk as clean
        chunk.markClean()
    }
    
    /**
     * Remove a chunk from rendering
     */
    fun removeChunk(chunkX: Int, chunkZ: Int) {
        val chunkKey = getChunkKey(chunkX, chunkZ)
        chunkMeshes.remove(chunkKey)?.cleanup()
    }
    
    /**
     * Render all visible chunks
     */
    fun render(commandBuffer: VkCommandBuffer) {
        if (chunkMeshes.isEmpty()) {
            return // No chunks to render
        }
        
        // Update uniform buffer with latest matrices
        updateUniformBuffer()
        
        // Bind the pipeline
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)
        
        // Bind uniform descriptors
        val descriptorSets = MemoryStack.stackPush().use { stack ->
            val sets = stack.mallocLong(3)
            sets.put(0, uniformDescriptorSet)
            sets.put(1, textureDescriptorSets!![0]) // Diffuse texture
            sets.put(2, lightDescriptorSet)
            sets.flip()
            sets
        }
        
        vkCmdBindDescriptorSets(
            commandBuffer,
            VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipelineLayout,
            0,
            descriptorSets,
            null
        )
        
        // Render each chunk
        for (chunkMesh in chunkMeshes.values) {
            // If the chunk has no vertices, skip rendering
            if (chunkMesh.isEmpty()) continue
            
            // Bind vertex and index buffers
            val vertexBuffers = MemoryStack.stackPush().use { stack ->
                stack.longs(chunkMesh.getVertexBuffer())
            }
            
            val offsets = MemoryStack.stackPush().use { stack ->
                stack.longs(0)
            }
            
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets)
            vkCmdBindIndexBuffer(commandBuffer, chunkMesh.getIndexBuffer(), 0, VK_INDEX_TYPE_UINT32)
            
            // Draw
            vkCmdDrawIndexed(commandBuffer, chunkMesh.getIndexCount(), 1, 0, 0, 0)
        }
    }
    
    /**
     * Update the uniform buffer with current matrices
     */
    private fun updateUniformBuffer() {
        MemoryStack.stackPush().use { stack ->
            // Calculate model matrix (identity for now)
            val modelMatrix = Matrix4f()
            
            // Map memory
            val data = stack.mallocPointer(1)
            vkMapMemory(device, uniformBufferMemory, 0, 3 * 16 * 4, 0, data)
            
            // Write matrices
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memFloatBuffer(mappedMemory, 3 * 16)
            
            // Model matrix (first 16 floats)
            modelMatrix.get(0, buffer)
            
            // View matrix (next 16 floats)
            viewMatrix.get(16, buffer)
            
            // Projection matrix (next 16 floats)
            projMatrix.get(32, buffer)
            
            // Unmap
            vkUnmapMemory(device, uniformBufferMemory)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        // Clean up chunk meshes
        for (mesh in chunkMeshes.values) {
            mesh.cleanup()
        }
        chunkMeshes.clear()
        
        // Clean up the mesh builder
        chunkMeshBuilder.cleanup()
        
        // Clean up uniform buffers
        vkDestroyBuffer(device, uniformBuffer, null)
        vkFreeMemory(device, uniformBufferMemory, null)
        
        vkDestroyBuffer(device, lightBuffer, null)
        vkFreeMemory(device, lightBufferMemory, null)
        
        // Clean up pipeline resources
        vkDestroyPipeline(device, pipeline, null)
        vkDestroyPipelineLayout(device, pipelineLayout, null)
        
        // Clean up descriptor resources
        vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null)
        vkDestroyDescriptorPool(device, descriptorPool, null)
        
        // Clean up texture resources
        TextureAtlasLoader.cleanup(device)
    }
    
    /**
     * Generate a unique key for a chunk position
     */
    private fun getChunkKey(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}