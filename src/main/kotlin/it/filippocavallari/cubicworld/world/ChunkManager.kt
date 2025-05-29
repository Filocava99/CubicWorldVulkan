package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.integration.VulkanIntegration
import it.filippocavallari.cubicworld.world.chunk.Chunk
import org.joml.Vector2i
import org.joml.Vector3i
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
    private val loadedChunks = ConcurrentHashMap<Vector3i, ChunkInfo>()
    private val pendingChunks = mutableSetOf<Vector3i>() // Chunks currently being loaded
    
    // Player position tracking
    private var lastPlayerChunk = Vector3i(0, 0, 0)
    private var isInitialized = false
    
    /**
     * Initialize the chunk manager with initial chunks around spawn point
     */
    fun initialize(spawnChunkX: Int = 0, spawnChunkY: Int = 0, spawnChunkZ: Int = 0) {
        if (isInitialized) return
        
        println("Initializing ChunkManager with 3D grid around spawn ($spawnChunkX, $spawnChunkY, $spawnChunkZ)")
        lastPlayerChunk.set(spawnChunkX, spawnChunkY, spawnChunkZ)
        
        // Load initial chunks in circular pattern
        loadChunksCircular(spawnChunkX, spawnChunkY, spawnChunkZ)
        
        isInitialized = true
        println("ChunkManager initialized with ${loadedChunks.size} chunks")
    }
    
    /**
     * Update player position and trigger chunk loading/unloading as needed
     */
    fun updatePlayerPosition(worldX: Float, worldY: Float, worldZ: Float) {
        // Convert world coordinates to chunk coordinates
        val playerChunkX = Chunk.worldToChunkXZ(worldX.toInt())
        val playerChunkY = Chunk.worldToChunkY(worldY.toInt())
        val playerChunkZ = Chunk.worldToChunkXZ(worldZ.toInt())
        val currentPlayerChunk = Vector3i(playerChunkX, playerChunkY, playerChunkZ)
        
        // Check if player moved to a different chunk
        if (currentPlayerChunk != lastPlayerChunk) {
            println("Player moved to chunk ($playerChunkX, $playerChunkY, $playerChunkZ)")
            lastPlayerChunk.set(currentPlayerChunk)
            
            // Update chunks around new position
            loadChunksCircular(playerChunkX, playerChunkY, playerChunkZ)
            unloadDistantChunks(playerChunkX, playerChunkY, playerChunkZ)
        } else {
            // Check if player is approaching chunk borders
            checkForBorderApproach(worldX, worldY, worldZ, playerChunkX, playerChunkY, playerChunkZ)
        }
    }
    
    /**
     * Check if player is approaching chunk borders and preload adjacent chunks
     */
    private fun checkForBorderApproach(worldX: Float, worldY: Float, worldZ: Float, chunkX: Int, chunkY: Int, chunkZ: Int) {
        val localX = Chunk.worldToLocalXZ(worldX.toInt())
        val localY = Chunk.worldToLocalY(worldY.toInt())
        val localZ = Chunk.worldToLocalXZ(worldZ.toInt())
        
        // Check if player is near chunk edges
        val nearWestEdge = localX < LOAD_THRESHOLD_BLOCKS && localX >= 0
        val nearEastEdge = localX >= (Chunk.SIZE - LOAD_THRESHOLD_BLOCKS) && localX < Chunk.SIZE
        val nearNorthEdge = localZ < LOAD_THRESHOLD_BLOCKS && localZ >= 0
        val nearSouthEdge = localZ >= (Chunk.SIZE - LOAD_THRESHOLD_BLOCKS) && localZ < Chunk.SIZE
        val nearTopEdge = localY < LOAD_THRESHOLD_BLOCKS && localY >=0 
        val nearBottomEdge = localY >= (Chunk.HEIGHT - LOAD_THRESHOLD_BLOCKS) && localY < Chunk.HEIGHT
        
        // Preload adjacent chunks if approaching borders (current Y level)
        if (nearWestEdge) preloadChunk(chunkX - 1, chunkY, chunkZ)
        if (nearEastEdge) preloadChunk(chunkX + 1, chunkY, chunkZ)
        if (nearNorthEdge) preloadChunk(chunkX, chunkY, chunkZ - 1)
        if (nearSouthEdge) preloadChunk(chunkX, chunkY, chunkZ + 1)
        
        // Preload corner chunks if approaching corners (current Y level)
        if (nearWestEdge && nearNorthEdge) preloadChunk(chunkX - 1, chunkY, chunkZ - 1)
        if (nearEastEdge && nearNorthEdge) preloadChunk(chunkX + 1, chunkY, chunkZ - 1)
        if (nearWestEdge && nearSouthEdge) preloadChunk(chunkX - 1, chunkY, chunkZ + 1)
        if (nearEastEdge && nearSouthEdge) preloadChunk(chunkX + 1, chunkY, chunkZ + 1)

        // Preload Y-axis neighbors
        if (nearTopEdge) preloadChunk(chunkX, chunkY - 1, chunkZ)
        if (nearBottomEdge) preloadChunk(chunkX, chunkY + 1, chunkZ)
    }
    
    /**
     * Preload a single chunk if it's not already loaded or pending
     */
    private fun preloadChunk(chunkX: Int, chunkY: Int, chunkZ: Int) {
        val chunkPos = Vector3i(chunkX, chunkY, chunkZ)
        if (!loadedChunks.containsKey(chunkPos) && !pendingChunks.contains(chunkPos)) {
            loadChunkAsync(chunkX, chunkY, chunkZ)
        }
    }
    
    /**
     * Load chunks in a circular pattern around the center position
     */
    private fun loadChunksCircular(centerChunkX: Int, centerChunkY: Int, centerChunkZ: Int) {
        println("Loading chunks in circular pattern around ($centerChunkX, $centerChunkY, $centerChunkZ)")
        
        val RENDER_DISTANCE_Y = RENDER_DISTANCE // Or a different value if Y render distance varies
        val chunksToLoad = mutableListOf<Pair<Vector3i, Double>>()
        
        for (dx in -RENDER_DISTANCE..RENDER_DISTANCE) {
            for (dy in -RENDER_DISTANCE_Y..RENDER_DISTANCE_Y) {
                for (dz in -RENDER_DISTANCE..RENDER_DISTANCE) {
                    val chunkX = centerChunkX + dx
                    val chunkY = centerChunkY + dy
                    val chunkZ = centerChunkZ + dz
                    val distance = getDistanceFromCenter3D(dx, dy, dz)
                    
                    if (distance <= RENDER_DISTANCE) { // Spherical/circular check
                        chunksToLoad.add(Pair(Vector3i(chunkX, chunkY, chunkZ), distance))
                    }
                }
            }
        }
        
        // Sort by distance from center (circular loading)
        chunksToLoad.sortBy { it.second }
        
        println("Found ${chunksToLoad.size} chunks to potentially load in 3D grid")
        
        // Load chunks in order of distance from center
        var chunksLoadedCount = 0
        for ((chunkPos, distanceValue) in chunksToLoad) {
            // Skip if already loaded or pending
            if (loadedChunks.containsKey(chunkPos) || pendingChunks.contains(chunkPos)) {
                continue
            }
            
            // Load the chunk
            loadChunkAsync(chunkPos.x, chunkPos.y, chunkPos.z)
            chunksLoadedCount++
            
            // Limit chunks loaded per update for performance
            if (chunksLoadedCount >= MAX_CHUNKS_PER_FRAME) {
                break
            }
        }
        
        println("Started loading $chunksLoadedCount chunks this frame")
    }
    
    /**
     * Load a chunk asynchronously
     */
    private fun loadChunkAsync(chunkX: Int, chunkY: Int, chunkZ: Int) {
        val chunkPos = Vector3i(chunkX, chunkY, chunkZ)
        pendingChunks.add(chunkPos)
        
        try {
            println("Loading chunk at ($chunkX, $chunkY, $chunkZ)...")
            
            // Load chunk synchronously for now (can be made async later)
            val chunk = world.loadChunkSynchronously(chunkX, chunkY, chunkZ)
            
            // Create mesh for the chunk
            val entityId = vulkanIntegration.createChunkMesh(chunk)
            
            if (entityId.isNotEmpty()) {
                // Store chunk info
                val chunkInfo = ChunkInfo(
                    chunk = chunk,
                    entityId = entityId,
                    loadTime = System.currentTimeMillis()
                    // distance field in ChunkInfo is not updated here, but could be if needed
                )
                
                loadedChunks[chunkPos] = chunkInfo
                println("Successfully loaded chunk at ($chunkX, $chunkY, $chunkZ) with entity ID: $entityId")
            } else {
                println("WARNING: Failed to create mesh for chunk at ($chunkX, $chunkY, $chunkZ)")
            }
            
        } catch (e: Exception) {
            println("ERROR: Failed to load chunk at ($chunkX, $chunkY, $chunkZ): ${e.message}")
            e.printStackTrace()
        } finally {
            pendingChunks.remove(chunkPos)
        }
    }
    
    /**
     * Unload chunks that are too far from the player
     */
    private fun unloadDistantChunks(playerChunkX: Int, playerChunkY: Int, playerChunkZ: Int) {
        val chunksToUnload = mutableListOf<Vector3i>()
        
        // Find chunks that are too far away
        for ((chunkPos, chunkInfo) in loadedChunks) {
            val distance = getDistanceFromCenter3D(
                chunkPos.x - playerChunkX,
                chunkPos.y - playerChunkY,
                chunkPos.z - playerChunkZ
            )
            
            if (distance > UNLOAD_DISTANCE) {
                chunksToUnload.add(chunkPos)
            }
        }
        
        // Unload distant chunks
        for (chunkPosValue in chunksToUnload) {
            unloadChunk(chunkPosValue.x, chunkPosValue.y, chunkPosValue.z)
        }
        
        if (chunksToUnload.isNotEmpty()) {
            println("Unloaded ${chunksToUnload.size} distant chunks")
        }
    }
    
    /**
     * Unload a specific chunk
     */
    private fun unloadChunk(chunkX: Int, chunkY: Int, chunkZ: Int) {
        val chunkPos = Vector3i(chunkX, chunkY, chunkZ)
        val chunkInfo = loadedChunks.remove(chunkPos)
        
        if (chunkInfo != null) {
            try {
                // Remove mesh from Vulkan rendering
                vulkanIntegration.removeChunkMesh(chunkInfo.entityId)
                
                // Unload from world
                world.unloadChunk(chunkX, chunkY, chunkZ)
                
                println("Unloaded chunk at ($chunkX, $chunkY, $chunkZ)")
            } catch (e: Exception) {
                println("ERROR: Failed to unload chunk at ($chunkX, $chunkY, $chunkZ): ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Calculate distance from center using Euclidean distance for 3D
     */
    private fun getDistanceFromCenter3D(dx: Int, dy: Int, dz: Int): Double {
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
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
    fun isChunkLoaded(chunkX: Int, chunkY: Int, chunkZ: Int): Boolean {
        return loadedChunks.containsKey(Vector3i(chunkX, chunkY, chunkZ))
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        println("Cleaning up ChunkManager...")
        
        // Unload all chunks
        val allChunks = loadedChunks.keys.toList()
        for (chunkPos in allChunks) {
            unloadChunk(chunkPos.x, chunkPos.y, chunkPos.z)
        }
        
        loadedChunks.clear()
        pendingChunks.clear()
        
        println("ChunkManager cleanup complete")
    }
}
