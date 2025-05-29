package it.filippocavallari.cubicworld.debug

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.BiodiverseWorldGenerator
import it.filippocavallari.cubicworld.world.generators.biome.BiomeRegistry
import it.filippocavallari.cubicworld.world.generators.noise.NoiseFactory
import org.joml.Vector2i
import org.joml.Vector3i

/**
 * Debug utility to identify biomes at specific world coordinates
 */
object BiomeDebugger {
    
    /**
     * Get the biome at a specific world coordinate
     */
    fun getBiomeAt(worldX: Int, worldZ: Int, worldGenerator: BiodiverseWorldGenerator): String {
        // Calculate temperature and humidity using the same logic as BiodiverseWorldGenerator
        val seed = worldGenerator.seed
        
        // Generate temperature and humidity with enhanced variation
        val baseTemperatureNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() * 0.001f + seed * 0.1f, 
            worldZ.toFloat() * 0.001f + seed * 0.2f,
            4,
            0.0025f // BIOME_SCALE * 0.5f
        )
        
        val baseHumidityNoise = NoiseFactory.octavedSimplexNoise(
            worldX.toFloat() * 0.001f + seed * 0.3f, 
            worldZ.toFloat() * 0.001f + seed * 0.4f,
            4,
            0.0025f // BIOME_SCALE * 0.5f
        )
        
        // Add continental drift effects
        val continentalTempShift = NoiseFactory.simplexNoise(
            worldX.toFloat() * 0.0001f + seed * 0.5f,
            worldZ.toFloat() * 0.0001f + seed * 0.6f,
            1.0f
        ) * 0.3f
        
        val continentalHumidityShift = NoiseFactory.simplexNoise(
            worldX.toFloat() * 0.0001f + seed * 0.7f,
            worldZ.toFloat() * 0.0001f + seed * 0.8f,
            1.0f
        ) * 0.3f
        
        // Combine noise values
        var temperature = (baseTemperatureNoise + 1.0f) * 0.5f + continentalTempShift
        var humidity = (baseHumidityNoise + 1.0f) * 0.5f + continentalHumidityShift
        
        // Enhance contrast (S-curve)
        temperature = temperature.coerceIn(0.0f, 1.0f)
        temperature = temperature * temperature * (3.0f - 2.0f * temperature)
        
        humidity = humidity.coerceIn(0.0f, 1.0f)
        humidity = humidity * humidity * (3.0f - 2.0f * humidity)
        
        // Clamp to valid range
        temperature = temperature.coerceIn(0.0f, 1.0f)
        humidity = humidity.coerceIn(0.0f, 1.0f)
        
        // Get biome based on climate values
        val biome = BiomeRegistry.getBiomeByClimate(temperature, humidity)
        
        return "${biome.name} (ID: ${biome.id}, Temp: %.2f, Humidity: %.2f)".format(temperature, humidity)
    }
    
    /**
     * Debug method to print biome information for a chunk
     */
    fun debugChunkBiomes(chunkX: Int, chunkZ: Int, worldGenerator: BiodiverseWorldGenerator) {
        println("=== Biome Debug for Chunk ($chunkX, $chunkZ) ===")
        
        // Sample corners and center of chunk
        val positions = listOf(
            Pair(0, 0) to "Top-left",
            Pair(Chunk.SIZE - 1, 0) to "Top-right",
            Pair(0, Chunk.SIZE - 1) to "Bottom-left",
            Pair(Chunk.SIZE - 1, Chunk.SIZE - 1) to "Bottom-right",
            Pair(Chunk.SIZE / 2, Chunk.SIZE / 2) to "Center"
        )
        
        for ((localPos, label) in positions) {
            val worldX = chunkX * Chunk.SIZE + localPos.first
            val worldZ = chunkZ * Chunk.SIZE + localPos.second
            val biomeInfo = getBiomeAt(worldX, worldZ, worldGenerator)
            println("  $label: $biomeInfo")
        }
        
        println("===============================")
    }
    
    /**
     * Find areas with exposed stone at surface level
     */
    fun findExposedStoneAreas(chunk: Chunk, heightMap: Array<IntArray>): List<Vector2i> {
        val exposedStonePositions = mutableListOf<Vector2i>()
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                val height = heightMap[x][z]
                
                // Check if the surface block is stone
                if (chunk.getBlock(x, height, z) == 3) { // BlockType.STONE.id
                    val worldX = chunk.position.x * Chunk.SIZE + x
                    val worldZ = chunk.position.y * Chunk.SIZE + z
                    exposedStonePositions.add(Vector2i(worldX, worldZ))
                }
            }
        }
        
        return exposedStonePositions
    }
    
    /**
     * Get biome information at player position - useful for debugging in-game
     */
    fun debugPlayerPosition(playerX: Int, playerY: Int, playerZ: Int, worldGenerator: BiodiverseWorldGenerator): String {
        val biomeInfo = getBiomeAt(playerX, playerZ, worldGenerator)
        
        // Calculate chunk position
        val chunkX = playerX / Chunk.SIZE
        val chunkZ = playerZ / Chunk.SIZE
        val localX = playerX % Chunk.SIZE
        val localZ = playerZ % Chunk.SIZE
        
        return """
            === Player Position Debug ===
            World Position: ($playerX, $playerY, $playerZ)
            Chunk: ($chunkX, $chunkZ)
            Local Position in Chunk: ($localX, $localZ)
            Biome: $biomeInfo
            ============================
        """.trimIndent()
    }
}
