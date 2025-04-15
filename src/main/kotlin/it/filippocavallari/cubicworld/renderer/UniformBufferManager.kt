package it.filippocavallari.cubicworld.renderer

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkWriteDescriptorSet
import java.nio.LongBuffer

/**
 * Manages uniform buffers and descriptor sets for shader uniforms.
 */
class UniformBufferManager(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice
) {
    
    // Uniform buffers
    private val uniformBuffers = mutableMapOf<String, VulkanBuffer>()
    
    // Descriptor pool and sets
    private var descriptorPool = VK_NULL_HANDLE
    private val descriptorSets = mutableMapOf<String, Long>()
    private val descriptorSetLayouts = mutableMapOf<String, Long>()
    
    /**
     * Creates a uniform buffer with the specified name and size.
     * 
     * @param name The name of the uniform buffer
     * @param size The size of the uniform buffer in bytes
     */
    fun createUniformBuffer(name: String, size: Long) {
        // Create the uniform buffer
        val uniformBuffer = VulkanBuffer(
            device,
            physicalDevice,
            size,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        
        uniformBuffers[name] = uniformBuffer
    }
    
    /**
     * Updates a uniform buffer with new data.
     * 
     * @param name The name of the uniform buffer
     * @param data The data to update the buffer with
     * @param offset The offset into the buffer in bytes
     * @param size The size of the data in bytes
     */
    fun updateUniformBuffer(name: String, data: java.nio.ByteBuffer, offset: Long = 0, size: Long = data.remaining().toLong()) {
        val uniformBuffer = uniformBuffers[name]
            ?: throw IllegalArgumentException("Uniform buffer not found: $name")
        
        MemoryStack.stackPush().use { stack ->
            val memory = stack.mallocPointer(1)
            vkMapMemory(device, uniformBuffer.getMemory(), offset, size, 0, memory)
            
            val dst = memory.getByteBuffer(0, size.toInt())
            dst.put(data)
            data.flip() // Reset for future use
            
            vkUnmapMemory(device, uniformBuffer.getMemory())
        }
    }
    
    /**
     * Creates a descriptor set layout.
     * 
     * @param name The name of the descriptor set layout
     * @param bindings The descriptor set layout bindings
     */
    fun createDescriptorSetLayout(name: String, bindings: List<DescriptorSetLayoutBinding>) {
        MemoryStack.stackPush().use { stack ->
            val layoutBindings = VkDescriptorSetLayoutBinding.calloc(bindings.size, stack)
            
            for (i in bindings.indices) {
                val binding = bindings[i]
                layoutBindings.get(i)
                    .binding(binding.binding)
                    .descriptorType(binding.descriptorType)
                    .descriptorCount(binding.descriptorCount)
                    .stageFlags(binding.stageFlags)
            }
            
            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(layoutBindings)
            
            val pSetLayout = stack.mallocLong(1)
            if (vkCreateDescriptorSetLayout(device, layoutInfo, null, pSetLayout) != VK_SUCCESS) {
                throw RuntimeException("Failed to create descriptor set layout")
            }
            
            descriptorSetLayouts[name] = pSetLayout.get(0)
        }
    }
    
    /**
     * Creates a descriptor pool.
     * 
     * @param maxSets The maximum number of descriptor sets to allocate
     * @param poolSizes The descriptor pool sizes
     */
    fun createDescriptorPool(maxSets: Int, poolSizes: List<DescriptorPoolSize>) {
        MemoryStack.stackPush().use { stack ->
            val typeCounts = VkDescriptorPoolSize.calloc(poolSizes.size, stack)
            
            for (i in poolSizes.indices) {
                val poolSize = poolSizes[i]
                typeCounts.get(i)
                    .type(poolSize.type)
                    .descriptorCount(poolSize.descriptorCount)
            }
            
            val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(maxSets)
                .pPoolSizes(typeCounts)
            
            val pDescriptorPool = stack.mallocLong(1)
            if (vkCreateDescriptorPool(device, poolInfo, null, pDescriptorPool) != VK_SUCCESS) {
                throw RuntimeException("Failed to create descriptor pool")
            }
            
            descriptorPool = pDescriptorPool.get(0)
        }
    }
    
    /**
     * Allocates a descriptor set.
     * 
     * @param name The name of the descriptor set
     * @param layoutName The name of the descriptor set layout to use
     */
    fun allocateDescriptorSet(name: String, layoutName: String) {
        val layout = descriptorSetLayouts[layoutName]
            ?: throw IllegalArgumentException("Descriptor set layout not found: $layoutName")
        
        MemoryStack.stackPush().use { stack ->
            val layouts = stack.longs(layout)
            
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts)
            
            val pDescriptorSet = stack.mallocLong(1)
            if (vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate descriptor set")
            }
            
            descriptorSets[name] = pDescriptorSet.get(0)
        }
    }
    
    /**
     * Updates a descriptor set with a uniform buffer.
     * 
     * @param setName The name of the descriptor set
     * @param binding The binding index
     * @param bufferName The name of the uniform buffer
     * @param offset The offset into the buffer in bytes
     * @param range The size of the data in bytes
     */
    fun updateDescriptorSet(
        setName: String,
        binding: Int,
        bufferName: String,
        offset: Long = 0,
        range: Long = VK_WHOLE_SIZE
    ) {
        val descriptorSet = descriptorSets[setName]
            ?: throw IllegalArgumentException("Descriptor set not found: $setName")
        
        val uniformBuffer = uniformBuffers[bufferName]
            ?: throw IllegalArgumentException("Uniform buffer not found: $bufferName")
        
        MemoryStack.stackPush().use { stack ->
            val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(uniformBuffer.getBuffer())
                .offset(offset)
                .range(range)
            
            val descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(binding)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(bufferInfo)
            
            vkUpdateDescriptorSets(device, descriptorWrite, null)
        }
    }
    
    /**
     * Binds a descriptor set for rendering.
     * 
     * @param commandBuffer The command buffer to record the bind command to
     * @param pipelineLayout The pipeline layout to bind to
     * @param setName The name of the descriptor set to bind
     * @param setIndex The descriptor set index to bind to
     */
    fun bindDescriptorSet(
        commandBuffer: VkCommandBuffer,
        pipelineLayout: Long,
        setName: String,
        setIndex: Int = 0
    ) {
        val descriptorSet = descriptorSets[setName]
            ?: throw IllegalArgumentException("Descriptor set not found: $setName")
        
        MemoryStack.stackPush().use { stack ->
            val descriptorSets = stack.longs(descriptorSet)
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineLayout,
                setIndex,
                descriptorSets,
                null
            )
        }
    }
    
    /**
     * Gets a descriptor set layout.
     * 
     * @param name The name of the descriptor set layout
     * @return The descriptor set layout handle, or VK_NULL_HANDLE if not found
     */
    fun getDescriptorSetLayout(name: String): Long {
        return descriptorSetLayouts[name] ?: VK_NULL_HANDLE
    }
    
    /**
     * Gets all descriptor set layouts as a LongBuffer.
     * 
     * @param stack The memory stack to use for allocation
     * @return A LongBuffer containing all descriptor set layouts
     */
    fun getDescriptorSetLayouts(stack: MemoryStack): LongBuffer {
        val layouts = stack.mallocLong(descriptorSetLayouts.size)
        
        var i = 0
        for (layout in descriptorSetLayouts.values) {
            layouts.put(i, layout)
            i++
        }
        
        return layouts.flip()
    }
    
    /**
     * Cleans up resources.
     */
    fun cleanup() {
        // Clean up uniform buffers
        for (uniformBuffer in uniformBuffers.values) {
            uniformBuffer.cleanup(device)
        }
        uniformBuffers.clear()
        
        // Clean up descriptor set layouts
        for (layout in descriptorSetLayouts.values) {
            vkDestroyDescriptorSetLayout(device, layout, null)
        }
        descriptorSetLayouts.clear()
        
        // Clean up descriptor pool (also frees all descriptor sets)
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null)
            descriptorPool = VK_NULL_HANDLE
        }
        
        descriptorSets.clear()
    }
    
    /**
     * Descriptor set layout binding configuration.
     */
    data class DescriptorSetLayoutBinding(
        val binding: Int,
        val descriptorType: Int,
        val descriptorCount: Int = 1,
        val stageFlags: Int = VK_SHADER_STAGE_VERTEX_BIT or VK_SHADER_STAGE_FRAGMENT_BIT
    )
    
    /**
     * Descriptor pool size configuration.
     */
    data class DescriptorPoolSize(
        val type: Int,
        val descriptorCount: Int
    )
}