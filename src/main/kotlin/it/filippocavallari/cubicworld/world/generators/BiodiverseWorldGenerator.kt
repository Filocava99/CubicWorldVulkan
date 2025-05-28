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
        
        // Register mod-inspired biomes
        biomeRegistry.register(MagicalForestBiomeGenerator())
        
        // More biomes would be registered here in a full implementation
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
     * Generate the biome distribution map for this chunk using Worley noise
     */
    private fun generateBiomeMap(chunkX: Int, chunkZ: Int): Array<Array<Pair<BiomeGenerator, Float>>> {
        val biomeMap = Array(Chunk.SIZE) { Array(Chunk.SIZE) { Pair(biomeRegistry.getBiome(1)!!, 1.0f) } }
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Calculate ABSOLUTE world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Generate temperature and humidity with proper world coordinates
                // Use seed-based offsets to ensure unique worlds
                val temperatureNoise = NoiseFactory.octavedSimplexNoise(
                    worldX.toFloat() + seed * 0.1f, 
                    worldZ.toFloat() + seed * 0.2f,
                    3,
                    BIOME_SCALE
                )
                
                val humidityNoise = NoiseFactory.octavedSimplexNoise(
                    worldX.toFloat() + seed * 0.3f, 
                    worldZ.toFloat() + seed * 0.4f,
                    3,
                    BIOME_SCALE
                )
                
                // Scale to 0-1 range
                val temperature = (temperatureNoise + 1.0f) * 0.5f
                val humidity = (humidityNoise + 1.0f) * 0.5f
                
                // Get biome based on climate values
                val biome = biomeRegistry.getBiomeByClimate(temperature, humidity)
                
                // Calculate biome blend factor for this position
                val blendFactor = calculateBiomeBlendFactor(worldX, worldZ)
                
                // Store the biome and blend factor
                biomeMap[x][z] = Pair(biome, blendFactor)
            }
        }
        
        return biomeMap
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
                // Caves generate from bedrock level up to 120 (increased from 60)
                for (y in 5 until 120) { 
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
        const val CONTINENT_HEIGHT_SCALE = 35  // Increased for more varied terrain
        
        const val BIOME_SCALE = 0.005f
        const val BIOME_BORDER_SCALE = 0.01f
        const val BIOME_BLEND_AREA = 16
        
        const val MIN_HEIGHT = 1
        const val MAX_HEIGHT = 180  // Significantly increased to allow full terrain generation
    }
}