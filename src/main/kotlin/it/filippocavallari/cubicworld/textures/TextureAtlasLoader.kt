package it.filippocavallari.cubicworld.textures

import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.LongBuffer
import javax.imageio.ImageIO

/**
 * Utility class for loading texture atlases to GPU.
 * Provides implementations for both OpenGL and Vulkan.
 */
object TextureAtlasLoader {
    // Store the texture resources for cleanup
    private var textureResources: VulkanTextureResources? = null
    
    /**
     * Load and stitch textures from a directory
     *
     * @param textureDir Directory containing textures
     * @param outputDir Directory to save stitched textures
     * @param textureSize Size of each texture in atlas
     * @return The texture stitcher with loaded textures
     */
    fun loadAndStitchTextures(textureDir: String, outputDir: String, textureSize: Int = 128): TextureStitcher {
        val stitcher = TextureStitcher(textureDir)
        try {
            stitcher.build(textureSize)
            stitcher.saveAtlases(outputDir)
            println("Successfully loaded and stitched textures from $textureDir to $outputDir")
        } catch (e: Exception) {
            println("Warning: Error stitching textures: ${e.message}")
            e.printStackTrace()
        }
        return stitcher
    }
    
    /**
     * Legacy method for OpenGL compatibility.
     * 
     * @param stitcher The texture stitcher containing the atlases
     * @return An array of texture IDs [diffuseId, normalId, specularId]
     */
    fun loadAtlasesToGPU(stitcher: TextureStitcher): IntArray {
        println("Warning: Using stub texture loading for OpenGL. Consider using Vulkan implementation.")
        // Return placeholder mock IDs for compatibility
        return intArrayOf(1, 2, 3)
    }
    
    /**
     * Load the texture atlases to Vulkan.
     * 
     * @param stitcher The texture stitcher containing the atlases
     * @param renderPass The Vulkan render pass to use (used for descriptor set layout)
     * @param device The logical device to use (if null, will be obtained from renderer)
     * @param physicalDevice The physical device to use (if 0, will be obtained from renderer)
     * @param commandPool The command pool to use for transfers
     * @param graphicsQueue The graphics queue to use for transfers
     * @return An array of descriptor set handles for the texture atlases
     */
    fun loadAtlasesToVulkan(
        stitcher: TextureStitcher, 
        renderPass: Long,
        device: VkDevice,
        physicalDevice: Long,
        instance: VkInstance?,  // Make instance nullable
        commandPool: Long,
        graphicsQueue: VkQueue
    ): LongArray {
        try {
            println("Loading texture atlases to Vulkan...")
            
            // Check if we have valid Vulkan resources
            if (device == null || physicalDevice == 0L || commandPool == 0L || graphicsQueue == null) {
                println("Warning: Missing required Vulkan resources for texture loading")
                println("  device: ${if (device != null) "Available" else "NULL"}")
                println("  physicalDevice: ${if (physicalDevice != 0L) physicalDevice else "NULL"}")
                println("  commandPool: $commandPool")
                println("  graphicsQueue: ${if (graphicsQueue != null) "Available" else "NULL"}")
                
                // Return mock descriptors for development
                return longArrayOf(1001L, 1002L, 1003L)
            }
            
            // Get the atlas file paths
            val diffuseAtlasPath = "src/main/resources/atlas/diffuse_atlas.png"
            val normalAtlasPath = "src/main/resources/atlas/normal_atlas.png"
            val specularAtlasPath = "src/main/resources/atlas/specular_atlas.png"
            
            MemoryStack.stackPush().use { stack ->
                // Check if atlas files exist
                val diffuseFile = File(diffuseAtlasPath)
                val normalFile = File(normalAtlasPath)
                val specularFile = File(specularAtlasPath)
                
                if (!diffuseFile.exists() || !normalFile.exists() || !specularFile.exists()) {
                    println("Warning: One or more atlas files not found")
                    println("  diffuse: ${if (diffuseFile.exists()) "Found" else "Missing"}")
                    println("  normal: ${if (normalFile.exists()) "Found" else "Missing"}")
                    println("  specular: ${if (specularFile.exists()) "Found" else "Missing"}")
                    
                    // Return mock descriptors for development
                    return longArrayOf(1001L, 1002L, 1003L)
                }
                
                // Load atlas images
                val diffuseImage = loadImage(diffuseAtlasPath)
                val normalImage = loadImage(normalAtlasPath)
                val specularImage = loadImage(specularAtlasPath)
                
                // Get image dimensions
                val width = diffuseImage.width
                val height = diffuseImage.height
                
                println("Atlas dimensions: ${width}x${height}")
                
                // Convert images to byte buffers
                val diffuseBuffer = imageToByteBuffer(diffuseImage)
                val normalBuffer = imageToByteBuffer(normalImage)
                val specularBuffer = imageToByteBuffer(specularImage)
                
                try {
                    // Create texture images
                    val diffuseTextureImage = createTextureImage(device, physicalDevice, instance, commandPool, graphicsQueue, diffuseBuffer, width, height, stack)
                    val normalTextureImage = createTextureImage(device, physicalDevice, instance, commandPool, graphicsQueue, normalBuffer, width, height, stack)
                    val specularTextureImage = createTextureImage(device, physicalDevice, instance, commandPool, graphicsQueue, specularBuffer, width, height, stack)
                    
                    // Create image views
                    val diffuseImageView = createImageView(device, diffuseTextureImage.image, VK_FORMAT_R8G8B8A8_UNORM, stack)
                    val normalImageView = createImageView(device, normalTextureImage.image, VK_FORMAT_R8G8B8A8_UNORM, stack)
                    val specularImageView = createImageView(device, specularTextureImage.image, VK_FORMAT_R8G8B8A8_UNORM, stack)
                    
                    // Create samplers
                    val diffuseSampler = createSampler(device, stack)
                    val normalSampler = createSampler(device, stack)
                    val specularSampler = createSampler(device, stack)
                    
                    // Create descriptor set layout
                    val descriptorSetLayout = createDescriptorSetLayout(device, stack)
                    
                    // Create descriptor pool
                    val descriptorPool = createDescriptorPool(device, 3, stack)
                    
                    // Allocate descriptor sets
                    val descriptorSets = allocateDescriptorSets(device, descriptorPool, descriptorSetLayout, 3, stack)
                    
                    // Update descriptor sets
                    updateTextureDescriptorSet(device, descriptorSets.get(0), diffuseImageView, diffuseSampler, stack)
                    updateTextureDescriptorSet(device, descriptorSets.get(1), normalImageView, normalSampler, stack)
                    updateTextureDescriptorSet(device, descriptorSets.get(2), specularImageView, specularSampler, stack)
                    
                    // Store resources for cleanup
                    textureResources = VulkanTextureResources(
                        diffuseTextureImage.image, diffuseTextureImage.memory, diffuseImageView, diffuseSampler,
                        normalTextureImage.image, normalTextureImage.memory, normalImageView, normalSampler,
                        specularTextureImage.image, specularTextureImage.memory, specularImageView, specularSampler,
                        descriptorPool, descriptorSetLayout
                    )
                    
                    // Return descriptor set handles as LongArray
                    val result = LongArray(3)
                    result[0] = descriptorSets.get(0)
                    result[1] = descriptorSets.get(1)
                    result[2] = descriptorSets.get(2)
                    
                    println("Successfully loaded texture atlases to Vulkan")
                    return result
                    
                } finally {
                    // Free byte buffers
                    MemoryUtil.memFree(diffuseBuffer)
                    MemoryUtil.memFree(normalBuffer)
                    MemoryUtil.memFree(specularBuffer)
                }
            }
            
        } catch (e: Exception) {
            println("Error loading texture atlases to Vulkan: ${e.message}")
            e.printStackTrace()
            
            // Return mock descriptor sets as fallback
            return longArrayOf(1001L, 1002L, 1003L)
        }
    }
    
    /**
     * Load an image from file
     */
    private fun loadImage(filePath: String): BufferedImage {
        try {
            return ImageIO.read(File(filePath))
        } catch (e: Exception) {
            println("Error loading image from $filePath: ${e.message}")
            throw e
        }
    }
    
    /**
     * Convert a BufferedImage to a ByteBuffer.
     * 
     * @param image The image to convert
     * @return The ByteBuffer containing the image data
     */
    private fun imageToByteBuffer(image: BufferedImage): ByteBuffer {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        
        // Get RGB data
        image.getRGB(0, 0, width, height, pixels, 0, width)
        
        // Create ByteBuffer
        val buffer = MemoryUtil.memAlloc(width * height * 4)
        
        // Convert ARGB to RGBA for Vulkan
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
                buffer.put(((pixel shr 8) and 0xFF).toByte())  // G
                buffer.put((pixel and 0xFF).toByte())         // B
                buffer.put(((pixel shr 24) and 0xFF).toByte()) // A
            }
        }
        
        buffer.flip()
        return buffer
    }
    
    /**
     * Helper class to hold image and its memory
     */
    private class VulkanImage(val image: Long, val memory: Long)
    
    /**
     * Create a Vulkan texture image from pixel data
     */
    private fun createTextureImage(
        device: VkDevice,
        physicalDevice: Long,
        instance: VkInstance?,  // Make instance nullable
        commandPool: Long,
        graphicsQueue: VkQueue,
        pixels: ByteBuffer,
        width: Int,
        height: Int,
        stack: MemoryStack
    ): VulkanImage {
        val imageSize = width.toLong() * height.toLong() * 4L
        
        // Create a staging buffer
        val stagingBuffer = createBuffer(
            device,
            physicalDevice,
            instance,  // Pass the instance
            imageSize,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            stack
        )
        
        // Copy data to staging buffer
        val data = stack.mallocPointer(1)
        vkMapMemory(device, stagingBuffer.second, 0, imageSize, 0, data)
        
        // Copy pixel data to mapped memory - byte by byte to avoid ByteBuffer/long issues
        val mappedMemory = data.get(0)
        for (i in 0 until pixels.limit()) {
            MemoryUtil.memPutByte(mappedMemory + i, pixels.get(i))
        }
        
        vkUnmapMemory(device, stagingBuffer.second)
        
        // Create image
        val image = createImage(
            device,
            physicalDevice,
            instance,  // Pass the instance
            width,
            height,
            VK_FORMAT_R8G8B8A8_UNORM,
            VK_IMAGE_TILING_OPTIMAL,
            VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            stack
        )
        
        // Transition image layout for copy
        transitionImageLayout(
            device,
            commandPool,
            graphicsQueue,
            image.first,
            VK_FORMAT_R8G8B8A8_UNORM,
            VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            stack
        )
        
        // Copy buffer to image
        copyBufferToImage(
            device,
            commandPool,
            graphicsQueue,
            stagingBuffer.first,
            image.first,
            width,
            height,
            stack
        )
        
        // Transition to shader read optimal
        transitionImageLayout(
            device,
            commandPool,
            graphicsQueue,
            image.first,
            VK_FORMAT_R8G8B8A8_UNORM,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            stack
        )
        
        // Cleanup staging buffer
        vkDestroyBuffer(device, stagingBuffer.first, null)
        vkFreeMemory(device, stagingBuffer.second, null)
        
        return VulkanImage(image.first, image.second)
    }
    
    /**
     * Create a Vulkan buffer with memory
     */
    private fun createBuffer(
        device: VkDevice,
        physicalDevice: Long,
        instance: VkInstance?,  // Make instance nullable
        size: Long,
        usage: Int,
        properties: Int,
        stack: MemoryStack
    ): Pair<Long, Long> {
        // Create buffer
        val bufferInfo = VkBufferCreateInfo.callocStack(stack)
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
        val memReqs = VkMemoryRequirements.callocStack(stack)
        vkGetBufferMemoryRequirements(device, buffer, memReqs)
        
        // Allocate memory
        val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(findMemoryType(
                device,
                physicalDevice,
                instance,  // Pass the instance
                memReqs.memoryTypeBits(),
                properties,
                stack
            ))
        
        val pMemory = stack.mallocLong(1)
        if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
            throw RuntimeException("Failed to allocate buffer memory")
        }
        val bufferMemory = pMemory.get(0)
        
        // Bind memory to buffer
        vkBindBufferMemory(device, buffer, bufferMemory, 0)
        
        return Pair(buffer, bufferMemory)
    }
    
    /**
     * Find a suitable memory type
     */
    private fun findMemoryType(
        device: VkDevice,
        physicalDevice: Long,
        instance: VkInstance?,  // Make instance nullable
        typeFilter: Int,
        properties: Int,
        stack: MemoryStack
    ): Int {
        // If physicalDevice is 0 or invalid, return a safe default
        if (physicalDevice == 0L) {
            println("Warning: Invalid physical device handle (0)")
            return 0
        }
        
        try {
            // Use the provided instance parameter (with null check)
            val vkPhysicalDevice = if (instance != null) {
                VkPhysicalDevice(physicalDevice, instance)
            } else {
                // Fallback to null if instance is null
                VkPhysicalDevice(physicalDevice, null)
            }
            
            val memProperties = VkPhysicalDeviceMemoryProperties.callocStack(stack)
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, memProperties)
            
            // Find a memory type that matches both the type filter and the required properties
            for (i in 0 until memProperties.memoryTypeCount()) {
                val isTypeMatch = (typeFilter and (1 shl i)) != 0
                val hasProperties = (memProperties.memoryTypes(i).propertyFlags() and properties) == properties
                
                if (isTypeMatch && hasProperties) {
                    return i
                }
            }
            
            throw RuntimeException("Failed to find suitable memory type")
        } catch (e: Exception) {
            println("Error in findMemoryType: ${e.message}")
            e.printStackTrace()
            // Return a default memory type index as fallback
            return 0
        }
    }
    
    /**
     * Create a Vulkan image with memory
     */
    private fun createImage(
        device: VkDevice,
        physicalDevice: Long,
        instance: VkInstance?,  // Make instance nullable
        width: Int,
        height: Int,
        format: Int,
        tiling: Int,
        usage: Int,
        properties: Int,
        stack: MemoryStack
    ): Pair<Long, Long> {
        // Create image
        val imageInfo = VkImageCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(format)
            .extent { extent ->
                extent
                    .width(width)
                    .height(height)
                    .depth(1)
            }
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(tiling)
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
        
        val pImage = stack.mallocLong(1)
        if (vkCreateImage(device, imageInfo, null, pImage) != VK_SUCCESS) {
            throw RuntimeException("Failed to create image")
        }
        val image = pImage.get(0)
        
        // Get memory requirements
        val memReqs = VkMemoryRequirements.callocStack(stack)
        vkGetImageMemoryRequirements(device, image, memReqs)
        
        // Allocate memory
        val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(findMemoryType(
                device,
                physicalDevice,
                instance,  // Pass the instance
                memReqs.memoryTypeBits(),
                properties,
                stack
            ))
        
        val pMemory = stack.mallocLong(1)
        if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
            throw RuntimeException("Failed to allocate image memory")
        }
        val imageMemory = pMemory.get(0)
        
        // Bind memory to image
        vkBindImageMemory(device, image, imageMemory, 0)
        
        return Pair(image, imageMemory)
    }
    
    /**
     * Transition image layout
     */
    private fun transitionImageLayout(
        device: VkDevice,
        commandPool: Long,
        graphicsQueue: VkQueue,
        image: Long,
        format: Int,
        oldLayout: Int,
        newLayout: Int,
        stack: MemoryStack
    ) {
        val commandBuffer = beginSingleTimeCommands(device, commandPool, stack)
        
        val barrier = VkImageMemoryBarrier.callocStack(1, stack)
        barrier.get(0)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image)
            .subresourceRange { subresourceRange ->
                subresourceRange
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
        
        var sourceStage = 0
        var destinationStage = 0
        
        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            barrier.get(0)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            
            sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            barrier.get(0)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
            
            sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
        } else {
            throw IllegalArgumentException("Unsupported layout transition")
        }
        
        vkCmdPipelineBarrier(
            commandBuffer,
            sourceStage, destinationStage,
            0,
            null,
            null,
            barrier
        )
        
        endSingleTimeCommands(device, commandPool, graphicsQueue, commandBuffer, stack)
    }
    
    /**
     * Copy buffer to image
     */
    private fun copyBufferToImage(
        device: VkDevice,
        commandPool: Long,
        graphicsQueue: VkQueue,
        buffer: Long,
        image: Long,
        width: Int,
        height: Int,
        stack: MemoryStack
    ) {
        val commandBuffer = beginSingleTimeCommands(device, commandPool, stack)
        
        val region = VkBufferImageCopy.callocStack(1, stack)
        region.get(0)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0)
            .imageSubresource { subresource ->
                subresource
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
            .imageOffset { offset -> offset.x(0).y(0).z(0) }
            .imageExtent { extent ->
                extent
                    .width(width)
                    .height(height)
                    .depth(1)
            }
        
        vkCmdCopyBufferToImage(
            commandBuffer,
            buffer,
            image,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            region
        )
        
        endSingleTimeCommands(device, commandPool, graphicsQueue, commandBuffer, stack)
    }
    
    /**
     * Begin single time commands
     */
    private fun beginSingleTimeCommands(
        device: VkDevice,
        commandPool: Long,
        stack: MemoryStack
    ): VkCommandBuffer {
        val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandPool(commandPool)
            .commandBufferCount(1)
        
        val pCommandBuffer = stack.mallocPointer(1)
        vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer)
        val commandBuffer = VkCommandBuffer(pCommandBuffer.get(0), device)
        
        val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
        
        vkBeginCommandBuffer(commandBuffer, beginInfo)
        
        return commandBuffer
    }
    
    /**
     * End single time commands
     */
    private fun endSingleTimeCommands(
        device: VkDevice,
        commandPool: Long,
        graphicsQueue: VkQueue,
        commandBuffer: VkCommandBuffer,
        stack: MemoryStack
    ) {
        vkEndCommandBuffer(commandBuffer)
        
        val submitInfo = VkSubmitInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pCommandBuffers(stack.pointers(commandBuffer))
        
        vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE)
        vkQueueWaitIdle(graphicsQueue)
        
        vkFreeCommandBuffers(device, commandPool, stack.pointers(commandBuffer))
    }
    
    /**
     * Create an image view
     */
    private fun createImageView(
        device: VkDevice,
        image: Long,
        format: Int,
        stack: MemoryStack
    ): Long {
        val viewInfo = VkImageViewCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format)
            .components { components ->
                components
                    .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .a(VK_COMPONENT_SWIZZLE_IDENTITY)
            }
            .subresourceRange { subresourceRange ->
                subresourceRange
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
        
        val pImageView = stack.mallocLong(1)
        if (vkCreateImageView(device, viewInfo, null, pImageView) != VK_SUCCESS) {
            throw RuntimeException("Failed to create image view")
        }
        
        return pImageView.get(0)
    }
    
    /**
     * Create a sampler
     */
    private fun createSampler(
        device: VkDevice,
        stack: MemoryStack
    ): Long {
        val samplerInfo = VkSamplerCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .anisotropyEnable(true)
            .maxAnisotropy(16.0f)
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .unnormalizedCoordinates(false)
            .compareEnable(false)
            .compareOp(VK_COMPARE_OP_ALWAYS)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .mipLodBias(0.0f)
            .minLod(0.0f)
            .maxLod(0.0f)
        
        val pSampler = stack.mallocLong(1)
        if (vkCreateSampler(device, samplerInfo, null, pSampler) != VK_SUCCESS) {
            throw RuntimeException("Failed to create sampler")
        }
        
        return pSampler.get(0)
    }
    
    /**
     * Create a descriptor set layout
     */
    private fun createDescriptorSetLayout(
        device: VkDevice,
        stack: MemoryStack
    ): Long {
        val binding = VkDescriptorSetLayoutBinding.callocStack(1, stack)
        binding.get(0)
            .binding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
        
        val layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pBindings(binding)
        
        val pDescriptorSetLayout = stack.mallocLong(1)
        if (vkCreateDescriptorSetLayout(device, layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
            throw RuntimeException("Failed to create descriptor set layout")
        }
        
        return pDescriptorSetLayout.get(0)
    }
    
    /**
     * Create a descriptor pool
     */
    private fun createDescriptorPool(
        device: VkDevice,
        descriptorCount: Int,
        stack: MemoryStack
    ): Long {
        val poolSize = VkDescriptorPoolSize.callocStack(1, stack)
        poolSize.get(0)
            .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(descriptorCount)
        
        val poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .maxSets(descriptorCount)
            .pPoolSizes(poolSize)
        
        val pDescriptorPool = stack.mallocLong(1)
        if (vkCreateDescriptorPool(device, poolInfo, null, pDescriptorPool) != VK_SUCCESS) {
            throw RuntimeException("Failed to create descriptor pool")
        }
        
        return pDescriptorPool.get(0)
    }
    
    /**
     * Allocate descriptor sets
     */
    private fun allocateDescriptorSets(
        device: VkDevice,
        descriptorPool: Long,
        descriptorSetLayout: Long,
        count: Int,
        stack: MemoryStack
    ): LongBuffer {
        val layouts = stack.mallocLong(count)
        for (i in 0 until count) {
            layouts.put(i, descriptorSetLayout)
        }
        
        val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(layouts)
        
        val pDescriptorSets = stack.mallocLong(count)
        if (vkAllocateDescriptorSets(device, allocInfo, pDescriptorSets) != VK_SUCCESS) {
            throw RuntimeException("Failed to allocate descriptor sets")
        }
        
        return pDescriptorSets
    }
    
    /**
     * Update a texture descriptor set
     */
    private fun updateTextureDescriptorSet(
        device: VkDevice,
        descriptorSet: Long,
        imageView: Long,
        sampler: Long,
        stack: MemoryStack
    ) {
        val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
        imageInfo.get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(imageView)
            .sampler(sampler)
        
        val descriptorWrite = VkWriteDescriptorSet.callocStack(1, stack)
        descriptorWrite.get(0)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstSet(descriptorSet)
            .dstBinding(0)
            .dstArrayElement(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .pImageInfo(imageInfo)
        
        vkUpdateDescriptorSets(device, descriptorWrite, null)
    }
    
    /**
     * Clean up Vulkan texture resources
     */
    fun cleanup(device: VkDevice) {
        textureResources?.let { resources ->
            // Destroy samplers
            vkDestroySampler(device, resources.diffuseSampler, null)
            vkDestroySampler(device, resources.normalSampler, null)
            vkDestroySampler(device, resources.specularSampler, null)
            
            // Destroy image views
            vkDestroyImageView(device, resources.diffuseImageView, null)
            vkDestroyImageView(device, resources.normalImageView, null)
            vkDestroyImageView(device, resources.specularImageView, null)
            
            // Destroy images
            vkDestroyImage(device, resources.diffuseImage, null)
            vkDestroyImage(device, resources.normalImage, null)
            vkDestroyImage(device, resources.specularImage, null)
            
            // Free memory
            vkFreeMemory(device, resources.diffuseMemory, null)
            vkFreeMemory(device, resources.normalMemory, null)
            vkFreeMemory(device, resources.specularMemory, null)
            
            // Destroy descriptor set layout and pool
            vkDestroyDescriptorSetLayout(device, resources.descriptorSetLayout, null)
            vkDestroyDescriptorPool(device, resources.descriptorPool, null)
            
            // Clear the resources
            textureResources = null
        }
    }
    
    /**
     * Vulkan texture resources class
     */
    class VulkanTextureResources(
        val diffuseImage: Long,
        val diffuseMemory: Long,
        val diffuseImageView: Long,
        val diffuseSampler: Long,
        val normalImage: Long,
        val normalMemory: Long,
        val normalImageView: Long,
        val normalSampler: Long,
        val specularImage: Long,
        val specularMemory: Long,
        val specularImageView: Long,
        val specularSampler: Long,
        val descriptorPool: Long,
        val descriptorSetLayout: Long
    )
}