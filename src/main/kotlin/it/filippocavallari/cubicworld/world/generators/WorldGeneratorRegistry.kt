package it.filippocavallari.cubicworld.world.generators

/**
 * Registry for world generators.
 * This allows easy switching between different world generation approaches.
 */
object WorldGeneratorRegistry {
    // Map of registered world generators
    private val generators = mutableMapOf<String, (Long) -> WorldGenerator>()
    
    /**
     * Register a world generator factory
     */
    fun register(name: String, factory: (Long) -> WorldGenerator) {
        generators[name] = factory
    }
    
    /**
     * Create a world generator by name
     */
    fun create(name: String, seed: Long = System.currentTimeMillis()): WorldGenerator {
        val factory = generators[name] ?: throw IllegalArgumentException("No world generator registered with name: $name")
        return factory(seed)
    }
    
    /**
     * Get all registered generator names
     */
    fun getRegisteredGenerators(): Set<String> {
        return generators.keys
    }
    
    /**
     * Initialize the registry with standard generators
     */
    fun initialize() {
        // Register the flat world generator
        register("flat") { seed -> FlatWorldGenerator() }
        
        // Register the terrain generator
        register("terrain") { seed -> TerrainGenerator() }
        
        // Register our new diverse biome generator
        register("biodiverse") { seed -> BiodiverseWorldGenerator(seed) }
    }
    
    /**
     * Get the default world generator
     */
    fun getDefault(seed: Long = System.currentTimeMillis()): WorldGenerator {
        return create("biodiverse", seed)
    }
}