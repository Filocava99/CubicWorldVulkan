package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
import kotlin.math.min

/**
 * Generator for swamp biomes with water, scattered dead trees, and flat terrain.
 */
class SwampBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 5
    override val name: String = "Swamp"
    override val temperature: Float = 0.7f  // Moved to warm-hot range
    override val humidity: Float = 0.8f    // Moved down slightly
    
    /**
     * Override surface generation for swampy areas
     */
    override fun generateSurface(chunk: Chunk, x: Int, z: Int, height: Int) {
        // Add deeper dirt layer for swamps
        for (y in max(height - 6, 0) until height) {
            chunk.setBlock(x, y, z, BlockType.DIRT.id)
        }
        
        // Top layer - grass or water depending on height
        if (height <= SEA_LEVEL + 2) {
            // Keep swampy areas at or below sea level
            chunk.setBlock(x, height, z, BlockType.DIRT.id)
        } else {
            chunk.setBlock(x, height, z, BlockType.GRASS.id)
        }
    }
    
    /**
     * Get the height for swamp biome - very flat terrain
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Swamps are very flat with minimal variation
        val swampNoise = NoiseFactory.simplexNoise(
            worldX.toFloat() + seed * 0.05f,
            worldZ.toFloat() + seed * 0.06f,
            0.05f
        )
        
        // Very small height variations and bias towards sea level
        val heightVariation = (swampNoise * 3).toInt()
        val targetHeight = SEA_LEVEL + heightVariation
        
        // Blend towards sea level to create swampy areas
        return (baseHeight + targetHeight) / 2
    }
    
    /**
     * Generate swamp decorations - dead trees and water patches
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
        
        // Moderate tree coverage for swamps
        val treeAttempts = (width * length * 0.04).toInt() + 1
        
        // Generate water patches first
        generateWaterPatches(chunk, startX, startZ, endX, endZ, heightMap, random)
        
        // Try to place dead trees
        for (i in 0 until treeAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            // Place dead trees even at/below sea level
            if (height >= SEA_LEVEL - 1) {
                if (!isTreeNearby(chunk, x, z, height, 4)) {
                    generateDeadTree(chunk, x, height + 1, z, random)
                }
            }
        }
    }
    
    /**
     * Generate water patches in the swamp
     */
    private fun generateWaterPatches(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        endX: Int,
        endZ: Int,
        heightMap: Array<IntArray>,
        random: java.util.Random
    ) {
        val patchAttempts = (endX - startX) * (endZ - startZ) / 32
        
        for (i in 0 until patchAttempts) {
            val centerX = startX + random.nextInt(endX - startX)
            val centerZ = startZ + random.nextInt(endZ - startZ)
            
            if (centerX !in 0 until Chunk.SIZE || centerZ !in 0 until Chunk.SIZE) continue
            
            val centerHeight = heightMap[centerX][centerZ]
            
            // Only create water patches at low elevations
            if (centerHeight <= SEA_LEVEL + 1) {
                val patchSize = 2 + random.nextInt(3)
                
                for (px in centerX - patchSize..centerX + patchSize) {
                    for (pz in centerZ - patchSize..centerZ + patchSize) {
                        if (px < 0 || px >= Chunk.SIZE || pz < 0 || pz >= Chunk.SIZE) continue
                        
                        val distance = kotlin.math.sqrt(
                            ((px - centerX) * (px - centerX) + (pz - centerZ) * (pz - centerZ)).toDouble()
                        )
                        
                        if (distance <= patchSize && heightMap[px][pz] <= SEA_LEVEL + 1) {
                            // Create small water depression
                            heightMap[px][pz] = SEA_LEVEL - 1
                            chunk.setBlock(px, SEA_LEVEL - 1, pz, BlockType.DIRT.id)
                            chunk.setBlock(px, SEA_LEVEL, pz, BlockType.WATER.id)
                        }
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
     * Generate a dead tree (just trunk, no leaves)
     */
    private fun generateDeadTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        if (y + 8 >= Chunk.HEIGHT) return
        
        val trunkHeight = 3 + random.nextInt(4)
        
        // Generate trunk only (dead tree)
        for (ty in y until y + trunkHeight) {
            if (ty >= Chunk.HEIGHT) break
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Occasionally add a few dead branches (no leaves)
        if (random.nextFloat() < 0.3f && y + trunkHeight < Chunk.HEIGHT) {
            // Add a single branch extending out
            val branchDir = random.nextInt(4)
            val branchX = x + if (branchDir == 0) 1 else if (branchDir == 1) -1 else 0
            val branchZ = z + if (branchDir == 2) 1 else if (branchDir == 3) -1 else 0
            
            if (branchX in 0 until Chunk.SIZE && branchZ in 0 until Chunk.SIZE) {
                chunk.setBlock(branchX, y + trunkHeight - 1, branchZ, BlockType.LOG_OAK.id)
            }
        }
    }
}
