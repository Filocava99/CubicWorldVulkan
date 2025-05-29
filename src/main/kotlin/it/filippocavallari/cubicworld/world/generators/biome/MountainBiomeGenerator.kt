package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Generator for mountain biomes with steep cliffs and stone peaks.
 */
class MountainBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 3
    override val name: String = "Mountains"
    override val temperature: Float = 0.4f
    override val humidity: Float = 0.5f
    
    /**
     * Override the surface generation for mountains
     */
    override fun generateSurface(chunk: Chunk, x: Int, z: Int, height: Int) {
        // Base surface is stone
        val dirtDepth = if (height > 120) {
            // Less dirt at high elevations
            1 + (height % 2)
        } else {
            // More dirt at lower elevations
            3 + (height % 3)
        }
        
        // Add dirt layer if below snow line
        if (height < SNOW_LINE) {
            for (y in max(height - dirtDepth, 0) until height) {
                chunk.setBlock(x, y, z, BlockType.DIRT.id)
            }
            chunk.setBlock(x, height, z, BlockType.GRASS.id)
        } else {
            // Stone with snow at higher elevations
            chunk.setBlock(x, height, z, BlockType.STONE.id)
            // Snow layer would go here if we had that block type
        }
    }
    
    /**
     * Get the height for mountain biome - dramatic peaks and valleys
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Create sharp mountain peaks with ridged noise
        val mountainNoise = NoiseFactory.ridgedNoise(
            worldX.toFloat(),
            worldZ.toFloat(),
            0.01f,
            4,
            0.5f,
            2.5f
        )
        
        // Apply exponential function to create sharper peaks
        // Increased multiplier from 70 to 100 for taller mountains
        val peakFactor = mountainNoise.pow(2.5f) * 100
        
        // Apply extra height to base terrain
        // Removed the -5 buffer to allow mountains to reach closer to max height
        return min(baseHeight + peakFactor.toInt(), MAX_HEIGHT - 2)
    }
    
    /**
     * Generate mountain-specific caves
     * Mountains have cave systems with more vertical variation
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
        
        // 3D Perlin noise for cave generation
        for (x in startX until endX) {
            for (z in startZ until endZ) {
                // Higher cave ceiling in mountains
                val caveCeiling = 180
                
                for (y in BEDROCK_LEVEL + 2 until caveCeiling) {
                    // Skip if already air
                    if (chunk.getBlock(x, y, z) == 0) continue
                    
                    // Calculate world coordinates
                    val worldX = (chunkX * Chunk.SIZE) + x
                    val worldZ = (chunkZ * Chunk.SIZE) + z
                    
                    // Generate cave using 3D noise with vertical stretching
                    val caveNoise = NoiseFactory.caveNoise(
                        worldX.toFloat(), 
                        y.toFloat() * 0.7f, // Vertical stretching factor
                        worldZ.toFloat(),
                        0.025f
                    )
                    
                    // Variable threshold to create more connected vertical cave systems
                    val threshold = 0.32f + (y % 7) * 0.01f
                    
                    if (caveNoise > threshold) {
                        chunk.setBlock(x, y, z, 0) // Set to air
                    }
                }
            }
        }
    }
    
    /**
     * Generate decorations for the mountain biome (sparse trees)
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
        
        // Calculate number of features to generate - mountains have sparse vegetation
        val featureAttempts = (width * length * 0.02).toInt() + 1
        
        // Try to place features
        for (i in 0 until featureAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            // Skip if out of bounds
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            // Only place features below snow line
            if (height > SEA_LEVEL && height < SNOW_LINE) {
                // Generate spruce trees occasionally
                if (random.nextFloat() < 0.3f) {
                    generateSpruceTree(chunk, x, height + 1, z, random)
                }
            }
        }
    }
    
    /**
     * Generate a spruce tree
     */
    private fun generateSpruceTree(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough vertical space
        if (y + 7 >= Chunk.HEIGHT) return
        
        // Trunk height (6-8 blocks)
        val trunkHeight = 6 + random.nextInt(3)
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            // Skip if out of bounds
            if (ty >= Chunk.HEIGHT) break
            
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate leaves (conical shape)
        val leafRadius = 2
        for (ly in y + 2 until y + trunkHeight) {
            // Calculate radius for this level
            val levelRadius = max(1, leafRadius - (ly - (y + 2)))
            
            for (lx in x - levelRadius..x + levelRadius) {
                for (lz in z - levelRadius..z + levelRadius) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Calculate distance from trunk
                    val dx = lx - x
                    val dz = lz - z
                    val distance = kotlin.math.sqrt((dx * dx + dz * dz).toDouble())
                    
                    // Add leaves in a conical pattern
                    if (distance <= levelRadius && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
        
        // Top leaf
        if (y + trunkHeight < Chunk.HEIGHT) {
            chunk.setBlock(x, y + trunkHeight, z, BlockType.LEAVES_OAK.id)
        }
    }
    
    companion object {
        // Snow line - above this elevation, stone instead of grass
        private const val SNOW_LINE = 140
    }
}