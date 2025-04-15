package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.WorldGenerator
import org.joml.Vector2i
import org.joml.Vector3i

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Manages the game world, including chunks and world generation.
 */
class World(
    // World generator
    private val generator: WorldGenerator,
    // Resources paths
    val texturesPath: String = "src/main/resources/textures",
    val modelsPath: String = "src/main/resources/models"
) {
    // Map of loaded chunks
    private val chunksMap: MutableMap<Vector2i, Chunk> = ConcurrentHashMap()
    
    // Chunk generation thread pool
    private val chunkGenerationExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val chunkGenerationTasks: MutableMap<Vector2i, Future<*>> = ConcurrentHashMap()
    
    // Chunk event listeners
    private val chunkListeners: MutableList<ChunkListener> = ArrayList()
    
    /**
     * Load a chunk at the specified position
     */
    fun loadChunk(x: Int, z: Int): Chunk {
        val position = Vector2i(x, z)
        
        // Check if chunk is already loaded
        chunksMap[position]?.let { return it }
        
        // Create a new chunk
        val chunk = Chunk(x, z)
        chunksMap[position] = chunk
        
        // Generate the chunk in a separate thread
        val task = chunkGenerationExecutor.submit {
            try {
                generator.generateChunk(chunk)
                
                // Notify listeners that chunk is loaded
                for (listener in chunkListeners) {
                    listener.onChunkLoaded(chunk)
                }
            } catch (e: Exception) {
                System.err.println("Error generating chunk at $x, $z: ${e.message}")
                e.printStackTrace()
            } finally {
                chunkGenerationTasks.remove(position)
            }
        }
        
        chunkGenerationTasks[position] = task
        
        return chunk
    }
    
    /**
     * Unload a chunk at the specified position
     */
    fun unloadChunk(x: Int, z: Int) {
        val position = Vector2i(x, z)
        
        // Cancel generation task if in progress
        val task = chunkGenerationTasks.remove(position)
        task?.cancel(false)
        
        // Remove the chunk
        val chunk = chunksMap.remove(position)
        
        // Notify listeners that chunk is unloaded
        chunk?.let {
            for (listener in chunkListeners) {
                listener.onChunkUnloaded(it)
            }
        }
    }
    
    /**
     * Get a chunk at the specified position
     */
    fun getChunk(x: Int, z: Int): Chunk? {
        return chunksMap[Vector2i(x, z)]
    }
    
    /**
     * Get a block at the specified world position
     */
    fun getBlock(x: Int, y: Int, z: Int): Int {
        // Convert to chunk coordinates
        val chunkX = Chunk.worldToChunk(x)
        val chunkZ = Chunk.worldToChunk(z)
        
        // Get the chunk
        val chunk = getChunk(chunkX, chunkZ) ?: return 0 // Return air for unloaded chunks
        
        // Convert to local coordinates within the chunk
        val localX = Chunk.worldToLocal(x)
        val localZ = Chunk.worldToLocal(z)
        
        return chunk.getBlock(localX, y, localZ)
    }
    
    /**
     * Set a block at the specified world position
     */
    fun setBlock(x: Int, y: Int, z: Int, blockId: Int) {
        // Convert to chunk coordinates
        val chunkX = Chunk.worldToChunk(x)
        val chunkZ = Chunk.worldToChunk(z)
        
        // Get the chunk
        val chunk = getChunk(chunkX, chunkZ) ?: return // Ignore if chunk is not loaded
        
        // Convert to local coordinates within the chunk
        val localX = Chunk.worldToLocal(x)
        val localZ = Chunk.worldToLocal(z)
        
        // Set the block
        chunk.setBlock(localX, y, localZ, blockId)
        
        // Notify listeners that chunk is updated
        for (listener in chunkListeners) {
            listener.onChunkUpdated(chunk)
        }
        
        // Also update adjacent chunks if the block is at the edge
        if (localX == 0) updateAdjacentChunk(chunkX - 1, chunkZ)
        if (localX == Chunk.SIZE - 1) updateAdjacentChunk(chunkX + 1, chunkZ)
        if (localZ == 0) updateAdjacentChunk(chunkX, chunkZ - 1)
        if (localZ == Chunk.SIZE - 1) updateAdjacentChunk(chunkX, chunkZ + 1)
    }
    
    /**
     * Update an adjacent chunk if it's loaded
     */
    private fun updateAdjacentChunk(x: Int, z: Int) {
        val chunk = getChunk(x, z)
        chunk?.let {
            it.markDirty()
            for (listener in chunkListeners) {
                listener.onChunkUpdated(it)
            }
        }
    }
    
    /**
     * Get a collection of all loaded chunks
     */
    val loadedChunks: Collection<Chunk>
        get() = this.chunksMap.values
    
    /**
     * Add a chunk event listener
     */
    fun addChunkListener(listener: ChunkListener) {
        chunkListeners.add(listener)
    }
    
    /**
     * Remove a chunk event listener
     */
    fun removeChunkListener(listener: ChunkListener) {
        chunkListeners.remove(listener)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        chunkGenerationExecutor.shutdown()
    }
    
    /**
     * Update chunks - called every frame
     */
    fun updateChunks(deltaTime: Float) {
        // Process any pending chunk operations
        // This method would handle dynamic chunk loading/unloading
        // based on player position, etc.
    }
    
    /**
     * Interface for listening to chunk events
     */
    interface ChunkListener {
        /**
         * Called when a chunk is loaded
         */
        fun onChunkLoaded(chunk: Chunk)
        
        /**
         * Called when a chunk is unloaded
         */
        fun onChunkUnloaded(chunk: Chunk)
        
        /**
         * Called when a chunk is updated
         */
        fun onChunkUpdated(chunk: Chunk)
    }
}