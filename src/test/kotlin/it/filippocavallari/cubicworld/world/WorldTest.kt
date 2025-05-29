package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.WorldGenerator
import org.joml.Vector3i
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class WorldTest {

    // Inner class for TestWorldGenerator
    class TestWorldGenerator : WorldGenerator {
        var lastGeneratedChunk: Chunk? = null
        var generatedChunksPositions = mutableListOf<Vector3i>()

        override fun generateChunk(chunk: Chunk) {
            // Example: fill based on chunk's Y position for variety, or a fixed value
            val fillBlockId = chunk.position.y + 100 
            chunk.fill(fillBlockId)
            lastGeneratedChunk = chunk
            generatedChunksPositions.add(Vector3i(chunk.position))
        }
    }

    private lateinit var testGenerator: TestWorldGenerator
    private lateinit var world: World

    @BeforeEach
    fun setUp() {
        testGenerator = TestWorldGenerator()
        world = World(testGenerator) // Assuming default constructor for World is fine or provide paths if needed
    }

    @Test
    fun testLoadGetIsLoadedUnloadChunk() {
        val chunk = world.loadChunkSynchronously(1, 2, 3)
        assertNotNull(chunk, "Loaded chunk should not be null")
        assertEquals(Vector3i(1, 2, 3), chunk.position, "Chunk position mismatch")
        assertTrue(world.isChunkLoaded(1, 2, 3), "isChunkLoaded should be true for loaded chunk")
        assertSame(chunk, world.getChunk(1, 2, 3), "getChunk should return the same loaded chunk instance")
        
        // chunk.position.y is 2, TestGenerator fills with y+100 => 2+100 = 102
        assertEquals(102, chunk.getBlock(0, 0, 0), "Block ID check based on TestWorldGenerator logic")

        world.unloadChunk(1, 2, 3)
        assertNull(world.getChunk(1, 2, 3), "getChunk should return null after unload")
        assertFalse(world.isChunkLoaded(1, 2, 3), "isChunkLoaded should be false after unload")
    }

    @Test
    fun testGetAndSetBlockInWorldCoordinates() {
        val chunk000 = world.loadChunkSynchronously(0, 0, 0) // Filled with ID 0+100 = 100
        val chunk100 = world.loadChunkSynchronously(1, 0, 0) // Filled with ID 0+100 = 100
        val chunk010 = world.loadChunkSynchronously(0, 1, 0) // Filled with ID 1+100 = 101

        // Test getBlock
        assertEquals(100, world.getBlock(0, 0, 0), "getBlock in chunk (0,0,0)")
        assertEquals(100, world.getBlock(15, 15, 15), "getBlock at edge of chunk (0,0,0), localY 15") //Chunk.HEIGHT is 16
        assertEquals(0, world.getBlock(0, 0, 16), "getBlock in non-loaded chunk (0,0,1) should be AIR")
        assertEquals(100, world.getBlock(16, 0, 0), "getBlock in chunk (1,0,0)")
        assertEquals(101, world.getBlock(0, 16, 0), "getBlock in chunk (0,1,0), worldY 16 is localY 0 in this chunk")

        // Test setBlock
        world.setBlock(1, 1, 1, 55) // This is in chunk (0,0,0)
        assertEquals(55, world.getBlock(1, 1, 1), "getBlock after setBlock")
        assertNotNull(chunk000, "Chunk (0,0,0) should exist")
        assertTrue(chunk000.isDirty(), "Chunk (0,0,0) should be dirty after setBlock")

        world.setBlock(15, 0, 0, 11) // Modifies chunk (0,0,0)
        assertEquals(11, world.getBlock(15,0,0))

        world.setBlock(16, 0, 0, 22) // Modifies chunk (1,0,0)
        assertEquals(22, world.getBlock(16,0,0))
        assertNotNull(chunk100, "Chunk (1,0,0) should exist")
        assertTrue(chunk100.isDirty(), "Chunk (1,0,0) should be dirty after setBlock")
    }
    
    @Test
    fun testSetBlockMarksAdjacentChunksDirty() {
        val c000 = world.loadChunkSynchronously(0,0,0)
        val c100 = world.loadChunkSynchronously(1,0,0) // Adjacent X+
        val c_100 = world.loadChunkSynchronously(-1,0,0) // Adjacent X-
        val c010 = world.loadChunkSynchronously(0,1,0) // Adjacent Y+
        val c0_10 = world.loadChunkSynchronously(0,-1,0) // Adjacent Y-
        val c001 = world.loadChunkSynchronously(0,0,1) // Adjacent Z+
        val c00_1 = world.loadChunkSynchronously(0,0,-1) // Adjacent Z-

        c000.markClean(); c100.markClean(); c_100.markClean(); 
        c010.markClean(); c0_10.markClean(); 
        c001.markClean(); c00_1.markClean()

        // Test X+ edge
        world.setBlock(15, 5, 5, 123) // Edge of c000, localX = 15
        assertTrue(c000.isDirty(), "c000 should be dirty (X+ edge)")
        assertTrue(c100.isDirty(), "c100 (X+ neighbor) should be dirty")
        assertFalse(c_100.isDirty(), "c_100 (X- neighbor) should NOT be dirty (X+ edge)")
        c000.markClean(); c100.markClean()

        // Test X- edge
        world.setBlock(0, 5, 5, 124) // Edge of c000, localX = 0
        assertTrue(c000.isDirty(), "c000 should be dirty (X- edge)")
        assertTrue(c_100.isDirty(), "c_100 (X- neighbor) should be dirty")
        c000.markClean(); c_100.markClean()

        // Test Y+ edge
        world.setBlock(5, 15, 5, 125) // Edge of c000, localY = 15
        assertTrue(c000.isDirty(), "c000 should be dirty (Y+ edge)")
        assertTrue(c010.isDirty(), "c010 (Y+ neighbor) should be dirty")
        assertFalse(c100.isDirty(), "c100 should NOT be dirty (Y+ edge)") 
        c000.markClean(); c010.markClean()

        // Test Y- edge
        world.setBlock(5, 0, 5, 126) // Edge of c000, localY = 0
        assertTrue(c000.isDirty(), "c000 should be dirty (Y- edge)")
        assertTrue(c0_10.isDirty(), "c0_10 (Y- neighbor) should be dirty")
        c000.markClean(); c0_10.markClean()
        
        // Test Z+ edge
        world.setBlock(5, 5, 15, 127) // Edge of c000, localZ = 15
        assertTrue(c000.isDirty(), "c000 should be dirty (Z+ edge)")
        assertTrue(c001.isDirty(), "c001 (Z+ neighbor) should be dirty")
        c000.markClean(); c001.markClean()

        // Test Z- edge
        world.setBlock(5, 5, 0, 128) // Edge of c000, localZ = 0
        assertTrue(c000.isDirty(), "c000 should be dirty (Z- edge)")
        assertTrue(c00_1.isDirty(), "c00_1 (Z- neighbor) should be dirty")
        c000.markClean(); c00_1.markClean()
    }

    @Test
    fun testChunkListener() {
        var loaded = false
        var unloaded = false
        var updated = false
        
        val listener = object : World.ChunkListener {
            override fun onChunkLoaded(chunk: Chunk) { loaded = true }
            override fun onChunkUnloaded(chunk: Chunk) { unloaded = true }
            override fun onChunkUpdated(chunk: Chunk) { updated = true }
        }
        world.addChunkListener(listener)

        val c = world.loadChunkSynchronously(0,0,0)
        assertTrue(loaded, "onChunkLoaded should have been called")
        loaded = false // Reset for next check

        world.setBlock(0,0,0,1) // In chunk c
        assertTrue(updated, "onChunkUpdated should have been called")
        updated = false 

        world.unloadChunk(0,0,0)
        assertTrue(unloaded, "onChunkUnloaded should have been called")
        unloaded = false

        // Test listener removal
        world.removeChunkListener(listener)
        world.loadChunkSynchronously(1,1,1)
        assertFalse(loaded, "onChunkLoaded should NOT be called after listener removal")
        world.setBlock(1*Chunk.SIZE + 0, 1*Chunk.HEIGHT + 0, 1*Chunk.SIZE + 0, 99) // setBlock in chunk (1,1,1)
        assertFalse(updated, "onChunkUpdated should NOT be called after listener removal")
        world.unloadChunk(1,1,1)
        assertFalse(unloaded, "onChunkUnloaded should NOT be called after listener removal")
    }

    @Test
    fun testGetLoadedChunksProperty() {
        assertEquals(0, world.loadedChunks.size, "Initially no chunks should be loaded")
        world.loadChunkSynchronously(0,0,0)
        assertEquals(1, world.loadedChunks.size, "Size should be 1 after loading one chunk")
        world.loadChunkSynchronously(1,0,0)
        assertEquals(2, world.loadedChunks.size, "Size should be 2 after loading another chunk")
        world.loadChunkSynchronously(0,0,0) // Loading same chunk again
        assertEquals(2, world.loadedChunks.size, "Size should still be 2 after loading same chunk")
        world.unloadChunk(0,0,0)
        assertEquals(1, world.loadedChunks.size, "Size should be 1 after unloading one chunk")
        world.unloadChunk(1,0,0)
        assertEquals(0, world.loadedChunks.size, "Size should be 0 after unloading all chunks")
    }

    // Test for loading a chunk that is already being generated (async scenario, simplified for sync test)
    // In the current World.loadChunk, if a chunk is already in chunksMap, it's returned.
    // If we want to test the chunkGenerationTasks part, that would require more setup for async execution.
    // This test primarily checks that calling loadChunk for an existing chunk doesn't break things.
    @Test
    fun testLoadExistingChunk() {
        val chunk1 = world.loadChunkSynchronously(0,0,0)
        val chunk2 = world.loadChunkSynchronously(0,0,0) // Request same chunk
        assertSame(chunk1, chunk2, "Loading an already loaded chunk should return the same instance")
        assertEquals(1, testGenerator.generatedChunksPositions.count { it == Vector3i(0,0,0) },
            "Generator should only be called once for chunk (0,0,0)")
    }
    
    @Test
    fun testGetBlockOnUnloadedChunk() {
        assertEquals(0, world.getBlock(100, 100, 100), "getBlock on an unloaded chunk should return AIR (0)")
    }

    @Test
    fun testSetBlockOnUnloadedChunk() {
        assertDoesNotThrow({
            world.setBlock(200, 200, 200, 123) // Should not throw, just do nothing
        }, "setBlock on an unloaded chunk should not throw")
        assertEquals(0, world.getBlock(200,200,200), "Block should remain AIR after setBlock on unloaded chunk")
    }
}
