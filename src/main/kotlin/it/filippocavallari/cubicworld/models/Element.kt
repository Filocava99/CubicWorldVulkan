package it.filippocavallari.cubicworld.models

/**
 * Represents a cube element in a model.
 */
class Element {
    // Cube coordinates - from x,y,z to x,y,z
    var from: FloatArray = floatArrayOf(0f, 0f, 0f)
    var to: FloatArray = floatArrayOf(16f, 16f, 16f)
    
    // Rotation data (optional)
    var rotation: ElementRotation? = null
    
    // Whether to shade across faces
    var shade: Boolean = true
    
    // Faces of the cube
    var faces: MutableMap<String, Face> = HashMap()
    
    override fun toString(): String {
        return "Element(from=${from.contentToString()}, to=${to.contentToString()}, faces=${faces.size})"
    }
}

/**
 * Rotation data for an element.
 */
class ElementRotation {
    // Origin point for rotation
    var origin: FloatArray = floatArrayOf(8f, 8f, 8f)
    
    // Rotation axis
    var axis: String = "y"
    
    // Rotation angle in degrees
    var angle: Float = 0f
    
    // Whether to scale faces across the whole block
    var rescale: Boolean = false
    
    override fun toString(): String {
        return "ElementRotation(origin=${origin.contentToString()}, axis=$axis, angle=$angle, rescale=$rescale)"
    }
}