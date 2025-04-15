package it.filippocavallari.cubicworld.renderer

import it.filippocavallari.cubicworld.utils.VulkanUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Represents a mesh for a chunk in the Vulkan rendering pipeline.
 * Manages vertex and index buffers for solid and transparent geometry.
 */
class ChunkMesh(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    private val commandPool: Long,
    private val graphicsQueue: VkQueue
) {
    // Solid geometry buffers
    private var vertexBuffer: Long = VK_NULL_HANDLE
    private var vertexBufferMemory: Long = VK_NULL_HANDLE
    private var indexBuffer: Long = VK_NULL_HANDLE
    private var indexBufferMemory: Long = VK_NULL_HANDLE
    private var indexCount: Int = 0
    
    // Transparent geometry buffers
    private var transVertexBuffer: Long = VK_NULL_HANDLE
    private var transVertexBufferMemory: Long = VK_NULL_HANDLE
    private var transIndexBuffer: Long = VK_NULL_HANDLE
    private var transIndexBufferMemory: Long = VK_NULL_HANDLE
    private var transIndexCount: Int = 0
    
    // Flag to check if mesh is empty
    private var empty: Boolean = true
    
    /**
     * Initialize the mesh with data
     */
    fun init(meshData: ChunkMeshBuilder.ChunkMeshData) {
        // Check if we have any data to render
        if (meshData.isEmpty) {
            empty = true
            return
        }
        
        empty = false
        
        // Create solid geometry buffers
        if (meshData.solidIndices.isNotEmpty()) {
            createVertexBuffer(meshData.solidVertices, meshData.solidNormals, meshData.solidUVs)
            createIndexBuffer(meshData.solidIndices)
            indexCount = meshData.solidIndices.size
        }
        
        // Create transparent geometry buffers
        if (meshData.transparentIndices.isNotEmpty()) {
            createTransparentVertexBuffer(meshData.transparentVertices, meshData.transparentNormals, meshData.transparentUVs)
            createTransparentIndexBuffer(meshData.transparentIndices)
            transIndexCount = meshData.transparentIndices.size
        }
    }
    
    /**
     * Update the mesh with new data
     */
    fun update(meshData: ChunkMeshBuilder.ChunkMeshData, device: VkDevice, commandPool: Long, graphicsQueue: VkQueue) {
        // Clean up existing buffers
        cleanup()
        
        // Initialize with new data
        init(meshData)
    }
    
    /**
     * Create vertex buffer for solid geometry
     */
    private fun createVertexBuffer(positions: FloatArray, normals: FloatArray, texCoords: FloatArray) {
        MemoryStack.stackPush().use { stack ->
            // Calculate buffer size
            val vertexCount = positions.size / 3
            val bufferSize = vertexCount * 8 * 4 // 8 floats per vertex (3 pos + 3 normal + 2 texcoord)
            
            // Create staging buffer
            val stagingBuffer = createBuffer(
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            
            // Map staging buffer and copy data
            val data = stack.mallocPointer(1)
            vkMapMemory(device, stagingBuffer.second, 0, bufferSize.toLong(), 0, data)
            
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memFloatBuffer(mappedMemory, vertexCount * 8)
            
            // Interleave data: pos1, normal1, uv1, pos2, normal2, uv2, ...
            for (i in 0 until vertexCount) {
                // Position
                buffer.put(positions[i * 3])
                buffer.put(positions[i * 3 + 1])
                buffer.put(positions[i * 3 + 2])
                
                // Normal
                buffer.put(normals[i * 3])
                buffer.put(normals[i * 3 + 1])
                buffer.put(normals[i * 3 + 2])
                
                // Texture coordinates
                buffer.put(texCoords[i * 2])
                buffer.put(texCoords[i * 2 + 1])
            }
            
            vkUnmapMemory(device, stagingBuffer.second)
            
            // Create device local buffer
            val deviceLocalBuffer = createBuffer(
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            
            vertexBuffer = deviceLocalBuffer.first
            vertexBufferMemory = deviceLocalBuffer.second
            
            // Copy from staging to device local
            copyBuffer(stagingBuffer.first, vertexBuffer, bufferSize.toLong())
            
            // Cleanup staging buffer
            vkDestroyBuffer(device, stagingBuffer.first, null)
            vkFreeMemory(device, stagingBuffer.second, null)
        }
    }
    
    /**
     * Create index buffer for solid geometry
     */
    private fun createIndexBuffer(indices: IntArray) {
        MemoryStack.stackPush().use { stack ->
            // Calculate buffer size
            val bufferSize = indices.size * 4L
            
            // Create staging buffer
            val stagingBuffer = createBuffer(
                bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            
            // Map staging buffer and copy data
            val data = stack.mallocPointer(1)
            vkMapMemory(device, stagingBuffer.second, 0, bufferSize, 0, data)
            
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memIntBuffer(mappedMemory, indices.size)
            buffer.put(indices)
            buffer.flip()
            
            vkUnmapMemory(device, stagingBuffer.second)
            
            // Create device local buffer
            val deviceLocalBuffer = createBuffer(
                bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            
            indexBuffer = deviceLocalBuffer.first
            indexBufferMemory = deviceLocalBuffer.second
            
            // Copy from staging to device local
            copyBuffer(stagingBuffer.first, indexBuffer, bufferSize)
            
            // Cleanup staging buffer
            vkDestroyBuffer(device, stagingBuffer.first, null)
            vkFreeMemory(device, stagingBuffer.second, null)
        }
    }
    
    /**
     * Create vertex buffer for transparent geometry
     */
    private fun createTransparentVertexBuffer(positions: FloatArray, normals: FloatArray, texCoords: FloatArray) {
        MemoryStack.stackPush().use { stack ->
            // Calculate buffer size
            val vertexCount = positions.size / 3
            val bufferSize = vertexCount * 8 * 4 // 8 floats per vertex (3 pos + 3 normal + 2 texcoord)
            
            // Create staging buffer
            val stagingBuffer = createBuffer(
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            
            // Map staging buffer and copy data
            val data = stack.mallocPointer(1)
            vkMapMemory(device, stagingBuffer.second, 0, bufferSize.toLong(), 0, data)
            
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memFloatBuffer(mappedMemory, vertexCount * 8)
            
            // Interleave data: pos1, normal1, uv1, pos2, normal2, uv2, ...
            for (i in 0 until vertexCount) {
                // Position
                buffer.put(positions[i * 3])
                buffer.put(positions[i * 3 + 1])
                buffer.put(positions[i * 3 + 2])
                
                // Normal
                buffer.put(normals[i * 3])
                buffer.put(normals[i * 3 + 1])
                buffer.put(normals[i * 3 + 2])
                
                // Texture coordinates
                buffer.put(texCoords[i * 2])
                buffer.put(texCoords[i * 2 + 1])
            }
            
            vkUnmapMemory(device, stagingBuffer.second)
            
            // Create device local buffer
            val deviceLocalBuffer = createBuffer(
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            
            transVertexBuffer = deviceLocalBuffer.first
            transVertexBufferMemory = deviceLocalBuffer.second
            
            // Copy from staging to device local
            copyBuffer(stagingBuffer.first, transVertexBuffer, bufferSize.toLong())
            
            // Cleanup staging buffer
            vkDestroyBuffer(device, stagingBuffer.first, null)
            vkFreeMemory(device, stagingBuffer.second, null)
        }
    }
    
    /**
     * Create index buffer for transparent geometry
     */
    private fun createTransparentIndexBuffer(indices: IntArray) {
        MemoryStack.stackPush().use { stack ->
            // Calculate buffer size
            val bufferSize = indices.size * 4L
            
            // Create staging buffer
            val stagingBuffer = createBuffer(
                bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            
            // Map staging buffer and copy data
            val data = stack.mallocPointer(1)
            vkMapMemory(device, stagingBuffer.second, 0, bufferSize, 0, data)
            
            val mappedMemory = data.get(0)
            val buffer = MemoryUtil.memIntBuffer(mappedMemory, indices.size)
            buffer.put(indices)
            buffer.flip()
            
            vkUnmapMemory(device, stagingBuffer.second)
            
            // Create device local buffer
            val deviceLocalBuffer = createBuffer(
                bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            
            transIndexBuffer = deviceLocalBuffer.first
            transIndexBufferMemory = deviceLocalBuffer.second
            
            // Copy from staging to device local
            copyBuffer(stagingBuffer.first, transIndexBuffer, bufferSize)
            
            // Cleanup staging buffer
            vkDestroyBuffer(device, stagingBuffer.first, null)
            vkFreeMemory(device, stagingBuffer.second, null)
        }
    }
    
    /**
     * Create a Vulkan buffer
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
     * Copy data between buffers using a command buffer
     */
    private fun copyBuffer(srcBuffer: Long, dstBuffer: Long, size: Long) {
        MemoryStack.stackPush().use { stack ->
            // Create a command buffer for copy operation
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandPool(commandPool)
                .commandBufferCount(1)
            
            val pCommandBuffer = stack.mallocPointer(1)
            vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer)
            val commandBuffer = VkCommandBuffer(pCommandBuffer.get(0), device)
            
            // Begin command buffer
            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
            
            vkBeginCommandBuffer(commandBuffer, beginInfo)
            
            // Record copy command
            val copyRegion = VkBufferCopy.calloc(1, stack)
                .srcOffset(0)
                .dstOffset(0)
                .size(size)
            
            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)
            
            // End command buffer
            vkEndCommandBuffer(commandBuffer)
            
            // Submit to queue
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(pCommandBuffer)
            
            vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(graphicsQueue)
            
            // Free command buffer
            vkFreeCommandBuffers(device, commandPool, pCommandBuffer)
        }
    }
    
    /**
     * Get the vertex buffer handle
     */
    fun getVertexBuffer(): Long {
        return vertexBuffer
    }
    
    /**
     * Get the index buffer handle
     */
    fun getIndexBuffer(): Long {
        return indexBuffer
    }
    
    /**
     * Get the index count for drawing
     */
    fun getIndexCount(): Int {
        return indexCount
    }
    
    /**
     * Get the transparent vertex buffer handle
     */
    fun getTransparentVertexBuffer(): Long {
        return transVertexBuffer
    }
    
    /**
     * Get the transparent index buffer handle
     */
    fun getTransparentIndexBuffer(): Long {
        return transIndexBuffer
    }
    
    /**
     * Get the transparent index count for drawing
     */
    fun getTransparentIndexCount(): Int {
        return transIndexCount
    }
    
    /**
     * Check if the mesh is empty
     */
    fun isEmpty(): Boolean {
        return empty
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        // Clean up solid geometry buffers
        if (vertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, vertexBuffer, null)
            vertexBuffer = VK_NULL_HANDLE
        }
        
        if (vertexBufferMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, vertexBufferMemory, null)
            vertexBufferMemory = VK_NULL_HANDLE
        }
        
        if (indexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, indexBuffer, null)
            indexBuffer = VK_NULL_HANDLE
        }
        
        if (indexBufferMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, indexBufferMemory, null)
            indexBufferMemory = VK_NULL_HANDLE
        }
        
        // Clean up transparent geometry buffers
        if (transVertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, transVertexBuffer, null)
            transVertexBuffer = VK_NULL_HANDLE
        }
        
        if (transVertexBufferMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, transVertexBufferMemory, null)
            transVertexBufferMemory = VK_NULL_HANDLE
        }
        
        if (transIndexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, transIndexBuffer, null)
            transIndexBuffer = VK_NULL_HANDLE
        }
        
        if (transIndexBufferMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, transIndexBufferMemory, null)
            transIndexBufferMemory = VK_NULL_HANDLE
        }
        
        // Reset counters
        indexCount = 0
        transIndexCount = 0
        empty = true
    }
}