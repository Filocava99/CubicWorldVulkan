package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk

/**
 * A simple flat world generator.
 * Creates a flat world with specific layers of different block types.
 */
class FlatWorldGenerator : WorldGenerator {
    
    companion object {
        // Layer heights for the flat world - simple and predictable
        private const val BEDROCK_LEVEL = 0     // Bedrock only at bottom (y=0)
        private const val STONE_LEVEL = 10      // Stone layer from y=1 to y=10
        private const val DIRT_LEVEL = 13       // Dirt layer from y=11 to y=13
        private const val GRASS_LEVEL = 14      // Grass only at top (y=14)
    }
    
    override fun generateChunk(chunk: Chunk) {
        println("Generating flat world chunk at (${chunk.position.x}, ${chunk.position.y})")
        
        // Fill the chunk with the appropriate blocks
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Add bedrock at the bottom
                chunk.setBlock(x, BEDROCK_LEVEL, z, BlockType.BEDROCK.id)
                
                // Fill with stone up to stone level
                for (y in BEDROCK_LEVEL + 1..STONE_LEVEL) {
                    chunk.setBlock(x, y, z, BlockType.STONE.id)
                }
                
                // Add dirt layer
                for (y in STONE_LEVEL + 1..DIRT_LEVEL) {
                    chunk.setBlock(x, y, z, BlockType.DIRT.id)
                }
                
                // Add grass on top
                chunk.setBlock(x, GRASS_LEVEL, z, BlockType.GRASS.id)
                
                // Add reference markers for orientation (only at chunk borders)
                addReferenceMarkers(chunk, x, z)
            }
        }
        
        println("Flat world chunk generation complete for (${chunk.position.x}, ${chunk.position.y})")
    }
    
    /**
     * Add visual reference markers to help with orientation
     */
    private fun addReferenceMarkers(chunk: Chunk, x: Int, z: Int) {
        // Create markers only at chunk borders for visual reference
        if (x == 0 || x == Chunk.SIZE - 1 || z == 0 || z == Chunk.SIZE - 1) {
            // Add a bedrock block at the borders above grass level
            chunk.setBlock(x, GRASS_LEVEL + 1, z, BlockType.BEDROCK.id)
        }
        
        // Create a small pillar at chunk center for orientation
        if (x == Chunk.SIZE / 2 && z == Chunk.SIZE / 2) {
            // Create a 3-block tall pillar at the center
            for (y in GRASS_LEVEL + 1..GRASS_LEVEL + 3) {
                chunk.setBlock(x, y, z, BlockType.BEDROCK.id)
            }
        }
    }
}