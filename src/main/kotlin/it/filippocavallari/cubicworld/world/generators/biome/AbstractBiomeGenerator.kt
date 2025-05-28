package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Abstract base class for biome generators that provides common functionality.
 * Specific biomes can extend this class to inherit common behavior.
 */
abstract class AbstractBiomeGenerator : BiomeGenerator {
    
    // Common terrain constants
    companion object {
        const val SEA_LEVEL = 62
        const val MAX_HEIGHT = 90  // Reduced to match mesh builder limits
        const val MIN_HEIGHT = 10
        const val BEDROCK_LEVEL = 5
    }
    
    /**
     * This implementation handles the basic terrain generation for a biome.
     * Subclasses can override for custom terrain generation.
     */
    override fun generate(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        heightMap: Array<IntArray>,
        seed: Long
    ) {
        val endX = min(startX + width, Chunk.SIZE)
        val endZ = min(startZ + length, Chunk.SIZE)
        
        // Generate the basic terrain shape
        for (x in startX until endX) {
            for (z in startZ until endZ) {
                val height = heightMap[x][z]
                
                // Generate bedrock layer
                for (y in 0..BEDROCK_LEVEL) {
                    // Add noise to make bedrock uneven
                    if (y < BEDROCK_LEVEL || Math.random() > y.toDouble() / BEDROCK_LEVEL) {
                        chunk.setBlock(x, y, z, BlockType.BEDROCK.id)
                    }
                }
                
                // Generate stone layer
                for (y in BEDROCK_LEVEL + 1 until height - 4) {
                    chunk.setBlock(x, y, z, BlockType.STONE.id)
                }
                
                // Generate surfaceDirt, main soil and top layer
                generateSurface(chunk, x, z, height)
                
                // Add water up to sea level if needed
                if (height < SEA_LEVEL) {
                    for (y in height + 1..SEA_LEVEL) {
                        if (chunk.getBlock(x, y, z) == 0) { // Only add water to empty space
                            chunk.setBlock(x, y, z, BlockType.WATER.id)
                        }
                    }
                }
            }
        }
        
        // Generate caves and underground features
        generateCaves(chunk, startX, startZ, width, length, seed)
        
        // Generate ores
        generateOres(chunk, startX, startZ, width, length, seed)
    }
    
    /**
     * Generate surface blocks for this biome.
     * Default implementation adds a dirt layer with grass on top.
     */
    protected open fun generateSurface(chunk: Chunk, x: Int, z: Int, height: Int) {
        // Default is a 3-block dirt layer with grass on top
        for (y in height - 4 until height) {
            chunk.setBlock(x, y, z, BlockType.DIRT.id)
        }
        
        // Top layer - grass if above sea level, otherwise dirt or sand
        if (height > SEA_LEVEL) {
            chunk.setBlock(x, height, z, BlockType.GRASS.id)
        } else {
            chunk.setBlock(x, height, z, BlockType.DIRT.id)
        }
    }
    
    /**
     * Generate ores in the chunk.
     * This is a common implementation that all biomes can use.
     */
    protected fun generateOres(
        chunk: Chunk,
        startX: Int,
        startZ: Int,
        width: Int,
        length: Int,
        seed: Long
    ) {
        // Define ore parameters - type, frequency, min/max height, vein size
        val oreTypes = arrayOf(
            OreType(BlockType.COAL_ORE.id, 20, 5, 128, 8, seed),
            OreType(BlockType.IRON_ORE.id, 15, 5, 64, 6, seed),
            OreType(BlockType.GOLD_ORE.id, 8, 5, 32, 6, seed),
            OreType(BlockType.DIAMOND_ORE.id, 3, 5, 16, 4, seed),
            OreType(BlockType.REDSTONE_ORE.id, 10, 5, 32, 5, seed),
            OreType(BlockType.LAPIS_ORE.id, 5, 5, 32, 4, seed)
        )
        
        val endX = min(startX + width, Chunk.SIZE)
        val endZ = min(startZ + length, Chunk.SIZE)
        val random = java.util.Random(seed + chunk.position.x * 341873128712L + chunk.position.y * 132897987541L)
        
        for (ore in oreTypes) {
            // Number of veins to generate
            val veinsPerRegion = max(1, ore.frequency * width * length / (Chunk.SIZE * Chunk.SIZE))
            
            for (i in 0 until veinsPerRegion) {
                // Random position within region
                val x = startX + random.nextInt(width)
                val y = ore.minHeight + random.nextInt(ore.maxHeight - ore.minHeight)
                val z = startZ + random.nextInt(length)
                
                if (y < 0 || y >= Chunk.HEIGHT) continue
                
                // Generate vein using 3D blob algorithm
                generateOreVein(chunk, x, y, z, ore.veinSize, ore.blockId, random)
            }
        }
    }
    
    /**
     * Generate a vein of ore at the specified position.
     */
    private fun generateOreVein(
        chunk: Chunk,
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        veinSize: Int,
        oreId: Int,
        random: java.util.Random
    ) {
        // Apply 3D ellipsoid algorithm for natural-looking veins
        val ellipsoidA = 1.0 + random.nextDouble() * veinSize / 2.0
        val ellipsoidB = 1.0 + random.nextDouble() * veinSize / 2.0
        val ellipsoidC = 1.0 + random.nextDouble() * veinSize / 2.0
        
        // Cached square of radius for optimization
        val radiusSquared = veinSize * veinSize
        
        // Iterate through a cube containing our ellipsoid
        for (x in max(0, centerX - veinSize)..min(Chunk.SIZE - 1, centerX + veinSize)) {
            for (y in max(0, centerY - veinSize)..min(Chunk.HEIGHT - 1, centerY + veinSize)) {
                for (z in max(0, centerZ - veinSize)..min(Chunk.SIZE - 1, centerZ + veinSize)) {
                    // Calculate normalized ellipsoid distance
                    val dx = (x - centerX).toDouble() / ellipsoidA
                    val dy = (y - centerY).toDouble() / ellipsoidB
                    val dz = (z - centerZ).toDouble() / ellipsoidC
                    val distanceSquared = dx * dx + dy * dy + dz * dz
                    
                    // If inside the ellipsoid
                    if (distanceSquared <= 1.0) {
                        // Only replace stone blocks
                        if (chunk.getBlock(x, y, z) == BlockType.STONE.id) {
                            chunk.setBlock(x, y, z, oreId)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Generate caves and underground features.
     * Default implementation that can be overridden by biome.
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
                    
                    // Calculate world coordinates - CRITICAL for proper cave generation
                    val worldX = (chunkX * Chunk.SIZE) + x
                    val worldZ = (chunkZ * Chunk.SIZE) + z
                    
                    // Generate cave using 3D noise with seed offset for uniqueness
                    val caveNoise = NoiseFactory.caveNoise(
                        worldX.toFloat() + seed * 0.001f, 
                        y.toFloat() + seed * 0.002f, 
                        worldZ.toFloat() + seed * 0.003f,
                        0.03f
                    )
                    
                    // Create cave where noise is above threshold
                    // Adjust threshold based on depth for more caves lower down
                    val depthFactor = 1.0f - (y.toFloat() / caveCeiling.toFloat())
                    val threshold = 0.3f + (depthFactor * 0.2f)
                    
                    if (caveNoise > threshold) {
                        chunk.setBlock(x, y, z, 0) // Set to air
                    }
                }
            }
        }
    }
    
    /**
     * Generate decorations for this biome (trees, plants, etc.)
     * Default empty implementation - biomes should override this.
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
        // Default empty implementation
    }
    
    /**
     * Default height calculation that can be overridden by specific biomes
     */
    override fun getHeight(worldX: Int, worldZ: Int, baseHeight: Int, seed: Long): Int {
        // Default implementation just returns the base height
        return baseHeight
    }
    
    /**
     * Utility data class for ore generation
     */
    protected data class OreType(
        val blockId: Int,
        val frequency: Int,
        val minHeight: Int,
        val maxHeight: Int,
        val veinSize: Int,
        val seed: Long
    )
}