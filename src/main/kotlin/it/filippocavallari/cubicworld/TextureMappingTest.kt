package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.textures.TextureStitcher

/**
 * Focused test for texture mapping issues
 */
fun main() {
    println("=== Texture Mapping Test ===")
    
    val texturesPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures"
    
    // Step 1: Load the textures
    println("\nLoading textures from $texturesPath...")
    val stitcher = TextureStitcher(texturesPath)
    stitcher.build(64)
    
    // Step 2: Test specific textures we know are problematic
    println("\nTesting specific textures:")
    val testTextures = arrayOf(
        "stone", "block/stone", 
        "dirt", "block/dirt", 
        "grass_block_top", "block/grass_block_top"
    )
    
    for (textureName in testTextures) {
        val index = stitcher.getTextureIndex(textureName)
        if (index >= 0) {
            println("✓ Found '$textureName' at index $index")
        } else {
            println("✗ MISSING '$textureName'")
            
            // Show similar textures that were found
            println("  Looking for similar textures:")
            val similar = stitcher.getTextureNameKeys().filter { 
                val baseName = textureName.substringAfterLast('/')
                it.contains(baseName, ignoreCase = true) 
            }
            
            if (similar.isEmpty()) {
                println("  No similar textures found!")
            } else {
                similar.forEach { println("  - $it") }
            }
        }
    }
    
    // Step 3: Check file structure 
    println("\nAnalyzing texture directory structure:")
    val stoneFile = java.io.File("$texturesPath/stone.png")
    val blockDir = java.io.File("$texturesPath/block")
    
    println("stone.png exists: ${stoneFile.exists()}")
    println("block directory exists: ${blockDir.exists()}")
    
    if (blockDir.exists() && blockDir.isDirectory) {
        println("Contents of block directory:")
        blockDir.listFiles()?.forEach { println("  - ${it.name}") }
    }
    
    // Step 4: Print all mappings for reference
    println("\nAll texture mappings (first 20):")
    stitcher.getTextureNameKeys().sorted().take(20).forEach { key ->
        val index = stitcher.getTextureIndex(key)
        println("$key -> $index")
    }
    
    println("\nTotal mappings: ${stitcher.getTextureNameKeys().size}")
}
