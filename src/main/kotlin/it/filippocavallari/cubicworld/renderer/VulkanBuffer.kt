package it.filippocavallari.cubicworld.renderer

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer

/**
 * Represents a Vulkan buffer with its associated memory.
 * Handles buffer creation, memory allocation, and data transfer.
 */
class VulkanBuffer(
    device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    size: Long,
    usage: Int,
    properties: Int
) {
    private var buffer: Long = 0
    private var memory: Long = 0
    private var size: Long = 0
    
    init {
        createBuffer(device, physicalDevice, size, usage, properties)
    }
    
    /**
     * Creates a Vulkan buffer and allocates memory for it.
     * 
     * @param device Vulkan logical device
     * @param physicalDevice Vulkan physical device
     * @param size Size of the buffer in bytes
     * @param usage Buffer usage flags
     * @param properties Memory property flags
     */
    private fun createBuffer(
        device: VkDevice,
        physicalDevice: VkPhysicalDevice,
        size: Long,
        usage: Int,
        properties: Int
    ) {
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
            buffer = pBuffer.get(0)
            
            // Get memory requirements
            val memRequirements = VkMemoryRequirements.calloc(stack)
            vkGetBufferMemoryRequirements(device, buffer, memRequirements)
            
            // Allocate memory
            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        physicalDevice,
                        memRequirements.memoryTypeBits(),
                        properties
                    )
                )
            
            val pMemory = stack.mallocLong(1)
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate buffer memory")
            }
            memory = pMemory.get(0)
            
            // Bind memory to buffer
            vkBindBufferMemory(device, buffer, memory, 0)
            
            this.size = size
        }
    }
    
    /**
     * Uploads data to the buffer.
     * 
     * @param device Vulkan logical device
     * @param commandPool Command pool to allocate command buffer from
     * @param queue Queue to submit commands to
     * @param data Data to upload
     */
    fun uploadData(device: VkDevice, commandPool: Long, queue: VkQueue, data: ByteBuffer) {
        if (data.remaining() > size) {
            throw IllegalArgumentException("Data size exceeds buffer size")
        }
        
        // Create staging buffer
        val stagingBuffer = createStagingBuffer(device, data.remaining().toLong())
        
        try {
            // Copy data to staging buffer
            MemoryStack.stackPush().use { stack ->
                val pData = stack.mallocPointer(1)
                vkMapMemory(device, stagingBuffer.memory, 0, data.remaining().toLong(), 0, pData)
                val targetBuffer = pData.getByteBuffer(0, data.remaining())
                targetBuffer.put(data)
                data.flip() // Reset position to read again in the future
                vkUnmapMemory(device, stagingBuffer.memory)
            }
            
            // Copy from staging buffer to device buffer
            copyBuffer(device, commandPool, queue, stagingBuffer.buffer, buffer, data.remaining().toLong())
        } finally {
            // Clean up staging buffer
            vkDestroyBuffer(device, stagingBuffer.buffer, null)
            vkFreeMemory(device, stagingBuffer.memory, null)
        }
    }
    
    /**
     * Creates a staging buffer for data transfer.
     * 
     * @param device Vulkan logical device
     * @param physicalDevice Vulkan physical device
     * @param size Size of the buffer in bytes
     * @return A tuple of buffer handle and memory handle
     */
    private fun createStagingBuffer(
        device: VkDevice,
        size: Long
    ): StagingBuffer {
        MemoryStack.stackPush().use { stack ->
            // Create buffer
            val bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            
            val pBuffer = stack.mallocLong(1)
            if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                throw RuntimeException("Failed to create staging buffer")
            }
            val stagingBuffer = pBuffer.get(0)
            
            // Get memory requirements
            val memRequirements = VkMemoryRequirements.calloc(stack)
            vkGetBufferMemoryRequirements(device, stagingBuffer, memRequirements)
            
            // Allocate memory
            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        this.physicalDevice,
                        memRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                    )
                )
            
            val pMemory = stack.mallocLong(1)
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate staging buffer memory")
            }
            val stagingMemory = pMemory.get(0)
            
            // Bind memory to buffer
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)
            
            return StagingBuffer(stagingBuffer, stagingMemory)
        }
    }
    
    /**
     * Copies data from one buffer to another.
     * 
     * @param device Vulkan logical device
     * @param commandPool Command pool to allocate command buffer from
     * @param queue Queue to submit commands to
     * @param srcBuffer Source buffer
     * @param dstBuffer Destination buffer
     * @param size Size of data to copy in bytes
     */
    private fun copyBuffer(
        device: VkDevice,
        commandPool: Long,
        queue: VkQueue,
        srcBuffer: Long,
        dstBuffer: Long,
        size: Long
    ) {
        MemoryStack.stackPush().use { stack ->
            // Allocate command buffer
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)
            
            val pCommandBuffer = stack.mallocPointer(1)
            vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer)
            
            // Get the command buffer as VkCommandBuffer
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
            
            // Submit command buffer
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(pCommandBuffer.get(0)))
            
            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(queue)
            
            // Free command buffer
            vkFreeCommandBuffers(device, commandPool, pCommandBuffer)
        }
    }
    
    /**
     * Finds a suitable memory type for the buffer.
     * 
     * @param physicalDevice Vulkan physical device
     * @param typeFilter Bit field of suitable memory types
     * @param properties Required memory properties
     * @return Index of a suitable memory type
     */
    private fun findMemoryType(
        physicalDevice: VkPhysicalDevice,
        typeFilter: Int,
        properties: Int
    ): Int {
        MemoryStack.stackPush().use { stack ->
            val memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack)
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties)
            
            for (i in 0 until memProperties.memoryTypeCount()) {
                if ((typeFilter and (1 shl i)) != 0 &&
                    (memProperties.memoryTypes(i).propertyFlags() and properties) == properties) {
                    return i
                }
            }
            
            throw RuntimeException("Failed to find suitable memory type")
        }
    }
    
    /**
     * Gets the buffer handle.
     * 
     * @return The buffer handle
     */
    fun getBuffer(): Long {
        return buffer
    }
    
    /**
     * Gets the memory handle.
     * 
     * @return The memory handle
     */
    fun getMemory(): Long {
        return memory
    }
    
    /**
     * Gets the buffer size.
     * 
     * @return The buffer size in bytes
     */
    fun getSize(): Long {
        return size
    }
    
    /**
     * Cleans up resources.
     * 
     * @param device Vulkan logical device
     */
    fun cleanup(device: VkDevice) {
        vkDestroyBuffer(device, buffer, null)
        vkFreeMemory(device, memory, null)
    }
    
    /**
     * Helper class for staging buffer operations.
     */
    private data class StagingBuffer(val buffer: Long, val memory: Long)
}