package it.filippocavallari.cubicworld.models

/**
 * Represents a display position for the model.
 * Used for item rendering in different positions like
 * first-person, third-person, etc.
 */
class DisplayPosition {
    // Rotation of the model [x, y, z]
    var rotation: FloatArray = floatArrayOf(0f, 0f, 0f)
    
    // Translation of the model [x, y, z]
    var translation: FloatArray = floatArrayOf(0f, 0f, 0f)
    
    // Scale of the model [x, y, z]
    var scale: FloatArray = floatArrayOf(1f, 1f, 1f)
    
    override fun toString(): String {
        return "DisplayPosition(rotation=${rotation.contentToString()}, " +
               "translation=${translation.contentToString()}, " +
               "scale=${scale.contentToString()})"
    }
}