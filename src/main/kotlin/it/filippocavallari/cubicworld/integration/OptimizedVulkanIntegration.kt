package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.chunk.Chunk
import org.vulkanb.eng.graph.vk.OptimizedVertexBufferStructure
import org.vulkanb.eng.scene.Entity
import org.vulkanb.eng.scene.ModelData
import org.vulkanb.eng.scene.Scene
import java.util.concurrent.ConcurrentHashMap

/**
 * Vulkan integration that uses optimized vertex buffers for ~80% memory reduction.
 * 
 * This integration uses the OptimizedVertexBufferStructure which stores:
 * - Position: 3 shorts (6 bytes) instead of 3 floats (12 bytes)
 * - Normal Index: 1 byte instead of 3 floats (12 bytes)
 * - Texture Coords: 2 shorts (4 bytes) instead of 2 floats (8 bytes)
 * - Tangent/BiTangent: Computed in shader (0 bytes instead of 24 bytes)
 * 
 * Total: 11 bytes per vertex (was 56 bytes) = 80% memory reduction
 */
class OptimizedVulkanIntegration(private val scene: Scene) {
    
    private val textureStitcher = TextureStitcher("src/main/resources/textures")
    private val meshBuilder = OptimizedVulkanChunkMeshBuilder(textureStitcher)
    
    // Cache for chunk meshes and entities
    private val chunkMeshCache = ConcurrentHashMap<String, ModelData>()
    private val loadedChunks = ConcurrentHashMap<String, Entity>()
    
    // Statistics
    private var totalVertices = 0
    private var totalMemoryBytes = 0
    
    init {
        // Initialize texture atlas
        initializeTextureAtlas()
    }
    
    /**
     * Initialize the texture atlas system
     */
    private fun initializeTextureAtlas() {
        try {
            textureStitcher.build(16)
            println("✓ Optimized texture atlas initialized with ${textureStitcher.totalTextures} textures")
        } catch (e: Exception) {
            println("⚠ Warning: Could not initialize texture atlas: ${e.message}")
        }
    }
    
    /**
     * Add or update a chunk in the scene with optimized vertex data
     */
    fun addOrUpdateChunk(chunk: Chunk) {
        val chunkKey = "${chunk.getWorldX()}_${chunk.getWorldZ()}"
        
        try {
            // Remove existing chunk if present
            removeChunk(chunk)
            
            // Build optimized mesh
            val meshData = meshBuilder.buildMesh(chunk) ?: return
            
            // Cache the mesh
            chunkMeshCache[chunkKey] = meshData
            
            // Create entity and add to scene
            val entity = Entity("chunk_entity_$chunkKey", meshData.modelId, org.joml.Vector3f())
            loadedChunks[chunkKey] = entity
            scene.addEntity(entity)
            
            // Update statistics
            updateMemoryStats(meshData)
            
            println("✓ Optimized chunk loaded: $chunkKey (${getVertexCount(meshData)} vertices, ${getMemoryUsage(meshData)} bytes)")
            
        } catch (e: Exception) {
            println("✗ Error loading optimized chunk $chunkKey: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Remove a chunk from the scene
     */
    fun removeChunk(chunk: Chunk) {
        val chunkKey = "${chunk.getWorldX()}_${chunk.getWorldZ()}"
        
        loadedChunks[chunkKey]?.let { entity ->
            scene.removeEntity(entity)
            loadedChunks.remove(chunkKey)
            
            chunkMeshCache[chunkKey]?.let { meshData ->
                // Update statistics
                totalVertices -= getVertexCount(meshData)
                totalMemoryBytes -= getMemoryUsage(meshData)
                chunkMeshCache.remove(chunkKey)
            }
            
            println("✓ Optimized chunk removed: $chunkKey")
        }
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        val originalMemory = totalVertices * 56  // Original: 56 bytes per vertex
        val optimizedMemory = totalMemoryBytes   // Optimized: ~11 bytes per vertex
        val savings = originalMemory - optimizedMemory
        val savingsPercent = if (originalMemory > 0) (savings * 100.0 / originalMemory) else 0.0
        
        return MemoryStats(
            totalVertices = totalVertices,
            originalMemoryBytes = originalMemory,
            optimizedMemoryBytes = optimizedMemory,
            memoryReduction = savings,
            reductionPercent = savingsPercent
        )
    }
    
    /**
     * Print current memory usage statistics
     */
    fun printMemoryStats() {
        val stats = getMemoryStats()
        println("""
            |🔧 Optimized Vertex Memory Statistics:
            |  Total vertices: ${stats.totalVertices}
            |  Original memory: ${stats.originalMemoryBytes} bytes (${stats.originalMemoryBytes / 1024 / 1024} MB)
            |  Optimized memory: ${stats.optimizedMemoryBytes} bytes (${stats.optimizedMemoryBytes / 1024 / 1024} MB)
            |  Memory saved: ${stats.memoryReduction} bytes (${stats.memoryReduction / 1024 / 1024} MB)
            |  Reduction: ${String.format("%.1f", stats.reductionPercent)}%
        """.trimMargin())
    }
    
    /**
     * Reset all chunks and clear caches
     */
    fun reset() {
        loadedChunks.values.forEach { entity ->
            scene.removeEntity(entity)
        }
        loadedChunks.clear()
        chunkMeshCache.clear()
        totalVertices = 0
        totalMemoryBytes = 0
        println("✓ Optimized chunk system reset")
    }
    
    /**
     * Get number of loaded chunks
     */
    fun getLoadedChunkCount(): Int = loadedChunks.size
    
    /**
     * Check if a chunk is loaded
     */
    fun isChunkLoaded(chunk: Chunk): Boolean {
        val chunkKey = "${chunk.getWorldX()}_${chunk.getWorldZ()}"
        return loadedChunks.containsKey(chunkKey)
    }
    
    // Helper methods
    private fun getVertexCount(meshData: ModelData): Int {
        return meshData.meshDataList[0].indices.size / 6 * 4  // 6 indices per face, 4 vertices per face
    }
    
    private fun getMemoryUsage(meshData: ModelData): Int {
        val vertexCount = getVertexCount(meshData)
        return vertexCount * OptimizedVulkanChunkMeshBuilder.VERTEX_SIZE_BYTES  // 11 bytes per vertex
    }
    
    private fun updateMemoryStats(meshData: ModelData) {
        val vertices = getVertexCount(meshData)
        val memory = getMemoryUsage(meshData)
        totalVertices += vertices
        totalMemoryBytes += memory
    }
    
    /**
     * Data class for memory usage statistics
     */
    data class MemoryStats(
        val totalVertices: Int,
        val originalMemoryBytes: Int,
        val optimizedMemoryBytes: Int,
        val memoryReduction: Int,
        val reductionPercent: Double
    )
}