package it.filippocavallari.cubicworld.renderer

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Represents a 3D mesh with vertices, indices, and GPU buffers.
 * Handles the creation, upload, and rendering of mesh data.
 */
class Mesh(
    val positions: FloatArray, 
    val normals: FloatArray? = null,
    val texCoords: FloatArray? = null,
    val indices: IntArray? = null
) {
    private var vertexBuffer: VulkanBuffer? = null
    private var indexBuffer: VulkanBuffer? = null
    private var vertexCount: Int = positions.size / 3
    private var indexCount: Int = indices?.size ?: 0
    
    /**
     * Uploads the mesh data to GPU buffers.
     * 
     * @param device Vulkan logical device
     * @param physicalDevice Vulkan physical device
     * @param commandPool Command pool for transfer operations
     * @param queue Queue for transfer operations
     */
    fun uploadToGPU(device: VkDevice, physicalDevice: VkPhysicalDevice, commandPool: Long, queue: VkQueue) {
        // Create and upload vertex buffer
        val vertexBufferSize = calcVertexBufferSize()
        val vertexBufferData = createVertexBufferData(vertexBufferSize)
        
        try {
            vertexBuffer = VulkanBuffer(
                device,
                physicalDevice,
                vertexBufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            
            // Upload vertex data to GPU
            vertexBuffer!!.uploadData(device, commandPool, queue, vertexBufferData)
            
            // Create and upload index buffer if indices are provided
            if (indices != null) {
                val indexBufferSize = indices.size * Int.SIZE_BYTES
                val indexBufferData = MemoryUtil.memAlloc(indexBufferSize)
                
                try {
                    // Fill index buffer
                    indices.forEach { indexBufferData.putInt(it) }
                    indexBufferData.flip()
                    
                    indexBuffer = VulkanBuffer(
                        device,
                        physicalDevice,
                        indexBufferSize.toLong(),
                        VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    )
                    
                    // Upload index data to GPU
                    indexBuffer!!.uploadData(device, commandPool, queue, indexBufferData)
                } finally {
                    MemoryUtil.memFree(indexBufferData)
                }
            }
            
        } finally {
            MemoryUtil.memFree(vertexBufferData)
        }
    }
    
    /**
     * Calculates the size of the vertex buffer.
     * 
     * @return The size of the vertex buffer in bytes
     */
    private fun calcVertexBufferSize(): Int {
        var size = positions.size * Float.SIZE_BYTES
        
        if (normals != null) {
            size += normals.size * Float.SIZE_BYTES
        }
        
        if (texCoords != null) {
            size += texCoords.size * Float.SIZE_BYTES
        }
        
        return size
    }
    
    /**
     * Creates the vertex buffer data.
     * 
     * @param bufferSize The size of the buffer in bytes
     * @return The vertex buffer data
     */
    private fun createVertexBufferData(bufferSize: Int): ByteBuffer {
        val buffer = MemoryUtil.memAlloc(bufferSize)
        
        // Add positions
        for (i in positions.indices) {
            buffer.putFloat(positions[i])
        }
        
        // Add normals if available
        if (normals != null) {
            for (i in normals.indices) {
                buffer.putFloat(normals[i])
            }
        }
        
        // Add texture coordinates if available
        if (texCoords != null) {
            for (i in texCoords.indices) {
                buffer.putFloat(texCoords[i])
            }
        }
        
        buffer.flip()
        return buffer
    }
    
    /**
     * Binds the mesh for rendering.
     * 
     * @param commandBuffer Command buffer to record binding commands
     */
    fun bind(commandBuffer: VkCommandBuffer) {
        MemoryStack.stackPush().use { stack ->
            val vertexBuffers = stack.mallocLong(1)
            vertexBuffers.put(0, vertexBuffer!!.getBuffer())
            
            val offsets = stack.mallocLong(1)
            offsets.put(0, 0)
            
            // Bind vertex buffer
            vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets)
            
            // Bind index buffer if available
            if (indexBuffer != null) {
                vkCmdBindIndexBuffer(commandBuffer, indexBuffer!!.getBuffer(), 0, VK_INDEX_TYPE_UINT32)
            }
        }
    }
    
    /**
     * Draws the mesh.
     * 
     * @param commandBuffer Command buffer to record draw commands
     */
    fun draw(commandBuffer: VkCommandBuffer) {
        if (indexBuffer != null) {
            vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0)
        } else {
            vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0)
        }
    }
    
    /**
     * Cleans up resources.
     * 
     * @param device Vulkan logical device
     */
    fun cleanup(device: VkDevice) {
        vertexBuffer?.cleanup(device)
        indexBuffer?.cleanup(device)
    }
}