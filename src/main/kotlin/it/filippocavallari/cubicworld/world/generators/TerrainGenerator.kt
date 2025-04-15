package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import org.joml.SimplexNoise
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A generator for a more interesting terrain with mountains and various biomes.
 */
class TerrainGenerator : WorldGenerator {
    
    /**
     * Generate a chunk at the specified position
     */
    override fun generateChunk(chunk: Chunk) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        // Generate base heightmap using simplex noise
        val heightMap = generateHeightMap(chunkX, chunkZ)
        
        // Generate biome map
        val biomeMap = generateBiomeMap(chunkX, chunkZ)
        
        // Fill the chunk with blocks
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                val biome = biomeMap[x][z]
                val height = heightMap[x][z]
                
                // Generate terrain layers
                generateTerrain(chunk, x, z, height, biome)
                
                // Add water up to sea level
                for (y in height + 1..SEA_LEVEL) {
                    if (chunk.getBlock(x, y, z) == 0) { // Only add water to empty space
                        chunk.setBlock(x, y, z, BlockType.WATER.id)
                    }
                }
            }
        }
        
        // Generate caves
        generateCaves(chunk)
        
        // Generate ores
        generateOres(chunk)
        
        // Generate trees and vegetation
        generateVegetation(chunk, heightMap, biomeMap)
    }
    
    /**
     * Generate the height map for this chunk
     */
    private fun generateHeightMap(chunkX: Int, chunkZ: Int): Array<IntArray> {
        val heightMap = Array(Chunk.SIZE) { IntArray(Chunk.SIZE) }
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Calculate world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Generate base terrain using simplex noise
                val baseNoise = SimplexNoise.noise(worldX * HEIGHT_FREQUENCY, worldZ * HEIGHT_FREQUENCY)
                val detailNoise = SimplexNoise.noise(worldX * HEIGHT_DETAIL_FREQUENCY, worldZ * HEIGHT_DETAIL_FREQUENCY)
                
                // Combine noise layers
                val combinedNoise = (baseNoise * 0.7f) + (detailNoise * 0.3f)
                
                // Calculate the terrain height
                var height = SEA_LEVEL + (combinedNoise * HEIGHT_SCALE).toInt()
                
                // Add some detailed variations
                height += (detailNoise * HEIGHT_DETAIL_SCALE).toInt()
                
                // Clamp to valid range
                height = max(10, min(height, 240))
                
                heightMap[x][z] = height
            }
        }
        
        return heightMap
    }
    
    /**
     * Generate the biome map for this chunk
     */
    private fun generateBiomeMap(chunkX: Int, chunkZ: Int): Array<Array<BiomeType>> {
        val biomeMap = Array(Chunk.SIZE) { Array(Chunk.SIZE) { BiomeType.PLAINS } }
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                // Calculate world coordinates
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Use noise to determine biome distribution
                val temperature = SimplexNoise.noise(worldX * BIOME_FREQUENCY, worldZ * BIOME_FREQUENCY)
                val humidity = SimplexNoise.noise(worldX * BIOME_FREQUENCY + 100, worldZ * BIOME_FREQUENCY + 100)
                
                // Convert to range 0-1
                val adjustedTemperature = (temperature + 1.0f) * 0.5f
                val adjustedHumidity = (humidity + 1.0f) * 0.5f
                
                // Determine biome based on temperature and humidity
                biomeMap[x][z] = getBiomeFromTemperatureAndHumidity(adjustedTemperature, adjustedHumidity)
            }
        }
        
        return biomeMap
    }
    
    /**
     * Get biome type based on temperature and humidity values
     */
    private fun getBiomeFromTemperatureAndHumidity(temperature: Float, humidity: Float): BiomeType {
        return when {
            temperature < 0.3f -> {
                if (humidity < 0.4f) BiomeType.TUNDRA else BiomeType.TAIGA
            }
            temperature < 0.6f -> {
                when {
                    humidity < 0.3f -> BiomeType.PLAINS
                    humidity < 0.7f -> BiomeType.FOREST
                    else -> BiomeType.SWAMP
                }
            }
            else -> {
                when {
                    humidity < 0.3f -> BiomeType.DESERT
                    humidity < 0.7f -> BiomeType.SAVANNA
                    else -> BiomeType.JUNGLE
                }
            }
        }
    }
    
    /**
     * Generate terrain layers for a column
     */
    private fun generateTerrain(chunk: Chunk, x: Int, z: Int, height: Int, biome: BiomeType) {
        // Bedrock
        for (y in 0..BEDROCK_LEVEL) {
            chunk.setBlock(x, y, z, BlockType.BEDROCK.id)
        }
        
        // Stone layer
        val stoneHeight = min(height, MIN_STONE_HEIGHT)
        for (y in BEDROCK_LEVEL + 1..stoneHeight) {
            chunk.setBlock(x, y, z, BlockType.STONE.id)
        }
        
        // Get surface block based on biome
        val surfaceBlock = getSurfaceBlockForBiome(biome)
        
        // Surface layer
        val surfaceDepth = 3 + (SimplexNoise.noise(x * 0.1f, z * 0.1f) * 2).toInt()
        for (y in stoneHeight + 1..height) {
            if (height - y < surfaceDepth) {
                chunk.setBlock(x, y, z, surfaceBlock)
            } else {
                chunk.setBlock(x, y, z, BlockType.STONE.id)
            }
        }
    }
    
    /**
     * Get the appropriate surface block for a biome
     */
    private fun getSurfaceBlockForBiome(biome: BiomeType): Int {
        return when (biome) {
            BiomeType.DESERT -> BlockType.SAND.id
            BiomeType.TUNDRA -> BlockType.DIRT.id
            BiomeType.PLAINS, BiomeType.FOREST, BiomeType.TAIGA -> BlockType.GRASS.id
            BiomeType.SWAMP -> BlockType.DIRT.id
            BiomeType.SAVANNA -> BlockType.GRASS.id
            BiomeType.JUNGLE -> BlockType.GRASS.id
        }
    }
    
    /**
     * Generate caves using 3D noise
     */
    private fun generateCaves(chunk: Chunk) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                for (y in BEDROCK_LEVEL + 1 until SEA_LEVEL) {
                    // Skip if already air
                    if (chunk.getBlock(x, y, z) == 0) continue
                    
                    // Calculate world coordinates
                    val worldX = (chunkX * Chunk.SIZE) + x
                    val worldZ = (chunkZ * Chunk.SIZE) + z
                    
                    // Generate cave using 3D noise
                    val noise1 = SimplexNoise.noise(worldX * CAVE_FREQ_1, y * CAVE_FREQ_1, worldZ * CAVE_FREQ_1)
                    val noise2 = SimplexNoise.noise(worldX * CAVE_FREQ_2, y * CAVE_FREQ_2, worldZ * CAVE_FREQ_2)
                    
                    // Combine noise values
                    val caveNoise = (noise1 + noise2) * 0.5f
                    
                    // Create cave where noise is above threshold
                    if (caveNoise > CAVE_THRESHOLD) {
                        chunk.setBlock(x, y, z, 0) // Set to air
                    }
                }
            }
        }
    }
    
    /**
     * Generate ore deposits
     */
    private fun generateOres(chunk: Chunk) {
        // Define ore parameters
        val oreTypes = arrayOf(
            OreType(BlockType.COAL_ORE.id, 20, 128, 0.02f, 8),
            OreType(BlockType.IRON_ORE.id, 5, 64, 0.015f, 6),
            OreType(BlockType.GOLD_ORE.id, 2, 32, 0.01f, 4),
            OreType(BlockType.DIAMOND_ORE.id, 1, 16, 0.005f, 3),
            OreType(BlockType.REDSTONE_ORE.id, 8, 32, 0.012f, 5),
            OreType(BlockType.LAPIS_ORE.id, 2, 32, 0.008f, 4)
        )
        
        // Generate each ore type
        for (ore in oreTypes) {
            for (attempt in 0 until ore.veinsPerChunk) {
                // Random position within chunk
                val ox = (Math.random() * Chunk.SIZE).toInt()
                val oy = (Math.random() * ore.maxHeight).toInt()
                val oz = (Math.random() * Chunk.SIZE).toInt()
                
                // Skip if below minimum height
                if (oy < BEDROCK_LEVEL + 1) continue
                
                // Generate vein
                val veinSize = (Math.random() * ore.maxVeinSize + 1).toInt()
                
                var cx = ox
                var cy = oy
                var cz = oz
                
                for (i in 0 until veinSize) {
                    // Random offset from center
                    val dx = (Math.random() * 3 - 1).toInt()
                    val dy = (Math.random() * 3 - 1).toInt()
                    val dz = (Math.random() * 3 - 1).toInt()
                    
                    val x = cx + dx
                    val y = cy + dy
                    val z = cz + dz
                    
                    // Check bounds
                    if (x in 0 until Chunk.SIZE && 
                        y in 0 until 256 &&
                        z in 0 until Chunk.SIZE) {
                        
                        // Replace stone with ore
                        if (chunk.getBlock(x, y, z) == BlockType.STONE.id) {
                            chunk.setBlock(x, y, z, ore.blockId)
                        }
                    }
                    
                    // Set new center for next loop
                    cx = x
                    cy = y
                    cz = z
                }
            }
        }
    }
    
    /**
     * Generate trees and vegetation
     */
    private fun generateVegetation(chunk: Chunk, heightMap: Array<IntArray>, biomeMap: Array<Array<BiomeType>>) {
        val chunkX = chunk.position.x
        val chunkZ = chunk.position.y
        
        // Generate trees based on biome
        for (x in 2 until Chunk.SIZE - 2) {
            for (z in 2 until Chunk.SIZE - 2) {
                val height = heightMap[x][z]
                val biome = biomeMap[x][z]
                
                // Skip if underwater
                if (height < SEA_LEVEL) continue
                
                // Calculate world coordinates for noise
                val worldX = (chunkX * Chunk.SIZE) + x
                val worldZ = (chunkZ * Chunk.SIZE) + z
                
                // Use noise for tree distribution
                val treeNoise = SimplexNoise.noise(worldX * 0.1f, worldZ * 0.1f)
                
                // Get tree chance based on biome
                val treeChance = getTreeChanceForBiome(biome)
                
                // Generate a tree if noise value exceeds threshold
                if (treeNoise > (1.0f - treeChance)) {
                    // Generate different trees based on biome
                    when (biome) {
                        BiomeType.FOREST, BiomeType.PLAINS -> 
                            generateOakTree(chunk, x, height + 1, z)
                        BiomeType.TAIGA -> 
                            generateSpruceTree(chunk, x, height + 1, z)
                        BiomeType.JUNGLE -> 
                            generateJungleTree(chunk, x, height + 1, z)
                        else -> {}
                    }
                }
            }
        }
    }
    
    /**
     * Get tree generation chance for a biome
     */
    private fun getTreeChanceForBiome(biome: BiomeType): Float {
        return when (biome) {
            BiomeType.DESERT -> 0.001f // Very rare
            BiomeType.TUNDRA -> 0.01f // Rare
            BiomeType.PLAINS -> 0.03f // Uncommon
            BiomeType.FOREST -> 0.15f // Common
            BiomeType.TAIGA -> 0.1f // Fairly common
            BiomeType.SWAMP -> 0.08f // Moderate
            BiomeType.SAVANNA -> 0.02f // Sparse
            BiomeType.JUNGLE -> 0.2f // Very common
        }
    }
    
    /**
     * Generate an oak tree
     */
    private fun generateOakTree(chunk: Chunk, x: Int, y: Int, z: Int) {
        // Trunk height (5-7 blocks)
        val trunkHeight = 5 + (Math.random() * 3).toInt()
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate leaves (spherical shape)
        for (lx in x - 2..x + 2) {
            for (ly in y + trunkHeight - 3..y + trunkHeight + 1) {
                for (lz in z - 2..z + 2) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= 256 || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Distance from trunk
                    val distance = sqrt(
                        (lx - x).toDouble().pow(2) + 
                        (ly - (y + trunkHeight - 1)).toDouble().pow(2) + 
                        (lz - z).toDouble().pow(2)
                    )
                    
                    // Add leaves in a spherical pattern
                    if (distance <= 2.5 && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
    
    /**
     * Generate a spruce tree
     */
    private fun generateSpruceTree(chunk: Chunk, x: Int, y: Int, z: Int) {
        // Trunk height (6-8 blocks)
        val trunkHeight = 6 + (Math.random() * 3).toInt()
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
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
                    
                    // Distance from trunk
                    val distance = sqrt((lx - x).toDouble().pow(2) + (lz - z).toDouble().pow(2))
                    
                    // Add leaves in a conical pattern
                    if (distance <= levelRadius && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
        
        // Top leaves
        if (y + trunkHeight < 256 && x >= 0 && x < Chunk.SIZE && z >= 0 && z < Chunk.SIZE) {
            chunk.setBlock(x, y + trunkHeight, z, BlockType.LEAVES_OAK.id)
        }
    }
    
    /**
     * Generate a jungle tree
     */
    private fun generateJungleTree(chunk: Chunk, x: Int, y: Int, z: Int) {
        // Trunk height (8-12 blocks)
        val trunkHeight = 8 + (Math.random() * 5).toInt()
        
        // Generate trunk
        for (ty in y until y + trunkHeight) {
            chunk.setBlock(x, ty, z, BlockType.LOG_OAK.id)
        }
        
        // Generate leaves (large irregular sphere)
        for (lx in x - 3..x + 3) {
            for (ly in y + trunkHeight - 4..y + trunkHeight + 2) {
                for (lz in z - 3..z + 3) {
                    // Skip if outside chunk bounds
                    if (lx < 0 || lx >= Chunk.SIZE || ly < 0 || ly >= 256 || lz < 0 || lz >= Chunk.SIZE) {
                        continue
                    }
                    
                    // Distance from trunk
                    val distance = sqrt(
                        (lx - x).toDouble().pow(2) + 
                        (ly - (y + trunkHeight - 1)).toDouble().pow(2) + 
                        (lz - z).toDouble().pow(2)
                    )
                    
                    // Add leaves in a irregular spherical pattern (with noise)
                    val noise = SimplexNoise.noise(lx * 0.5f, ly * 0.5f, lz * 0.5f) * 0.5f
                    if (distance <= (3.0 + noise) && chunk.getBlock(lx, ly, lz) == 0) {
                        chunk.setBlock(lx, ly, lz, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
    
    /**
     * Helper class to define ore types
     */
    private data class OreType(
        val blockId: Int,
        val veinsPerChunk: Int,
        val maxHeight: Int,
        val density: Float,
        val maxVeinSize: Int
    )
    
    /**
     * Enum for biome types
     */
    private enum class BiomeType {
        DESERT,
        TUNDRA,
        PLAINS,
        FOREST,
        TAIGA,
        SWAMP,
        SAVANNA,
        JUNGLE
    }
    
    companion object {
        // Generation parameters
        private const val SEA_LEVEL = 60
        private const val BEDROCK_LEVEL = 5
        private const val MIN_STONE_HEIGHT = 40
        
        // Noise parameters for height map
        private const val HEIGHT_SCALE = 50.0f
        private const val HEIGHT_FREQUENCY = 0.01f
        private const val HEIGHT_DETAIL_SCALE = 15.0f
        private const val HEIGHT_DETAIL_FREQUENCY = 0.05f
        
        // Noise parameters for biomes
        private const val BIOME_FREQUENCY = 0.005f
        
        // Cave parameters
        private const val CAVE_FREQ_1 = 0.03f
        private const val CAVE_FREQ_2 = 0.05f
        private const val CAVE_THRESHOLD = 0.25f
    }
}