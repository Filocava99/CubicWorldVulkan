package it.filippocavallari.cubicworld.utils

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties

/**
 * Utility functions for Vulkan operations.
 */
object VulkanUtils {
    
    /**
     * Create a PointerBuffer from a String array
     * 
     * @param stack The memory stack to allocate from
     * @param strings The array of strings to convert
     * @return A PointerBuffer containing the strings
     */
    fun asPointerBuffer(stack: MemoryStack, strings: Array<String>): PointerBuffer {
        val buffer = stack.mallocPointer(strings.size)
        for (i in strings.indices) {
            buffer.put(i, stack.UTF8(strings[i]))
        }
        return buffer
    }
    
    /**
     * Find a memory type that satisfies requirements
     * 
     * @param physicalDevice The physical device
     * @param typeFilter The memory type filter
     * @param properties The required memory properties
     * @return The memory type index
     */
    fun findMemoryType(
        physicalDevice: VkPhysicalDevice,
        typeFilter: Int,
        properties: Int
    ): Int {
        MemoryStack.stackPush().use { stack ->
            val memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack)
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties)
            
            // Find a memory type that matches both the type filter and the required properties
            for (i in 0 until memProperties.memoryTypeCount()) {
                val isTypeMatch = (typeFilter and (1 shl i)) != 0
                val hasProperties = (memProperties.memoryTypes(i).propertyFlags() and properties) == properties
                
                if (isTypeMatch && hasProperties) {
                    return i
                }
            }
            
            throw RuntimeException("Failed to find suitable memory type")
        }
    }
    
    /**
     * Check if a format supports a specific feature
     * 
     * @param physicalDevice The physical device
     * @param format The format to check
     * @param feature The feature to check for
     * @return True if the format supports the feature
     */
    fun formatSupportsFeature(
        physicalDevice: VkPhysicalDevice,
        format: Int,
        feature: Int
    ): Boolean {
        MemoryStack.stackPush().use { stack ->
            val formatProps = org.lwjgl.vulkan.VkFormatProperties.calloc(stack)
            vkGetPhysicalDeviceFormatProperties(physicalDevice, format, formatProps)
            
            return (formatProps.optimalTilingFeatures() and feature) == feature
        }
    }
    
    /**
     * Calculate the mip levels for an image
     * 
     * @param width Image width
     * @param height Image height
     * @return The number of mip levels
     */
    fun calculateMipLevels(width: Int, height: Int): Int {
        return Math.floor(Math.log(Math.max(width, height).toDouble()) / Math.log(2.0)).toInt() + 1
    }
}