package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.min

/**
 * Generator for plains biomes with grass, scattered trees, and gentle rolling terrain.
 */
class PlainsBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 4
    override val name: String = "Plains"
    override val temperature: Float = 0.6f  // Moved slightly left
    override val humidity: Float = 0.3f    // Moved down for better separation
    
    /**
     * Get the height for plains biome - gentle rolling hills
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Create gentle rolling hills with low amplitude
        val hillNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() + seed * 0.01f,
            worldZ.toFloat() + seed * 0.02f,
            2,
            0.02f
        )
        
        // Very gentle height variations for plains
        val heightVariation = (hillNoise * 6).toInt()
        
        return baseHeight + heightVariation
    }
    
    /**
     * Generate decorations for plains biome - scattered trees and grass
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
        
        // Very sparse tree coverage for plains
        val treeAttempts = (width * length * 0.02).toInt() + 1
        
        // Try to place trees
        for (i in 0 until treeAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            if (height > SEA_LEVEL) {
                // Only place trees occasionally to keep plains open
                if (random.nextFloat() < 0.3f && !isTreeNearby(chunk, x, z, height, 8)) {
                    generateOakTree(chunk, x, height + 1, z, random)
                }
            }
        }
    }
    
    /**
     * Check if there's a tree nearby (larger radius for plains)
     */
    private fun isTreeNearby(chunk: Chunk, x: Int, z: Int, height: Int, radius: Int): Boolean {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val tx = x + dx
                val tz = z + dz
                
                if (tx !in 0 until Chunk.SIZE || tz !in 0 until Chunk.SIZE) continue
                
                for (ty in height until height + 8) {
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
     * Generate a simple oak tree
     */
    private fun generateOakTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        if (y + 6 >= Chunk.HEIGHT) return
        
        val trunkHeight = 4 + random.nextInt(3)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            if (ty >= Chunk.HEIGHT) break
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate simple leaf crown
        for (lx in x - 2..x + 2) {
            for (ly in y + trunkHeight - 2..y + trunkHeight + 1) {
                for (lz in z - 2..z + 2) {
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= Chunk.HEIGHT || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    val dx = kotlin.math.abs(lx - x)
                    val dz = kotlin.math.abs(lz - z)
                    val distance = kotlin.math.max(dx, dz)
                    
                    if (distance <= 2 && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
}
