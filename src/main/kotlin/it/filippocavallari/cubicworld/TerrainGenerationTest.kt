package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.generators.BiodiverseWorldGenerator
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.data.block.BlockType

/**
 * Test terrain generation for chunk uniqueness and connectivity
 */
fun main() {
    println("Testing terrain generation...")
    
    // Create a world with BiodiverseWorldGenerator
    val seed = System.currentTimeMillis()
    val generator = BiodiverseWorldGenerator(seed)
    val world = World(generator)
    
    println("Using seed: $seed")
    
    // Test 1: Generate adjacent chunks and verify they have different content
    println("\n=== Test 1: Chunk Uniqueness ===")
    val chunk1 = world.loadChunkSynchronously(0, 0, 0) // Assuming Y=0 for these test chunks
    val chunk2 = world.loadChunkSynchronously(1, 0, 0) // Assuming Y=0
    val chunk3 = world.loadChunkSynchronously(0, 0, 1) // Assuming Y=0, and (0,1) means x=0, z=1
    val chunk4 = world.loadChunkSynchronously(1, 0, 1) // Assuming Y=0, and (1,1) means x=1, z=1
    
    // Compare chunks by sampling blocks at same local positions
    var identicalBlocks = 0
    var totalSamples = 0
    
    for (x in 0 until Chunk.SIZE step 4) {
        for (z in 0 until Chunk.SIZE step 4) {
            for (y in 0 until 128 step 8) {
                totalSamples++
                
                val block1 = chunk1.getBlock(x, y, z)
                val block2 = chunk2.getBlock(x, y, z)
                val block3 = chunk3.getBlock(x, y, z)
                val block4 = chunk4.getBlock(x, y, z)
                
                if (block1 == block2 && block2 == block3 && block3 == block4) {
                    identicalBlocks++
                }
            }
        }
    }
    
    val uniquenessPercentage = ((totalSamples - identicalBlocks).toFloat() / totalSamples) * 100
    println("Chunk uniqueness: %.2f%% (${totalSamples - identicalBlocks}/$totalSamples blocks are different)".format(uniquenessPercentage))
    
    if (uniquenessPercentage < 80) {
        println("WARNING: Chunks appear to be too similar! Check noise generation.")
    } else {
        println("SUCCESS: Chunks are properly unique.")
    }
    
    // Test 2: Check chunk borders for continuity
    println("\n=== Test 2: Chunk Border Continuity ===")
    var discontinuities = 0
    var borderChecks = 0
    
    // Check east border of chunk1 against west border of chunk2
    for (z in 0 until Chunk.SIZE) {
        for (y in 0 until 128) {
            borderChecks++
            
            // Get height at the border
            val eastBorderBlock = chunk1.getBlock(Chunk.SIZE - 1, y, z)
            val westBorderBlock = chunk2.getBlock(0, y, z)
            
            // Get the world position for debugging
            val worldX1 = chunk1.getWorldX() + Chunk.SIZE - 1
            val worldX2 = chunk2.getWorldX()
            val worldZ = chunk1.getWorldZ() + z
            
            // These should be adjacent blocks in the world
            if (worldX1 + 1 != worldX2) {
                println("ERROR: World coordinate mismatch at border!")
            }
        }
    }
    
    // Check surface height continuity
    for (z in 0 until Chunk.SIZE) {
        // Find surface height at east edge of chunk1
        var height1 = 0
        for (y in Chunk.HEIGHT - 1 downTo 0) {
            if (chunk1.getBlock(Chunk.SIZE - 1, y, z) != BlockType.AIR.id) {
                height1 = y
                break
            }
        }
        
        // Find surface height at west edge of chunk2
        var height2 = 0
        for (y in Chunk.HEIGHT - 1 downTo 0) {
            if (chunk2.getBlock(0, y, z) != BlockType.AIR.id) {
                height2 = y
                break
            }
        }
        
        // Check if heights differ by more than reasonable amount
        val heightDiff = kotlin.math.abs(height1 - height2)
        if (heightDiff > 3) {
            discontinuities++
            println("Height discontinuity at z=$z: chunk1=$height1, chunk2=$height2 (diff=$heightDiff)")
        }
    }
    
    println("Border discontinuities: $discontinuities/${Chunk.SIZE}")
    
    if (discontinuities > 5) {
        println("WARNING: Too many discontinuities at chunk borders!")
    } else {
        println("SUCCESS: Chunk borders are reasonably continuous.")
    }
    
    // Test 3: Verify chunk generation produces complete meshes
    println("\n=== Test 3: Chunk Completeness ===")
    
    // Count non-air blocks in each layer
    for (chunkCoord in listOf(Pair(0, 0), Pair(1, 0), Pair(0, 1), Pair(1, 1))) { // These are (x,z) pairs
        val chunk = world.getChunk(chunkCoord.first, 0, chunkCoord.second)!! // Assuming Y=0 for getChunk
        
        var totalBlocks = 0
        var airBlocks = 0
        
        for (x in 0 until Chunk.SIZE) {
            for (z in 0 until Chunk.SIZE) {
                for (y in 0 until Chunk.HEIGHT) {
                    totalBlocks++
                    if (chunk.getBlock(x, y, z) == BlockType.AIR.id) {
                        airBlocks++
                    }
                }
            }
        }
        
        val fillPercentage = ((totalBlocks - airBlocks).toFloat() / totalBlocks) * 100
        println("Chunk (${chunkCoord.first}, ${chunkCoord.second}): %.2f%% filled".format(fillPercentage))
        
        if (fillPercentage < 10) {
            println("WARNING: Chunk appears to be mostly empty!")
        }
    }
    
    // Test 4: Print a height map slice for visual inspection
    println("\n=== Test 4: Height Map Visualization ===")
    println("Height map for chunks (0,0) and (1,0) at z=8:")
    
    for (x in 0 until Chunk.SIZE * 2) {
        val chunkX = if (x < Chunk.SIZE) 0 else 1
        val localX = x % Chunk.SIZE
        // Assuming the '0' in world.getChunk(chunkX, 0) was intended for the Y-coordinate of the chunk.
        // The Z-coordinate of the chunk for this test (where it says "chunks (0,0) and (1,0)") is 0.
        val chunk = world.getChunk(chunkX, 0, 0)!! 
        
        // Find surface height
        var height = 0
        for (y in Chunk.HEIGHT - 1 downTo 0) {
            if (chunk.getBlock(localX, y, 8) != BlockType.AIR.id) {
                height = y
                break
            }
        }
        
        // Print height as ASCII art
        val normalizedHeight = ((height - 50) / 3).coerceIn(0, 9)
        print(normalizedHeight)
        
        if (x == Chunk.SIZE - 1) {
            print("|") // Mark chunk boundary
        }
    }
    println()
    
    println("\n=== Terrain Generation Test Complete ===")
    
    // Cleanup
    world.cleanup()
}