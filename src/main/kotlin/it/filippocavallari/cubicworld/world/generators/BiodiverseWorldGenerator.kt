package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.biome.*
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import org.joml.Vector2i
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * A world generator inspired by Minecraft's modern biome system and popular mods
 * like "Biomes O' Plenty" and "Oh The Biomes You'll Go".
 * 
 * Features:
 * - Diverse biome system using Worley noise for distribution
 * - Custom terrain generation for each biome
 * - Multi-layered noise for heightmaps
 * - Extensive cave systems
 * - Smooth biome transitions
 */
class BiodiverseWorldGenerator(
    val seed: Long = System.currentTimeMillis()
) : WorldGenerator {
    
    // Biome registry
    private val biomeRegistry: BiomeRegistry = BiomeRegistry
    
    // Cache to store biome data - stores biome data per block, not per chunk area
    private val biomeCache: MutableMap<Vector2i, Int> = ConcurrentHashMap()
    
    // Height cache for ensuring continuity between chunks
    private val heightCache: MutableMap<Vector2i, Int> = ConcurrentHashMap()
    
    init {
        // Register all biome types
        registerBiomes()
        
        println("Initialized BiodiverseWorldGenerator with seed: $seed")
        println("Registered ${biomeRegistry.count()} biomes")
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
        
        println("Registered ${biomeRegistry.count()} biomes:")
        for (biome in biomeRegistry.getAllBiomes()) {
            val tempIndex = (biome.temperature * 9).toInt().coerceIn(0, 9)
            val humidityIndex = (biome.humidity * 9).toInt().coerceIn(0, 9)
            println("  - ${biome.name} (ID: ${biome.id}) - Temp: ${biome.temperature} (${tempIndex}), Humidity: ${biome.humidity} (${humidityIndex})")
        }
    }
    
    /**
     * Generate a chunk at the specified position
     */
    override fun generateChunk(chunk: Chunk) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        println("Generating diverse chunk at ($chunkX, $chunkZ) - Height range: $MIN_HEIGHT to $MAX_HEIGHT")
        
        // Generate the base height map for this chunk
        val heightMap = generateBaseHeightMap(chunkX, chunkZ)
        
        // Calculate height statistics for this chunk
        var minHeightInChunk = MAX_HEIGHT
        var maxHeightInChunk = MIN_HEIGHT
        var totalHeight = 0
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                val height = heightMap[x][z]
                minHeightInChunk = kotlin.math.min(minHeightInChunk, height)
                maxHeightInChunk = kotlin.math.max(maxHeightInChunk, height)
                totalHeight += height
            }
        }
        
        val avgHeight = totalHeight / (Chunk.SIZE * Chunk.SIZE)
        println("  Chunk height stats: Min=$minHeightInChunk, Max=$maxHeightInChunk, Avg=$avgHeight")
        
        // Generate the biome distribution for this chunk
        val biomeMap = generateBiomeMap(chunkX, chunkZ)
        
        // Process each biome region in the chunk
        processBiomeRegions(chunk, biomeMap, heightMap)
        
        // Generate caves after terrain generation
        generateCaves(chunk)
        
        println("Chunk generation complete for ($chunkX, $chunkZ) - Full height range utilized")
    }
    
    /**
     * Generate the base height map for the chunk
     */
    private fun generateBaseHeightMap(chunkX: Int, chunkZ: Int): Array<IntArray> {
        val heightMap = Array(Chunk.SIZE) { IntArray(Chunk.SIZE) }
        
        // Generate height with additional buffer for smooth blending
        val bufferSize = 4
        val extendedHeightMap = Array(Chunk.SIZE + bufferSize * 2) { IntArray(Chunk.SIZE + bufferSize * 2) }
        
        // First pass - generate extended height map
        for (x in -bufferSize until Chunk.SIZE + bufferSize) {
            for (z in -bufferSize until Chunk.SIZE + bufferSize) {
                // Calculate ABSOLUTE world coordinates - this is critical for chunk uniqueness
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Check height cache first
                val cacheKey = Vector2i(worldX, worldZ)
                val cachedHeight = heightCache[cacheKey]
                
                if (cachedHeight != null) {
                    extendedHeightMap[x + bufferSize][z + bufferSize] = cachedHeight
                } else {
                    // Generate continental noise with proper world coordinates
                    // Add seed offset to ensure different worlds generate different terrain
                    val continentNoise = NoiseFactory.continentNoise(
                        worldX.toFloat() + seed * 0.001f,
                        worldZ.toFloat() + seed * 0.002f
                    )
                    
                    // Add some domain warping for more natural terrain
                    val warpedNoise = NoiseFactory.domainWarpNoise(
                        worldX.toFloat() + seed * 0.003f,
                        worldZ.toFloat() + seed * 0.004f,
                        warpStrength = 5.0f,
                        scale = 0.002f
                    )
                    
                    // Combine noises for final height
                    val combinedNoise = continentNoise * 0.8f + warpedNoise * 0.2f
                    
                    // Scale continent noise to create base terrain
                    val baseHeight = CONTINENT_MEAN_HEIGHT + (combinedNoise * CONTINENT_HEIGHT_SCALE).toInt()
                    
                    extendedHeightMap[x + bufferSize][z + bufferSize] = baseHeight
                    
                    // Cache the height for consistency
                    heightCache[cacheKey] = baseHeight
                }
            }
        }
        
        // Second pass - apply smoothing and copy to final height map
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Apply some smoothing with neighbors for better chunk connections
                var smoothedHeight = 0
                var count = 0
                
                // 3x3 kernel for smoothing
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        val weight = if (dx == 0 && dz == 0) 4 else 1 // Center has more weight
                        smoothedHeight += extendedHeightMap[x + bufferSize + dx][z + bufferSize + dz] * weight
                        count += weight
                    }
                }
                
                heightMap[x][z] = smoothedHeight / count
            }
        }
        
        return heightMap
    }
    
    /**
     * Generate the biome distribution map for this chunk using improved climate system
     */
    private fun generateBiomeMap(chunkX: Int, chunkZ: Int): Array<Array<Pair<BiomeGenerator, Float>>> {
        val biomeMap = Array(Chunk.SIZE) { Array(Chunk.SIZE) { Pair(biomeRegistry.getBiome(1)!!, 1.0f) } }
        val biomeCounts = mutableMapOf<String, Int>()
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Calculate ABSOLUTE world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Generate temperature and humidity with enhanced variation
                // Use multiple octaves for more complex climate patterns
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
                
                // Add continental drift effects for large-scale climate variation
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
                
                // Enhance contrast to ensure all biome types appear
                temperature = enhanceContrast(temperature)
                humidity = enhanceContrast(humidity)
                
                // Clamp to valid range
                temperature = temperature.coerceIn(0.0f, 1.0f)
                humidity = humidity.coerceIn(0.0f, 1.0f)
                
                // Get biome based on climate values
                val biome = biomeRegistry.getBiomeByClimate(temperature, humidity)
                
                // Track biome distribution for debugging
                biomeCounts[biome.name] = (biomeCounts[biome.name] ?: 0) + 1
                
                // Calculate biome blend factor for this position
                val blendFactor = calculateBiomeBlendFactor(worldX, worldZ)
                
                // Store the biome and blend factor
                biomeMap[x][z] = Pair(biome, blendFactor)
            }
        }
        
        // Print biome distribution for this chunk
        if (biomeCounts.size > 1) {
            println("Chunk ($chunkX, $chunkZ) biome distribution:")
            for ((biomeName, count) in biomeCounts.toList().sortedByDescending { it.second }) {
                val percentage = (count * 100) / (Chunk.SIZE * Chunk.SIZE)
                println("  - $biomeName: $count blocks ($percentage%)")
            }
        }
        
        return biomeMap
    }
    
    /**
     * Enhance contrast in climate values to ensure all biome types are represented
     */
    private fun enhanceContrast(value: Float): Float {
        // Apply S-curve to enhance contrast
        val normalized = value.coerceIn(0.0f, 1.0f)
        return (normalized * normalized * (3.0f - 2.0f * normalized))
    }
    
    /**
     * Calculate the biome blend factor for smooth transitions
     */
    private fun calculateBiomeBlendFactor(worldX: Int, worldZ: Int): Float {
        // Use Worley noise to create natural biome borders
        // Critical: use absolute world coordinates with seed offset
        val borderNoise = NoiseFactory.worleyNoise(
            worldX.toFloat() + seed * 0.5f,
            worldZ.toFloat() + seed * 0.6f,
            BIOME_BORDER_SCALE
        )
        
        // Create a blend factor that peaks in the center of biomes
        // and drops off toward the edges - clamped to ensure we don't get extreme values
        return (1.0f - borderNoise * 2.0f).coerceIn(0.1f, 1.0f)
    }
    
    /**
     * Process the biome regions in a chunk
     */
    private fun processBiomeRegions(
        chunk: Chunk,
        biomeMap: Array<Array<Pair<BiomeGenerator, Float>>>,
        heightMap: Array<IntArray>
    ) {
        // First, apply biome-specific height modifications
        applyBiomeHeightModifications(chunk, biomeMap, heightMap)
        
        // Find contiguous biome regions to process efficiently
        val processedPositions = Array(Chunk.SIZE) { BooleanArray(Chunk.SIZE) }
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                if (!processedPositions[x][z]) {
                    // Get the biome at this position
                    val biome = biomeMap[x][z].first
                    
                    // Find the region of this biome
                    val regionPositions = findBiomeRegion(x, z, biomeMap, processedPositions)
                    
                    // Calculate the region bounds
                    var minX = Chunk.SIZE
                    var minZ = Chunk.SIZE
                    var maxX = 0
                    var maxZ = 0
                    
                    for (pos in regionPositions) {
                        minX = min(minX, pos.first)
                        minZ = min(minZ, pos.second)
                        maxX = max(maxX, pos.first)
                        maxZ = max(maxZ, pos.second)
                    }
                    
                    // Generate the biome in this region
                    biome.generate(chunk, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, heightMap, seed)
                    
                    // Generate biome-specific decorations
                    biome.generateDecorations(chunk, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, heightMap, seed)
                }
            }
        }
    }
    
    /**
     * Apply biome-specific height modifications to the height map
     */
    private fun applyBiomeHeightModifications(
        chunk: Chunk,
        biomeMap: Array<Array<Pair<BiomeGenerator, Float>>>,
        heightMap: Array<IntArray>
    ) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Calculate absolute world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Get the biome and blend factor
                val (biome, blendFactor) = biomeMap[x][z]
                
                // Get the base height
                var baseHeight = heightMap[x][z]
                
                // Modify height based on biome - pass absolute world coordinates
                val biomeHeight = biome.getHeight(worldX, worldZ, baseHeight, seed)
                
                // Apply the blend factor for smooth transitions
                baseHeight = (baseHeight * (1.0f - blendFactor) + biomeHeight * blendFactor).toInt()
                
                // Clamp to valid range
                baseHeight = max(MIN_HEIGHT, min(baseHeight, MAX_HEIGHT))
                
                // Update the height map
                heightMap[x][z] = baseHeight
            }
        }
    }
    
    /**
     * Generate caves in the chunk
     */
    private fun generateCaves(chunk: Chunk) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        // Generate 3D cave noise for the entire chunk with increased height range
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Caves generate from bedrock level up to 180 (increased to match terrain heights)
                for (y in 5 until 180) { 
                    // Calculate absolute world coordinates
                    val worldX = (chunkX * Chunk.SIZE) + x
                    val worldZ = (chunkZ * Chunk.SIZE) + z
                    
                    // Generate cave noise with world coordinates
                    val caveNoise = NoiseFactory.caveNoise(
                        worldX.toFloat() + seed * 0.7f,
                        y.toFloat(),
                        worldZ.toFloat() + seed * 0.8f
                    )
                    
                    // Carve out cave if noise is above threshold
                    if (caveNoise > 0.7f) {
                        // Don't carve through bedrock
                        if (chunk.getBlock(x, y, z) != BlockType.BEDROCK.id) {
                            chunk.setBlock(x, y, z, BlockType.AIR.id)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Find contiguous regions of the same biome
     */
    private fun findBiomeRegion(
        startX: Int,
        startZ: Int,
        biomeMap: Array<Array<Pair<BiomeGenerator, Float>>>,
        processedPositions: Array<BooleanArray>
    ): List<Pair<Int, Int>> {
        val targetBiome = biomeMap[startX][startZ].first
        val positions = mutableListOf<Pair<Int, Int>>()
        val queue = LinkedList<Pair<Int, Int>>()
        
        // Add the starting position
        queue.add(Pair(startX, startZ))
        processedPositions[startX][startZ] = true
        
        // Process the queue
        while (queue.isNotEmpty()) {
            val (x, z) = queue.poll()
            positions.add(Pair(x, z))
            
            // Check neighbors
            val neighbors = listOf(
                Pair(x + 1, z),
                Pair(x - 1, z),
                Pair(x, z + 1),
                Pair(x, z - 1)
            )
            
            for ((nx, nz) in neighbors) {
                if (nx in 0 until Chunk.SIZE && nz in 0 until Chunk.SIZE && 
                    !processedPositions[nx][nz] && 
                    biomeMap[nx][nz].first.id == targetBiome.id) {
                    
                    queue.add(Pair(nx, nz))
                    processedPositions[nx][nz] = true
                }
            }
        }
        
        return positions
    }
    
    companion object {
        // World generation constants
        const val CONTINENT_MEAN_HEIGHT = 64
        const val CONTINENT_HEIGHT_SCALE = 45  // Increased for more varied terrain with taller peaks
        
        // Medium-sized biomes - for variety while being traversable
        const val BIOME_SCALE = 0.04f        // Medium scale = medium biomes (~200-500 blocks)
        const val BIOME_BORDER_SCALE = 1.0f  // High scale = sharp borders
        const val BIOME_BLEND_AREA = 16
        
        const val MIN_HEIGHT = 1
        const val MAX_HEIGHT = 240  // Increased to allow tall mountain peaks while leaving room for decorations
    }
}