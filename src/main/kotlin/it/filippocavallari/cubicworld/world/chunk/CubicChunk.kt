package it.filippocavallari.cubicworld.world.chunk

import it.filippocavallari.cubicworld.world.CubicWorld
import org.joml.Vector3i

/**
 * Represents a cubic chunk of the world, containing a 16x16x16 grid of blocks.
 * This replaces the traditional 16x256x16 chunk system with cubic chunks,
 * allowing for infinite world height and better memory efficiency.
 */
class CubicChunk(x: Int, y: Int, z: Int, val world: CubicWorld? = null) {
    // Position of this chunk in the world (in chunk coordinates)
    val position: Vector3i = Vector3i(x, y, z)
    
    // Block data storage (stored as block IDs)
    // Using ByteArray for memory efficiency since most blocks have IDs < 256
    private val blocks: ByteArray = ByteArray(SIZE * SIZE * SIZE)
    
    // Dirty flag for whether the chunk needs to be rebuilt
    private var dirty: Boolean = true
    
    // Flag to track if this chunk is completely empty (optimization)
    private var isEmpty: Boolean = true
    
    // Neighbor chunk references for efficient occlusion culling
    private var neighborUp: CubicChunk? = null
    private var neighborDown: CubicChunk? = null
    private var neighborNorth: CubicChunk? = null
    private var neighborSouth: CubicChunk? = null
    private var neighborEast: CubicChunk? = null
    private var neighborWest: CubicChunk? = null
    
    /**
     * Get the block ID at the specified position
     */
    fun getBlock(x: Int, y: Int, z: Int): Int {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return 0 // Return air for out of bounds
        }
        val index = getIndex(x, y, z)
        return blocks[index].toInt() and 0xFF // Convert to unsigned
    }
    
    /**
     * Set the block at the specified position
     */
    fun setBlock(x: Int, y: Int, z: Int, blockId: Int) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return // Ignore out of bounds
        }
        
        val index = getIndex(x, y, z)
        val currentBlock = blocks[index].toInt() and 0xFF
        
        // Only mark dirty if the block is actually changing
        if (currentBlock != blockId) {
            blocks[index] = blockId.toByte()
            dirty = true
            
            // Update empty flag
            if (blockId != 0) {
                isEmpty = false
            } else {
                // Check if chunk is now empty
                checkIfEmpty()
            }
            
            // Notify neighboring chunks if block is on edge
            notifyNeighborsIfEdge(x, y, z)
        }
    }
    
    /**
     * Check if the chunk is completely empty
     */
    private fun checkIfEmpty() {
        for (block in blocks) {
            if (block.toInt() != 0) {
                isEmpty = false
                return
            }
        }
        isEmpty = true
    }
    
    /**
     * Notify neighboring chunks if a block change is on the edge
     */
    private fun notifyNeighborsIfEdge(x: Int, y: Int, z: Int) {
        if (x == 0) neighborWest?.markDirty()
        if (x == SIZE - 1) neighborEast?.markDirty()
        if (y == 0) neighborDown?.markDirty()
        if (y == SIZE - 1) neighborUp?.markDirty()
        if (z == 0) neighborNorth?.markDirty()
        if (z == SIZE - 1) neighborSouth?.markDirty()
    }
    
    /**
     * Get block from neighboring chunk for occlusion culling
     */
    fun getBlockFromNeighbor(x: Int, y: Int, z: Int): Int {
        // Handle block queries that go outside this chunk
        when {
            x < 0 -> return neighborWest?.getBlock(SIZE - 1, y, z) ?: 0
            x >= SIZE -> return neighborEast?.getBlock(0, y, z) ?: 0
            y < 0 -> return neighborDown?.getBlock(x, SIZE - 1, z) ?: 0
            y >= SIZE -> return neighborUp?.getBlock(x, 0, z) ?: 0
            z < 0 -> return neighborNorth?.getBlock(x, y, SIZE - 1) ?: 0
            z >= SIZE -> return neighborSouth?.getBlock(x, y, 0) ?: 0
            else -> return getBlock(x, y, z)
        }
    }
    
    /**
     * Set neighbor chunks for efficient occlusion culling
     */
    fun setNeighbor(direction: Direction, neighbor: CubicChunk?) {
        when (direction) {
            Direction.UP -> neighborUp = neighbor
            Direction.DOWN -> neighborDown = neighbor
            Direction.NORTH -> neighborNorth = neighbor
            Direction.SOUTH -> neighborSouth = neighbor
            Direction.EAST -> neighborEast = neighbor
            Direction.WEST -> neighborWest = neighbor
        }
    }
    
    /**
     * Get the array index for a block position
     */
    private fun getIndex(x: Int, y: Int, z: Int): Int {
        return (y * SIZE * SIZE) + (z * SIZE) + x
    }
    
    /**
     * Check if this chunk is dirty (needs mesh rebuilding)
     */
    fun isDirty(): Boolean = dirty
    
    /**
     * Mark this chunk as clean after rebuilding
     */
    fun markClean() {
        dirty = false
    }
    
    /**
     * Mark this chunk as dirty to force a rebuild
     */
    fun markDirty() {
        dirty = true
    }
    
    /**
     * Check if this chunk is completely empty
     */
    fun isEmpty(): Boolean = isEmpty
    
    /**
     * Get the world X coordinate of the start of this chunk
     */
    fun getWorldX(): Int = position.x * SIZE
    
    /**
     * Get the world Y coordinate of the start of this chunk
     */
    fun getWorldY(): Int = position.y * SIZE
    
    /**
     * Get the world Z coordinate of the start of this chunk
     */
    fun getWorldZ(): Int = position.z * SIZE
    
    /**
     * Fill the entire chunk with the specified block
     */
    fun fill(blockId: Int) {
        val byteValue = blockId.toByte()
        blocks.fill(byteValue)
        dirty = true
        isEmpty = blockId == 0
    }
    
    /**
     * Fill a layer of the chunk with the specified block
     */
    fun fillLayer(y: Int, blockId: Int) {
        if (y < 0 || y >= SIZE) return
        
        val byteValue = blockId.toByte()
        val startIndex = y * SIZE * SIZE
        val endIndex = startIndex + SIZE * SIZE
        
        for (i in startIndex until endIndex) {
            blocks[i] = byteValue
        }
        
        dirty = true
        if (blockId != 0) isEmpty = false
    }
    
    /**
     * Get memory usage of this chunk in bytes
     */
    fun getMemoryUsage(): Int {
        // Block data + overhead (position, flags, references)
        return blocks.size + 64 // Approximate overhead
    }
    
    /**
     * Get the hash code for this chunk
     */
    override fun hashCode(): Int = position.hashCode()
    
    /**
     * Check if this chunk equals another object
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val chunk = other as CubicChunk
        return position == chunk.position
    }
    
    /**
     * Direction enum for neighbors
     */
    enum class Direction {
        UP, DOWN, NORTH, SOUTH, EAST, WEST
    }
    
    companion object {
        // Chunk dimensions - 16x16x16 cubic chunks
        const val SIZE = 16
        
        // Total blocks in a chunk
        const val TOTAL_BLOCKS = SIZE * SIZE * SIZE // 4096 blocks
        
        /**
         * Convert world coordinates to local chunk coordinates
         */
        fun worldToLocal(worldCoord: Int): Int {
            // Handle negative coordinates correctly
            return ((worldCoord % SIZE) + SIZE) % SIZE
        }
        
        /**
         * Convert world coordinates to chunk coordinates
         */
        fun worldToChunk(worldCoord: Int): Int {
            // Handle negative coordinates correctly
            return if (worldCoord < 0) {
                ((worldCoord + 1) / SIZE) - 1
            } else {
                worldCoord / SIZE
            }
        }
        
        /**
         * Calculate memory savings compared to traditional chunks
         */
        fun calculateMemorySavings(): String {
            val traditionalChunkSize = 16 * 256 * 16 * 2 // shorts
            val cubicChunkSize = SIZE * SIZE * SIZE * 1 // bytes
            val savings = ((traditionalChunkSize - cubicChunkSize).toFloat() / traditionalChunkSize * 100)
            return "Memory usage: ${cubicChunkSize} bytes vs ${traditionalChunkSize} bytes (${savings.toInt()}% savings)"
        }
    }
}
