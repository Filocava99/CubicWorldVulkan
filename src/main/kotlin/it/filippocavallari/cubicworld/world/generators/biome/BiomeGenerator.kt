package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.world.chunk.Chunk

/**
 * Interface for a biome generator.
 * Each biome generator is responsible for filling a specific region
 * of a chunk with the appropriate blocks for that biome.
 */
interface BiomeGenerator {
    /**
     * Get the unique identifier for this biome
     */
    val id: Int
    
    /**
     * Get the display name of this biome
     */
    val name: String
    
    /**
     * Get the temperature of this biome (0.0 = cold, 1.0 = hot)
     */
    val temperature: Float
    
    /**
     * Get the humidity of this biome (0.0 = dry, 1.0 = wet)
     */
    val humidity: Float
    
    /**
     * Generate the biome terrain in the chunk at the specified position.
     * 
     * @param chunk The chunk to modify
     * @param startX The starting X coordinate within the chunk
     * @param startZ The starting Z coordinate within the chunk
     * @param width The width of the region to generate
     * @param length The length of the region to generate
     * @param heightMap The pre-generated height map for this region
     * @param seed The world seed for consistent generation
     */
    fun generate(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        heightMap: Array<IntArray>,
        seed: Long
    )
    
    /**
     * Get the height value for a specific position within this biome.
     * This allows biomes to have their own unique terrain shapes.
     * 
     * @param worldX The world X coordinate
     * @param worldZ The world Z coordinate
     * @param baseHeight The base height value from the continent noise
     * @param seed The world seed
     * @return The adjusted height value for this biome
     */
    fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int
    
    /**
     * Generate decorations for this biome (trees, plants, etc.)
     * This is called after the basic terrain is generated.
     * 
     * @param chunk The chunk to modify
     * @param startX The starting X coordinate within the chunk
     * @param startZ The starting Z coordinate within the chunk
     * @param width The width of the region to generate
     * @param length The length of the region to generate
     * @param heightMap The height map for this region
     * @param seed The world seed
     */
    fun generateDecorations(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        heightMap: Array<IntArray>,
        seed: Long
    )
    
    /**
     * Generate cave features for this biome.
     * This allows biomes to have their own unique underground features.
     * 
     * @param chunk The chunk to modify
     * @param startX The starting X coordinate within the chunk
     * @param startZ The starting Z coordinate within the chunk
     * @param width The width of the region to generate
     * @param length The length of the region to generate
     * @param seed The world seed
     */
    fun generateCaves(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        seed: Long
    )
}