package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
import kotlin.math.min

/**
 * Generator for taiga biomes with dense spruce forests and rolling hills.
 */
class TaigaBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 6
    override val name: String = "Taiga"
    override val temperature: Float = 0.1f  // Moved to very cold range
    override val humidity: Float = 0.7f    // Lowered slightly
    
    /**
     * Get the height for taiga biome - moderate hills
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Create moderate rolling hills with some randomness
        val hillNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() + seed * 0.03f,
            worldZ.toFloat() + seed * 0.04f,
            3,
            0.025f
        )
        
        // Moderate height variations for taiga
        val heightVariation = (hillNoise * 15).toInt()
        
        return baseHeight + heightVariation
    }
    
    /**
     * Generate decorations for taiga biome - dense spruce forests
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
        
        // Dense forest coverage for taiga
        val treeAttempts = (width * length * 0.12).toInt() + 1
        
        // Try to place trees
        for (i in 0 until treeAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            if (height > SEA_LEVEL) {
                if (!isTreeNearby(chunk, x, z, height, 3)) {
                    // Mostly spruce trees with occasional birch
                    if (random.nextFloat() < 0.8f) {
                        generateSpruceTree(chunk, x, height + 1, z, random)
                    } else {
                        generateBirchTree(chunk, x, height + 1, z, random)
                    }
                }
            }
        }
    }
    
    /**
     * Check if there's a tree nearby
     */
    private fun isTreeNearby(chunk: Chunk, x: Int, z: Int, height: Int, radius: Int): Boolean {
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val tx = x + dx
                val tz = z + dz
                
                if (tx !in 0 until Chunk.SIZE || tz !in 0 until Chunk.SIZE) continue
                
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
     * Generate a spruce tree
     */
    private fun generateSpruceTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        if (y + 10 >= Chunk.HEIGHT) return
        
        val trunkHeight = 6 + random.nextInt(4)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            if (ty >= Chunk.HEIGHT) break
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate conical leaves (classic spruce shape)
        for (layer in 0 until 4) {
            val layerY = y + trunkHeight - 4 + layer
            if (layerY >= Chunk.HEIGHT) break
            
            val radius = 3 - layer
            if (radius <= 0) continue
            
            for (lx in x - radius..x + radius) {
                for (lz in z - radius..z + radius) {
                    if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) continue
                    
                    val dx = kotlin.math.abs(lx - x)
                    val dz = kotlin.math.abs(lz - z)
                    val distance = kotlin.math.max(dx, dz)
                    
                    if (distance <= radius && chunk.getBlock(lx, layerY, lz) == 0) {
                        chunk.setBlock(lx, layerY, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
        
        // Top point
        if (y + trunkHeight < Chunk.HEIGHT) {
            chunk.setBlock(x, y + trunkHeight, z, BlockType.LEAVES_OAK.id)
        }
    }
    
    /**
     * Generate a birch tree
     */
    private fun generateBirchTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        if (y + 8 >= Chunk.HEIGHT) return
        
        val trunkHeight = 5 + random.nextInt(3)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            if (ty >= Chunk.HEIGHT) break
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate oval-shaped leaves
        for (lx in x - 2..x + 2) {
            for (ly in y + trunkHeight - 3..y + trunkHeight + 1) {
                for (lz in z - 2..z + 2) {
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= Chunk.HEIGHT || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    val dx = kotlin.math.abs(lx - x)
                    val dy = kotlin.math.abs(ly - (y + trunkHeight - 1))
                    val dz = kotlin.math.abs(lz - z)
                    
                    // Create oval shape (wider horizontally than vertically)
                    if (dx <= 2 && dz <= 2 && dy <= 2 && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
}
