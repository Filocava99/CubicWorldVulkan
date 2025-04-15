package it.filippocavallari.cubicworld.utils

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.io.File
import java.nio.ByteBuffer

/**
 * Utility class for shader-related operations.
 */
object ShaderUtils {
    
    /**
     * Creates a shader module from SPIR-V bytecode.
     * 
     * @param device Vulkan logical device
     * @param shaderCode SPIR-V bytecode as ByteBuffer
     * @return Handle to the created shader module
     */
    fun createShaderModule(device: VkDevice, shaderCode: ByteBuffer): Long {
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(shaderCode)
            
            val pShaderModule = stack.mallocLong(1)
            if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw RuntimeException("Failed to create shader module")
            }
            
            return pShaderModule.get(0)
        }
    }
    
    /**
     * Loads and creates a shader module from a resource file.
     * 
     * @param device Vulkan logical device
     * @param resourcePath Path to the shader file in resources
     * @return Handle to the created shader module
     */
    fun loadShader(device: VkDevice, resourcePath: String): Long {
        val shaderBytes = readShaderFile(resourcePath)
        val shaderBuffer = MemoryUtil.memAlloc(shaderBytes.size)
        
        try {
            // Put the shader bytes into the byte buffer
            for (i in shaderBytes.indices) {
                shaderBuffer.put(i, shaderBytes[i])
            }
            
            return createShaderModule(device, shaderBuffer)
        } finally {
            MemoryUtil.memFree(shaderBuffer)
        }
    }
    
    /**
     * Read shader file from resources
     * 
     * @param resourcePath Path to the shader file in resources
     * @return ByteArray containing the shader file content
     */
    fun readShaderFile(resourcePath: String): ByteArray {
        val classLoader = Thread.currentThread().contextClassLoader
        val inputStream = classLoader.getResourceAsStream(resourcePath)
            ?: throw RuntimeException("Failed to load shader file: $resourcePath")
        
        return inputStream.readBytes()
    }
    
    /**
     * Read shader file from filesystem
     * 
     * @param filePath Path to the shader file
     * @return ByteArray containing the shader file content
     */
    fun readFile(filePath: String): ByteArray {
        return File(filePath).readBytes()
    }
    
    /**
     * Compiles a GLSL shader to SPIR-V using external process.
     * 
     * @param shaderCode GLSL shader code
     * @param shaderType Type of shader (vertex, fragment, etc.)
     * @param fileName Name of the shader file (for error reporting)
     * @return ByteBuffer containing SPIR-V bytecode
     */
    fun compileShader(shaderCode: String, shaderType: Int, fileName: String): ByteBuffer {
        return it.filippocavallari.cubicworld.utils.ShaderCompiler.compileShader(shaderCode, shaderType, fileName)
    }
}