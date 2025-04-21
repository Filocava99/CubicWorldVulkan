package it.filippocavallari.cubicworld.world.generators.biome

/**
 * A registry for all biome generators in the game.
 * This manages biome registration and retrieval.
 */
object BiomeRegistry {
    // Map of all registered biomes by ID
    private val biomes = mutableMapOf<Int, BiomeGenerator>()
    
    // List of all biomes for iteration
    private val biomeList = mutableListOf<BiomeGenerator>()
    
    // For efficient climate-based biome lookup
    private val climateMap = Array(10) { Array(10) { mutableListOf<BiomeGenerator>() } }
    
    /**
     * Register a biome generator
     */
    fun register(biome: BiomeGenerator) {
        biomes[biome.id] = biome
        biomeList.add(biome)
        
        // Add to climate map for efficient lookup
        val tempIndex = (biome.temperature * 9).toInt().coerceIn(0, 9)
        val humidityIndex = (biome.humidity * 9).toInt().coerceIn(0, 9)
        climateMap[tempIndex][humidityIndex].add(biome)
    }
    
    /**
     * Get a biome by its ID
     */
    fun getBiome(id: Int): BiomeGenerator? {
        return biomes[id]
    }
    
    /**
     * Get a biome based on climate values
     */
    fun getBiomeByClimate(temperature: Float, humidity: Float): BiomeGenerator {
        val tempIndex = (temperature * 9).toInt().coerceIn(0, 9)
        val humidityIndex = (humidity * 9).toInt().coerceIn(0, 9)
        
        val candidates = climateMap[tempIndex][humidityIndex]
        
        // If we have biomes in this climate zone, return a random one
        if (candidates.isNotEmpty()) {
            val randomIndex = (temperature * 1000 + humidity * 100).toInt() % candidates.size
            return candidates[randomIndex]
        }
        
        // Otherwise, find the closest biome by climate difference
        return findClosestBiomeByClimate(temperature, humidity)
    }
    
    /**
     * Find the biome that most closely matches the given climate values
     */
    private fun findClosestBiomeByClimate(temperature: Float, humidity: Float): BiomeGenerator {
        if (biomeList.isEmpty()) {
            throw IllegalStateException("No biomes registered")
        }
        
        var closestBiome = biomeList[0]
        var minDistance = Float.MAX_VALUE
        
        for (biome in biomeList) {
            val tempDiff = biome.temperature - temperature
            val humidityDiff = biome.humidity - humidity
            val distance = tempDiff * tempDiff + humidityDiff * humidityDiff
            
            if (distance < minDistance) {
                minDistance = distance
                closestBiome = biome
            }
        }
        
        return closestBiome
    }
    
    /**
     * Get list of all registered biomes
     */
    fun getAllBiomes(): List<BiomeGenerator> {
        return biomeList.toList()
    }
    
    /**
     * Get the number of registered biomes
     */
    fun count(): Int {
        return biomes.size
    }
}