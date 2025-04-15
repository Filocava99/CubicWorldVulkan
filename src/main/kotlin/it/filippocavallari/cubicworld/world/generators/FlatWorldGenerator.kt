package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk

/**
 * A simple flat world generator.
 * Creates a flat world with a few layers of different block types.
 */
class FlatWorldGenerator : WorldGenerator {
    
    companion object {
        private const val STONE_LEVEL = 60
        private const val DIRT_LEVEL = 67
        private const val GRASS_LEVEL = 68
    }
    
    override fun generateChunk(chunk: Chunk) {
        // Fill the chunk with the appropriate blocks
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Add bedrock at the bottom
                chunk.setBlock(x, 0, z, BlockType.BEDROCK.id)
                
                // Fill with stone up to stone level
                for (y in 1..STONE_LEVEL) {
                    chunk.setBlock(x, y, z, BlockType.STONE.id)
                }
                
                // Add dirt layer
                for (y in STONE_LEVEL + 1..DIRT_LEVEL) {
                    chunk.setBlock(x, y, z, BlockType.DIRT.id)
                }
                
                // Add grass on top
                chunk.setBlock(x, GRASS_LEVEL, z, BlockType.GRASS.id)
                
                // Add some random features for variety
                addRandomFeatures(chunk, x, z)
                
                // Add visual reference markers (checkerboard pattern)
                addReferenceMarkers(chunk, x, z)
            }
        }
    }
    
    /**
     * Add some random features to make the flat world more interesting
     */
    private fun addRandomFeatures(chunk: Chunk, x: Int, z: Int) {
        // Get chunk coordinates to have consistent randomness
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        // Random number based on position (simple hash)
        val randomValue = ((chunkX * 31 + chunkZ) * 17 + x * 13 + z * 7) % 1000 / 1000.0f
        
        // Occasionally add trees
        if (randomValue < 0.005 && x > 2 && x < Chunk.SIZE - 2 && z > 2 && z < Chunk.SIZE - 2) {
            generateTree(chunk, x, GRASS_LEVEL + 1, z)
        }
        
        // Occasionally add flowers
        if (randomValue > 0.97) {
            // Add a flower on the grass
            chunk.setBlock(x, GRASS_LEVEL + 1, z, BlockType.getById(37 + (randomValue * 10).toInt() % 9).id)
        }
    }
    
    /**
     * Generate a simple tree
     */
    private fun generateTree(chunk: Chunk, x: Int, y: Int, z: Int) {
        // Create the trunk (4 blocks high)
        for (treeY in y until y + 4) {
            chunk.setBlock(x, treeY, z, BlockType.LOG_OAK.id)
        }
        
        // Create the leaves (a 5x5x3 box with corners missing)
        for (leafX in x - 2..x + 2) {
            for (leafZ in z - 2..z + 2) {
                // Skip the corners for a more natural look
                if ((leafX == x - 2 || leafX == x + 2) && (leafZ == z - 2 || leafZ == z + 2)) {
                    continue
                }
                
                for (leafY in y + 2..y + 4) {
                    // Make the top layer smaller
                    if (leafY == y + 4 && (leafX == x - 2 || leafX == x + 2 || leafZ == z - 2 || leafZ == z + 2)) {
                        continue
                    }
                    
                    // Don't overwrite the trunk
                    if (leafX == x && leafZ == z && leafY < y + 4) {
                        continue
                    }
                    
                    chunk.setBlock(leafX, leafY, leafZ, BlockType.LEAVES_OAK.id)
                }
            }
        }
    }
    
    /**
     * Add visual reference markers to help with orientation
     */
    private fun addReferenceMarkers(chunk: Chunk, x: Int, z: Int) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        // Create checkerboard pattern on chunk borders for visual reference
        if (x == 0 || x == Chunk.SIZE-1 || z == 0 || z == Chunk.SIZE-1) {
            // Border blocks - use brightly colored blocks
            chunk.setBlock(x, GRASS_LEVEL + 1, z, BlockType.BEDROCK.id)
        }
        
        // Create a pillar at chunk center for orientation
        if (x == Chunk.SIZE/2 && z == Chunk.SIZE/2) {
            for (y in GRASS_LEVEL + 1..GRASS_LEVEL + 5) {
                // Different color for each chunk's center pillar based on position
                chunk.setBlock(x, y, z, BlockType.BEDROCK.id)
            }
        }
        
        // Add coordinate reference at origin point
        if (chunkX == 0 && chunkZ == 0) {
            // Create an "origin marker" structure
            for (y in GRASS_LEVEL + 1..GRASS_LEVEL + 10) {
                // Center pillar
                chunk.setBlock(Chunk.SIZE/2, y, Chunk.SIZE/2, BlockType.BEDROCK.id)
                
                // X-axis indicator (red)
                if (y <= GRASS_LEVEL + 5) {
                    for (i in 0 until 5) {
                        val xPos = Chunk.SIZE/2 + i + 1
                        if (xPos < Chunk.SIZE) {
                            chunk.setBlock(xPos, y, Chunk.SIZE/2, BlockType.BEDROCK.id)
                        }
                    }
                    
                    // Z-axis indicator (blue)
                    for (i in 0 until 5) {
                        val zPos = Chunk.SIZE/2 + i + 1
                        if (zPos < Chunk.SIZE) {
                            chunk.setBlock(Chunk.SIZE/2, y, zPos, BlockType.BEDROCK.id)
                        }
                    }
                }
            }
        }
    }
}