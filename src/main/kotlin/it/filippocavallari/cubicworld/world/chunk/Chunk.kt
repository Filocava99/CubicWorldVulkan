package it.filippocavallari.cubicworld.world.chunk

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.World
import org.joml.Vector2i
import org.joml.Vector3i

/**
 * Represents a chunk of the world, containing a 16x16x16 grid of blocks.
 */
class Chunk(x: Int, y: Int, z: Int, val world: World? = null) {
    // Position of this chunk in the world (in chunk coordinates)
    val position: Vector3i = Vector3i(x, y, z)

    // Block data storage (stored as block IDs)
    private val blocks: Array<Array<ShortArray>> = Array(SIZE) {
        Array(HEIGHT) {
            ShortArray(SIZE)
        }
    }
    
    // Dirty flag for whether the chunk needs to be rebuilt
    private var dirty: Boolean = true
    
    /**
     * Get the block ID at the specified position
     */
    fun getBlock(x: Int, y: Int, z: Int): Int {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return 0 // Return air for out of bounds
        }
        return blocks[x][y][z].toInt()
    }
    
    /**
     * Set the block at the specified position
     */
    fun setBlock(x: Int, y: Int, z: Int, blockId: Int) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return // Ignore out of bounds
        }
        
        // Only mark dirty if the block is actually changing
        if (blocks[x][y][z].toInt() != blockId) {
            blocks[x][y][z] = blockId.toShort()
            dirty = true
        }
    }
    
    /**
     * Check if this chunk is dirty (needs mesh rebuilding)
     */
    fun isDirty(): Boolean {
        return dirty
    }
    
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
     * Get the world X coordinate of the start of this chunk
     */
    fun getWorldX(): Int {
        return position.x * SIZE
    }
    
    /**
     * Get the world Z coordinate of the start of this chunk
     */
    fun getWorldZ(): Int {
        return position.z * SIZE
    }

    fun getWorldY(): Int {
        return position.y * HEIGHT
    }
    
    /**
     * Fill the entire chunk with the specified block
     */
    fun fill(blockId: Int) {
        for (x in 0 until SIZE) {
            for (y in 0 until HEIGHT) {
                for (z in 0 until SIZE) {
                    blocks[x][y][z] = blockId.toShort()
                }
            }
        }
        dirty = true
    }
    
    /**
     * Fill a layer of the chunk with the specified block
     */
    fun fillLayer(y: Int, blockId: Int) {
        if (y < 0 || y >= HEIGHT) {
            return
        }
        
        for (x in 0 until SIZE) {
            for (z in 0 until SIZE) {
                blocks[x][y][z] = blockId.toShort()
            }
        }
        dirty = true
    }
    
    /**
     * Get the hash code for this chunk
     */
    override fun hashCode(): Int {
        return position.hashCode()
    }
    
    /**
     * Check if this chunk equals another object
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val chunk = other as Chunk
        return position == chunk.position
    }
    
    companion object {
        // Chunk dimensions
        const val SIZE = 16
        const val HEIGHT = 16
        
        /**
         * Convert world coordinates to local chunk coordinates for X and Z axes
         */
        fun worldToLocalXZ(worldCoord: Int): Int {
            // Handle negative coordinates correctly
            return ((worldCoord % SIZE) + SIZE) % SIZE
        }

        /**
         * Convert world coordinates to local chunk coordinates for Y axis
         */
        fun worldToLocalY(worldCoord: Int): Int {
            // Handle negative coordinates correctly
            return ((worldCoord % HEIGHT) + HEIGHT) % HEIGHT
        }
        
        /**
         * Convert world coordinates to chunk coordinates for X and Z axes
         */
        fun worldToChunkXZ(worldCoord: Int): Int {
            // Handle negative coordinates correctly
            // For negative coordinates, we need to ensure proper flooring behavior
            return if (worldCoord < 0) {
                // Example: worldCoord = -17, SIZE = 16
                // Should return -2 (chunk -2 contains blocks -32 to -17)
                ((worldCoord + 1) / SIZE) - 1
            } else {
                // Example: worldCoord = 17, SIZE = 16
                // Should return 1 (chunk 1 contains blocks 16 to 31)
                worldCoord / SIZE
            }
        }

        /**
         * Convert world coordinates to chunk coordinates for Y axis
         */
        fun worldToChunkY(worldCoord: Int): Int {
            // Handle negative coordinates correctly
            // For negative coordinates, we need to ensure proper flooring behavior
            return if (worldCoord < 0) {
                // Example: worldCoord = -17, HEIGHT = 16
                // Should return -2 (chunk -2 contains blocks -32 to -17)
                ((worldCoord + 1) / HEIGHT) - 1
            } else {
                // Example: worldCoord = 17, HEIGHT = 16
                // Should return 1 (chunk 1 contains blocks 16 to 31)
                worldCoord / HEIGHT
            }
        }
    }
}