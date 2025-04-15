package it.filippocavallari.cubicworld.utils

/**
 * Simple main function to run the shader compiler.
 */
fun main() {
    println("Compiling shaders...")
    ShaderCompiler.compileAllShaders(
        "src/main/resources/shaders",
        "src/main/resources/shaders"
    )
    println("Done compiling shaders.")
}
