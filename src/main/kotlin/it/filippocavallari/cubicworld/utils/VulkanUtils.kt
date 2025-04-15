package it.filippocavallari.cubicworld.utils

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

/**
 * Utility class for Vulkan-related operations.
 */
object VulkanUtils {
    
    /**
     * Converts an array of strings to a pointer buffer.
     * Useful for creating API request parameters.
     * 
     * @param stack Memory stack for allocation
     * @param strings Array of strings to convert
     * @return PointerBuffer containing pointers to the strings
     */
    fun asPointerBuffer(stack: MemoryStack, strings: Array<String>): PointerBuffer {
        val buffer = stack.mallocPointer(strings.size)
        for (i in strings.indices) {
            buffer.put(i, stack.UTF8(strings[i]))
        }
        return buffer
    }
    
    /**
     * Reads a binary file into a byte array.
     * Useful for loading shader bytecode.
     * 
     * @param filePath Path to the file
     * @return Byte array containing the file data
     */
    fun readFile(filePath: String): ByteArray {
        val classLoader = VulkanUtils::class.java.classLoader
        val url = classLoader.getResource(filePath)
            ?: throw IllegalArgumentException("File not found: $filePath")
        
        return url.openStream().use { it.readBytes() }
    }
}
