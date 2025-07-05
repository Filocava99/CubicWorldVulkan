package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.integration.VulkanIntegration
import it.filippocavallari.cubicworld.world.chunk.Chunk
import org.joml.Vector2i
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Manages dynamic chunk loading and unloading around the player position.
 * Implements a 4x4 chunk grid with circular loading pattern.
 */
class ChunkManager(
    private val world: World,
    private val vulkanIntegration: VulkanIntegration
) {
    companion object {
        const val RENDER_DISTANCE = 2 // 4x4 grid (2 chunks in each direction from center)
        const val LOAD_THRESHOLD_BLOCKS = 8 // Start loading new chunks when within 8 blocks of edge
        const val MAX_CHUNKS_PER_FRAME = 1 // Limit chunk loading per frame for smooth performance
        const val UNLOAD_DISTANCE = 3 // Unload chunks beyond this distance
    }
    
    // Data class to store chunk information
    data class ChunkInfo(
        val chunk: Chunk,
        val entityId: String,
        val loadTime: Long,
        val distance: Double = 0.0
    )
    
    // Maps to track loaded chunks and their render entities
    private val loadedChunks = ConcurrentHashMap<Vector2i, ChunkInfo>()
    private val pendingChunks = mutableSetOf<Vector2i>() // Chunks currently being loaded
    
    // Player position tracking
    private var lastPlayerChunk = Vector2i(0, 0)
    private var isInitialized = false
    
    /**
     * Initialize the chunk manager with initial chunks around spawn point
     */
    fun initialize(spawnChunkX: Int = 0, spawnChunkZ: Int = 0) {
        if (isInitialized) return
        
        println("Initializing ChunkManager with 4x4 grid around spawn ($spawnChunkX, $spawnChunkZ)")
        lastPlayerChunk.set(spawnChunkX, spawnChunkZ)
        
        // Load initial chunks in circular pattern
        loadChunksCircular(spawnChunkX, spawnChunkZ)
        
        isInitialized = true
        println("ChunkManager initialized with ${loadedChunks.size} chunks")
    }
    
    /**
     * Update player position and trigger chunk loading/unloading as needed
     */
    fun updatePlayerPosition(worldX: Float, worldZ: Float) {
        // Convert world coordinates to chunk coordinates
        val playerChunkX = Chunk.worldToChunk(worldX.toInt())
        val playerChunkZ = Chunk.worldToChunk(worldZ.toInt())
        val currentPlayerChunk = Vector2i(playerChunkX, playerChunkZ)
        
        // Check if player moved to a different chunk
        if (currentPlayerChunk != lastPlayerChunk) {
            println("Player moved to chunk ($playerChunkX, $playerChunkZ)")
            lastPlayerChunk.set(currentPlayerChunk)
            
            // Update chunks around new position
            loadChunksCircular(playerChunkX, playerChunkZ)
            unloadDistantChunks(playerChunkX, playerChunkZ)
        } else {
            // Check if player is approaching chunk borders
            checkForBorderApproach(worldX, worldZ, playerChunkX, playerChunkZ)
        }
    }
    
    /**
     * Check if player is approaching chunk borders and preload adjacent chunks
     */
    private fun checkForBorderApproach(worldX: Float, worldZ: Float, chunkX: Int, chunkZ: Int) {
        val localX = Chunk.worldToLocal(worldX.toInt())
        val localZ = Chunk.worldToLocal(worldZ.toInt())
        
        // Check if player is near chunk edges
        val nearWestEdge = localX < LOAD_THRESHOLD_BLOCKS
        val nearEastEdge = localX > (Chunk.SIZE - LOAD_THRESHOLD_BLOCKS)
        val nearNorthEdge = localZ < LOAD_THRESHOLD_BLOCKS
        val nearSouthEdge = localZ > (Chunk.SIZE - LOAD_THRESHOLD_BLOCKS)
        
        // Preload adjacent chunks if approaching borders
        if (nearWestEdge) preloadChunk(chunkX - 1, chunkZ)
        if (nearEastEdge) preloadChunk(chunkX + 1, chunkZ)
        if (nearNorthEdge) preloadChunk(chunkX, chunkZ - 1)
        if (nearSouthEdge) preloadChunk(chunkX, chunkZ + 1)
        
        // Preload corner chunks if approaching corners
        if (nearWestEdge && nearNorthEdge) preloadChunk(chunkX - 1, chunkZ - 1)
        if (nearEastEdge && nearNorthEdge) preloadChunk(chunkX + 1, chunkZ - 1)
        if (nearWestEdge && nearSouthEdge) preloadChunk(chunkX - 1, chunkZ + 1)
        if (nearEastEdge && nearSouthEdge) preloadChunk(chunkX + 1, chunkZ + 1)
    }
    
    /**
     * Preload a single chunk if it's not already loaded or pending
     */
    private fun preloadChunk(chunkX: Int, chunkZ: Int) {
        val chunkPos = Vector2i(chunkX, chunkZ)
        if (!loadedChunks.containsKey(chunkPos) && !pendingChunks.contains(chunkPos)) {
            loadChunkAsync(chunkX, chunkZ)
        }
    }
    
    /**
     * Load chunks in a circular pattern around the center position
     */
    private fun loadChunksCircular(centerChunkX: Int, centerChunkZ: Int) {
        println("Loading chunks in circular pattern around ($centerChunkX, $centerChunkZ)")
        
        // Generate list of chunk positions in spiral pattern
        val chunksToLoad = mutableListOf<Triple<Int, Int, Double>>()
        
        // Create a proper spiral pattern starting from center
        val maxRadius = RENDER_DISTANCE
        for (radius in 0..maxRadius) {
            // Start with center chunk (radius 0)
            if (radius == 0) {
                val distance = getDistanceFromCenter(0, 0)
                chunksToLoad.add(Triple(centerChunkX, centerChunkZ, distance))
            } else {
                // Add chunks at current radius in spiral order
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        val distance = getDistanceFromCenter(dx, dz)
                        
                        // Only add chunks that are exactly at current radius
                        if (distance <= radius && distance > radius - 1) {
                            val chunkX = centerChunkX + dx
                            val chunkZ = centerChunkZ + dz
                            chunksToLoad.add(Triple(chunkX, chunkZ, distance))
                        }
                    }
                }
            }
        }
        
        // Sort by distance from center (ensures closest chunks load first)
        chunksToLoad.sortBy { it.third }
        
        println("Found ${chunksToLoad.size} chunks to load in spiral pattern")
        
        // Load chunks in order of distance from center (spiral pattern)
        var chunksLoaded = 0
        for ((chunkX, chunkZ, distance) in chunksToLoad) {
            val chunkPos = Vector2i(chunkX, chunkZ)
            
            // Skip if already loaded or pending
            if (loadedChunks.containsKey(chunkPos) || pendingChunks.contains(chunkPos)) {
                continue
            }
            
            // Load the chunk
            loadChunkAsync(chunkX, chunkZ)
            chunksLoaded++
            
            println("Loading chunk at ($chunkX, $chunkZ) - distance: ${"%.2f".format(distance)}")
            
            // Limit chunks loaded per update for performance
            if (chunksLoaded >= MAX_CHUNKS_PER_FRAME) {
                break
            }
        }
        
        println("Started loading $chunksLoaded chunks this frame")
    }
    
    /**
     * Load a chunk asynchronously
     */
    private fun loadChunkAsync(chunkX: Int, chunkZ: Int) {
        val chunkPos = Vector2i(chunkX, chunkZ)
        pendingChunks.add(chunkPos)
        
        try {
            println("Loading chunk at ($chunkX, $chunkZ)...")
            
            // Load chunk synchronously for now (can be made async later)
            val chunk = world.loadChunkSynchronously(chunkX, chunkZ)
            
            // Create mesh for the chunk
            val entityId = vulkanIntegration.createChunkMesh(chunk)
            
            if (entityId.isNotEmpty()) {
                // Store chunk info
                val chunkInfo = ChunkInfo(
                    chunk = chunk,
                    entityId = entityId,
                    loadTime = System.currentTimeMillis()
                )
                
                loadedChunks[chunkPos] = chunkInfo
                println("Successfully loaded chunk at ($chunkX, $chunkZ) with entity ID: $entityId")
            } else {
                println("WARNING: Failed to create mesh for chunk at ($chunkX, $chunkZ)")
            }
            
        } catch (e: Exception) {
            println("ERROR: Failed to load chunk at ($chunkX, $chunkZ): ${e.message}")
            e.printStackTrace()
        } finally {
            pendingChunks.remove(chunkPos)
        }
    }
    
    /**
     * Unload chunks that are too far from the player
     */
    private fun unloadDistantChunks(playerChunkX: Int, playerChunkZ: Int) {
        val chunksToUnload = mutableListOf<Vector2i>()
        
        // Find chunks that are too far away
        for ((chunkPos, chunkInfo) in loadedChunks) {
            val distance = getDistanceFromCenter(
                chunkPos.x - playerChunkX,
                chunkPos.y - playerChunkZ
            )
            
            if (distance > UNLOAD_DISTANCE) {
                chunksToUnload.add(chunkPos)
            }
        }
        
        // Unload distant chunks
        for (chunkPos in chunksToUnload) {
            unloadChunk(chunkPos.x, chunkPos.y)
        }
        
        if (chunksToUnload.isNotEmpty()) {
            println("Unloaded ${chunksToUnload.size} distant chunks")
        }
    }
    
    /**
     * Unload a specific chunk
     */
    private fun unloadChunk(chunkX: Int, chunkZ: Int) {
        val chunkPos = Vector2i(chunkX, chunkZ)
        val chunkInfo = loadedChunks.remove(chunkPos)
        
        if (chunkInfo != null) {
            try {
                // Remove mesh from Vulkan rendering
                vulkanIntegration.removeChunkMesh(chunkInfo.entityId)
                
                // Unload from world
                world.unloadChunk(chunkX, chunkZ)
                
                println("Unloaded chunk at ($chunkX, $chunkZ)")
            } catch (e: Exception) {
                println("ERROR: Failed to unload chunk at ($chunkX, $chunkZ): ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Calculate distance from center using Euclidean distance
     */
    private fun getDistanceFromCenter(dx: Int, dz: Int): Double {
        return sqrt((dx * dx + dz * dz).toDouble())
    }
    
    /**
     * Get information about currently loaded chunks
     */
    fun getLoadedChunkInfo(): String {
        val totalChunks = loadedChunks.size
        val pendingCount = pendingChunks.size
        
        return "Loaded chunks: $totalChunks, Pending: $pendingCount"
    }
    
    /**
     * Get the number of loaded chunks
     */
    fun getLoadedChunkCount(): Int = loadedChunks.size
    
    /**
     * Check if a chunk is loaded
     */
    fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean {
        return loadedChunks.containsKey(Vector2i(chunkX, chunkZ))
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        println("Cleaning up ChunkManager...")
        
        // Unload all chunks
        val allChunks = loadedChunks.keys.toList()
        for (chunkPos in allChunks) {
            unloadChunk(chunkPos.x, chunkPos.y)
        }
        
        loadedChunks.clear()
        pendingChunks.clear()
        
        println("ChunkManager cleanup complete")
    }
}
