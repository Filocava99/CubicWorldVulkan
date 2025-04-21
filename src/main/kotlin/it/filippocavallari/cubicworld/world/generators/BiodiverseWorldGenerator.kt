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
    
    // Cache to store biome data
    private val biomeCache: MutableMap<Vector2i, Int> = ConcurrentHashMap()
    
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
        
        println("Generating diverse chunk at ($chunkX, $chunkZ)")
        
        // Generate the base height map for this chunk
        val heightMap = generateBaseHeightMap(chunkX, chunkZ)
        
        // Generate the biome distribution for this chunk
        val biomeMap = generateBiomeMap(chunkX, chunkZ)
        
        // Process each biome region in the chunk
        processBiomeRegions(chunk, biomeMap, heightMap)
        
        println("Chunk generation complete for ($chunkX, $chunkZ)")
    }
    
    /**
     * Generate the base height map for the chunk
     */
    private fun generateBaseHeightMap(chunkX: Int, chunkZ: Int): Array<IntArray> {
        val heightMap = Array(Chunk.SIZE) { IntArray(Chunk.SIZE) }
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Calculate world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Generate continental noise (large scale terrain features)
                // Use the actual world coordinates for consistent noise
                val continentNoise = NoiseFactory.continentNoise(
                    worldX.toFloat(),
                    worldZ.toFloat()
                )
                
                // Scale continent noise to create base terrain
                val baseHeight = CONTINENT_MEAN_HEIGHT + (continentNoise * CONTINENT_HEIGHT_SCALE).toInt()
                
                heightMap[x][z] = baseHeight
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
                // Calculate world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Use the cached biome if available
                val cacheKey = Vector2i((worldX / BIOME_BLEND_AREA), (worldZ / BIOME_BLEND_AREA))
                var cachedBiomeId = biomeCache[cacheKey]
                
                if (cachedBiomeId == null) {
                    // Ensure we use the absolute world coordinates for the noise
                    // Add seed-based offsets to create variation but maintain consistency
                    val temperatureNoise = NoiseFactory.octavedSimplexNoise(
                        worldX.toFloat() + seed % 1000, 
                        worldZ.toFloat() + seed % 500,
                        3,
                        BIOME_SCALE
                    )
                    
                    val humidityNoise = NoiseFactory.octavedSimplexNoise(
                        worldX.toFloat() + seed % 500, 
                        worldZ.toFloat() + seed % 1000,
                        3,
                        BIOME_SCALE
                    )
                    
                    // Scale to 0-1 range
                    val temperature = (temperatureNoise + 1.0f) * 0.5f
                    val humidity = (humidityNoise + 1.0f) * 0.5f
                    
                    // Get biome based on climate values
                    val biome = biomeRegistry.getBiomeByClimate(temperature, humidity)
                    
                    // Cache the biome ID
                    biomeCache[cacheKey] = biome.id
                    cachedBiomeId = biome.id
                }
                
                // Get the primary biome for this position
                val primaryBiome = biomeRegistry.getBiome(cachedBiomeId)!!
                
                // Calculate biome blend factor for this position
                val blendFactor = calculateBiomeBlendFactor(worldX, worldZ)
                
                // Store the biome and blend factor
                biomeMap[x][z] = Pair(primaryBiome, blendFactor)
            }
        }
        
        return biomeMap
    }
    
    /**
     * Calculate the biome blend factor for smooth transitions
     */
    private fun calculateBiomeBlendFactor(worldX: Int, worldZ: Int): Float {
        // Use Worley noise to create natural biome borders
        // Ensure we're using absolute world coordinates without any adjustments
        // that might cause repetition across chunks
        val borderNoise = NoiseFactory.worleyNoise(
            worldX.toFloat(),
            worldZ.toFloat(),
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
                // Calculate world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Get the biome and blend factor
                val (biome, blendFactor) = biomeMap[x][z]
                
                // Get the base height
                var baseHeight = heightMap[x][z]
                
                // Modify height based on biome - ensure we use absolute world coordinates
                // This is critical for proper chunk differentiation
                val biomeHeight = biome.getHeight(worldX, worldZ, baseHeight, seed)
                
                // Apply the blend factor for smooth transitions
                // Note: biomeHeight already includes chunk position data
                baseHeight = (baseHeight * (1.0f - blendFactor) + biomeHeight * blendFactor).toInt()
                
                // Clamp to valid range
                baseHeight = max(MIN_HEIGHT, min(baseHeight, MAX_HEIGHT))
                
                // Update the height map
                heightMap[x][z] = baseHeight
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
        const val CONTINENT_HEIGHT_SCALE = 40
        
        const val BIOME_SCALE = 0.005f
        const val BIOME_BORDER_SCALE = 0.01f
        const val BIOME_BLEND_AREA = 16
        
        const val MIN_HEIGHT = 5
        const val MAX_HEIGHT = 220
    }
}