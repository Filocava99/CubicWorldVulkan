package it.filippocavallari.cubicworld.textures

import it.filippocavallari.cubicworld.models.ModelManager
import java.io.File

/**
 * Utility class to debug and test the texture system.
 * This can be used to verify texture loading and model texture resolution.
 */
object TextureDebugger {
    @JvmStatic
    fun main(args: Array<String>) {
        val texturesPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures"
        val modelsPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\models"
        val outputPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\atlas"
        
        println("=== Texture & Model Debugger ===")
        
        // Ensure output directory exists
        File(outputPath).mkdirs()
        
        println("\n1. Loading and stitching textures...")
        val stitcher = TextureStitcher(texturesPath)
        stitcher.build(64)  // 64px texture size
        
        // Don't save the atlases to reduce console output
        // stitcher.saveAtlases(outputPath)
        
        println("\n2. Testing texture index lookup...")
        // Test a few common texture names
        val testTextures = listOf(
            "stone", "dirt", "grass_block_top", "oak_log", 
            "block/stone", "block/dirt", "block/grass_block_top",
            "minecraft:block/stone"
        )
        
        for (textureName in testTextures) {
            val index = stitcher.getTextureIndex(textureName)
            if (index >= 0) {
                println("Found texture '$textureName' at index $index")
            } else {
                println("Texture '$textureName' NOT FOUND")
            }
        }
        
        println("\n3. Loading models...")
        val modelManager = ModelManager(modelsPath)
        val models = modelManager.loadModels(stitcher)
        
        println("\nTexture & Model test complete!")
        println("Texture atlas dimensions: ${stitcher.atlasWidth}x${stitcher.atlasHeight}")
        println("Total textures: ${stitcher.totalTextures}")
        println("Total models: ${models.size}")
        println("Atlas files saved to: $outputPath")
    }
}