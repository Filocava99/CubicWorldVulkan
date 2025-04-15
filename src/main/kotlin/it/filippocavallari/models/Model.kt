package it.filippocavallari.models

import it.filippocavallari.textures.TextureRegion
import it.filippocavallari.textures.TextureStitcher

/**
 * Represents a 3D model loaded from a JSON definition.
 * This can be a block, item, or any other 3D model.
 */
class Model {
    // Basic properties
    var id: String? = null
    var parent: String? = null
    var ambientOcclusion: Boolean = true
    
    // Textures configuration
    var textures: MutableMap<String, String> = HashMap()
    var resolvedTextureIndexes: MutableMap<String, Int> = HashMap()
    
    // Display settings for different positions (inventory, ground, etc.)
    var display: MutableMap<String, DisplayPosition> = HashMap()
    
    // Elements (cubes) that make up the model
    var elements: MutableList<Element> = ArrayList()
    
    /**
     * Inherit properties from a parent model.
     *
     * @param parentModel The parent model to inherit from
     */
    fun inheritFromParent(parentModel: Model) {
        // Only inherit empty/null properties
        if (this.ambientOcclusion != parentModel.ambientOcclusion) {
            this.ambientOcclusion = parentModel.ambientOcclusion
        }
        
        // Merge textures, preferring our own values
        for ((key, value) in parentModel.textures) {
            if (!this.textures.containsKey(key)) {
                this.textures[key] = value
            }
        }
        
        // Inherit elements if we don't have any
        if (this.elements.isEmpty() && parentModel.elements.isNotEmpty()) {
            this.elements = ArrayList(parentModel.elements)
        }
        
        // Inherit display positions
        for ((key, value) in parentModel.display) {
            if (!this.display.containsKey(key)) {
                this.display[key] = value
            }
        }
    }
    
    /**
     * Resolves texture references to actual texture indexes in the atlas.
     *
     * @param textureStitcher The texture stitcher containing atlas information
     */
    fun resolveTextureReferences(textureStitcher: TextureStitcher) {
        // First, resolve any texture references that use the # syntax
        val resolvedTextures = HashMap<String, String>()
        
        for ((key, value) in textures) {
            if (value.startsWith("#")) {
                // This is a reference to another texture in this model
                val referencedKey = value.substring(1)
                
                if (!textures.containsKey(referencedKey)) {
                    System.err.println("Warning: Texture reference not found: $referencedKey")
                    continue
                }
                
                val resolvedValue = textures[referencedKey]
                if (resolvedValue != null) {
                    resolvedTextures[key] = resolvedValue
                }
            }
        }
        
        // Apply resolved references
        textures.putAll(resolvedTextures)
        
        // Now resolve texture paths to actual texture indexes
        for ((key, texturePath) in textures) {
            // Skip references for now - they should be resolved in the next step
            if (texturePath.startsWith("#")) {
                continue
            }
            
            // Convert texture path to an index
            // The format is typically: "block/stone" or "items/sword"
            // We'll just use the filename part as a lookup
            var textureFileName = texturePath
            
            if (texturePath.contains("/")) {
                textureFileName = texturePath.substring(texturePath.lastIndexOf('/') + 1)
            }
            
            // Find the texture index (simplified for now - would need a proper lookup in practice)
            // This is just a placeholder - in a real implementation you would have a proper lookup
            // based on the texture name in your atlas
            val textureIndex = getTextureIndexForName(textureFileName)
            resolvedTextureIndexes[key] = textureIndex
        }
    }
    
    /**
     * Get the texture index for a given texture name.
     * This is a placeholder implementation - in a real system,
     * you would look up the actual index in your texture atlas.
     */
    private fun getTextureIndexForName(textureName: String): Int {
        // Placeholder - in a real implementation you would look up the
        // actual texture in your atlas based on the name
        return textureName.hashCode() % 100 // Just for demonstration
    }
}