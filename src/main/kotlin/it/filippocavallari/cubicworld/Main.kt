package it.filippocavallari.cubicworld

/**
 * Main entry point for the CubicWorld application.
 * Initializes and starts the game engine.
 */
fun main() {
    try {
        val engine = CubicWorldEngine()
        engine.start()
    } catch (e: Exception) {
        e.printStackTrace()
        System.exit(-1)
    }
}
