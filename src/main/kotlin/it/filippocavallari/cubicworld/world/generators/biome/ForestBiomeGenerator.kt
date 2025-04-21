package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Generator for forest biomes with oak trees and gentle hills.
 */
class ForestBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 1
    override val name: String = "Forest"
    override val temperature: Float = 0.6f
    override val humidity: Float = 0.6f
    
    /**
     * Get the height for the forest biome - gentle hills
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Add some variability with medium hills - ensure we use true world coordinates
        // Mix in the seed to break up patterns while maintaining consistency across worlds
        val detailNoise = NoiseFactory.ridgedNoise(
            worldX.toFloat() + (seed % 1000 * 0.01f),
            worldZ.toFloat() + (seed % 500 * 0.01f),
            0.02f,
            3
        )
        
        // Apply the detail noise to the base height
        val detailHeight = (detailNoise * 12).toInt()
        
        return baseHeight + detailHeight
    }
    
    /**
     * Generate decorations for the forest biome
     */
    override fun generateDecorations(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        heightMap: Array<IntArray>,
        seed: Long
    ) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        val endX = min(startX + width, Chunk.SIZE)
        val endZ = min(startZ + length, Chunk.SIZE)
        val random = java.util.Random(seed + chunkX * 341873128712L + chunkZ * 132897987541L)
        
        // Calculate number of trees to generate
        val treeAttempts = (width * length * 0.08).toInt() + 1
        
        // Try to place trees
        for (i in 0 until treeAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            // Skip if out of bounds
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            // Only place trees above sea level
            if (height > SEA_LEVEL) {
                // Check if there's already a tree nearby
                if (!isTreeNearby(chunk, x, z, height, 3)) {
                    // Random chance for different tree types
                    val treeType = random.nextInt(10)
                    
                    if (treeType < 7) {
                        generateOakTree(chunk, x, height + 1, z, random)
                    } else {
                        generateBirchTree(chunk, x, height + 1, z, random)
                    }
                }
            }
        }
        
        // Generate undergrowth (grass, flowers)
        generateUndergrowth(chunk, startX, startZ, endX, endZ, heightMap, random)
    }
    
    /**
     * Check if there's a tree near the specified position
     */
    private fun isTreeNearby(chunk: Chunk, x: Int, z: Int, height: Int, radius: Int): Boolean {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val tx = x + dx
                val tz = z + dz
                
                // Skip if out of bounds
                if (tx !in 0 until Chunk.SIZE || tz !in 0 until Chunk.SIZE) continue
                
                // Check if there's a log at this position
                for (ty in height until height + 10) {
                    if (ty >= Chunk.HEIGHT) break
                    
                    if (chunk.getBlock(tx, ty, tz) == BlockType.LOG_OAK.id) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    /**
     * Generate an oak tree at the specified position
     */
    private fun generateOakTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough vertical space
        if (y + 7 >= Chunk.HEIGHT) return
        
        // Trunk height (5-7 blocks)
        val trunkHeight = 5 + random.nextInt(3)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            // Skip if out of bounds
            if (ty >= Chunk.HEIGHT) break
            
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate leaves (spherical shape)
        for (lx in x - 2..x + 2) {
            for (ly in y + trunkHeight - 3..y + trunkHeight + 1) {
                for (lz in z - 2..z + 2) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= Chunk.HEIGHT || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Distance from trunk
                    val distance = sqrt(
                        (lx - x).toDouble().pow(2) + 
                        (ly - (y + trunkHeight - 1)).toDouble().pow(2) + 
                        (lz - z).toDouble().pow(2)
                    )
                    
                    // Add leaves in a spherical pattern
                    if (distance <= 2.5 && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
    
    /**
     * Generate a birch tree at the specified position
     */
    private fun generateBirchTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough vertical space
        if (y + 7 >= Chunk.HEIGHT) return
        
        // Trunk height (6-8 blocks, slightly taller than oak)
        val trunkHeight = 6 + random.nextInt(3)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            // Skip if out of bounds
            if (ty >= Chunk.HEIGHT) break
            
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id) // Using oak log for now
        }
        
        // Generate leaves (more oval shape)
        for (lx in x - 2..x + 2) {
            for (ly in y + trunkHeight - 4..y + trunkHeight) {
                for (lz in z - 2..z + 2) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= Chunk.HEIGHT || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Distance from trunk center, with different scaling for height
                    val horizontalDist = sqrt((lx - x).toDouble().pow(2) + (lz - z).toDouble().pow(2))
                    val verticalDist = (ly - (y + trunkHeight - 2)).toDouble()
                    val distance = sqrt(horizontalDist.pow(2) + (verticalDist * 1.5).pow(2))
                    
                    // Add leaves in a oval pattern
                    if (distance <= 2.2 && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id) // Using oak leaves for now
                    }
                }
            }
        }
    }
    
    /**
     * Generate undergrowth like grass and flowers
     */
    private fun generateUndergrowth(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        endX: Int,
        endZ: Int,
        heightMap: Array<IntArray>,
        random: java.util.Random
    ) {
        // We'd want to add tall grass and flowers here
        // For now, we don't have a proper implementation since the BlockType enum
        // doesn't include decoration blocks yet
    }
}