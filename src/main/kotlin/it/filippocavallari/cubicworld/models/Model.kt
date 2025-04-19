package it.filippocavallari.cubicworld.models

import it.filippocavallari.cubicworld.textures.TextureStitcher

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
        
        // Log just model ID
        println("Resolving textures for model: $id")
        
        for ((key, value) in textures) {
            if (value.startsWith("#")) {
                // This is a reference to another texture in this model
                val referencedKey = value.substring(1)
                
                if (!textures.containsKey(referencedKey)) {
                    System.err.println("Warning: Texture reference not found: $referencedKey in model $id")
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
            // Skip references that weren't resolved
            if (texturePath.startsWith("#")) {
                System.err.println("Warning: Unresolved texture reference: $texturePath in model $id")
                continue
            }
            
            // Use the TextureStitcher to look up the index
            val textureIndex = textureStitcher.getTextureIndex(texturePath)
            
            // Debug output for specific textures
            if (id?.contains("stone") == true || id?.contains("dirt") == true || id?.contains("grass") == true) {
                println("DEBUG: Model $id trying to resolve texture '$key' => '$texturePath'")
                println("  - Result index: $textureIndex")
            }
            
            if (textureIndex >= 0) {
                resolvedTextureIndexes[key] = textureIndex
                // Only log errors, not successes to reduce output
                // println("Resolved texture '$key' ($texturePath) to index $textureIndex")
            } else {
                System.err.println("Warning: Texture not found in atlas: $texturePath in model $id")
                // Use a fallback texture (index 0) if available
                resolvedTextureIndexes[key] = 0
            }
        }
        
        // Apply these resolved indexes to all faces in the model
        for (element in elements) {
            for ((_, face) in element.faces) {
                if (face.texture.startsWith("#")) {
                    val textureRef = face.texture.substring(1)
                    face.textureId = resolvedTextureIndexes.getOrDefault(textureRef, 0)
                }
            }
        }
    }
    
    /**
     * Fix texture paths to handle the potential mismatch between model references and actual texture locations.
     * This method modifies the textures map to fix common path issues.
     */
    fun fixTexturePaths() {
        val fixedTextures = HashMap<String, String>()
        
        for ((key, value) in textures) {
            // Skip reference textures (they'll be resolved later)
            if (value.startsWith("#")) {
                fixedTextures[key] = value
                continue
            }
            
            // Handle block/ prefix - look for files in the root folder
            if (value.startsWith("block/")) {
                val simpleTextureName = value.substring("block/".length)
                fixedTextures[key] = simpleTextureName
                println("Fixed texture path: $value -> $simpleTextureName for model $id")
            } else {
                fixedTextures[key] = value
            }
        }
        
        // Update the textures map
        textures.clear()
        textures.putAll(fixedTextures)
    }
}