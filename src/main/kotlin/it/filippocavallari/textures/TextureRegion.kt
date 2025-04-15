package it.filippocavallari.textures

/**
 * Represents a region within a texture atlas.
 * Contains normalized UV coordinates (range 0-1).
 */
class TextureRegion(
    /**
     * Get the left U coordinate.
     */
    val u1: Float, // Left
    
    /**
     * Get the top V coordinate.
     */
    val v1: Float, // Top
    
    /**
     * Get the right U coordinate.
     */
    val u2: Float, // Right
    
    /**
     * Get the bottom V coordinate.
     */
    val v2: Float  // Bottom
) {
    /**
     * Get the width of the region in UV space.
     */
    val width: Float
        get() = u2 - u1
    
    /**
     * Get the height of the region in UV space.
     */
    val height: Float
        get() = v2 - v1
    
    override fun toString(): String {
        return String.format("TextureRegion[u1=%.3f, v1=%.3f, u2=%.3f, v2=%.3f]", u1, v1, u2, v2)
    }
}