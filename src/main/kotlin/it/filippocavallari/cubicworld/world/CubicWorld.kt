package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import it.filippocavallari.cubicworld.world.generators.CubicWorldGenerator
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Manages the game world using cubic chunks (16x16x16) instead of traditional column chunks.
 * This allows for infinite world height and significantly reduced memory usage.
 */
class CubicWorld(
    // Cubic world generator
    private val generator: CubicWorldGenerator,
    // Resources paths
    val texturesPath: String = "src/main/resources/textures",
    val modelsPath: String = "src/main/resources/models"
) {
    // Map of loaded chunks using 3D coordinates
    private val chunksMap: MutableMap<Vector3i, CubicChunk> = ConcurrentHashMap()
    
    // Chunk generation thread pool
    private val chunkGenerationExecutor: ExecutorService = Executors.newFixedThreadPool(4) // More threads for 3D generation
    private val chunkGenerationTasks: MutableMap<Vector3i, Future<*>> = ConcurrentHashMap()
    
    // Chunk event listeners
    private val chunkListeners: MutableList<ChunkListener> = ArrayList()
    
    // Statistics tracking
    private var totalChunksLoaded = 0
    private var totalMemoryUsed = 0L
    
    /**
     * Load a cubic chunk synchronously (used during startup)
     */
    fun loadChunkSynchronously(x: Int, y: Int, z: Int): CubicChunk {
        val position = Vector3i(x, y, z)
        
        // Check if chunk is already loaded
        chunksMap[position]?.let { return it }
        
        // Create a new chunk
        val chunk = CubicChunk(x, y, z, this)
        chunksMap[position] = chunk
        
        // Generate the chunk synchronously
        try {
            generateCubicChunk(chunk)
            
            // Update neighbors
            updateChunkNeighbors(chunk)
            
            // Update statistics
            totalChunksLoaded++
            totalMemoryUsed += chunk.getMemoryUsage()
            
            // Notify listeners that chunk is loaded
            for (listener in chunkListeners) {
                listener.onChunkLoaded(chunk)
            }
        } catch (e: Exception) {
            System.err.println("Error generating cubic chunk at $x, $y, $z: ${e.message}")
            e.printStackTrace()
        }
        
        return chunk
    }
    
    /**
     * Load a cubic chunk asynchronously
     */
    fun loadChunk(x: Int, y: Int, z: Int): CubicChunk {
        val position = Vector3i(x, y, z)
        
        // Check if chunk is already loaded
        chunksMap[position]?.let { return it }
        
        // Create a new chunk
        val chunk = CubicChunk(x, y, z, this)
        chunksMap[position] = chunk
        
        // Generate the chunk in a separate thread
        val task = chunkGenerationExecutor.submit {
            try {
                generateCubicChunk(chunk)
                
                // Update neighbors
                updateChunkNeighbors(chunk)
                
                // Update statistics
                totalChunksLoaded++
                totalMemoryUsed += chunk.getMemoryUsage()
                
                // Notify listeners that chunk is loaded
                for (listener in chunkListeners) {
                    listener.onChunkLoaded(chunk)
                }
            } catch (e: Exception) {
                System.err.println("Error generating cubic chunk at $x, $y, $z: ${e.message}")
                e.printStackTrace()
            } finally {
                chunkGenerationTasks.remove(position)
            }
        }
        
        chunkGenerationTasks[position] = task
        
        return chunk
    }
    
    /**
     * Generate content for a cubic chunk using native cubic world generator
     */
    private fun generateCubicChunk(chunk: CubicChunk) {
        // Use the CubicWorldGenerator directly - no temporary objects needed
        generator.generateCubicChunk(chunk)
    }
    
    
    /**
     * Update chunk neighbors for efficient occlusion culling
     */
    private fun updateChunkNeighbors(chunk: CubicChunk) {
        val pos = chunk.position
        
        // Set all six neighbors
        chunk.setNeighbor(CubicChunk.Direction.UP, getChunk(pos.x, pos.y + 1, pos.z))
        chunk.setNeighbor(CubicChunk.Direction.DOWN, getChunk(pos.x, pos.y - 1, pos.z))
        chunk.setNeighbor(CubicChunk.Direction.NORTH, getChunk(pos.x, pos.y, pos.z - 1))
        chunk.setNeighbor(CubicChunk.Direction.SOUTH, getChunk(pos.x, pos.y, pos.z + 1))
        chunk.setNeighbor(CubicChunk.Direction.EAST, getChunk(pos.x + 1, pos.y, pos.z))
        chunk.setNeighbor(CubicChunk.Direction.WEST, getChunk(pos.x - 1, pos.y, pos.z))
        
        // Update neighbors to point back to this chunk
        getChunk(pos.x, pos.y + 1, pos.z)?.setNeighbor(CubicChunk.Direction.DOWN, chunk)
        getChunk(pos.x, pos.y - 1, pos.z)?.setNeighbor(CubicChunk.Direction.UP, chunk)
        getChunk(pos.x, pos.y, pos.z - 1)?.setNeighbor(CubicChunk.Direction.SOUTH, chunk)
        getChunk(pos.x, pos.y, pos.z + 1)?.setNeighbor(CubicChunk.Direction.NORTH, chunk)
        getChunk(pos.x + 1, pos.y, pos.z)?.setNeighbor(CubicChunk.Direction.WEST, chunk)
        getChunk(pos.x - 1, pos.y, pos.z)?.setNeighbor(CubicChunk.Direction.EAST, chunk)
    }
    
    /**
     * Unload a cubic chunk at the specified position
     */
    fun unloadChunk(x: Int, y: Int, z: Int) {
        val position = Vector3i(x, y, z)
        
        // Cancel generation task if in progress
        val task = chunkGenerationTasks.remove(position)
        task?.cancel(false)
        
        // Remove the chunk
        val chunk = chunksMap.remove(position)
        
        // Update statistics
        chunk?.let {
            totalChunksLoaded--
            totalMemoryUsed -= it.getMemoryUsage()
            
            // Clear neighbor references
            clearChunkNeighbors(it)
            
            // Notify listeners that chunk is unloaded
            for (listener in chunkListeners) {
                listener.onChunkUnloaded(it)
            }
        }
        
        println("Unloaded cubic chunk at ($x, $y, $z)")
    }
    
    /**
     * Clear neighbor references when unloading a chunk
     */
    private fun clearChunkNeighbors(chunk: CubicChunk) {
        val pos = chunk.position
        
        // Clear references from neighbors
        getChunk(pos.x, pos.y + 1, pos.z)?.setNeighbor(CubicChunk.Direction.DOWN, null)
        getChunk(pos.x, pos.y - 1, pos.z)?.setNeighbor(CubicChunk.Direction.UP, null)
        getChunk(pos.x, pos.y, pos.z - 1)?.setNeighbor(CubicChunk.Direction.SOUTH, null)
        getChunk(pos.x, pos.y, pos.z + 1)?.setNeighbor(CubicChunk.Direction.NORTH, null)
        getChunk(pos.x + 1, pos.y, pos.z)?.setNeighbor(CubicChunk.Direction.WEST, null)
        getChunk(pos.x - 1, pos.y, pos.z)?.setNeighbor(CubicChunk.Direction.EAST, null)
    }
    
    /**
     * Check if a chunk is loaded at the specified position
     */
    fun isChunkLoaded(x: Int, y: Int, z: Int): Boolean {
        return chunksMap.containsKey(Vector3i(x, y, z))
    }
    
    /**
     * Get a chunk at the specified position
     */
    fun getChunk(x: Int, y: Int, z: Int): CubicChunk? {
        return chunksMap[Vector3i(x, y, z)]
    }
    
    /**
     * Get a block at the specified world position
     */
    fun getBlock(x: Int, y: Int, z: Int): Int {
        // Convert to chunk coordinates
        val chunkX = CubicChunk.worldToChunk(x)
        val chunkY = CubicChunk.worldToChunk(y)
        val chunkZ = CubicChunk.worldToChunk(z)
        
        // Get the chunk, loading it synchronously if needed for block access
        var chunk = getChunk(chunkX, chunkY, chunkZ)
        if (chunk == null) {
            // For raycasting and block queries, load the chunk synchronously
            println("DEBUG: Loading chunk ($chunkX, $chunkY, $chunkZ) synchronously for block query")
            chunk = loadChunkSynchronously(chunkX, chunkY, chunkZ)
        }
        
        // Convert to local coordinates within the chunk
        val localX = CubicChunk.worldToLocal(x)
        val localY = CubicChunk.worldToLocal(y)
        val localZ = CubicChunk.worldToLocal(z)
        
        return chunk.getBlock(localX, localY, localZ)
    }
    
    /**
     * Set a block at the specified world position
     */
    fun setBlock(x: Int, y: Int, z: Int, blockId: Int) {
        println("DEBUG: CubicWorld.setBlock called for position ($x, $y, $z) with blockId $blockId")
        
        // Convert to chunk coordinates
        val chunkX = CubicChunk.worldToChunk(x)
        val chunkY = CubicChunk.worldToChunk(y)
        val chunkZ = CubicChunk.worldToChunk(z)
        
        println("DEBUG: Converted to chunk coordinates ($chunkX, $chunkY, $chunkZ)")
        
        // Get the chunk, loading it synchronously if not loaded
        var chunk = getChunk(chunkX, chunkY, chunkZ)
        if (chunk == null) {
            println("DEBUG: Chunk at ($chunkX, $chunkY, $chunkZ) is not loaded - loading synchronously for block interaction")
            chunk = loadChunkSynchronously(chunkX, chunkY, chunkZ)
            println("DEBUG: Chunk loaded synchronously, proceeding with block change")
        }
        
        // Convert to local coordinates within the chunk
        val localX = CubicChunk.worldToLocal(x)
        val localY = CubicChunk.worldToLocal(y)
        val localZ = CubicChunk.worldToLocal(z)
        
        println("DEBUG: Local chunk coordinates: ($localX, $localY, $localZ)")
        
        // Set the block
        chunk.setBlock(localX, localY, localZ, blockId)
        
        println("DEBUG: Block set in chunk, notifying ${chunkListeners.size} listeners")
        
        // Notify listeners that chunk is updated
        for (listener in chunkListeners) {
            listener.onChunkUpdated(chunk)
        }
        
        println("DEBUG: All chunk listeners notified")
    }
    
    /**
     * Get all loaded chunks
     */
    val loadedChunks: Collection<CubicChunk>
        get() = this.chunksMap.values
    
    /**
     * Get statistics about the world
     */
    fun getStatistics(): String {
        val totalChunks = chunksMap.size
        val memoryMB = totalMemoryUsed / (1024.0 * 1024.0)
        val avgMemoryPerChunk = if (totalChunks > 0) totalMemoryUsed / totalChunks else 0
        
        return """
            Cubic World Statistics:
            - Loaded chunks: $totalChunks
            - Total memory: ${String.format("%.2f", memoryMB)} MB
            - Avg memory/chunk: $avgMemoryPerChunk bytes
            - ${CubicChunk.calculateMemorySavings()}
        """.trimIndent()
    }
    
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
        chunksMap.clear()
        chunkListeners.clear()
    }
    
    /**
     * Interface for listening to chunk events
     */
    interface ChunkListener {
        /**
         * Called when a chunk is loaded
         */
        fun onChunkLoaded(chunk: CubicChunk)
        
        /**
         * Called when a chunk is unloaded
         */
        fun onChunkUnloaded(chunk: CubicChunk)
        
        /**
         * Called when a chunk is updated
         */
        fun onChunkUpdated(chunk: CubicChunk)
    }
}
