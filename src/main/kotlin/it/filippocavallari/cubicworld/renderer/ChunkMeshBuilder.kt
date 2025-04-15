package it.filippocavallari.cubicworld.renderer

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.data.block.FaceDirection
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.models.ModelManager
import it.filippocavallari.models.Model
import it.filippocavallari.textures.TextureStitcher
import it.filippocavallari.textures.TextureRegion

import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.joml.Vector3f
import org.joml.Vector3i

/**
 * Builds optimized mesh data for chunks using greedy meshing algorithm.
 * Integrates with the model manager and texture atlas system.
 */
class ChunkMeshBuilder(
    private val modelManager: ModelManager,
    private val textureStitcher: TextureStitcher
) {
    // Debug tracking
    private val accessedModels = HashSet<String>()
    private val failedModels = HashSet<String>()
    
    // Thread pool for mesh building
    private val meshBuildExecutor: ExecutorService = Executors.newFixedThreadPool(NUM_THREADS)
    private val buildTasks: MutableMap<Long, Future<ChunkMeshData>> = ConcurrentHashMap()

    /**
     * Face data for greedy meshing
     */
    private class FaceData(
        val blockId: Int,
        val faceDirection: FaceDirection,
        val textureIndex: Int
    )

    /**
     * Chunk mesh data containing all rendering information
     */
    class ChunkMeshData(
        val solidVertices: FloatArray,
        val solidIndices: IntArray,
        val solidNormals: FloatArray,
        val solidUVs: FloatArray,
        val solidTangents: FloatArray,
        val transparentVertices: FloatArray,
        val transparentIndices: IntArray,
        val transparentNormals: FloatArray,
        val transparentUVs: FloatArray,
        val transparentTangents: FloatArray
    ) {
        val isEmpty: Boolean
            get() = solidIndices.isEmpty() && transparentIndices.isEmpty()
    }

    /**
     * Start building a mesh for a chunk asynchronously
     */
    fun buildChunkMesh(chunk: Chunk): Future<ChunkMeshData> {
        val chunkKey = getChunkKey(chunk.position.x, chunk.position.y)
        
        // Cancel existing task if present
        val existingTask = buildTasks[chunkKey]
        existingTask?.cancel(false)
        
        // Submit new task
        val task = meshBuildExecutor.submit<ChunkMeshData> {
            try {
                generateMeshData(chunk)
            } catch (e: Exception) {
                e.printStackTrace()
                createEmptyMeshData()
            }
        }
        
        buildTasks[chunkKey] = task
        return task
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        meshBuildExecutor.shutdown()
        try {
            if (!meshBuildExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                meshBuildExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            meshBuildExecutor.shutdownNow()
        }
    }

    /**
     * Generate mesh data for a chunk using greedy meshing
     */
    fun generateMeshData(chunk: Chunk): ChunkMeshData {
        // Data buffers for solid blocks
        val solidVertices = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY)
        val solidIndices = ArrayList<Int>(ESTIMATED_INDICES_CAPACITY)
        val solidNormals = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY)
        val solidUVs = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY)
        val solidTangents = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY)
        
        // Data buffers for transparent blocks
        val transparentVertices = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY / 4)
        val transparentIndices = ArrayList<Int>(ESTIMATED_INDICES_CAPACITY / 4)
        val transparentNormals = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY / 4)
        val transparentUVs = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY / 4)
        val transparentTangents = ArrayList<Float>(ESTIMATED_VERTICES_CAPACITY / 4)

        // Create visibility maps for all 6 directions
        val visibilityMaps = createVisibilityMaps(chunk)
        
        // Process each face direction
        for (direction in FaceDirection.values()) {
            performGreedyMeshing(
                chunk,
                direction,
                visibilityMaps[direction.ordinal],
                solidVertices, solidIndices, solidNormals, solidUVs, solidTangents,
                transparentVertices, transparentIndices, transparentNormals, transparentUVs, transparentTangents
            )
        }

        return ChunkMeshData(
            toFloatArray(solidVertices),
            toIntArray(solidIndices),
            toFloatArray(solidNormals),
            toFloatArray(solidUVs),
            toFloatArray(solidTangents),
            toFloatArray(transparentVertices),
            toIntArray(transparentIndices),
            toFloatArray(transparentNormals),
            toFloatArray(transparentUVs),
            toFloatArray(transparentTangents)
        )
    }

    /**
     * Create visibility maps for all six face directions
     */
    private fun createVisibilityMaps(chunk: Chunk): Array<Array<Array<FaceData?>>> {
        val maps = Array(6) { Array(CHUNK_SIZE) { arrayOfNulls<FaceData>(CHUNK_SIZE) } }

        // Fill the visibility maps
        for (x in 0 until CHUNK_SIZE) {
            for (y in 0 until CHUNK_HEIGHT) {
                for (z in 0 until CHUNK_SIZE) {
                    val blockId = chunk.getBlock(x, y, z)
                    if (blockId != 0) { // Not air
                        // Check each face direction
                        for (direction in FaceDirection.values()) {
                            if (isFaceVisible(chunk, x, y, z, direction, blockId)) {
                                // Map directional coordinates to array indices
                                val (u, v, sliceIndex) = when (direction) {
                                    FaceDirection.UP, FaceDirection.DOWN -> Triple(x, z, y)
                                    FaceDirection.NORTH, FaceDirection.SOUTH -> Triple(x, y, z)
                                    FaceDirection.EAST, FaceDirection.WEST -> Triple(z, y, x)
                                }
                                
                                // Adjust slice index based on direction
                                val adjustedSliceIndex = when (direction) {
                                    FaceDirection.UP -> sliceIndex
                                    FaceDirection.DOWN -> sliceIndex - 1
                                    FaceDirection.NORTH -> CHUNK_SIZE - 1 - sliceIndex
                                    FaceDirection.SOUTH -> sliceIndex
                                    FaceDirection.EAST -> sliceIndex
                                    FaceDirection.WEST -> CHUNK_SIZE - 1 - sliceIndex
                                }
                                
                                // Clamp to valid range
                                val clampedSliceIndex = Math.max(0, Math.min(adjustedSliceIndex, CHUNK_SIZE - 1))
                                
                                // Get the texture index for this face
                                val textureIndex = getTextureIndexForFace(blockId, direction)
                                
                                // Store face data in the visibility map
                                if (u in 0 until CHUNK_SIZE && v in 0 until CHUNK_SIZE) {
                                    maps[direction.ordinal][u][v] = FaceData(blockId, direction, textureIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return maps
    }

    /**
     * Determine if a face is visible
     */
    private fun isFaceVisible(chunk: Chunk, x: Int, y: Int, z: Int, direction: FaceDirection, blockId: Int): Boolean {
        // Get coordinates of the adjacent block in this direction
        val nx = x + direction.offsetX
        val ny = y + direction.offsetY
        val nz = z + direction.offsetZ
        
        // Check if we're at chunk boundaries
        if (nx < 0 || nx >= CHUNK_SIZE || ny < 0 || ny >= CHUNK_HEIGHT || nz < 0 || nz >= CHUNK_SIZE) {
            // Edge of chunk - check neighboring chunks if available
            // For simplicity, we'll just assume faces at chunk boundaries are visible
            return true
        }
        
        // Get the adjacent block
        val adjacentBlockId = chunk.getBlock(nx, ny, nz)
        
        // The face is visible if the adjacent block is air or transparent
        return adjacentBlockId == 0 || isBlockTransparent(adjacentBlockId)
    }

    /**
     * Check if a block is transparent
     */
    private fun isBlockTransparent(blockId: Int): Boolean {
        val blockType = BlockType.getById(blockId)
        return blockType != null && blockType.isTransparent
    }

    /**
     * Get the texture index for a specific face of a block
     */
    private fun getTextureIndexForFace(blockId: Int, direction: FaceDirection): Int {
        // Get the model ID from the block type
        val blockType = BlockType.getById(blockId)
        val modelId = blockType?.modelId ?: "block/stone" // Fallback
        
        try {
            // Log first access to each model for debugging
            if (!accessedModels.contains(modelId)) {
                println("Loading model: $modelId for block $blockType")
                accessedModels.add(modelId)
            }
            
            val model = modelManager.getModel(modelId)
            
            // Get the face texture from the model
            val textureName = when (direction) {
                FaceDirection.UP -> "up"
                FaceDirection.DOWN -> "down"
                FaceDirection.NORTH -> "north"
                FaceDirection.SOUTH -> "south"
                FaceDirection.EAST -> "east"
                FaceDirection.WEST -> "west"
            }
            
            // First try specific face, then fallback to "all", then to first available texture
            val resolvedTextures = model.resolvedTextureIndexes
            var textureIndex = resolvedTextures[textureName]
            
            // If specific face texture not found, try "all"
            if (textureIndex == null) {
                textureIndex = resolvedTextures["all"]
            }
            
            // If still null, try to get the first texture from the map
            if (textureIndex == null && resolvedTextures.isNotEmpty()) {
                textureIndex = resolvedTextures.values.iterator().next()
            }
            
            // If found a texture, return it. Make sure it's non-negative.
            if (textureIndex != null) {
                // Ensure texture index is positive
                return if (textureIndex < 0) 0 else textureIndex
            }
            
            // Fallback to block ID as a basic coloring mechanism if no texture found
            return Math.abs(blockId % 10)
        } catch (e: Exception) {
            // Log the error for debugging (only once per model)
            if (!failedModels.contains(modelId)) {
                println("Failed to get texture for block $blockId model $modelId: ${e.message}")
                failedModels.add(modelId)
            }
            
            // Fallback to block ID as a basic coloring mechanism
            return Math.abs(blockId % 10)
        }
    }

    /**
     * The core greedy meshing algorithm
     */
    private fun performGreedyMeshing(
        chunk: Chunk,
        direction: FaceDirection,
        visibilityMap: Array<Array<FaceData?>>,
        solidVertices: MutableList<Float>,
        solidIndices: MutableList<Int>,
        solidNormals: MutableList<Float>,
        solidUVs: MutableList<Float>,
        solidTangents: MutableList<Float>,
        transparentVertices: MutableList<Float>,
        transparentIndices: MutableList<Int>,
        transparentNormals: MutableList<Float>,
        transparentUVs: MutableList<Float>,
        transparentTangents: MutableList<Float>
    ) {
        // Create a working copy of the visibility map that we can modify
        val workingMap = Array(CHUNK_SIZE) { u ->
            Array<FaceData?>(CHUNK_SIZE) { v ->
                visibilityMap[u][v]
            }
        }
        
        // Keep meshing until all faces are processed
        while (true) {
            // Find the first unprocessed face
            var startU = -1
            var startV = -1
            var startFace: FaceData? = null
            
            outerLoop@ for (u in 0 until CHUNK_SIZE) {
                for (v in 0 until CHUNK_SIZE) {
                    if (workingMap[u][v] != null) {
                        startU = u
                        startV = v
                        startFace = workingMap[u][v]
                        break@outerLoop
                    }
                }
            }
            
            // If no faces left, we're done with this direction
            if (startFace == null) break
            
            // Find maximum width (keeping same block type and texture)
            var width = 1
            while (startU + width < CHUNK_SIZE &&
                  workingMap[startU + width][startV] != null &&
                  canMerge(workingMap[startU + width][startV]!!, startFace)) {
                width++
            }
            
            // Find maximum height (keeping same block type and texture)
            var height = 1
            var canExpandHeight = true
            
            while (canExpandHeight && startV + height < CHUNK_SIZE) {
                for (du in 0 until width) {
                    if (workingMap[startU + du][startV + height] == null ||
                        !canMerge(workingMap[startU + du][startV + height]!!, startFace)) {
                        canExpandHeight = false
                        break
                    }
                }
                if (canExpandHeight) height++
            }
            
            // Mark the faces as processed
            for (du in 0 until width) {
                for (dv in 0 until height) {
                    workingMap[startU + du][startV + dv] = null
                }
            }
            
            // Get the depth (position along the normal) of this face
            val depth = getDepthForDirection(direction, startU, startV, chunk)
            
            // Add the merged face to our mesh
            addMergedFace(
                chunk,
                direction,
                startU,
                startV,
                width,
                height,
                depth,
                startFace,
                solidVertices,
                solidIndices,
                solidNormals,
                solidUVs,
                solidTangents,
                transparentVertices,
                transparentIndices,
                transparentNormals,
                transparentUVs,
                transparentTangents
            )
        }
    }

    /**
     * Check if two faces can be merged (same block type and texture)
     */
    private fun canMerge(face1: FaceData, face2: FaceData): Boolean {
        return face1.blockId == face2.blockId && 
               face1.textureIndex == face2.textureIndex
    }

    /**
     * Get the depth position for a face direction
     */
    private fun getDepthForDirection(direction: FaceDirection, u: Int, v: Int, chunk: Chunk): Int {
        // This is a simplification - a proper implementation would trace backward
        // from the visible face to find the actual depth
        return when (direction) {
            FaceDirection.UP -> CHUNK_HEIGHT - 1
            FaceDirection.DOWN -> 0
            FaceDirection.NORTH -> 0
            FaceDirection.SOUTH -> CHUNK_SIZE - 1
            FaceDirection.EAST -> CHUNK_SIZE - 1
            FaceDirection.WEST -> 0
        }
    }

    /**
     * Add a merged face to the mesh data
     */
    private fun addMergedFace(
        chunk: Chunk,
        direction: FaceDirection,
        u: Int,
        v: Int,
        width: Int,
        height: Int,
        depth: Int,
        faceData: FaceData,
        solidVertices: MutableList<Float>,
        solidIndices: MutableList<Int>,
        solidNormals: MutableList<Float>,
        solidUVs: MutableList<Float>,
        solidTangents: MutableList<Float>,
        transparentVertices: MutableList<Float>,
        transparentIndices: MutableList<Int>,
        transparentNormals: MutableList<Float>,
        transparentUVs: MutableList<Float>,
        transparentTangents: MutableList<Float>
    ) {
        // Select the appropriate buffers based on block transparency
        val isTransparent = isBlockTransparent(faceData.blockId)
        val vertices = if (isTransparent) transparentVertices else solidVertices
        val indices = if (isTransparent) transparentIndices else solidIndices
        val normals = if (isTransparent) transparentNormals else solidNormals
        val uvs = if (isTransparent) transparentUVs else solidUVs
        val tangents = if (isTransparent) transparentTangents else solidTangents
        
        // Get starting vertex index
        val indexOffset = vertices.size / 3
        
        // Calculate world position coordinates for the face
        var x1 = 0f
        var y1 = 0f
        var z1 = 0f
        var x2 = 0f
        var y2 = 0f
        var z2 = 0f
        
        when (direction) {
            FaceDirection.UP, FaceDirection.DOWN -> {
                x1 = u.toFloat()
                x2 = (u + width).toFloat()
                y1 = if (direction == FaceDirection.UP) (depth + 1).toFloat() else depth.toFloat()
                y2 = y1
                z1 = v.toFloat()
                z2 = (v + height).toFloat()
            }
            FaceDirection.NORTH, FaceDirection.SOUTH -> {
                x1 = u.toFloat()
                x2 = (u + width).toFloat()
                y1 = v.toFloat()
                y2 = (v + height).toFloat()
                z1 = if (direction == FaceDirection.SOUTH) (depth + 1).toFloat() else depth.toFloat()
                z2 = z1
            }
            FaceDirection.EAST, FaceDirection.WEST -> {
                x1 = if (direction == FaceDirection.EAST) (depth + 1).toFloat() else depth.toFloat()
                x2 = x1
                y1 = v.toFloat()
                y2 = (v + height).toFloat()
                z1 = u.toFloat()
                z2 = (u + width).toFloat()
            }
        }
        
        // Add vertices based on face direction
        when (direction) {
            FaceDirection.UP -> {
                // Top face (+Y)
                vertices.add(x1); vertices.add(y1); vertices.add(z1) // Bottom-left
                vertices.add(x2); vertices.add(y1); vertices.add(z1) // Bottom-right
                vertices.add(x2); vertices.add(y1); vertices.add(z2) // Top-right
                vertices.add(x1); vertices.add(y1); vertices.add(z2) // Top-left
            }
            FaceDirection.DOWN -> {
                // Bottom face (-Y)
                vertices.add(x1); vertices.add(y1); vertices.add(z2) // Bottom-left
                vertices.add(x2); vertices.add(y1); vertices.add(z2) // Bottom-right
                vertices.add(x2); vertices.add(y1); vertices.add(z1) // Top-right
                vertices.add(x1); vertices.add(y1); vertices.add(z1) // Top-left
            }
            FaceDirection.NORTH -> {
                // North face (-Z)
                vertices.add(x2); vertices.add(y1); vertices.add(z1) // Bottom-left
                vertices.add(x1); vertices.add(y1); vertices.add(z1) // Bottom-right
                vertices.add(x1); vertices.add(y2); vertices.add(z1) // Top-right
                vertices.add(x2); vertices.add(y2); vertices.add(z1) // Top-left
            }
            FaceDirection.SOUTH -> {
                // South face (+Z)
                vertices.add(x1); vertices.add(y1); vertices.add(z1) // Bottom-left
                vertices.add(x2); vertices.add(y1); vertices.add(z1) // Bottom-right
                vertices.add(x2); vertices.add(y2); vertices.add(z1) // Top-right
                vertices.add(x1); vertices.add(y2); vertices.add(z1) // Top-left
            }
            FaceDirection.EAST -> {
                // East face (+X)
                vertices.add(x1); vertices.add(y1); vertices.add(z2) // Bottom-left
                vertices.add(x1); vertices.add(y1); vertices.add(z1) // Bottom-right
                vertices.add(x1); vertices.add(y2); vertices.add(z1) // Top-right
                vertices.add(x1); vertices.add(y2); vertices.add(z2) // Top-left
            }
            FaceDirection.WEST -> {
                // West face (-X)
                vertices.add(x1); vertices.add(y1); vertices.add(z1) // Bottom-left
                vertices.add(x1); vertices.add(y1); vertices.add(z2) // Bottom-right
                vertices.add(x1); vertices.add(y2); vertices.add(z2) // Top-right
                vertices.add(x1); vertices.add(y2); vertices.add(z1) // Top-left
            }
        }
        
        // Add indices for the quad (two triangles)
        indices.add(indexOffset)
        indices.add(indexOffset + 1)
        indices.add(indexOffset + 2)
        indices.add(indexOffset)
        indices.add(indexOffset + 2)
        indices.add(indexOffset + 3)
        
        // Add normals
        val normal = getNormalForDirection(direction)
        repeat(4) {
            normals.add(normal.x)
            normals.add(normal.y)
            normals.add(normal.z)
        }
        
        // Get texture coordinates
        var textureRegion: TextureRegion? = null
        var textureIndex = faceData.textureIndex
        
        // Ensure texture index is valid (positive)
        if (textureIndex < 0) {
            println("Warning: Requested texture index $textureIndex is negative, using fallback index 0")
            textureIndex = 0  // Use first texture as fallback
        }
        
        try {
            textureRegion = textureStitcher.getTextureRegion(textureIndex)
        } catch (e: Exception) {
            println("Warning: Failed to get texture region for index $textureIndex on $direction face of block ${faceData.blockId}, using fallback")
            
            // Try to get default texture (index 0)
            try {
                textureRegion = textureStitcher.getTextureRegion(0)
            } catch (e2: Exception) {
                println("Warning: Failed to get fallback texture region")
            }
        }
        
        // Add UVs - use default UVs if texture region is not available
        val (u1, v1, u2, v2) = if (textureRegion != null) {
            listOf(textureRegion.u1, textureRegion.v1, textureRegion.u2, textureRegion.v2)
        } else {
            // Default UV coordinates (full texture)
            listOf(0.0f, 0.0f, 1.0f, 1.0f)
        }
        
        // Adjust UVs based on merged face size
        // This scales the texture across the merged face
        uvs.add(u1)
        uvs.add(v1)
        uvs.add(u1 + (u2-u1) * width)
        uvs.add(v1)
        uvs.add(u1 + (u2-u1) * width)
        uvs.add(v1 + (v2-v1) * height)
        uvs.add(u1)
        uvs.add(v1 + (v2-v1) * height)
        
        // Add tangents for normal mapping
        val tangent = getTangentForDirection(direction)
        repeat(4) {
            tangents.add(tangent.x)
            tangents.add(tangent.y)
            tangents.add(tangent.z)
        }
    }

    /**
     * Get normal vector for a face direction
     */
    private fun getNormalForDirection(direction: FaceDirection): Vector3f {
        return when (direction) {
            FaceDirection.UP -> Vector3f(0f, 1f, 0f)
            FaceDirection.DOWN -> Vector3f(0f, -1f, 0f)
            FaceDirection.NORTH -> Vector3f(0f, 0f, -1f)
            FaceDirection.SOUTH -> Vector3f(0f, 0f, 1f)
            FaceDirection.EAST -> Vector3f(1f, 0f, 0f)
            FaceDirection.WEST -> Vector3f(-1f, 0f, 0f)
        }
    }

    /**
     * Get tangent vector for a face direction (for normal mapping)
     */
    private fun getTangentForDirection(direction: FaceDirection): Vector3f {
        return when (direction) {
            FaceDirection.UP, FaceDirection.DOWN -> Vector3f(1f, 0f, 0f)
            FaceDirection.NORTH, FaceDirection.SOUTH -> Vector3f(1f, 0f, 0f)
            FaceDirection.EAST, FaceDirection.WEST -> Vector3f(0f, 0f, 1f)
        }
    }

    /**
     * Create an empty mesh data object
     */
    private fun createEmptyMeshData(): ChunkMeshData {
        return ChunkMeshData(
            FloatArray(0),
            IntArray(0),
            FloatArray(0),
            FloatArray(0),
            FloatArray(0),
            FloatArray(0),
            IntArray(0),
            FloatArray(0),
            FloatArray(0),
            FloatArray(0)
        )
    }

    /**
     * Convert a List<Float> to a float array
     */
    private fun toFloatArray(list: List<Float>): FloatArray {
        return list.toFloatArray()
    }

    /**
     * Convert a List<Integer> to an int array
     */
    private fun toIntArray(list: List<Int>): IntArray {
        return list.toIntArray()
    }

    /**
     * Generate a unique key for a chunk position
     */
    private fun getChunkKey(x: Int, y: Int): Long {
        // Note: y parameter is actually the z-coordinate in world space
        // In Vector2i, z is stored in the y component
        return (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }
    
    companion object {
        // Constants for chunk dimensions and mesh building
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 256
        private const val ESTIMATED_VERTICES_CAPACITY = CHUNK_SIZE * CHUNK_SIZE * CHUNK_HEIGHT / 4
        private const val ESTIMATED_INDICES_CAPACITY = CHUNK_SIZE * CHUNK_SIZE * CHUNK_HEIGHT / 2
        private val NUM_THREADS = Runtime.getRuntime().availableProcessors() - 1
    }
}