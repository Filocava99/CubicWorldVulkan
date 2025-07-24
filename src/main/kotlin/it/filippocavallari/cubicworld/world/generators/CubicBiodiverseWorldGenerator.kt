package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import it.filippocavallari.cubicworld.world.generators.biome.*
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import org.joml.Vector2i
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * CubicChunk-native BiodiverseWorldGenerator.
 * This generates diverse biomes directly for cubic chunks without using temporary Chunk objects.
 */
class CubicBiodiverseWorldGenerator(
    override val seed: Long = System.currentTimeMillis()
) : CubicWorldGenerator {
    
    // Biome registry
    private val biomeRegistry: BiomeRegistry = BiomeRegistry
    
    // Cache to store biome data and heights for consistency
    private val heightCache: MutableMap<Vector2i, Int> = ConcurrentHashMap()
    private val biomeCache: MutableMap<Vector2i, BiomeGenerator> = ConcurrentHashMap()
    
    init {
        // Register all biome types
        registerBiomes()
        
        println("Initialized CubicBiodiverseWorldGenerator with seed: $seed")
        println("Registered ${biomeRegistry.count()} biomes for cubic chunks")
    }
    
    /**
     * Register all biome types with the registry
     */
    private fun registerBiomes() {
        // Register standard biomes
        biomeRegistry.register(ForestBiomeGenerator())
        biomeRegistry.register(DesertBiomeGenerator())
        biomeRegistry.register(MountainBiomeGenerator())
        biomeRegistry.register(PlainsBiomeGenerator())
        biomeRegistry.register(SwampBiomeGenerator())
        biomeRegistry.register(TaigaBiomeGenerator())
        biomeRegistry.register(SavannaBiomeGenerator())
        
        // Register mod-inspired biomes
        biomeRegistry.register(MagicalForestBiomeGenerator())
        
        println("Registered ${biomeRegistry.count()} biomes for cubic generation:")
        for (biome in biomeRegistry.getAllBiomes()) {
            val tempIndex = (biome.temperature * 9).toInt().coerceIn(0, 9)
            val humidityIndex = (biome.humidity * 9).toInt().coerceIn(0, 9)
            println("  - ${biome.name} (ID: ${biome.id}) - Temp: ${biome.temperature} (${tempIndex}), Humidity: ${biome.humidity} (${humidityIndex})")
        }
    }
    
    /**
     * Generate content for a cubic chunk
     */
    override fun generateCubicChunk(chunk: CubicChunk) {
        val chunkWorldY = chunk.getWorldY()
        
        // Skip chunks that are too high above terrain
        if (chunkWorldY > MAX_HEIGHT + 16) {
            println("DEBUG: Skipping high altitude cubic chunk at (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z}) - Y=$chunkWorldY")
            return
        }
        
        println("DEBUG: Generating cubic chunk at (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z}) - WorldY=$chunkWorldY")
        
        // Generate terrain for this cubic chunk
        generateCubicTerrain(chunk)
        
        // Generate caves
        generateCubicCaves(chunk)
    }
    
    /**
     * Generate terrain for a cubic chunk
     */
    private fun generateCubicTerrain(chunk: CubicChunk) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.z
        val chunkWorldY = chunk.getWorldY()
        
        for (x in 0 until CubicChunk.SIZE) {
            for (z in 0 until CubicChunk.SIZE) {
                val worldX = chunk.getWorldX() + x
                val worldZ = chunk.getWorldZ() + z
                
                // Get or calculate terrain height for this column
                val terrainHeight = getTerrainHeight(worldX, worldZ)
                
                // Get or calculate biome for this column
                val biome = getBiomeAt(worldX, worldZ)
                
                // Generate blocks for this column in the current cubic chunk
                for (y in 0 until CubicChunk.SIZE) {
                    val worldY = chunkWorldY + y
                    
                    val blockType = when {
                        worldY <= 0 -> BlockType.BEDROCK.id
                        worldY <= BEDROCK_LEVEL -> {
                            // Uneven bedrock layer
                            if (worldY < BEDROCK_LEVEL || Math.random() > worldY.toDouble() / BEDROCK_LEVEL) {
                                BlockType.BEDROCK.id
                            } else {
                                BlockType.STONE.id
                            }
                        }
                        worldY < terrainHeight - 4 -> BlockType.STONE.id
                        worldY < terrainHeight -> {
                            // Use biome-specific subsurface material
                            getBiomeSubsurfaceMaterial(biome, worldY, terrainHeight)
                        }
                        worldY == terrainHeight -> {
                            // Use biome-specific surface material
                            getBiomeSurfaceMaterial(biome, worldY)
                        }
                        worldY <= SEA_LEVEL -> BlockType.WATER.id
                        else -> BlockType.AIR.id
                    }
                    
                    if (blockType != BlockType.AIR.id) {
                        chunk.setBlock(x, y, z, blockType)
                        
                        // Debug output for biome placement (sample positions to track diversity)
                        if (x == 8 && z == 8 && worldY == terrainHeight) {
                            val blockTypeName = BlockType.fromId(blockType)
                            println("DEBUG: Placed ${blockTypeName.name} at ($worldX, $worldY, $worldZ) in biome ${biome.name} (T:${biome.temperature}, H:${biome.humidity}) chunk ($chunkX, $chunkZ)")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get terrain height at world coordinates with caching
     */
    private fun getTerrainHeight(worldX: Int, worldZ: Int): Int {
        val cacheKey = Vector2i(worldX, worldZ)
        return heightCache.getOrPut(cacheKey) {
            calculateTerrainHeight(worldX, worldZ)
        }
    }
    
    /**
     * Calculate terrain height using noise functions
     */
    private fun calculateTerrainHeight(worldX: Int, worldZ: Int): Int {
        // Generate continental noise with proper world coordinates
        val continentNoise = NoiseFactory.continentNoise(
            worldX.toFloat() + seed * 0.001f,
            worldZ.toFloat() + seed * 0.002f
        )
        
        // Add domain warping for more natural terrain
        val warpedNoise = NoiseFactory.domainWarpNoise(
            worldX.toFloat() + seed * 0.003f,
            worldZ.toFloat() + seed * 0.004f,
            warpStrength = 5.0f,
            scale = 0.002f
        )
        
        // Combine noises for final height
        val combinedNoise = continentNoise * 0.8f + warpedNoise * 0.2f
        
        // Scale to create base terrain
        val baseHeight = CONTINENT_MEAN_HEIGHT + (combinedNoise * CONTINENT_HEIGHT_SCALE).toInt()
        
        // Get biome for height modification
        val biome = getBiomeAt(worldX, worldZ)
        val biomeHeight = biome.getHeight(worldX, worldZ, baseHeight, seed)
        
        return biomeHeight.coerceIn(MIN_HEIGHT, MAX_HEIGHT)
    }
    
    /**
     * Get biome at world coordinates with caching
     */
    private fun getBiomeAt(worldX: Int, worldZ: Int): BiomeGenerator {
        val cacheKey = Vector2i(worldX, worldZ)
        return biomeCache.getOrPut(cacheKey) {
            calculateBiomeAt(worldX, worldZ)
        }
    }
    
    /**
     * Calculate biome at world coordinates using climate system
     */
    private fun calculateBiomeAt(worldX: Int, worldZ: Int): BiomeGenerator {
        // Generate temperature and humidity with higher frequency for medium-sized biomes (~200-500 blocks)
        val baseTemperatureNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() * 0.008f + seed * 0.1f,  // Medium frequency = 200-500 block biomes
            worldZ.toFloat() * 0.008f + seed * 0.2f,  // Medium frequency = 200-500 block biomes
            3,  // Fewer octaves for clearer boundaries
            0.8f  // Strong detail
        )
        
        val baseHumidityNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() * 0.008f + seed * 0.3f,  // Medium frequency = 200-500 block biomes
            worldZ.toFloat() * 0.008f + seed * 0.4f,  // Medium frequency = 200-500 block biomes
            4,  // Different octaves for humidity to avoid correlation
            0.9f  // High detail for humidity variation
        )
        
        // Large-scale continental climate shifts for realistic climate zones
        val continentalTempShift = NoiseFactory.simplexNoise(
            worldX.toFloat() * 0.001f + seed * 0.5f,  // Very large-scale climate zones
            worldZ.toFloat() * 0.001f + seed * 0.6f,  // Very large-scale climate zones
            1.0f
        ) * 0.3f  // Moderate continental influence
        
        val continentalHumidityShift = NoiseFactory.simplexNoise(
            worldX.toFloat() * 0.0015f + seed * 0.7f,  // Different scale for humidity
            worldZ.toFloat() * 0.0015f + seed * 0.8f,  // Different scale for humidity
            1.0f
        ) * 0.25f  // Moderate continental influence
        
        // Combine noise values and apply world coordinate-based distribution
        // Use world coordinates to ensure different areas get different base climate tendencies
        val worldClimateX = (worldX / 1000.0f) % 1.0f
        val worldClimateZ = (worldZ / 1000.0f) % 1.0f
        
        var temperature = (baseTemperatureNoise + 1.0f) * 0.5f + continentalTempShift
        var humidity = (baseHumidityNoise + 1.0f) * 0.5f + continentalHumidityShift
        
        // Add coordinate-based variation to ensure all climate zones are represented
        temperature += worldClimateX * 0.3f - 0.15f  // ±0.15 variation based on X coordinate
        humidity += worldClimateZ * 0.3f - 0.15f     // ±0.15 variation based on Z coordinate
        
        // Enhance contrast and clamp
        temperature = enhanceContrast(temperature).coerceIn(0.0f, 1.0f)
        humidity = enhanceContrast(humidity).coerceIn(0.0f, 1.0f)
        
        return biomeRegistry.getBiomeByClimate(temperature, humidity)
    }
    
    /**
     * Enhance contrast in climate values
     */
    private fun enhanceContrast(value: Float): Float {
        val normalized = value.coerceIn(0.0f, 1.0f)
        return (normalized * normalized * (3.0f - 2.0f * normalized))
    }
    
    /**
     * Get biome-specific surface material
     */
    private fun getBiomeSurfaceMaterial(biome: BiomeGenerator, worldY: Int): Int {
        return when (biome.name) {
            "Desert" -> BlockType.SAND.id
            "Swamp" -> if (Random().nextFloat() < 0.3f) BlockType.WATER.id else BlockType.GRASS.id
            else -> if (worldY > SEA_LEVEL) BlockType.GRASS.id else BlockType.DIRT.id
        }
    }
    
    /**
     * Get biome-specific subsurface material
     */
    private fun getBiomeSubsurfaceMaterial(biome: BiomeGenerator, worldY: Int, terrainHeight: Int): Int {
        return when (biome.name) {
            "Desert" -> BlockType.SAND.id
            else -> BlockType.DIRT.id
        }
    }
    
    /**
     * Generate caves in the cubic chunk
     */
    private fun generateCubicCaves(chunk: CubicChunk) {
        val chunkWorldY = chunk.getWorldY()
        
        // Only generate caves in chunks that are underground
        if (chunkWorldY > 180) return
        
        for (x in 0 until CubicChunk.SIZE) {
            for (z in 0 until CubicChunk.SIZE) {
                for (y in 0 until CubicChunk.SIZE) {
                    val worldX = chunk.getWorldX() + x
                    val worldY = chunkWorldY + y
                    val worldZ = chunk.getWorldZ() + z
                    
                    // Skip if too high or too low
                    if (worldY < 5 || worldY > 180) continue
                    
                    // Generate cave noise
                    val caveNoise = NoiseFactory.caveNoise(
                        worldX.toFloat() + seed * 0.7f,
                        worldY.toFloat(),
                        worldZ.toFloat() + seed * 0.8f
                    )
                    
                    // Carve out cave if noise is above threshold
                    if (caveNoise > 0.7f) {
                        val currentBlock = chunk.getBlock(x, y, z)
                        if (currentBlock != BlockType.BEDROCK.id && currentBlock != BlockType.AIR.id) {
                            chunk.setBlock(x, y, z, BlockType.AIR.id)
                        }
                    }
                }
            }
        }
    }
    
    companion object {
        // World generation constants
        const val CONTINENT_MEAN_HEIGHT = 64
        const val CONTINENT_HEIGHT_SCALE = 45
        
        // Tiny biomes - for maximum variety within chunk distances
        const val BIOME_SCALE = 0.04f        // Medium scale = medium biomes (~200-500 blocks)
        const val BIOME_BORDER_SCALE = 1.0f  // High scale = sharp borders
        
        const val MIN_HEIGHT = 1
        const val MAX_HEIGHT = 240
        const val SEA_LEVEL = 62
        const val BEDROCK_LEVEL = 5
    }
}