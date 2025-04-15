package it.filippocavallari.cubicworld.test

import it.filippocavallari.cubicworld.CubicWorldEngine

/**
 * Simple test application for the voxel rendering pipeline.
 * Initializes the engine and renders a test scene.
 */
fun main() {
    println("Starting VoxelRenderer test...")
    
    try {
        // Create and start engine
        val engine = CubicWorldEngine()
        engine.start()
    } catch (e: Exception) {
        e.printStackTrace()
        System.exit(-1)
    }
}
