package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.models.ModelManager
import it.filippocavallari.cubicworld.textures.TextureStitcher
import java.io.File

/**
 * Main class to test texture and model loading
 */
fun main() {
    println("=== CubicWorld Texture System Test ===")
    
    val texturesPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures"
    val modelsPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\models"
    val atlasPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\atlas"
    
    // Ensure the atlas directory exists
    File(atlasPath).mkdirs()
    
    println("\n1. Loading textures...")
    val stitcher = TextureStitcher(texturesPath)
    stitcher.build(64)
    
    // Save atlases to disk
    stitcher.saveAtlases(atlasPath)
    println("Texture atlas saved to: $atlasPath")
    println("Atlas dimensions: ${stitcher.atlasWidth}x${stitcher.atlasHeight}")
    println("Total textures: ${stitcher.totalTextures}")
    
    println("\n2. Testing some texture lookups...")
    // Add function to debug texture mappings
    fun printTextureDebugInfo(textureStitcher: TextureStitcher, textureName: String) {
        val index = textureStitcher.getTextureIndex(textureName)
        if (index >= 0) {
            println("✓ Found texture '$textureName' at index $index")
        } else {
            println("✗ Texture '$textureName' NOT FOUND")
            // Print all keys that contain part of this name to help debug
            val partialMatches = textureStitcher.getTextureNameKeys().filter { it.contains(textureName.split("/").last()) }
            if (partialMatches.isNotEmpty()) {
                println("  - Similar texture names found:")
                partialMatches.take(10).forEach { println("    * $it") }
                if (partialMatches.size > 10) println("    * ... and ${partialMatches.size - 10} more")
            }
        }
    }

    // Test some common texture names
    val testTextures = arrayOf(
        "stone", "dirt", "grass_block_top", 
        "block/stone", "block/dirt", "block/grass_block_top",
        "minecraft:block/stone"
    )
    
    for (textureName in testTextures) {
        printTextureDebugInfo(stitcher, textureName)
    }
    
    println("\n3. Loading models...")
    val modelManager = ModelManager(modelsPath)
    val models = modelManager.loadModels(stitcher)
    
    println("\n=== Test Completed ===")
    println("Textures loaded: ${stitcher.totalTextures}")
    println("Models loaded: ${models.size}")
    println("All done! The system should now correctly map texture names to indices.")
}
