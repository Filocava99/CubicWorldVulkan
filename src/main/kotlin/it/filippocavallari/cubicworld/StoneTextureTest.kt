package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.models.ModelManager
import java.io.File

/**
 * A focused test specifically for the stone texture issue
 */
fun main() {
    println("=== Stone Texture Test ===")
    
    val texturesPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures"
    val modelsPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\models"
    
    // Step 1: Check if the stone.png file exists
    val stoneFile = File("$texturesPath/stone.png")
    println("stone.png exists: ${stoneFile.exists()}")
    
    // Step 2: Load the textures
    val stitcher = TextureStitcher(texturesPath)
    stitcher.build(64)
    
    // Step 3: Test texture lookup with different formats
    val formats = arrayOf(
        "stone",                 // Raw texture name
        "block/stone",           // With block/ prefix
        "minecraft:block/stone"  // With minecraft: prefix
    )
    
    for (format in formats) {
        val index = stitcher.getTextureIndex(format)
        if (index >= 0) {
            println("✓ Found texture '$format' at index $index")
        } else {
            println("✗ Could not find texture '$format'")
            
            // Examine all mappings that contain "stone"
            println("  Related texture mappings:")
            stitcher.getTextureNameKeys()
                .filter { it.contains("stone", ignoreCase = true) }
                .forEach { println("  - $it") }
        }
    }
    
    // Step 4: Load the stone model
    val stoneModelFile = File("$modelsPath/block/stone.json")
    println("\nStone model exists: ${stoneModelFile.exists()}")
    
    // Step 5: Test with the ModelManager
    println("\nTesting model resolution for stone:")
    val modelManager = ModelManager(modelsPath)
    val model = modelManager.loadModel(stoneModelFile.toPath(), stitcher)
    
    if (model != null) {
        println("Successfully loaded stone model")
        println("Model textures:")
        model.textures.forEach { (key, value) -> 
            println("- $key: $value (Resolved to index: ${model.resolvedTextureIndexes[key]})")
        }
    } else {
        println("Failed to load stone model")
    }
    
    println("\nTest complete!")
}
