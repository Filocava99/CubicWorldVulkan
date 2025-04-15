package it.filippocavallari.models

/**
 * Represents a face of a model element.
 */
class Face {
    // Texture reference (key in the textures map)
    var texture: String? = null
    
    // UV coordinates [u1, v1, u2, v2] or null for auto
    var uv: FloatArray? = null
    
    // Face tinting index or -1 for no tinting
    var tintindex: Int = -1
    
    // Direction for determining culling ("north", "south", etc.)
    var cullface: String? = null
    
    // Rotation of the texture in increments of 90 degrees
    var rotation: Int = 0
    
    // Whether this face should render on both sides
    var isDoubleSided: Boolean = false
    
    // Emissive intensity (0 for non-emissive)
    var emissive: Float = 0.0f
    
    /**
     * Default constructor for GSON deserialization
     */
    constructor()
    
    /**
     * Create a face with the specified texture.
     * 
     * @param texture The texture reference
     */
    constructor(texture: String) {
        this.texture = texture
    }
    
    /**
     * Create a face with texture and UV coordinates.
     * 
     * @param texture The texture reference
     * @param u1 Left U coordinate
     * @param v1 Top V coordinate
     * @param u2 Right U coordinate
     * @param v2 Bottom V coordinate
     */
    constructor(texture: String, u1: Float, v1: Float, u2: Float, v2: Float) {
        this.texture = texture
        this.uv = floatArrayOf(u1, v1, u2, v2)
    }
}