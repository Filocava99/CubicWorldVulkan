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
        val chunkYPos = chunk.position.y // Chunk's Y position in chunk coordinates
        val chunkZ = chunk.position.z

        // Generate base heightmap using simplex noise (worldX, worldZ calculation inside is fine)
        val heightMap = generateHeightMap(chunkX, chunkZ)
        // Generate biome map (worldX, worldZ calculation inside is fine)
        val biomeMap = generateBiomeMap(chunkX, chunkZ)

        val worldYOffset = chunkYPos * Chunk.HEIGHT

        for (localX in 0 until Chunk.SIZE) {
            for (localZ in 0 until Chunk.SIZE) {
                val surfaceHeightWorldY = heightMap[localX][localZ] // This is a world Y coordinate
                val biome = biomeMap[localX][localZ]

                for (localY in 0 until Chunk.HEIGHT) {
                    val worldY = worldYOffset + localY
                    var blockId: Int // Default to air - Initializer removed as it's always set

                    // Determine block type based on worldY, surfaceHeightWorldY, biome, etc.
                    if (worldY <= BEDROCK_LEVEL) {
                        blockId = BlockType.BEDROCK.id
                    } else if (worldY <= surfaceHeightWorldY) {
                        // Stone or surface block
                        val surfaceBlock = getSurfaceBlockForBiome(biome)
                        val worldXForNoise = chunkX * Chunk.SIZE + localX
                        val worldZForNoise = chunkZ * Chunk.SIZE + localZ
                        val surfaceDepth = 3 + (SimplexNoise.noise(worldXForNoise * 0.1f, worldZForNoise * 0.1f) * 2).toInt()
                        if (surfaceHeightWorldY - worldY < surfaceDepth) {
                            blockId = surfaceBlock
                        } else {
                            blockId = BlockType.STONE.id
                        }
                    } else if (worldY <= SEA_LEVEL) { // Above surface, up to sea level
                        blockId = BlockType.WATER.id
                    } else { // Above surface and above sea level
                        blockId = BlockType.AIR.id
                    }
                    chunk.setBlock(localX, localY, localZ, blockId)
                }
            }
        }

        generateCaves(chunk)
        generateOres(chunk)
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
        //val surfaceDepth = 3 + (SimplexNoise.noise(x * 0.1f, z * 0.1f) * 2).toInt() // Old logic, replaced in main loop
        for (y in stoneHeight + 1..height) {
            if (height - y < 3) { // Simplified depth, actual depth logic is in the main loop now
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
        val chunkXPos = chunk.position.x
        val chunkYPos = chunk.position.y
        val chunkZPos = chunk.position.z
        val worldYOffset = chunkYPos * Chunk.HEIGHT

        for (localX in 0 until Chunk.SIZE) {
            for (localZ in 0 until Chunk.SIZE) {
                for (localY in 0 until Chunk.HEIGHT) {
                    val currentWorldY = worldYOffset + localY

                    if (currentWorldY <= BEDROCK_LEVEL || currentWorldY >= SEA_LEVEL) continue

                    // Skip if already air or not stone (caves carve through stone)
                    if (chunk.getBlock(localX, localY, localZ) != BlockType.STONE.id) continue
                    
                    // Calculate world coordinates for noise
                    val worldX = (chunkXPos * Chunk.SIZE) + localX
                    val worldZ = (chunkZPos * Chunk.SIZE) + localZ
                    
                    // Generate cave using 3D noise
                    // Noise function expects currentWorldY for consistent cave shapes across chunk boundaries
                    val noise1 = SimplexNoise.noise(worldX * CAVE_FREQ_1, currentWorldY * CAVE_FREQ_1, worldZ * CAVE_FREQ_1)
                    val noise2 = SimplexNoise.noise(worldX * CAVE_FREQ_2, currentWorldY * CAVE_FREQ_2, worldZ * CAVE_FREQ_2)
                    
                    // Combine noise values
                    val caveNoise = (noise1 + noise2) * 0.5f
                    
                    // Create cave where noise is above threshold
                    if (caveNoise > CAVE_THRESHOLD) {
                        chunk.setBlock(localX, localY, localZ, BlockType.AIR.id) // Set to air
                    }
                }
            }
        }
    }
    
    /**
     * Generate ore deposits
     */
    private fun generateOres(chunk: Chunk) {
        val chunkYPos = chunk.position.y
        val worldYOffset = chunkYPos * Chunk.HEIGHT

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
                // Random local X and Z for vein center
                val ox = (Math.random() * Chunk.SIZE).toInt()
                val oz = (Math.random() * Chunk.SIZE).toInt()
                
                // Random world Y for vein center, then convert to localY
                val randomWorldY = (Math.random() * ore.maxHeight).toInt()
                
                // Skip if this world Y is below bedrock level (ores don't spawn in bedrock layer)
                // MaxHeight is a world Y limit.
                if (randomWorldY <= BEDROCK_LEVEL) continue

                val localYCenter = randomWorldY - worldYOffset
                
                // Skip if vein center is not in this chunk's Y slice
                if (localYCenter < 0 || localYCenter >= Chunk.HEIGHT) continue
                
                // Generate vein
                val veinSize = (Math.random() * ore.maxVeinSize + 1).toInt()
                
                var cx = ox // current local X of vein
                var cy = localYCenter // current local Y of vein
                var cz = oz // current local Z of vein
                
                for (i in 0 until veinSize) {
                    // Random local offsets
                    val dx = (Math.random() * 3 - 1).toInt()
                    val dy = (Math.random() * 3 - 1).toInt()
                    val dz = (Math.random() * 3 - 1).toInt()
                    
                    val x = cx + dx // target local X
                    val y = cy + dy // target local Y
                    val z = cz + dz // target local Z
                    
                    // Check local bounds
                    if (x in 0 until Chunk.SIZE && 
                        y in 0 until Chunk.HEIGHT && // Use Chunk.HEIGHT for local Y bound
                        z in 0 until Chunk.SIZE) {
                        
                        // Replace stone with ore
                        if (chunk.getBlock(x, y, z) == BlockType.STONE.id) {
                            chunk.setBlock(x, y, z, ore.blockId)
                        }
                    }
                    
                    // Set new center for next loop (local coordinates)
                    // Ensure the vein tends to stay within the current chunk's y slice or moves slowly
                    val next_cy_world = (worldYOffset + y)
                    if (next_cy_world <= BEDROCK_LEVEL || next_cy_world >= ore.maxHeight) {
                        // if it tries to go out of ore generation range, stop this vein path
                        break
                    }
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
     * @param x local X coordinate of the tree base
     * @param baseWorldY world Y coordinate of the tree base (on top of the surface block)
     * @param z local Z coordinate of the tree base
     */
    private fun generateOakTree(chunk: Chunk, x: Int, baseWorldY: Int, z: Int) {
        val chunkYPos = chunk.position.y
        val worldYOffset = chunkYPos * Chunk.HEIGHT
        val trunkHeight = 5 + (Math.random() * 3).toInt()

        // Generate trunk
        for (ty_offset in 0 until trunkHeight) {
            val currentTrunkWorldY = baseWorldY + ty_offset
            val localTy = currentTrunkWorldY - worldYOffset
            if (localTy in 0 until Chunk.HEIGHT) {
                // Ensure x and z are within chunk bounds as well (though usually they are if called from generateVegetation)
                if (x in 0 until Chunk.SIZE && z in 0 until Chunk.SIZE) {
                    chunk.setBlock(x, localTy, z, BlockType.LOG_OAK.id)
                }
            }
        }

        // Generate leaves (spherical shape)
        val leafCenterWorldY = baseWorldY + trunkHeight - 1 
        for (lx_offset in -2..2) {
            val currentLeafLocalX = x + lx_offset
            if (currentLeafLocalX < 0 || currentLeafLocalX >= Chunk.SIZE) continue

            for (lz_offset in -2..2) {
                val currentLeafLocalZ = z + lz_offset
                if (currentLeafLocalZ < 0 || currentLeafLocalZ >= Chunk.SIZE) continue

                // Iterate Y relative to the leaf sphere's center; y loop is for offsets from leafCenterWorldY
                for (ly_offset_from_center in -2..2) { 
                    val currentLeafWorldY = leafCenterWorldY + ly_offset_from_center
                    
                    // More precise leaf Y range check against where leaves should form relative to trunk top
                    if (currentLeafWorldY < baseWorldY + trunkHeight - 3 || currentLeafWorldY > baseWorldY + trunkHeight + 1) continue

                    val localLy = currentLeafWorldY - worldYOffset
                    if (localLy < 0 || localLy >= Chunk.HEIGHT) continue
                    
                    val distance = sqrt(
                        lx_offset.toDouble().pow(2) +
                        ly_offset_from_center.toDouble().pow(2) + // Use offset from center for spherical shape
                        lz_offset.toDouble().pow(2)
                    )
                    
                    if (distance <= 2.5 && chunk.getBlock(currentLeafLocalX, localLy, currentLeafLocalZ) == BlockType.AIR.id) {
                        chunk.setBlock(currentLeafLocalX, localLy, currentLeafLocalZ, BlockType.LEAVES_OAK.id)
                    }
                }
            }
        }
    }
    
    /**
     * Generate a spruce tree
     * @param x local X coordinate of the tree base
     * @param baseWorldY world Y coordinate of the tree base
     * @param z local Z coordinate of the tree base
     */
    private fun generateSpruceTree(chunk: Chunk, x: Int, baseWorldY: Int, z: Int) {
        val chunkYPos = chunk.position.y
        val worldYOffset = chunkYPos * Chunk.HEIGHT
        val trunkHeight = 6 + (Math.random() * 3).toInt()

        // Generate trunk
        for (ty_offset in 0 until trunkHeight) {
            val currentTrunkWorldY = baseWorldY + ty_offset
            val localTy = currentTrunkWorldY - worldYOffset
            if (localTy in 0 until Chunk.HEIGHT) {
                 if (x in 0 until Chunk.SIZE && z in 0 until Chunk.SIZE) {
                    chunk.setBlock(x, localTy, z, BlockType.LOG_OAK.id) // Assuming LOG_SPRUCE if available
                 }
            }
        }

        // Generate leaves (conical shape)
        val leafRadiusBase = 2 
        // Leaves start a bit up the trunk (e.g. baseWorldY + 2) and go almost to the top
        for (leafLayerWorldY in (baseWorldY + 2) until (baseWorldY + trunkHeight)) {
            val localLy = leafLayerWorldY - worldYOffset
            if (localLy < 0 || localLy >= Chunk.HEIGHT) continue

            val levelInCone = leafLayerWorldY - (baseWorldY + 2) 
            val radiusAtLevel = max(0, leafRadiusBase - levelInCone) 

            for (lx_offset in -radiusAtLevel..radiusAtLevel) {
                val currentLeafLocalX = x + lx_offset
                if (currentLeafLocalX < 0 || currentLeafLocalX >= Chunk.SIZE) continue

                for (lz_offset in -radiusAtLevel..radiusAtLevel) {
                    val currentLeafLocalZ = z + lz_offset
                    if (currentLeafLocalZ < 0 || currentLeafLocalZ >= Chunk.SIZE) continue
                    
                    val distanceXZ = sqrt(lx_offset.toDouble().pow(2) + lz_offset.toDouble().pow(2))
                    if (distanceXZ <= radiusAtLevel) { // Check if within the circle for this cone level
                        if (chunk.getBlock(currentLeafLocalX, localLy, currentLeafLocalZ) == BlockType.AIR.id) {
                            chunk.setBlock(currentLeafLocalX, localLy, currentLeafLocalZ, BlockType.LEAVES_OAK.id) // Assuming LEAVES_SPRUCE
                        }
                    }
                }
            }
        }
        
        // Topmost leaf block
        val topLeafWorldY = baseWorldY + trunkHeight
        val localTopLy = topLeafWorldY - worldYOffset
        if (localTopLy in 0 until Chunk.HEIGHT) {
            if (x in 0 until Chunk.SIZE && z in 0 until Chunk.SIZE) {
                 if (chunk.getBlock(x, localTopLy, z) == BlockType.AIR.id) {
                    chunk.setBlock(x, localTopLy, z, BlockType.LEAVES_OAK.id) // Assuming LEAVES_SPRUCE
                 }
            }
        }
    }
    
    /**
     * Generate a jungle tree
     * @param x local X coordinate of the tree base
     * @param baseWorldY world Y coordinate of the tree base
     * @param z local Z coordinate of the tree base
     */
    private fun generateJungleTree(chunk: Chunk, x: Int, baseWorldY: Int, z: Int) {
        val chunkYPos = chunk.position.y
        val worldYOffset = chunkYPos * Chunk.HEIGHT
        val trunkHeight = 8 + (Math.random() * 5).toInt()

        // Generate trunk
        for (ty_offset in 0 until trunkHeight) {
            val currentTrunkWorldY = baseWorldY + ty_offset
            val localTy = currentTrunkWorldY - worldYOffset
            if (localTy in 0 until Chunk.HEIGHT) {
                if (x in 0 until Chunk.SIZE && z in 0 until Chunk.SIZE) {
                    chunk.setBlock(x, localTy, z, BlockType.LOG_OAK.id) // Assuming LOG_JUNGLE
                }
            }
        }

        // Generate leaves (large irregular sphere)
        val leafCenterWorldY = baseWorldY + trunkHeight -1 
        for (lx_offset in -3..3) {
            val currentLeafLocalX = x + lx_offset
            if (currentLeafLocalX < 0 || currentLeafLocalX >= Chunk.SIZE) continue

            for (lz_offset in -3..3) {
                val currentLeafLocalZ = z + lz_offset
                if (currentLeafLocalZ < 0 || currentLeafLocalZ >= Chunk.SIZE) continue

                for (ly_offset_from_center in -3..3) { 
                    val currentLeafWorldY = leafCenterWorldY + ly_offset_from_center
                    
                    // Rough Y bounds for jungle leaves
                    if (currentLeafWorldY < baseWorldY + trunkHeight - 4 || currentLeafWorldY > baseWorldY + trunkHeight + 2) continue

                    val localLy = currentLeafWorldY - worldYOffset
                    if (localLy < 0 || localLy >= Chunk.HEIGHT) continue
                    
                    val distance = sqrt(
                        lx_offset.toDouble().pow(2) +
                        ly_offset_from_center.toDouble().pow(2) +
                        lz_offset.toDouble().pow(2)
                    )
                    
                    // Using local coordinates for noise here for simplicity, though world coords would be more consistent for noise patterns
                    val noise = SimplexNoise.noise(currentLeafLocalX * 0.5f, localLy * 0.5f, currentLeafLocalZ * 0.5f) * 0.5f
                    if (distance <= (3.0 + noise) && chunk.getBlock(currentLeafLocalX, localLy, currentLeafLocalZ) == BlockType.AIR.id) {
                        chunk.setBlock(currentLeafLocalX, localLy, currentLeafLocalZ, BlockType.LEAVES_OAK.id) // Assuming LEAVES_JUNGLE
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
        private const val SEA_LEVEL = 60 // World Y
        private const val BEDROCK_LEVEL = 5 // World Y
        private const val MIN_STONE_HEIGHT = 40 // World Y, though less relevant with new terrain logic
        
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