package it.filippocavallari.cubicworld.world.generators.biome

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import kotlin.math.max
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
        const val MAX_HEIGHT = 240  // Increased to match world generator limits and allow tall mountains
        const val MIN_HEIGHT = 1
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
                // Ensure stone stops below the surface to prevent exposure
                val stoneTop = max(BEDROCK_LEVEL + 1, height - 5)
                for (y in BEDROCK_LEVEL + 1 until stoneTop) {
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
        // Ensure we always have at least some dirt coverage to prevent exposed stone
        val dirtDepth = 4
        val startY = max(BEDROCK_LEVEL + 1, height - dirtDepth)
        
        // Fill from startY to height with dirt
        for (y in startY until height) {
            chunk.setBlock(x, y, z, BlockType.DIRT.id)
        }
        
        // Always set the surface block (at height position)
        // Top layer - grass if above sea level, otherwise dirt
        if (height > SEA_LEVEL) {
            chunk.setBlock(x, height, z, BlockType.GRASS.id)
            // Debug output for grass placement
            if (x == 8 && z == 8) { // Center of spawn chunk
                println("DEBUG: Placed GRASS at ($x, $height, $z) in biome ${this.name}")
            }
        } else {
            chunk.setBlock(x, height, z, BlockType.DIRT.id)
            // Debug output for underwater areas
            if (x == 8 && z == 8) { // Center of spawn chunk
                println("DEBUG: Placed DIRT at ($x, $height, $z) underwater in biome ${this.name}")
            }
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
            OreType(BlockType.COAL_ORE.id, 20, 5, 200, 8, seed),  // Increased max height
            OreType(BlockType.IRON_ORE.id, 15, 5, 160, 6, seed),  // Increased max height
            OreType(BlockType.GOLD_ORE.id, 8, 5, 64, 6, seed),
            OreType(BlockType.DIAMOND_ORE.id, 3, 5, 32, 4, seed),
            OreType(BlockType.REDSTONE_ORE.id, 10, 5, 64, 5, seed), // Increased max height
            OreType(BlockType.LAPIS_ORE.id, 5, 5, 48, 4, seed)      // Increased max height
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
                // Get cave ceiling based on reasonable underground depth
                val caveCeiling = 180  // Increased to allow caves throughout taller terrain
                
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