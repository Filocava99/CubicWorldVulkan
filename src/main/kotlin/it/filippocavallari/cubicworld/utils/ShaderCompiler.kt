package it.filippocavallari.cubicworld.utils

import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Utility for compiling GLSL shaders to SPIR-V bytecode.
 * This implementation uses glslangValidator executable which must be in the system path.
 */
object ShaderCompiler {
    
    // Constants for shader types
    object ShaderKind {
        const val VERTEX = 0
        const val FRAGMENT = 1
        const val COMPUTE = 2
        const val GEOMETRY = 3
        const val TESS_CONTROL = 4
        const val TESS_EVALUATION = 5
    }
    
    /**
     * Compile a GLSL shader to SPIR-V
     * 
     * @param shaderFile The source file path
     * @param shaderKind The kind of shader (vertex, fragment, etc.)
     * @param outputFile The output file path
     */
    fun compileShaderToFile(shaderFile: String, shaderKind: Int, outputFile: String) {
        val source = File(shaderFile).readText()
        val bytecode = compileShader(source, shaderKind, shaderFile)
        val byteArray = ByteArray(bytecode.remaining())
        bytecode.get(byteArray)
        File(outputFile).writeBytes(byteArray)
        MemoryUtil.memFree(bytecode)
    }
    
    /**
     * Compile a GLSL shader source to SPIR-V bytecode
     * 
     * @param source The shader source code
     * @param shaderKind The kind of shader (vertex, fragment, etc.)
     * @param fileName The file name for error reporting
     * @return A ByteBuffer containing the SPIR-V bytecode
     */
    fun compileShader(source: String, shaderKind: Int, fileName: String): ByteBuffer {
        // Create a temporary file for the shader source
        val tempSourceFile = File.createTempFile("shader", getShaderExtension(shaderKind))
        tempSourceFile.writeText(source)
        
        // Create a temporary file for the output
        val tempOutputFile = File.createTempFile("spirv", ".spv")
        tempOutputFile.delete() // glslangValidator needs the file to not exist
        
        try {
            // Build the command to run glslangValidator
            val command = arrayOf(
                "glslangValidator",
                "-V", // Generate SPIR-V
                "-o", tempOutputFile.absolutePath, // Output file
                tempSourceFile.absolutePath // Input file
            )
            
            // Execute the command
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            
            // Wait for the process to finish and check result
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            
            if (exitCode != 0) {
                throw RuntimeException("Failed to compile shader: $fileName\n$output")
            }
            
            // Read the SPIR-V bytecode
            val spirvBytes = tempOutputFile.readBytes()
            val buffer = MemoryUtil.memAlloc(spirvBytes.size)
            
            // Manually copy bytes to buffer to avoid ambiguity
            for (i in spirvBytes.indices) {
                buffer.put(i, spirvBytes[i])
            }
            buffer.rewind()
            
            return buffer
        } finally {
            // Clean up temporary files
            tempSourceFile.delete()
            tempOutputFile.delete()
        }
    }
    
    /**
     * Get the file extension for a shader kind
     */
    private fun getShaderExtension(shaderKind: Int): String {
        return when (shaderKind) {
            ShaderKind.VERTEX -> ".vert"
            ShaderKind.FRAGMENT -> ".frag"
            ShaderKind.COMPUTE -> ".comp"
            ShaderKind.GEOMETRY -> ".geom"
            ShaderKind.TESS_CONTROL -> ".tesc"
            ShaderKind.TESS_EVALUATION -> ".tese"
            else -> throw IllegalArgumentException("Unknown shader kind: $shaderKind")
        }
    }
    
    /**
     * Compile all shaders in a directory
     * 
     * @param sourceDir The source directory containing GLSL shaders
     * @param outputDir The output directory for SPIR-V bytecode
     */
    fun compileAllShaders(sourceDir: String, outputDir: String) {
        // Make sure output directory exists
        val outputDirFile = File(outputDir)
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs()
        }
        
        // Process all shader files
        val sourceDirFile = File(sourceDir)
        if (!sourceDirFile.exists() || !sourceDirFile.isDirectory) {
            throw IOException("Source directory does not exist or is not a directory: $sourceDir")
        }
        
        sourceDirFile.listFiles()?.forEach { file ->
            if (file.isFile) {
                val extension = file.extension.toLowerCase()
                val kind = when (extension) {
                    "vert" -> ShaderKind.VERTEX
                    "frag" -> ShaderKind.FRAGMENT
                    "comp" -> ShaderKind.COMPUTE
                    "geom" -> ShaderKind.GEOMETRY
                    "tesc" -> ShaderKind.TESS_CONTROL
                    "tese" -> ShaderKind.TESS_EVALUATION
                    else -> -1
                }
                
                if (kind != -1) {
                    val outputFile = File(outputDir, "${file.nameWithoutExtension}.$extension.spv")
                    println("Compiling ${file.name} to ${outputFile.name}")
                    compileShaderToFile(file.absolutePath, kind, outputFile.absolutePath)
                }
            }
        }
    }
    
    /**
     * Main function for standalone shader compilation
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val sourceDir = if (args.size > 0) args[0] else "src/main/resources/shaders"
        val outputDir = if (args.size > 1) args[1] else "src/main/resources/shaders"
        
        println("Compiling shaders from $sourceDir to $outputDir")
        compileAllShaders(sourceDir, outputDir)
        println("Done")
    }
}