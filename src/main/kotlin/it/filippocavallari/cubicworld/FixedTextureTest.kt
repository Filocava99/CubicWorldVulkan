package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.models.ModelManager
import java.io.File

/**
 * Test the fixes for texture mapping issues
 */
fun main() {
    println("=== Fixed Texture Mapping Test ===")
    
    val texturesPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures"
    val modelsPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\models"
    val atlasPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\atlas"
    
    // Load textures
    println("1. Loading textures...")
    val stitcher = TextureStitcher(texturesPath)
    stitcher.build(64)
    
    // Test specific problematic textures
    println("\n2. Testing specific textures:")
    val testTextures = arrayOf("stone", "dirt", "grass_block_top")
    
    for (textureName in testTextures) {
        val index = stitcher.getTextureIndex(textureName)
        println("Texture '$textureName' index: $index")
        
        val blockPrefixedName = "block/$textureName"
        val blockIndex = stitcher.getTextureIndex(blockPrefixedName)
        println("Texture '$blockPrefixedName' index: $blockIndex")
        
        // These should match now with our fix
        if (index == blockIndex && index >= 0) {
            println("✓ SUCCESS: Both paths resolve to the same valid texture")
        } else if (index >= 0) {
            println("✓ SUCCESS: Base texture name resolves correctly")
        } else {
            println("✗ FAILURE: Texture resolution not working correctly")
        }
        println()
    }
    
    // Load models and test texture mappings
    println("\n3. Testing model texture resolution:")
    
    // Load specific models to test
    val modelPaths = arrayOf(
        "$modelsPath/block/stone.json",
        "$modelsPath/block/dirt.json",
        "$modelsPath/block/grass_block.json"
    )
    
    val modelManager = ModelManager(modelsPath)
    
    for (modelPath in modelPaths) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            println("Model file not found: $modelPath")
            continue
        }
        
        val model = modelManager.loadModel(modelFile.toPath(), stitcher)
        
        if (model != null) {
            println("Model: ${model.id}")
            println("  Textures:")
            for ((key, value) in model.textures) {
                val textureId = model.resolvedTextureIndexes[key] ?: -1
                println("  - $key: $value (Resolved to index: $textureId)")
            }
            println()
        } else {
            println("Failed to load model: $modelPath")
        }
    }
    
    println("\nTest complete!")
}
