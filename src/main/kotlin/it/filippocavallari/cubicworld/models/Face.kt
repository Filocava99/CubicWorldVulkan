package it.filippocavallari.cubicworld.models

/**
 * Represents a face of a cube element in a model.
 */
class Face {
    // UV coordinates for the texture - [x1, y1, x2, y2]
    var uv: FloatArray? = null
    
    // Texture name reference
    var texture: String = ""
    
    // Direction to cull the face
    var cullface: String? = null
    
    // Rotation of the texture in degrees (clockwise)
    var rotation: Int = 0
    
    // Tint index for biome coloring
    var tintindex: Int = -1
    
    // Texture ID for the texture atlas
    var textureId: Int = -1
    
    override fun toString(): String {
        return "Face(texture=$texture, rotation=$rotation, uv=${uv?.contentToString()})"
    }
}