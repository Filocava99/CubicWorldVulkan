package it.filippocavallari.cubicworld.world.chunk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.joml.Vector3i

class ChunkTest {

    @Test
    fun testChunkInitialization() {
        val chunk = Chunk(1, 2, 3)
        assertEquals(Vector3i(1, 2, 3), chunk.position, "Chunk position should be (1, 2, 3)")
        assertTrue(chunk.isDirty(), "New chunk should initially be dirty")
    }

    @Test
    fun testWorldToChunkXZConversion() {
        assertEquals(0, Chunk.worldToChunkXZ(0), "worldToChunkXZ(0)")
        assertEquals(0, Chunk.worldToChunkXZ(15), "worldToChunkXZ(15)")
        assertEquals(1, Chunk.worldToChunkXZ(16), "worldToChunkXZ(16)")
        assertEquals(-1, Chunk.worldToChunkXZ(-1), "worldToChunkXZ(-1)")
        assertEquals(-1, Chunk.worldToChunkXZ(-16), "worldToChunkXZ(-16)") // Chunk containing -16 is chunk -1
        assertEquals(-2, Chunk.worldToChunkXZ(-17), "worldToChunkXZ(-17)") // Chunk containing -17 is chunk -2
    }

    @Test
    fun testWorldToChunkYConversion() {
        assertEquals(0, Chunk.worldToChunkY(0), "worldToChunkY(0)")
        assertEquals(0, Chunk.worldToChunkY(15), "worldToChunkY(15)") // Max Y in chunk 0 (HEIGHT is 16)
        assertEquals(1, Chunk.worldToChunkY(16), "worldToChunkY(16)") // Min Y in chunk 1
        assertEquals(-1, Chunk.worldToChunkY(-1), "worldToChunkY(-1)")
        assertEquals(-1, Chunk.worldToChunkY(-16), "worldToChunkY(-16)")
        assertEquals(-2, Chunk.worldToChunkY(-17), "worldToChunkY(-17)")
    }

    @Test
    fun testWorldToLocalXZConversion() {
        assertEquals(0, Chunk.worldToLocalXZ(0), "worldToLocalXZ(0)")
        assertEquals(15, Chunk.worldToLocalXZ(15), "worldToLocalXZ(15)")
        assertEquals(0, Chunk.worldToLocalXZ(16), "worldToLocalXZ(16)")
        assertEquals(1, Chunk.worldToLocalXZ(17), "worldToLocalXZ(17)")
        assertEquals(15, Chunk.worldToLocalXZ(-1), "worldToLocalXZ(-1)")
        assertEquals(0, Chunk.worldToLocalXZ(-16), "worldToLocalXZ(-16)")
        assertEquals(15, Chunk.worldToLocalXZ(-17), "worldToLocalXZ(-17)")
    }

    @Test
    fun testWorldToLocalYConversion() {
        assertEquals(0, Chunk.worldToLocalY(0), "worldToLocalY(0)")
        assertEquals(15, Chunk.worldToLocalY(15), "worldToLocalY(15)")
        assertEquals(0, Chunk.worldToLocalY(16), "worldToLocalY(16)")
        assertEquals(1, Chunk.worldToLocalY(17), "worldToLocalY(17)")
        assertEquals(15, Chunk.worldToLocalY(-1), "worldToLocalY(-1)")
        assertEquals(0, Chunk.worldToLocalY(-16), "worldToLocalY(-16)")
        assertEquals(15, Chunk.worldToLocalY(-17), "worldToLocalY(-17)")
    }

    @Test
    fun testGetAndSetBlock() {
        val chunk = Chunk(0, 0, 0)
        
        chunk.setBlock(0, 0, 0, 1)
        assertEquals(1, chunk.getBlock(0, 0, 0), "Block (0,0,0) should be 1")

        chunk.setBlock(15, 15, 15, 2) // Max local coords since SIZE/HEIGHT is 16
        assertEquals(2, chunk.getBlock(15, 15, 15), "Block (15,15,15) should be 2")

        chunk.setBlock(5, 7, 9, 3)
        assertEquals(3, chunk.getBlock(5, 7, 9), "Block (5,7,9) should be 3")

        // Test setting same block ID does not make chunk dirty if it was clean
        chunk.setBlock(0,0,0,1) // Ensure it's 1
        chunk.markClean()
        assertFalse(chunk.isDirty(), "Chunk should be clean after markClean()")
        chunk.setBlock(0,0,0,1) // Set same ID
        assertFalse(chunk.isDirty(), "Setting same block ID should not make chunk dirty")

        // Test setting different block ID makes chunk dirty
        chunk.markClean()
        assertFalse(chunk.isDirty(), "Chunk should be clean again")
        chunk.setBlock(0,0,0,2) // Set different ID
        assertTrue(chunk.isDirty(), "Setting different block ID should make chunk dirty")
        
        // Test out-of-bounds getBlock
        assertEquals(0, chunk.getBlock(-1, 0, 0), "Out-of-bounds getBlock X should return 0 (AIR)")
        assertEquals(0, chunk.getBlock(0, -1, 0), "Out-of-bounds getBlock Y should return 0 (AIR)")
        assertEquals(0, chunk.getBlock(0, 0, -1), "Out-of-bounds getBlock Z should return 0 (AIR)")
        assertEquals(0, chunk.getBlock(Chunk.SIZE, 0, 0), "Out-of-bounds getBlock X_MAX should return 0 (AIR)")
        assertEquals(0, chunk.getBlock(0, Chunk.HEIGHT, 0), "Out-of-bounds getBlock Y_MAX should return 0 (AIR)")
        assertEquals(0, chunk.getBlock(0, 0, Chunk.SIZE), "Out-of-bounds getBlock Z_MAX should return 0 (AIR)")

        // Test out-of-bounds setBlock doesn't throw and doesn't change valid blocks
        val originalBlock = chunk.getBlock(1,1,1)
        assertDoesNotThrow { chunk.setBlock(-1, 0, 0, 100) }
        assertDoesNotThrow { chunk.setBlock(0, -1, 0, 100) }
        assertDoesNotThrow { chunk.setBlock(0, 0, -1, 100) }
        assertDoesNotThrow { chunk.setBlock(Chunk.SIZE, 0, 0, 100) }
        assertDoesNotThrow { chunk.setBlock(0, Chunk.HEIGHT, 0, 100) }
        assertDoesNotThrow { chunk.setBlock(0, 0, Chunk.SIZE, 100) }
        assertEquals(originalBlock, chunk.getBlock(1,1,1), "Out-of-bounds setBlock should not affect other blocks")
    }

    @Test
    fun testFill() {
        val chunk = Chunk(0,0,0)
        chunk.fill(5)
        assertEquals(5, chunk.getBlock(0,0,0), "fill(5) check (0,0,0)")
        assertEquals(5, chunk.getBlock(15,15,15), "fill(5) check (15,15,15)")
        assertEquals(5, chunk.getBlock(7,8,9), "fill(5) check (7,8,9)")
        assertTrue(chunk.isDirty(), "fill() should make chunk dirty")
    }

    @Test
    fun testFillLayer() {
        val chunk = Chunk(0,0,0) // Default block is 0 (AIR)
        chunk.fillLayer(5, 3)
        assertEquals(3, chunk.getBlock(0,5,0), "fillLayer(5,3) check (0,5,0)")
        assertEquals(3, chunk.getBlock(15,5,15), "fillLayer(5,3) check (15,5,15)")
        assertEquals(0, chunk.getBlock(0,4,0), "fillLayer(5,3) check (0,4,0) should be AIR")
        assertEquals(0, chunk.getBlock(0,6,0), "fillLayer(5,3) check (0,6,0) should be AIR")
        assertTrue(chunk.isDirty(), "fillLayer() should make chunk dirty")

        // Test out-of-bounds fillLayer
        chunk.markClean()
        assertDoesNotThrow { chunk.fillLayer(-1, 100) }
        assertFalse(chunk.isDirty(), "fillLayer(-1) should not make chunk dirty")
        assertDoesNotThrow { chunk.fillLayer(Chunk.HEIGHT, 100) }
        assertFalse(chunk.isDirty(), "fillLayer(Chunk.HEIGHT) should not make chunk dirty")
        assertEquals(3, chunk.getBlock(0,5,0), "Out-of-bounds fillLayer should not have changed existing blocks")
    }

    @Test
    fun testGetWorldCoordinates() {
        val chunk1 = Chunk(0,0,0)
        assertEquals(0, chunk1.getWorldX(), "chunk1 worldX")
        assertEquals(0, chunk1.getWorldY(), "chunk1 worldY")
        assertEquals(0, chunk1.getWorldZ(), "chunk1 worldZ")

        val chunk2 = Chunk(1,2,3)
        assertEquals(1 * Chunk.SIZE, chunk2.getWorldX(), "chunk2 worldX")
        assertEquals(2 * Chunk.HEIGHT, chunk2.getWorldY(), "chunk2 worldY")
        assertEquals(3 * Chunk.SIZE, chunk2.getWorldZ(), "chunk2 worldZ")

        val chunk3 = Chunk(-1,-1,-1)
        assertEquals(-1 * Chunk.SIZE, chunk3.getWorldX(), "chunk3 worldX")
        assertEquals(-1 * Chunk.HEIGHT, chunk3.getWorldY(), "chunk3 worldY")
        assertEquals(-1 * Chunk.SIZE, chunk3.getWorldZ(), "chunk3 worldZ")
    }

    @Test
    fun testEqualsAndHashCode() {
        val chunkA1 = Chunk(1,2,3)
        val chunkA2 = Chunk(1,2,3)
        val chunkB = Chunk(1,2,4) // Different Z
        val chunkC = Chunk(4,2,3) // Different X
        val chunkD = Chunk(1,4,3) // Different Y

        assertEquals(chunkA1, chunkA2, "Chunks with same position should be equal")
        assertEquals(chunkA1.hashCode(), chunkA2.hashCode(), "Hashcodes for equal chunks should be same")

        assertNotEquals(chunkA1, chunkB, "Chunks with different Z should not be equal")
        assertNotEquals(chunkA1, chunkC, "Chunks with different X should not be equal")
        assertNotEquals(chunkA1, chunkD, "Chunks with different Y should not be equal")

        assertFalse(chunkA1.equals(null), "Chunk should not be equal to null")
        assertFalse(chunkA1.equals("some_string"), "Chunk should not be equal to a different type")
    }
}
