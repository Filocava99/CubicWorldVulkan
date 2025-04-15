package it.filippocavallari.models

/**
 * Represents display settings for a model in different contexts.
 */
class DisplayPosition {
    // Translation [x, y, z]
    var translation: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)
    
    // Rotation [x, y, z] in degrees
    var rotation: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)
    
    // Scale [x, y, z] where 1.0 is normal size
    var scale: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f)
    
    /**
     * Default constructor for GSON deserialization
     */
    constructor()
    
    /**
     * Create display settings with the specified transformations.
     * 
     * @param translation Translation vector [x, y, z]
     * @param rotation Rotation vector [x, y, z] in degrees
     * @param scale Scale vector [x, y, z]
     */
    constructor(translation: FloatArray, rotation: FloatArray, scale: FloatArray) {
        this.translation = translation
        this.rotation = rotation
        this.scale = scale
    }
}