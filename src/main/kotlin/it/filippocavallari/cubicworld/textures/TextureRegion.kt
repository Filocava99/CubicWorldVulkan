package it.filippocavallari.cubicworld.textures

/**
 * Represents a rectangular region in a texture atlas.
 * Contains the UV coordinates for the region.
 */
class TextureRegion(
    // UV coordinates (normalized 0-1)
    val u1: Float,
    val v1: Float,
    val u2: Float,
    val v2: Float
) {
    /**
     * Get the width of this region (in UV space)
     */
    val width: Float
        get() = u2 - u1
    
    /**
     * Get the height of this region (in UV space)
     */
    val height: Float
        get() = v2 - v1
    
    /**
     * Get the center U coordinate
     */
    val centerU: Float
        get() = (u1 + u2) * 0.5f
    
    /**
     * Get the center V coordinate
     */
    val centerV: Float
        get() = (v1 + v2) * 0.5f
    
    override fun toString(): String {
        return "TextureRegion(u1=$u1, v1=$v1, u2=$u2, v2=$v2)"
    }
}