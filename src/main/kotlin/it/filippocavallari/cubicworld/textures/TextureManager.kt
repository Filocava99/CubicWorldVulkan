package it.filippocavallari.cubicworld.textures

/**
 * Centralized manager for texture access that maps block textures to the correct atlas indices.
 * This solves the hardcoded texture index problem in BlockType enum.
 */
object TextureManager {
    // Map block names to their correct texture names
    private val blockToTextureName = mapOf(
        // Basic terrain blocks
        "stone" to "stone",
        "dirt" to "dirt", 
        "grass_side" to "dirt",
        "grass_top" to "grass_block_top",
        "grass_bottom" to "dirt",
        "bedrock" to "bedrock",
        "sand" to "sand",
        "gravel" to "gravel",
        
        // Wood types
        "log_oak_side" to "oak_log", 
        "log_oak_top" to "oak_log_top",
        "leaves_oak" to "oak_leaves",
        
        // Ore blocks
        "coal_ore" to "coal_ore",
        "iron_ore" to "iron_ore",
        "gold_ore" to "gold_ore",
        "diamond_ore" to "diamond_ore",
        "redstone_ore" to "redstone_ore",
        "lapis_ore" to "lapis_ore",
        
        // Water
        "water" to "water_still"
    )
    
    // Cached texture indices looked up from the stitcher
    private val textureIndices = mutableMapOf<String, Int>()
    
    /**
     * Initialize the texture manager with a texture stitcher to perform lookups.
     * This caches all the texture indices for faster access later.
     */
    fun initialize(stitcher: TextureStitcher) {
        // Clear any existing cached indices
        textureIndices.clear()
        
        // Look up and cache the correct index for each texture name
        for ((blockName, textureName) in blockToTextureName) {
            // Try different variations of the texture name
            val variations = listOf(
                textureName,
                "block/$textureName",
                "${textureName}.png",
                "block/${textureName}.png"
            )
            
            // Find the first working variation
            var index = -1
            for (variant in variations) {
                index = stitcher.getTextureIndex(variant)
                if (index >= 0) {
                    break
                }
            }
            
            if (index >= 0) {
                textureIndices[blockName] = index
                println("Mapped texture '$blockName' to atlas index $index")
            } else {
                println("WARNING: Failed to map texture for '$blockName'")
                // Fallback to a known texture (stone)
                val fallbackIndex = stitcher.getTextureIndex("stone")
                if (fallbackIndex >= 0) {
                    textureIndices[blockName] = fallbackIndex
                    println("  Using fallback texture at index $fallbackIndex")
                } else {
                    // Last resort: use index 0 (should be the default/error texture)
                    textureIndices[blockName] = 0
                    println("  Using emergency fallback texture at index 0")
                }
            }
        }
        
        // Print a summary of mapped textures
        println("Texture Manager initialized with ${textureIndices.size} mapped textures")
        println("Stone texture mapped to index: ${getTextureIndex("stone")}")
        println("Dirt texture mapped to index: ${getTextureIndex("dirt")}")
        println("Grass side texture mapped to index: ${getTextureIndex("grass_side")}")
        println("Bedrock texture mapped to index: ${getTextureIndex("bedrock")}")
    }
    
    /**
     * Get the texture index for a specific block texture
     */
    fun getTextureIndex(blockTextureName: String): Int {
        return textureIndices[blockTextureName] ?: 0 // Return 0 (default texture) if not found
    }
}