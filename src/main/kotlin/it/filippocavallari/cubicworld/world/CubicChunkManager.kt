package it.filippocavallari.cubicworld.world

import it.filippocavallari.cubicworld.integration.VulkanIntegration
import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import org.joml.Vector3f
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.*

/**
 * Manages dynamic cubic chunk loading and unloading around the player position.
 * Implements spherical loading pattern for cubic chunks (16x16x16).
 */
class CubicChunkManager(
    private val world: CubicWorld,
    private val vulkanIntegration: VulkanIntegration
) {
    companion object {
        const val HORIZONTAL_RENDER_DISTANCE = 8 // Chunks in X/Z directions
        const val VERTICAL_RENDER_DISTANCE = 4 // Chunks in Y direction (up/down)
        const val LOAD_THRESHOLD_BLOCKS = 8 // Start loading new chunks when within 8 blocks of edge
        const val MAX_CHUNKS_PER_FRAME = 2 // Limit chunk loading per frame
        const val UNLOAD_DISTANCE_HORIZONTAL = 10 // Unload chunks beyond this distance
        const val UNLOAD_DISTANCE_VERTICAL = 6 // Vertical unload distance
        const val MAX_LOADED_CHUNKS = 2000 // Much higher limit due to smaller chunks
    }
    
    // Data class to store chunk information
    data class ChunkInfo(
        val chunk: CubicChunk,
        val entityId: String,
        val loadTime: Long,
        val distance: Double = 0.0,
        val priority: Int = 0
    )
    
    // Priority queue entry for chunk loading
    data class ChunkLoadRequest(
        val position: Vector3i,
        val priority: Int
    ) : Comparable<ChunkLoadRequest> {
        override fun compareTo(other: ChunkLoadRequest): Int {
            return other.priority.compareTo(priority) // Higher priority loads first (reverse order)
        }
    }
    
    // Maps to track loaded chunks and their render entities
    private val loadedChunks = ConcurrentHashMap<Vector3i, ChunkInfo>()
    private val pendingChunks = ConcurrentHashMap<Vector3i, Boolean>()
    
    // Priority queue for chunk loading
    private val loadQueue = PriorityBlockingQueue<ChunkLoadRequest>()
    
    // Player position tracking
    private var lastPlayerChunkPos = Vector3i(0, 0, 0)
    private var playerWorldPos = Vector3f(0f, 64f, 0f)
    private var isInitialized = false
    
    // Performance tracking
    private var totalChunksLoaded = 0
    private var emptyChunksSkipped = 0
    
    /**
     * Initialize the chunk manager with initial chunks around spawn point
     */
    fun initialize(spawnX: Float = 0f, spawnY: Float = 64f, spawnZ: Float = 0f) {
        if (isInitialized) return
        
        println("Initializing CubicChunkManager with spherical loading pattern")
        
        playerWorldPos.set(spawnX, spawnY, spawnZ)
        val spawnChunkX = CubicChunk.worldToChunk(spawnX.toInt())
        val spawnChunkY = CubicChunk.worldToChunk(spawnY.toInt())
        val spawnChunkZ = CubicChunk.worldToChunk(spawnZ.toInt())
        
        lastPlayerChunkPos.set(spawnChunkX, spawnChunkY, spawnChunkZ)
        
        // Load initial chunks in spherical pattern
        loadChunksSpherical(spawnChunkX, spawnChunkY, spawnChunkZ, immediate = true)
        
        isInitialized = true
        println("CubicChunkManager initialized with ${loadedChunks.size} chunks")
        println(world.getStatistics())
    }
    
    /**
     * Update player position and trigger chunk loading/unloading as needed
     */
    fun updatePlayerPosition(worldX: Float, worldY: Float, worldZ: Float) {
        playerWorldPos.set(worldX, worldY, worldZ)
        
        // Convert world coordinates to chunk coordinates
        val playerChunkX = CubicChunk.worldToChunk(worldX.toInt())
        val playerChunkY = CubicChunk.worldToChunk(worldY.toInt())
        val playerChunkZ = CubicChunk.worldToChunk(worldZ.toInt())
        val currentPlayerChunkPos = Vector3i(playerChunkX, playerChunkY, playerChunkZ)
        
        // Check if player moved to a different chunk
        if (currentPlayerChunkPos != lastPlayerChunkPos) {
            println("Player moved to chunk ($playerChunkX, $playerChunkY, $playerChunkZ)")
            lastPlayerChunkPos.set(currentPlayerChunkPos)
            
            // Update chunks around new position
            loadChunksSpherical(playerChunkX, playerChunkY, playerChunkZ)
            unloadDistantChunks(playerChunkX, playerChunkY, playerChunkZ)
        }
        
        // Process pending chunk loads from queue
        processLoadQueue()
    }
    
    /**
     * Load chunks in a spherical pattern around the center position
     */
    private fun loadChunksSpherical(
        centerX: Int, 
        centerY: Int, 
        centerZ: Int,
        immediate: Boolean = false
    ) {
        // Clear existing load queue if position changed significantly
        if (!immediate) {
            loadQueue.clear()
        }
        
        // Generate list of chunk positions in spherical pattern, starting from center
        val chunksToProcess = mutableListOf<Pair<Vector3i, Double>>()
        
        // Generate all chunks within render distance
        for (dy in -VERTICAL_RENDER_DISTANCE..VERTICAL_RENDER_DISTANCE) {
            for (dx in -HORIZONTAL_RENDER_DISTANCE..HORIZONTAL_RENDER_DISTANCE) {
                for (dz in -HORIZONTAL_RENDER_DISTANCE..HORIZONTAL_RENDER_DISTANCE) {
                    val chunkX = centerX + dx
                    val chunkY = centerY + dy
                    val chunkZ = centerZ + dz
                    
                    // Skip chunks below bedrock or above build limit
                    if (chunkY < -4 || chunkY > 16) continue
                    
                    // Calculate distance with vertical weight
                    val horizontalDist = sqrt((dx * dx + dz * dz).toDouble())
                    val verticalDist = abs(dy) * 1.5 // Weight vertical distance more
                    val distance = sqrt(horizontalDist * horizontalDist + verticalDist * verticalDist)
                    
                    // Check if within render distance (ellipsoid shape)
                    if (horizontalDist <= HORIZONTAL_RENDER_DISTANCE && 
                        abs(dy) <= VERTICAL_RENDER_DISTANCE) {
                        
                        val chunkPos = Vector3i(chunkX, chunkY, chunkZ)
                        
                        // Skip if already loaded or pending
                        if (loadedChunks.containsKey(chunkPos) || pendingChunks.containsKey(chunkPos)) {
                            continue
                        }
                        
                        chunksToProcess.add(Pair(chunkPos, distance))
                    }
                }
            }
        }
        
        // Sort by distance to ensure closest chunks are processed first
        chunksToProcess.sortBy { it.second }
        
        println("Processing ${chunksToProcess.size} chunks in spherical pattern")
        
        // Process chunks in order of distance from center
        for ((chunkPos, distance) in chunksToProcess) {
            // Calculate priority (closer chunks have higher priority)
            val priority = (10000 - (distance * 100)).toInt()
            
            if (immediate && distance <= 2) {
                // Load nearby chunks immediately during initialization
                loadChunkImmediate(chunkPos.x, chunkPos.y, chunkPos.z)
            } else {
                // Add to load queue with proper priority
                loadQueue.offer(ChunkLoadRequest(chunkPos, priority))
            }
        }
        
        if (immediate) {
            println("Initial chunk loading complete: ${loadedChunks.size} chunks loaded")
        } else {
            println("Added ${chunksToProcess.size} chunks to load queue")
        }
    }
    
    /**
     * Process pending chunk loads from the queue
     */
    private fun processLoadQueue() {
        var chunksLoadedThisFrame = 0
        
        while (chunksLoadedThisFrame < MAX_CHUNKS_PER_FRAME && !loadQueue.isEmpty()) {
            val request = loadQueue.poll() ?: break
            
            // Double-check the chunk isn't already loaded
            if (!loadedChunks.containsKey(request.position) && 
                !pendingChunks.containsKey(request.position)) {
                
                loadChunkAsync(request.position.x, request.position.y, request.position.z)
                chunksLoadedThisFrame++
            }
        }
    }
    
    /**
     * Load a chunk immediately (synchronous)
     */
    private fun loadChunkImmediate(chunkX: Int, chunkY: Int, chunkZ: Int) {
        val chunkPos = Vector3i(chunkX, chunkY, chunkZ)
        
        try {
            val chunk = world.loadChunkSynchronously(chunkX, chunkY, chunkZ)
            
            // Skip empty chunks to save memory and rendering resources
            if (chunk.isEmpty()) {
                emptyChunksSkipped++
                return
            }
            
            // Create mesh for the chunk
            val entityId = vulkanIntegration.createCubicChunkMesh(chunk)
            
            if (entityId.isNotEmpty()) {
                val chunkInfo = ChunkInfo(
                    chunk = chunk,
                    entityId = entityId,
                    loadTime = System.currentTimeMillis()
                )
                
                loadedChunks[chunkPos] = chunkInfo
                totalChunksLoaded++
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load chunk at ($chunkX, $chunkY, $chunkZ): ${e.message}")
        }
    }
    
    /**
     * Load a chunk asynchronously
     */
    private fun loadChunkAsync(chunkX: Int, chunkY: Int, chunkZ: Int) {
        val chunkPos = Vector3i(chunkX, chunkY, chunkZ)
        pendingChunks[chunkPos] = true
        
        try {
            // Load chunk (this is actually synchronous for now)
            val chunk = world.loadChunk(chunkX, chunkY, chunkZ)
            
            // Skip empty chunks
            if (chunk.isEmpty()) {
                emptyChunksSkipped++
                pendingChunks.remove(chunkPos)
                return
            }
            
            // Create mesh for the chunk
            val entityId = vulkanIntegration.createCubicChunkMesh(chunk)
            
            if (entityId.isNotEmpty()) {
                val chunkInfo = ChunkInfo(
                    chunk = chunk,
                    entityId = entityId,
                    loadTime = System.currentTimeMillis()
                )
                
                loadedChunks[chunkPos] = chunkInfo
                totalChunksLoaded++
                
                if (totalChunksLoaded % 100 == 0) {
                    println("Chunk loading progress: $totalChunksLoaded loaded, $emptyChunksSkipped empty skipped")
                }
            }
        } catch (e: Exception) {
            println("ERROR: Failed to load chunk at ($chunkX, $chunkY, $chunkZ): ${e.message}")
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
            val dx = chunkPos.x - playerChunkX
            val dy = chunkPos.y - playerChunkY
            val dz = chunkPos.z - playerChunkZ
            
            val horizontalDist = sqrt((dx * dx + dz * dz).toDouble())
            val verticalDist = abs(dy)
            
            if (horizontalDist > UNLOAD_DISTANCE_HORIZONTAL || 
                verticalDist > UNLOAD_DISTANCE_VERTICAL) {
                chunksToUnload.add(chunkPos)
            }
        }
        
        // Unload distant chunks
        for (chunkPos in chunksToUnload) {
            unloadChunk(chunkPos.x, chunkPos.y, chunkPos.z)
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
                
                totalChunksLoaded--
            } catch (e: Exception) {
                println("ERROR: Failed to unload chunk at ($chunkX, $chunkY, $chunkZ): ${e.message}")
            }
        }
    }
    
    /**
     * Get information about currently loaded chunks
     */
    fun getLoadedChunkInfo(): String {
        val totalChunks = loadedChunks.size
        val pendingCount = pendingChunks.size
        val queueSize = loadQueue.size
        
        return """
            Cubic chunks loaded: $totalChunks/$MAX_LOADED_CHUNKS
            Pending: $pendingCount, Queue: $queueSize
            Empty chunks skipped: $emptyChunksSkipped
            ${world.getStatistics()}
        """.trimIndent()
    }
    
    /**
     * Get chunks that are visible using proper frustum culling
     */
    fun getVisibleChunks(frustumCuller: org.vulkanb.eng.scene.FrustumCuller): List<CubicChunk> {
        val visibleChunks = mutableListOf<CubicChunk>()
        
        for ((chunkPos, chunkInfo) in loadedChunks) {
            val chunk = chunkInfo.chunk
            
            // Calculate chunk world bounds
            val worldMinX = chunk.getWorldX().toFloat()
            val worldMinY = chunk.getWorldY().toFloat()
            val worldMinZ = chunk.getWorldZ().toFloat()
            val worldMaxX = worldMinX + CubicChunk.SIZE
            val worldMaxY = worldMinY + CubicChunk.SIZE
            val worldMaxZ = worldMinZ + CubicChunk.SIZE
            
            // Use proper frustum culling
            if (frustumCuller.isChunkInFrustum(worldMinX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ)) {
                visibleChunks.add(chunk)
            }
        }
        
        return visibleChunks
    }
    
    /**
     * Get chunks that should be prioritized for visibility culling (legacy method)
     */
    @Deprecated("Use getVisibleChunks(FrustumCuller) instead")
    fun getVisibleChunks(cameraPos: Vector3f, cameraForward: Vector3f): List<CubicChunk> {
        val visibleChunks = mutableListOf<CubicChunk>()
        
        for ((chunkPos, chunkInfo) in loadedChunks) {
            val chunk = chunkInfo.chunk
            
            // Simple frustum culling - check if chunk is in front of camera
            val chunkCenter = Vector3f(
                chunk.getWorldX() + CubicChunk.SIZE / 2f,
                chunk.getWorldY() + CubicChunk.SIZE / 2f,
                chunk.getWorldZ() + CubicChunk.SIZE / 2f
            )
            
            val toChunk = Vector3f(chunkCenter).sub(cameraPos)
            val dot = toChunk.dot(cameraForward)
            
            // Include chunks in front of camera or very close
            if (dot > -CubicChunk.SIZE || toChunk.length() < CubicChunk.SIZE * 2) {
                visibleChunks.add(chunk)
            }
        }
        
        return visibleChunks
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        println("Cleaning up CubicChunkManager...")
        
        // Unload all chunks
        val allChunks = loadedChunks.keys.toList()
        for (chunkPos in allChunks) {
            unloadChunk(chunkPos.x, chunkPos.y, chunkPos.z)
        }
        
        loadedChunks.clear()
        pendingChunks.clear()
        loadQueue.clear()
        
        println("CubicChunkManager cleanup complete")
    }
}
