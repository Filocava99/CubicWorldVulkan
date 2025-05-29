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
        println("Generating flat world chunk at (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z})")
        val worldYOffset = chunk.position.y * Chunk.HEIGHT

        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                for (localY in 0 until Chunk.HEIGHT) {
                    val currentWorldY = worldYOffset + localY
                    var blockId = BlockType.AIR.id // Default to air

                    if (currentWorldY == BEDROCK_LEVEL) {
                        blockId = BlockType.BEDROCK.id
                    } else if (currentWorldY > BEDROCK_LEVEL && currentWorldY <= STONE_LEVEL) {
                        blockId = BlockType.STONE.id
                    } else if (currentWorldY > STONE_LEVEL && currentWorldY <= DIRT_LEVEL) {
                        blockId = BlockType.DIRT.id
                    } else if (currentWorldY > DIRT_LEVEL && currentWorldY <= GRASS_LEVEL) {
                        // Place grass only at the exact GRASS_LEVEL
                        if (currentWorldY == GRASS_LEVEL) {
                             blockId = BlockType.GRASS.id
                        }
                        // Other Y levels in this range will remain air unless specified otherwise
                    }
                    // Any other currentWorldY values will result in BlockType.AIR.id as set by default

                    chunk.setBlock(x, localY, z, blockId)
                }
                // Reference markers (only if chunk.position.y == 0 for simplicity)
                if (chunk.position.y == 0) {
                    addReferenceMarkers(chunk, x, z)
                }
            }
        }
        println("Flat world chunk generation complete for (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z})")
    }
    
    /**
     * Add visual reference markers to help with orientation
     */
    private fun addReferenceMarkers(chunk: Chunk, x: Int, z: Int) {
        // Create markers only at chunk borders for visual reference
        if (x == 0 || x == Chunk.SIZE - 1 || z == 0 || z == Chunk.SIZE - 1) {
            // Add a bedrock block at the borders above grass level, if space allows
            if (GRASS_LEVEL + 1 < Chunk.HEIGHT) {
                chunk.setBlock(x, GRASS_LEVEL + 1, z, BlockType.BEDROCK.id)
            }
        }
        
        // Create a small pillar at chunk center for orientation
        if (x == Chunk.SIZE / 2 && z == Chunk.SIZE / 2) {
            // Create a 1-block tall marker at the center, if space allows
            if (GRASS_LEVEL + 1 < Chunk.HEIGHT) {
                chunk.setBlock(x, GRASS_LEVEL + 1, z, BlockType.BEDROCK.id)
            }
        }
    }
}