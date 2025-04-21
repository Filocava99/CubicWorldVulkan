package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.world.generators.biome.BiomeRegistry
import it.filippocavallari.cubicworld.world.generators.biome.ForestBiomeGenerator
import it.filippocavallari.cubicworld.world.generators.biome.DesertBiomeGenerator
import it.filippocavallari.cubicworld.world.generators.biome.MountainBiomeGenerator
import it.filippocavallari.cubicworld.world.generators.biome.MagicalForestBiomeGenerator
import it.filippocavallari.cubicworld.world.generators.WorldGeneratorRegistry

/**
 * Main entry point for the CubicWorld application.
 * Initializes the CubicWorldEngine with Vulkan integration.
 */
fun main() {
    try {
        println("Starting CubicWorld voxel game with Vulkan...")
        
        // Initialize core components
        initializeComponents()
        
        // Create and start the CubicWorld engine
        val engine = CubicWorldEngine()
        engine.start()
        
        println("CubicWorld engine exited successfully")
    } catch (e: Exception) {
        println("Error starting CubicWorld: ${e.message}")
        e.printStackTrace()
        System.exit(-1)
    }
}

/**
 * Initialize core game components before starting the engine
 */
private fun initializeComponents() {
    println("Initializing world generation components...")
    
    // Initialize the world generator registry
    WorldGeneratorRegistry.initialize()
    
    // Register additional biome generators (more would be added in a full implementation)
    println("Available world generators: ${WorldGeneratorRegistry.getRegisteredGenerators()}")
    
    println("World generation components initialized successfully!")
}