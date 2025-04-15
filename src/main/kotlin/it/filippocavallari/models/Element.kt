package it.filippocavallari.models

/**
 * Represents a cube element in a model.
 */
class Element {
    // Position in 3D space [x, y, z]
    var from: FloatArray = floatArrayOf(0f, 0f, 0f)
    var to: FloatArray = floatArrayOf(16f, 16f, 16f)
    
    // Rotation of this element
    var rotation: Rotation? = null
    
    // Determines if faces cast shadows
    var isShade: Boolean = true
    
    // Face definitions
    var faces: MutableMap<String, Face> = HashMap()
    
    /**
     * Default constructor for GSON deserialization
     */
    constructor()
    
    /**
     * Create an element with specific dimensions.
     * 
     * @param fromX Start X position
     * @param fromY Start Y position
     * @param fromZ Start Z position
     * @param toX End X position
     * @param toY End Y position
     * @param toZ End Z position
     */
    constructor(fromX: Float, fromY: Float, fromZ: Float, toX: Float, toY: Float, toZ: Float) {
        this.from = floatArrayOf(fromX, fromY, fromZ)
        this.to = floatArrayOf(toX, toY, toZ)
    }
    
    /**
     * Adds a face to this element.
     * 
     * @param direction The direction ("north", "south", etc.)
     * @param face The face definition
     */
    fun addFace(direction: String, face: Face) {
        faces[direction] = face
    }
    
    /**
     * Represents the rotation of an element.
     */
    class Rotation {
        var origin: FloatArray = floatArrayOf(8.0f, 8.0f, 8.0f) // Default center point
        var axis: String = "y"                  // "x", "y", or "z"
        var angle: Float = 0.0f                 // Angle in degrees
        var isRescale: Boolean = false            // Whether to rescale faces across the rotation
        
        /**
         * Default constructor for GSON deserialization
         */
        constructor()
        
        /**
         * Create a rotation around an axis.
         * 
         * @param axis The axis to rotate around ("x", "y", or "z")
         * @param angle The angle in degrees
         * @param origin The origin point [x, y, z]
         * @param rescale Whether to rescale faces
         */
        constructor(axis: String, angle: Float, origin: FloatArray, rescale: Boolean) {
            this.axis = axis
            this.angle = angle
            this.origin = origin
            this.isRescale = rescale
        }
    }
}