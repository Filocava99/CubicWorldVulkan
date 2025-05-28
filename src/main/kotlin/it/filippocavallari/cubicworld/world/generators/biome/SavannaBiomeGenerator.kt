package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.min

/**
 * Generator for savanna biomes with scattered acacia trees and dry grass.
 */
class SavannaBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 7
    override val name: String = "Savanna"
    override val temperature: Float = 0.85f
    override val humidity: Float = 0.3f
    
    /**
     * Override surface generation for savanna - drier appearance
     */
    override fun generateSurface(chunk: Chunk, x: Int, z: Int, height: Int) {
        // Add a thinner dirt layer for savanna
        for (y in height - 2 until height) {
            chunk.setBlock(x, y, z, BlockType.DIRT.id)
        }
        
        // Top layer - grass with some variation
        val worldX = chunk.position.x * Chunk.SIZE + x
        val worldZ = chunk.position.y * Chunk.SIZE + z
        
        val surfaceNoise = NoiseFactory.simplexNoise(
            worldX.toFloat(),
            worldZ.toFloat(),
            0.1f
        )
        
        // Mix grass with dirt for a dry appearance
        if (surfaceNoise > 0.3f) {
            chunk.setBlock(x, height, z, BlockType.GRASS.id)
        } else {
            chunk.setBlock(x, height, z, BlockType.DIRT.id)
        }
    }
    
    /**
     * Get the height for savanna biome - flat with gentle rolling hills
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Create gentle rolling terrain
        val savannaNoiseᅟ = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() + seed * 0.02f,
            worldZ.toFloat() + seed * 0.03f,
            2,
            0.03f
        )
        
        // Moderate height variations
        val heightVariation = (savannaNoiseᅟ * 8).toInt()
        
        return baseHeight + heightVariation
    }
    
    /**
     * Generate decorations for savanna biome - scattered acacia trees
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
        
        // Sparse tree coverage for savanna
        val treeAttempts = (width * length * 0.03).toInt() + 1
        
        // Try to place trees
        for (i in 0 until treeAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            if (height > SEA_LEVEL) {
                if (!isTreeNearby(chunk, x, z, height, 6)) {
                    // Generate acacia-style trees
                    generateAcaciaTree(chunk, x, height + 1, z, random)
                }
            }
        }
    }
    
    /**
     * Check if there's a tree nearby (larger spacing for savanna)
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
     * Generate an acacia tree with distinctive flat canopy
     */
    private fun generateAcaciaTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        if (y + 8 >= Chunk.HEIGHT) return
        
        val trunkHeight = 4 + random.nextInt(3)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            if (ty >= Chunk.HEIGHT) break
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate flat, umbrella-like canopy
        val canopyY = y + trunkHeight
        val canopyRadius = 3 + random.nextInt(2)
        
        // Create flat canopy layers
        for (layer in 0..2) {
            val layerY = canopyY + layer
            if (layerY >= Chunk.HEIGHT) break
            
            val layerRadius = canopyRadius - layer
            if (layerRadius <= 0) continue
            
            for (lx in x - layerRadius..x + layerRadius) {
                for (lz in z - layerRadius..z + layerRadius) {
                    if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) continue
                    
                    val dx = kotlin.math.abs(lx - x)
                    val dz = kotlin.math.abs(lz - z)
                    val distance = kotlin.math.sqrt((dx * dx + dz * dz).toDouble())
                    
                    // Create umbrella shape - wider at the top
                    if (distance <= layerRadius && chunk.getBlock(lx, layerY, lz) == 0) {
                        // Use different probability for edge vs center
                        val edgeProbability = if (distance >= layerRadius - 1) 0.7f else 0.9f
                        
                        if (random.nextFloat() < edgeProbability) {
                            chunk.setBlock(lx, layerY, lz, BlockType.LEAVES_OAK.id)
                        }
                    }
                }
            }
        }
        
        // Add some supporting branches
        val branchCount = 1 + random.nextInt(3)
        for (i in 0 until branchCount) {
            val branchDir = random.nextInt(4)
            val branchLength = 1 + random.nextInt(2)
            
            for (j in 1..branchLength) {
                val branchX = x + if (branchDir == 0) j else if (branchDir == 1) -j else 0
                val branchZ = z + if (branchDir == 2) j else if (branchDir == 3) -j else 0
                val branchY = y + trunkHeight - 1
                
                if (branchX in 0 until Chunk.SIZE && branchZ in 0 until Chunk.SIZE && branchY < Chunk.HEIGHT) {
                    if (chunk.getBlock(branchX, branchY, branchZ) == 0) {
                        chunk.setBlock(branchX, branchY, branchZ, BlockType.LOG_OAK.id)
                    }
                }
            }
        }
    }
}
