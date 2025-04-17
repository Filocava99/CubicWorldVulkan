package it.filippocavallari.cubicworld

/**
 * Main entry point for the CubicWorld application.
 * Initializes the CubicWorldEngine with Vulkan integration.
 */
fun main() {
    try {
        println("Starting CubicWorld voxel game with Vulkan...")
        
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