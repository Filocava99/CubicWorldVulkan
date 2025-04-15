package it.filippocavallari.cubicworld.utils

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.VK10.*

/**
 * Debug utility for Vulkan validation layers.
 * Provides debugging callbacks and utilities for development.
 */
object DebugUtils {
    
    private var debugMessenger: Long = VK_NULL_HANDLE
    
    /**
     * Sets up the debug messenger for Vulkan validation layers.
     * 
     * @param instance The Vulkan instance
     */
    fun setupDebugMessenger(instance: VkInstance) {
        if (!isValidationLayerEnabled()) {
            return
        }
        
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                .messageSeverity(
                    VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or
                    VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
                    VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                )
                .messageType(
                    VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                    VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                    VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
                )
                .pfnUserCallback { messageSeverity, messageTypes, pCallbackData, pUserData ->
                    val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                    val message = callbackData.pMessageString()
                    
                    when {
                        (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0 -> {
                            System.err.println("ERROR: $message")
                        }
                        (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0 -> {
                            System.err.println("WARNING: $message")
                        }
                        (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0 -> {
                            println("INFO: $message")
                        }
                        else -> {
                            println("DEBUG: $message")
                        }
                    }
                    
                    // Return false to indicate the message should not be aborted
                    VK_FALSE
                }
            
            val pDebugMessenger = stack.longs(VK_NULL_HANDLE)
            if (vkCreateDebugUtilsMessengerEXT(instance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
                throw RuntimeException("Failed to set up debug messenger")
            }
            
            debugMessenger = pDebugMessenger.get(0)
        }
    }
    
    /**
     * Destroys the debug messenger.
     * 
     * @param instance The Vulkan instance
     */
    fun destroyDebugMessenger(instance: VkInstance) {
        if (isValidationLayerEnabled() && debugMessenger != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null)
        }
    }
    
    /**
     * Creates a debug utils messenger create info for instance creation.
     * Used to capture debug messages during instance creation and destruction.
     * 
     * @param stack The memory stack to use for allocation
     * @return The debug utils messenger create info
     */
    fun populateDebugMessengerCreateInfo(stack: MemoryStack): VkDebugUtilsMessengerCreateInfoEXT {
        return VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
            .messageSeverity(
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
            )
            .messageType(
                VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
            )
            .pfnUserCallback { messageSeverity, messageTypes, pCallbackData, pUserData ->
                val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                val message = callbackData.pMessageString()
                
                when {
                    (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0 -> {
                        System.err.println("ERROR: $message")
                    }
                    (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0 -> {
                        System.err.println("WARNING: $message")
                    }
                    (messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0 -> {
                        println("INFO: $message")
                    }
                    else -> {
                        println("DEBUG: $message")
                    }
                }
                
                // Return false to indicate the message should not be aborted
                VK_FALSE
            }
    }
    
    /**
     * Checks if validation layers are enabled.
     * 
     * @return True if validation layers are enabled
     */
    fun isValidationLayerEnabled(): Boolean {
        // Enable validation layers in debug mode, disable in release mode
        return true // For development, you might want to make this configurable
    }
    
    // Extension function for creating debug messenger
    private fun vkCreateDebugUtilsMessengerEXT(
        instance: VkInstance,
        createInfo: VkDebugUtilsMessengerCreateInfoEXT,
        allocationCallbacks: VkAllocationCallbacks?,
        pDebugMessenger: java.nio.LongBuffer
    ): Int {
        val vkCreateDebugUtilsMessengerEXT = instance.getCapabilities().vkCreateDebugUtilsMessengerEXT
            ?: return VK_ERROR_EXTENSION_NOT_PRESENT
        return vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger)
    }
    
    // Extension function for destroying debug messenger
    private fun vkDestroyDebugUtilsMessengerEXT(
        instance: VkInstance,
        debugMessenger: Long,
        allocationCallbacks: VkAllocationCallbacks?
    ) {
        val vkDestroyDebugUtilsMessengerEXT = instance.getCapabilities().vkDestroyDebugUtilsMessengerEXT ?: return
        vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, allocationCallbacks)
    }
}