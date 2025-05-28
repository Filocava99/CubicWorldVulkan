package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.chunk.Chunk

/**
 * Simple test to verify chunk loading and positioning
 */
fun main() {
    println("=== Chunk Loading Test ===")
    
    // Create a test world
    val world = World(it.filippocavallari.cubicworld.world.generators.BiodiverseWorldGenerator())
    
    // Test chunk positions
    val testChunks = listOf(
        Pair(0, 0),
        Pair(1, 0),
        Pair(0, 1),
        Pair(-1, 0),
        Pair(0, -1)
    )
    
    for ((x, z) in testChunks) {
        val chunk = Chunk(x, z, world)
        val worldX = chunk.getWorldX()
        val worldZ = chunk.getWorldZ()
        
        println("\nChunk ($x, $z):")
        println("  World position: ($worldX, $worldZ)")
        println("  Expected: (${x * Chunk.SIZE}, ${z * Chunk.SIZE})")
        
        // Verify the calculation
        val expectedX = x * Chunk.SIZE
        val expectedZ = z * Chunk.SIZE
        
        if (worldX != expectedX || worldZ != expectedZ) {
            println("  ERROR: Position mismatch!")
        } else {
            println("  ✓ Position correct")
        }
    }
    
    // Test coordinate conversion
    println("\n=== Coordinate Conversion Test ===")
    val testCoords = listOf(-17, -16, -1, 0, 1, 15, 16, 17)
    
    for (coord in testCoords) {
        val chunkCoord = Chunk.worldToChunk(coord)
        val localCoord = Chunk.worldToLocal(coord)
        
        println("World coord $coord -> Chunk $chunkCoord, Local $localCoord")
        
        // Verify by reconstructing
        val reconstructed = chunkCoord * Chunk.SIZE + localCoord
        if (reconstructed != coord) {
            println("  ERROR: Reconstruction failed! Got $reconstructed")
        }
    }
    
    println("\n=== Test Complete ===")
}