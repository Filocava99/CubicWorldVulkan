package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generator for magical forest biomes inspired by the "Biomes O' Plenty" mod.
 * Features blue terrain, giant mushroom-like trees, and magical patterns.
 */
class MagicalForestBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 100 // Using high IDs for mod-inspired biomes
    override val name: String = "Magical Forest"
    override val temperature: Float = 0.7f
    override val humidity: Float = 0.8f
    
    /**
     * Override the surface generation for magical forest
     */
    override fun generateSurface(chunk: Chunk, x: Int, z: Int, height: Int) {
        // In a real implementation, we'd have custom blocks for this biome
        // But we'll use existing blocks creatively
        
        // Add a mixed soil layer
        for (y in max(height - 5, 0) until height) {
            chunk.setBlock(x, y, z, BlockType.DIRT.id)
        }
        
        // Top layer - grass
        chunk.setBlock(x, height, z, BlockType.GRASS.id)
        
        // Occasionally replace with other block types to create visual variety
        val worldX = chunk.position.x * Chunk.SIZE + x
        val worldZ = chunk.position.y * Chunk.SIZE + z
        
        val patternNoise = NoiseFactory.simplexNoise(
            worldX.toFloat(),
            worldZ.toFloat(),
            0.07f
        )
        
        // Create magical patterns with different block types
        if (patternNoise > 0.6f) {
            // Would use a custom magical block here
            // For now, using stone as a placeholder
            chunk.setBlock(x, height, z, BlockType.STONE.id)
        }
    }
    
    /**
     * Get the height for magical forest biome - rolling hills with occasional spikes
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Create magical terrain pattern
        val magicalNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat(),
            worldZ.toFloat(),
            3,
            0.02f
        )
        
        // Add occasional sharp spikes for dramatic effect
        val spikeNoise = NoiseFactory.simplexNoise(
            worldX.toFloat() * 0.04f, 
            worldZ.toFloat() * 0.04f, 
            1.0f
        )
        
        // Calculate height variation
        var heightVariation = (magicalNoise * 12).toInt()
        
        // Add spikes occasionally
        if (spikeNoise > 0.8f) {
            heightVariation += (spikeNoise * 15).toInt()
        }
        
        return baseHeight + heightVariation
    }
    
    /**
     * Generate magical caves with interesting patterns
     */
    override fun generateCaves(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        seed: Long
    ) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        val endX = min(startX + width, Chunk.SIZE)
        val endZ = min(startZ + length, Chunk.SIZE)
        
        // Generate standard caves first
        super.generateCaves(chunk, startX, startZ, width, length, seed)
        
        // Add special magical cave patterns
        for (x in startX until endX) {
            for (z in startZ until endZ) {
                for (y in 25..70) {
                    // Skip if already air
                    if (chunk.getBlock(x, y, z) == 0) continue
                    
                    // Calculate world coordinates
                    val worldX = (chunkX * Chunk.SIZE) + x
                    val worldZ = (chunkZ * Chunk.SIZE) + z
                    
                    // Create magical swirl patterns for caves
                    val swirlX = sin(worldX * 0.04f + y * 0.1f) * 5
                    val swirlZ = sin(worldZ * 0.04f + y * 0.1f) * 5
                    
                    val magicalCaveNoise = NoiseFactory.simplexNoise3D(
                        (worldX + swirlX).toFloat(),
                        y.toFloat(),
                        (worldZ + swirlZ).toFloat(),
                        0.05f
                    )
                    
                    // Create cave where noise is above threshold
                    if (magicalCaveNoise > 0.35f && magicalCaveNoise < 0.4f) {
                        chunk.setBlock(x, y, z, 0) // Air for caves
                    }
                }
            }
        }
    }
    
    /**
     * Generate decorations for the magical forest biome
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
        
        // Calculate number of features to generate
        val treeAttempts = (width * length * 0.05).toInt() + 1
        
        // Try to place magical trees
        for (i in 0 until treeAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            // Skip if out of bounds
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            // Only place trees above sea level
            if (height > SEA_LEVEL) {
                // Different tree types based on random value
                val treeType = random.nextFloat()
                
                if (treeType < 0.3f) {
                    generateMagicalTree(chunk, x, height + 1, z, random)
                } else if (treeType < 0.6f) {
                    generateGiantMushroom(chunk, x, height + 1, z, random)
                } else {
                    generateCrystalFormation(chunk, x, height + 1, z, random)
                }
            }
        }
    }
    
    /**
     * Generate a magical tree
     */
    private fun generateMagicalTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough vertical space
        if (y + 12 >= Chunk.HEIGHT) return
        
        // Trunk height (7-10 blocks)
        val trunkHeight = 7 + random.nextInt(4)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate swirling leaves pattern
        for (ly in y + trunkHeight - 6..y + trunkHeight + 2) {
            // Calculate radius for this level with a spiral pattern
            val spiralOffset = ((ly - y) * 0.4).toFloat()
            val angle = (ly - y) * 0.5f
            val levelRadius = 2 + (spiralOffset * 0.8f).toInt()
            
            for (lx in x - levelRadius..x + levelRadius) {
                for (lz in z - levelRadius..z + levelRadius) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= Chunk.HEIGHT || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Calculate spiral pattern position
                    val dx = lx - x
                    val dz = lz - z
                    val dist = sqrt((dx * dx + dz * dz).toDouble())
                    
                    // Add leaves in a spiral pattern
                    if (dist <= levelRadius - (angle % 1.5f) && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
    
    /**
     * Generate a giant mushroom
     */
    private fun generateGiantMushroom(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough vertical space
        if (y + 8 >= Chunk.HEIGHT) return
        
        // Stem height (4-6 blocks)
        val stemHeight = 4 + random.nextInt(3)
        
        // Generate stem
        for (ty in y until y + stemHeight) {
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id) // Using oak log as stem
        }
        
        // Generate cap
        val capRadius = 3 + random.nextInt(2)
        
        for (lx in x - capRadius..x + capRadius) {
            for (ly in y + stemHeight until y + stemHeight + 3) {
                for (lz in z - capRadius..z + capRadius) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= Chunk.HEIGHT || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Calculate distance from center
                    val dx = lx - x
                    val dy = ly - (y + stemHeight)
                    val dz = lz - z
                    
                    // Distance squared for comparison
                    val distSq = dx * dx + dy * dy * 3 + dz * dz
                    
                    // Add mushroom cap in an ellipsoid shape
                    if (distSq <= capRadius * capRadius && chunk.getBlock(lx, ly, lz) == 0) {
                        if (dy == 0 || distSq >= (capRadius - 1) * (capRadius - 1)) {
                            // Use bedrock for the cap (as a placeholder for a red mushroom block)
                            chunk.setBlock(lx, ly, lz, BlockType.BEDROCK.id)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Generate a crystal formation
     */
    private fun generateCrystalFormation(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough vertical space
        if (y + 5 >= Chunk.HEIGHT) return
        
        // Crystal height (3-5 blocks)
        val crystalHeight = 3 + random.nextInt(3)
        
        // Generate crystal shape
        for (cy in y until y + crystalHeight) {
            // Skip if out of bounds
            if (cy >= Chunk.HEIGHT) break
            
            // Calculate radius at this height
            val radius = crystalHeight - (cy - y)
            
            for (cx in x - radius..x + radius) {
                for (cz in z - radius..z + radius) {
                    // Skip if outside chunk bounds
                    if (cx < 0 || cx >= Chunk.SIZE || cz < 0 || cz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Calculate distance from center axis
                    val dx = cx - x
                    val dz = cz - z
                    val dist = max(abs(dx), abs(dz))
                    
                    // Create diamond/crystal shape
                    if (dist <= radius && 
                        // Only place at certain positions to create crystal pattern
                        (dist == radius || cx == x || cz == z) && 
                        chunk.getBlock(cx, cy, cz) == 0) {
                        // Using iron ore as a placeholder for crystal blocks
                        chunk.setBlock(cx, cy, cz, BlockType.IRON_ORE.id)
                    }
                }
            }
        }
    }
    
    /**
     * Absolute value function
     */
    private fun abs(value: Int): Int {
        return if (value < 0) -value else value
    }
}