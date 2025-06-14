package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import it.filippocavallari.cubicworld.world.generators.WorldGenerator
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
    // World generator
    private val generator: WorldGenerator,
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
     * Generate content for a cubic chunk
     */
    private fun generateCubicChunk(chunk: CubicChunk) {
        // Skip generation for chunks that are likely to be empty (high in the sky)
        if (chunk.position.y > 10) { // Above y=160 world height
            // Leave empty
            return
        }
        
        // Generate terrain for this cubic chunk
        // The generator needs to be aware of cubic chunks
        val worldY = chunk.getWorldY()
        
        // Basic terrain generation for cubic chunks
        for (x in 0 until CubicChunk.SIZE) {
            for (z in 0 until CubicChunk.SIZE) {
                val worldX = chunk.getWorldX() + x
                val worldZ = chunk.getWorldZ() + z
                
                // Get height at this position (you'll need to adapt your world generator)
                val terrainHeight = getTerrainHeightAt(worldX, worldZ)
                
                for (y in 0 until CubicChunk.SIZE) {
                    val blockY = worldY + y
                    
                    val blockId = when {
                        blockY > terrainHeight -> 0 // Air
                        blockY == terrainHeight -> 3 // Grass
                        blockY > terrainHeight - 4 -> 2 // Dirt
                        blockY > 0 -> 1 // Stone
                        else -> 7 // Bedrock at y=0
                    }
                    
                    if (blockId != 0) {
                        chunk.setBlock(x, y, z, blockId)
                    }
                }
            }
        }
    }
    
    /**
     * Get terrain height at world coordinates (placeholder - integrate with your world generator)
     */
    private fun getTerrainHeightAt(x: Int, z: Int): Int {
        // Simple noise-based terrain height
        // In production, this should use your actual world generator
        val scale = 0.05
        val height = 64 + (Math.sin(x * scale) * Math.cos(z * scale) * 16).toInt()
        return height.coerceIn(1, 128)
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
        
        // Get the chunk
        val chunk = getChunk(chunkX, chunkY, chunkZ) ?: return 0 // Return air for unloaded chunks
        
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
        // Convert to chunk coordinates
        val chunkX = CubicChunk.worldToChunk(x)
        val chunkY = CubicChunk.worldToChunk(y)
        val chunkZ = CubicChunk.worldToChunk(z)
        
        // Get the chunk
        val chunk = getChunk(chunkX, chunkY, chunkZ) ?: return // Ignore if chunk is not loaded
        
        // Convert to local coordinates within the chunk
        val localX = CubicChunk.worldToLocal(x)
        val localY = CubicChunk.worldToLocal(y)
        val localZ = CubicChunk.worldToLocal(z)
        
        // Set the block
        chunk.setBlock(localX, localY, localZ, blockId)
        
        // Notify listeners that chunk is updated
        for (listener in chunkListeners) {
            listener.onChunkUpdated(chunk)
        }
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
