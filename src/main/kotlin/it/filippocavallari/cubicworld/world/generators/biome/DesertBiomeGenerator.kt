package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.min

/**
 * Generator for desert biomes with sand dunes and occasional cacti.
 */
class DesertBiomeGenerator : AbstractBiomeGenerator() {
    
    override val id: Int = 2
    override val name: String = "Desert"
    override val temperature: Float = 0.95f
    override val humidity: Float = 0.1f
    
    /**
     * Override the surface generation for desert (deep sand layers)
     */
    override fun generateSurface(chunk: Chunk, x: Int, z: Int, height: Int) {
        // Sand surface with sandstone underneath
        val sandDepth = 4 + (height % 3) // Variable sand depth
        
        // Add sandstone layer
        for (y in height - sandDepth until height) {
            chunk.setBlock(x, y, z, BlockType.SAND.id)
        }
        
        // Top layer - always sand
        chunk.setBlock(x, height, z, BlockType.SAND.id)
        
        // Debug output for desert surface
        if (x == 8 && z == 8) { // Center of spawn chunk
            println("DEBUG: Placed SAND at ($x, $height, $z) in Desert biome")
        }
    }
    
    /**
     * Get the height for the desert biome - sand dunes
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Create rolling sand dunes with domain warping noise
        val duneNoise = NoiseFactory.domainWarpNoise(
            worldX.toFloat(),
            worldZ.toFloat(),
            5.0f,
            0.02f
        )
        
        // Scale the noise to create dune height variations
        val duneHeight = (duneNoise * 6).toInt()
        
        return baseHeight + duneHeight
    }
    
    /**
     * Generate desert-specific caves
     * Deserts have fewer caves, but sometimes larger cave openings
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
                // Get cave ceiling based on height
                val caveCeiling = SEA_LEVEL - 5
                
                for (y in BEDROCK_LEVEL + 2 until caveCeiling) {
                    // Skip if already air
                    if (chunk.getBlock(x, y, z) == 0) continue
                    
                    // Calculate world coordinates
                    val worldX = (chunkX * Chunk.SIZE) + x
                    val worldZ = (chunkZ * Chunk.SIZE) + z
                    
                    // Generate cave using 3D noise - less caves in desert
                    val caveNoise = NoiseFactory.caveNoise(
                        worldX.toFloat(), y.toFloat(), worldZ.toFloat(),
                        0.03f
                    )
                    
                    // Higher threshold means fewer caves
                    val threshold = 0.4f
                    
                    if (caveNoise > threshold) {
                        chunk.setBlock(x, y, z, 0) // Set to air
                    }
                }
            }
        }
    }
    
    /**
     * Generate decorations for the desert biome (cactus, dead bushes)
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
        // Calculate bounds (not used in current implementation)
        // val endX = min(startX + width, Chunk.SIZE)
        // val endZ = min(startZ + length, Chunk.SIZE)
        val random = java.util.Random(seed + chunkX * 341873128712L + chunkZ * 132897987541L)
        
        // Calculate number of features to generate - deserts have sparse vegetation
        val featureAttempts = (width * length * 0.01).toInt() + 1
        
        // Try to place features
        for (i in 0 until featureAttempts) {
            val x = startX + random.nextInt(width)
            val z = startZ + random.nextInt(length)
            
            // Skip if out of bounds
            if (x !in 0 until Chunk.SIZE || z !in 0 until Chunk.SIZE) continue
            
            val height = heightMap[x][z]
            
            // Only place features above sea level
            if (height > SEA_LEVEL) {
                if (random.nextFloat() < 0.7f) {
                    // Generate cactus (but we're limited by current block types)
                    // Using bedrock as a placeholder for cactus
                    generateCactus(chunk, x, height + 1, z, random)
                } else {
                    // Would generate dead bushes here
                    // Currently no dead bush block type
                }
            }
        }
    }
    
    /**
     * Generate a cactus
     * Using bedrock as a placeholder for cactus blocks
     */
    private fun generateCactus(chunk: Chunk, x: Int, y: Int, z: Int, random: java.util.Random) {
        // Skip if not enough space
        if (y + 3 >= Chunk.HEIGHT) return
        
        // Check if there's space for the cactus
        if (x <= 0 || x >= Chunk.SIZE - 1 || z <= 0 || z >= Chunk.SIZE - 1) return
        
        // Cactus height (1-3 blocks)
        val cactusHeight = 1 + random.nextInt(3)
        
        for (cy in y until y + cactusHeight) {
            if (cy >= Chunk.HEIGHT) break
            
            chunk.setBlock(x, cy, z, BlockType.BEDROCK.id) // Using bedrock as a placeholder
        }
    }
}