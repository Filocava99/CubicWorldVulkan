package it.filippocavallari.cubicworld.util

import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.util.shaderc.ShadercIncludeResult
import org.lwjgl.vulkan.VK10
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Utility class for compiling GLSL shaders to SPIR-V
 */
object ShaderCompiler {
    /**
     * Compile GLSL shader source to SPIR-V
     */
    fun compileShader(sourceCode: String, shaderType: Int, fileName: String): ByteArray {
        println("Compiling shader: $fileName")
        
        // Create compiler
        val compiler = Shaderc.shaderc_compiler_initialize()
        if (compiler == 0L) {
            throw RuntimeException("Failed to create shader compiler")
        }
        
        try {
            // Create compiler options
            val options = Shaderc.shaderc_compile_options_initialize()
            if (options == 0L) {
                throw RuntimeException("Failed to create compiler options")
            }
            
            try {
                // Set compiler options
                Shaderc.shaderc_compile_options_set_optimization_level(
                    options, 
                    Shaderc.shaderc_optimization_level_performance
                )
                
                Shaderc.shaderc_compile_options_set_target_env(
                    options,
                    Shaderc.shaderc_target_env_vulkan,
                    Shaderc.shaderc_env_version_vulkan_1_0
                )
                
                // Map from Vulkan shader type to Shaderc shader kind
                val shadercKind = when (shaderType) {
                    VK10.VK_SHADER_STAGE_VERTEX_BIT -> Shaderc.shaderc_glsl_vertex_shader
                    VK10.VK_SHADER_STAGE_FRAGMENT_BIT -> Shaderc.shaderc_glsl_fragment_shader
                    VK10.VK_SHADER_STAGE_COMPUTE_BIT -> Shaderc.shaderc_glsl_compute_shader
                    VK10.VK_SHADER_STAGE_GEOMETRY_BIT -> Shaderc.shaderc_glsl_geometry_shader
                    VK10.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT -> Shaderc.shaderc_glsl_tess_control_shader
                    VK10.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT -> Shaderc.shaderc_glsl_tess_evaluation_shader
                    else -> throw IllegalArgumentException("Unsupported shader type: $shaderType")
                }
                
                // Compile shader to SPIR-V
                val result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    sourceCode,
                    shadercKind,
                    fileName,
                    "main", // Entry point
                    options
                )
                
                try {
                    // Check compilation status
                    val status = Shaderc.shaderc_result_get_compilation_status(result)
                    if (status != Shaderc.shaderc_compilation_status_success) {
                        val errorMsg = Shaderc.shaderc_result_get_error_message(result)
                        throw RuntimeException("Failed to compile shader: $errorMsg")
                    }
                    
                    // Get compiled SPIR-V size
                    val size = Shaderc.shaderc_result_get_length(result)
                    
                    // Create a byte array to hold the result
                    val spirvBytes = ByteArray(size.toInt())
                    
                    // Copy the data from native memory to the byte array
                    val dataPtr = Shaderc.shaderc_result_get_bytes(result)
                    
                    // Create a temporary direct ByteBuffer that wraps the native memory
                    val directBuffer = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(dataPtr), size.toInt())
                    directBuffer.get(spirvBytes)
                    
                    println("Successfully compiled shader: $fileName (${spirvBytes.size} bytes)")
                    return spirvBytes
                } finally {
                    Shaderc.shaderc_result_release(result)
                }
            } finally {
                Shaderc.shaderc_compile_options_release(options)
            }
        } finally {
            Shaderc.shaderc_compiler_release(compiler)
        }
    }
    
    /**
     * Compile GLSL shader from file to SPIR-V
     */
    fun compileShaderFile(filePath: String, shaderType: Int): ByteArray {
        println("Loading GLSL from: $filePath")
        val shaderSource = String(Files.readAllBytes(Paths.get(filePath)))
        return compileShader(shaderSource, shaderType, filePath)
    }
}
