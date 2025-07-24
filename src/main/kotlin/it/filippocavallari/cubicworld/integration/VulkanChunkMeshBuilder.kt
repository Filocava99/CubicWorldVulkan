package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.data.block.FaceDirection
import it.filippocavallari.cubicworld.textures.TextureManager
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.chunk.Chunk
import org.joml.Vector4f
import org.vulkanb.eng.scene.ModelData
import java.util.*

/**
 * Builds mesh data for chunks optimized for the Vulkan rendering engine.
 * This class converts the voxel data into vertex data suitable for rendering with Vulkan.
 */
class VulkanChunkMeshBuilder(private val textureStitcher: TextureStitcher) {
    
    init {
        // Initialize the TextureManager with the TextureStitcher
        TextureManager.initialize(textureStitcher)
    }
    
    // Mesh data lists
    private val positions = ArrayList<Float>()
    private val textCoords = ArrayList<Float>()
    private val normals = ArrayList<Float>()
    private val tangents = ArrayList<Float>()
    private val biTangents = ArrayList<Float>()
    private val indices = ArrayList<Int>()
    
    // Face normal vectors
    private val normalUp = floatArrayOf(0.0f, 1.0f, 0.0f)
    private val normalDown = floatArrayOf(0.0f, -1.0f, 0.0f)
    private val normalNorth = floatArrayOf(0.0f, 0.0f, -1.0f)
    private val normalSouth = floatArrayOf(0.0f, 0.0f, 1.0f)
    private val normalEast = floatArrayOf(1.0f, 0.0f, 0.0f)
    private val normalWest = floatArrayOf(-1.0f, 0.0f, 0.0f)
    
    // Constants for mesh limits
    companion object {
        const val MAX_VERTICES = 65536  // More conservative limit to avoid buffer overflow
        const val MAX_INDICES = MAX_VERTICES * 3  // Conservative estimate
        const val VERTICES_PER_FACE = 4
        const val INDICES_PER_FACE = 6
        const val CHUNK_MAX_HEIGHT = 200  // Increased to ensure we don't cut terrain
    }
    
    /**
     * Build a mesh from chunk data
     * 
     * @param chunk The chunk to build the mesh for
     * @return ModelData object ready for Vulkan rendering
     */
    fun buildMesh(chunk: Chunk): ModelData {
        // Clear previous mesh data
        positions.clear()
        textCoords.clear()
        normals.clear()
        tangents.clear()
        biTangents.clear()
        indices.clear()
        
        var vertexCount = 0
        var facesAdded = 0
        var skippedFaces = 0
        val maxYToCheck = 96
        try {
            // First pass: count visible faces to estimate memory needs
            var estimatedFaces = 0
            for (x in 0 until Chunk.SIZE) {
                for (y in 0 until minOf(Chunk.HEIGHT, maxYToCheck)) {
                    for (z in 0 until Chunk.SIZE) {
                        val blockId = chunk.getBlock(x, y, z)
                        if (blockId != 0) {
                            // Count potentially visible faces
                            if (isBlockFaceVisible(chunk, x, y + 1, z)) estimatedFaces++
                            if (isBlockFaceVisible(chunk, x, y - 1, z)) estimatedFaces++
                            if (isBlockFaceVisible(chunk, x, y, z - 1)) estimatedFaces++
                            if (isBlockFaceVisible(chunk, x, y, z + 1)) estimatedFaces++
                            if (isBlockFaceVisible(chunk, x + 1, y, z)) estimatedFaces++
                            if (isBlockFaceVisible(chunk, x - 1, y, z)) estimatedFaces++
                        }
                    }
                }
            }
            
            println("Chunk ${chunk.position.x},${chunk.position.y}: Estimated ${estimatedFaces} visible faces")
            
            // Check if we might exceed limits
            if (estimatedFaces * VERTICES_PER_FACE > MAX_VERTICES) {
                println("WARNING: Chunk ${chunk.position.x},${chunk.position.y} will exceed vertex limit!")
                println("  Estimated faces: $estimatedFaces")
                println("  Estimated vertices: ${estimatedFaces * VERTICES_PER_FACE}")
                println("  Max allowed vertices: $MAX_VERTICES")
                
                // Skip this chunk if it's way too complex
                if (estimatedFaces * VERTICES_PER_FACE > MAX_VERTICES * 1.5) {
                    println("ERROR: Chunk is too complex to render. Returning empty mesh.")
                    return ModelData("chunk_${chunk.position.x}_${chunk.position.y}", 
                                   ArrayList(), ArrayList())
                }
            }
            
            // Find the actual maximum height in this chunk to avoid processing empty air
            var actualMaxHeight = 0
            var totalBlocksFound = 0
            
            // First pass: scan for the highest block to optimize processing
            for (x in 0 until Chunk.SIZE) {
                for (z in 0 until Chunk.SIZE) {
                    for (y in 0 until Chunk.HEIGHT) {
                        if (chunk.getBlock(x, y, z) != 0) {
                            actualMaxHeight = maxOf(actualMaxHeight, y)
                            totalBlocksFound++
                        }
                    }
                }
            }
            
            // Add padding for decorations and safety
            actualMaxHeight = minOf(actualMaxHeight + 32, Chunk.HEIGHT - 1)
            
            println("Chunk ${chunk.position.x},${chunk.position.y}: Found $totalBlocksFound blocks, max height $actualMaxHeight")
            
            // If no blocks found, create a minimal ground plane for debugging
            if (totalBlocksFound == 0) {
                println("WARNING: Chunk has no blocks! This shouldn't happen with proper terrain generation.")
                return ModelData("empty_chunk_${chunk.position.x}_${chunk.position.y}", ArrayList(), ArrayList())
            }
            
            // Process all blocks up to actual terrain height (no artificial limits)
            for (x in 0 until Chunk.SIZE) {
                for (y in 0..actualMaxHeight) {
                    for (z in 0 until Chunk.SIZE) {
                        val blockId = chunk.getBlock(x, y, z)
                        
                        // Skip air blocks
                        if (blockId == 0) continue
                        
                        // Warning for vertex limits but continue processing to avoid gaps
                        if (vertexCount + 24 > MAX_VERTICES) {
                            println("WARNING: Chunk ${chunk.position.x},${chunk.position.y} approaching vertex limit")
                            println("  Current vertices: $vertexCount at block ($x, $y, $z)")
                            println("  Continuing to avoid gaps - implement LOD if this becomes frequent")
                        }
                        
                        // Convert to world coordinates for proper chunk offset
                        val worldX = chunk.getWorldX() + x
                        val worldZ = chunk.getWorldZ() + z
                        
                        // Add faces that are exposed to air or transparent blocks
                        val prevVertexCount = vertexCount
                        vertexCount = addVisibleFaces(chunk, x, y, z, blockId, vertexCount, worldX.toFloat(), y.toFloat(), worldZ.toFloat())
                        
                        // Count faces added
                        facesAdded += (vertexCount - prevVertexCount) / 4
                    }
                    
                    // Continue processing even if approaching limits to avoid gaps
                    // In a production system, implement proper LOD or chunk subdivision here
                }
            }
            
        } catch (e: Exception) {
            println("ERROR during mesh generation for chunk ${chunk.position.x},${chunk.position.y}: ${e.message}")
            e.printStackTrace()
        }
        
        println("Chunk ${chunk.position.x},${chunk.position.y}: Generated ${facesAdded} faces, ${vertexCount} vertices, ${indices.size} indices")
        
        // Validate mesh completeness
        validateChunkMesh(chunk, vertexCount, facesAdded)
        
        // Convert lists to arrays for ModelData
        val posArray = positions.toFloatArray()
        val texCoordsArray = textCoords.toFloatArray()
        val normalsArray = normals.toFloatArray()
        val tangentsArray = tangents.toFloatArray()
        val biTangentsArray = biTangents.toFloatArray()
        val indicesArray = indices.toIntArray()
        
        // Create material list
        val materialList = ArrayList<ModelData.Material>()
        
        // Default material - references texture atlas
        // Ensure paths to atlas files are absolute and correct
        val diffuseAtlasPath = "${System.getProperty("user.dir")}/src/main/resources/atlas/diffuse_atlas.png"  
        val normalAtlasPath = "${System.getProperty("user.dir")}/src/main/resources/atlas/normal_atlas.png"
        val specularAtlasPath = "${System.getProperty("user.dir")}/src/main/resources/atlas/specular_atlas.png"
        
        // Verify files exist
        val diffuseFile = java.io.File(diffuseAtlasPath)
        val normalFile = java.io.File(normalAtlasPath)
        val specularFile = java.io.File(specularAtlasPath)
        
        if (!diffuseFile.exists() || !normalFile.exists() || !specularFile.exists()) {
            println("WARNING: Atlas files missing!")
            println("Diffuse atlas: ${diffuseFile.exists()} at $diffuseAtlasPath")
            println("Normal atlas: ${normalFile.exists()} at $normalAtlasPath")
            println("Specular atlas: ${specularFile.exists()} at $specularAtlasPath")
        }
        
        materialList.add(ModelData.Material(
            diffuseAtlasPath,  // Absolute texture path
            normalAtlasPath,   // Absolute normal map path
            specularAtlasPath, // Absolute specular map path
            Vector4f(1.0f, 1.0f, 1.0f, 1.0f), // Default color
            0.5f,                 // Roughness
            0.0f                  // Metallic factor
        ))
        
        // Create mesh data list
        val meshDataList = ArrayList<ModelData.MeshData>()
        
        // Only add mesh if we have vertices
        if (positions.isNotEmpty()) {
            // Create mesh data with bounds checking
            try {
                meshDataList.add(ModelData.MeshData(
                    posArray,
                    normalsArray,
                    tangentsArray,
                    biTangentsArray,
                    texCoordsArray,
                    indicesArray,
                    0  // Material index
                ))
            } catch (e: Exception) {
                println("ERROR creating MeshData for chunk ${chunk.position.x},${chunk.position.y}: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("WARNING: Chunk ${chunk.position.x},${chunk.position.y} has no visible faces!")
        }
        
        // Generate a unique ID for this mesh
        val modelId = "chunk_${chunk.position.x}_${chunk.position.y}"
        
        // Print debug information about the mesh
        println("Created mesh for chunk: $modelId")
        println("  Chunk grid position: (${chunk.position.x}, ${chunk.position.y})")
        println("  World position: (${chunk.getWorldX()}, ${chunk.getWorldZ()})")
        println("  Vertex count: ${positions.size / 3}")
        println("  Index count: ${indices.size}")
        println("  Triangles: ${indices.size / 3}")
        println("  Mesh data list size: ${meshDataList.size}")
        
        // Return the model data
        return ModelData(modelId, meshDataList, materialList)
    }
    
    /**
     * Add visible faces for a block
     * 
     * @param chunk The chunk
     * @param x Block x position (local to chunk)
     * @param y Block y position
     * @param z Block z position (local to chunk)
     * @param blockId The block type
     * @param vertexCount Current vertex count
     * @param worldX World X position (for rendering)
     * @param worldY World Y position (for rendering)
     * @param worldZ World Z position (for rendering)
     * @return New vertex count
     */
    private fun addVisibleFaces(
        chunk: Chunk, 
        x: Int, 
        y: Int, 
        z: Int, 
        blockId: Int, 
        vertexCount: Int,
        worldX: Float,
        worldY: Float,
        worldZ: Float
    ): Int {
        var newVertexCount = vertexCount
        
        try {
            // Check and add top face
            if (isBlockFaceVisible(chunk, x, y + 1, z)) {
                addBlockFace(
                    worldX, worldY, worldZ,
                    FaceDirection.UP,
                    blockId,
                    newVertexCount
                )
                newVertexCount += 4  // 4 vertices per face
            }
            
            // Check and add bottom face
            if (isBlockFaceVisible(chunk, x, y - 1, z)) {
                addBlockFace(
                    worldX, worldY, worldZ,
                    FaceDirection.DOWN,
                    blockId,
                    newVertexCount
                )
                newVertexCount += 4
            }
            
            // Check and add north face
            if (isBlockFaceVisible(chunk, x, y, z - 1)) {
                addBlockFace(
                    worldX, worldY, worldZ,
                    FaceDirection.NORTH,
                    blockId,
                    newVertexCount
                )
                newVertexCount += 4
            }
            
            // Check and add south face
            if (isBlockFaceVisible(chunk, x, y, z + 1)) {
                addBlockFace(
                    worldX, worldY, worldZ,
                    FaceDirection.SOUTH,
                    blockId,
                    newVertexCount
                )
                newVertexCount += 4
            }
            
            // Check and add east face
            if (isBlockFaceVisible(chunk, x + 1, y, z)) {
                addBlockFace(
                    worldX, worldY, worldZ,
                    FaceDirection.EAST,
                    blockId,
                    newVertexCount
                )
                newVertexCount += 4
            }
            
            // Check and add west face
            if (isBlockFaceVisible(chunk, x - 1, y, z)) {
                addBlockFace(
                    worldX, worldY, worldZ,
                    FaceDirection.WEST,
                    blockId,
                    newVertexCount
                )
                newVertexCount += 4
            }
        } catch (e: Exception) {
            println("ERROR adding faces for block at ($x, $y, $z): ${e.message}")
            e.printStackTrace()
        }
        
        return newVertexCount
    }
    
    /**
     * Check if a block face is visible (not obscured by another block)
     * 
     * @param chunk The chunk
     * @param x Adjacent block x (local to chunk)
     * @param y Adjacent block y
     * @param z Adjacent block z (local to chunk)
     * @return true if face is visible
     */
    private fun isBlockFaceVisible(chunk: Chunk, x: Int, y: Int, z: Int): Boolean {
        // If y is out of bounds, handle appropriately
        if (y < 0) return false  // No blocks below bedrock
        if (y >= Chunk.HEIGHT) return true  // Air above max height
        
        // If position is outside the chunk, get block from neighboring chunk
        if (x < 0 || x >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            // Convert to world coordinates
            val worldX = chunk.getWorldX() + x
            val worldZ = chunk.getWorldZ() + z
            
            // Get the block from the world (may return 0 if chunk not loaded)
            val adjacentBlock = chunk.world?.getBlock(worldX, y, worldZ) ?: 0
            
            // Face is visible if adjacent block is air or transparent
            return adjacentBlock == 0 || isTransparent(adjacentBlock)
        }
        
        // Check if the adjacent block is air or transparent
        val blockType = chunk.getBlock(x, y, z)
        return blockType == 0 || isTransparent(blockType)
    }
    
    /**
     * Validate chunk mesh completeness
     */
    private fun validateChunkMesh(chunk: Chunk, vertexCount: Int, facesAdded: Int) {
        // Check if the chunk might be incomplete
        val expectedBlocks = countNonAirBlocks(chunk)
        val expectedFaces = estimateVisibleFaces(chunk)
        
        if (facesAdded < expectedFaces * 0.8) {
            println("WARNING: Chunk ${chunk.position.x},${chunk.position.y} may be incomplete:")
            println("  Expected ~$expectedFaces faces, got $facesAdded")
            println("  Non-air blocks: $expectedBlocks")
            println("  Vertices generated: $vertexCount")
        }
    }
    
    /**
     * Count non-air blocks in chunk
     */
    private fun countNonAirBlocks(chunk: Chunk): Int {
        var count = 0
        for (x in 0 until Chunk.SIZE) {
            for (y in 0 until Chunk.HEIGHT) {
                for (z in 0 until Chunk.SIZE) {
                    if (chunk.getBlock(x, y, z) != 0) count++
                }
            }
        }
        return count
    }
    
    /**
     * Estimate visible faces for validation
     */
    private fun estimateVisibleFaces(chunk: Chunk): Int {
        var visibleFaces = 0
        for (x in 0 until Chunk.SIZE) {
            for (y in 0 until Chunk.HEIGHT) {
                for (z in 0 until Chunk.SIZE) {
                    val blockId = chunk.getBlock(x, y, z)
                    if (blockId != 0) {
                        // Count potentially visible faces
                        if (isBlockFaceVisible(chunk, x, y + 1, z)) visibleFaces++
                        if (isBlockFaceVisible(chunk, x, y - 1, z)) visibleFaces++
                        if (isBlockFaceVisible(chunk, x, y, z - 1)) visibleFaces++
                        if (isBlockFaceVisible(chunk, x, y, z + 1)) visibleFaces++
                        if (isBlockFaceVisible(chunk, x + 1, y, z)) visibleFaces++
                        if (isBlockFaceVisible(chunk, x - 1, y, z)) visibleFaces++
                    }
                }
            }
        }
        return visibleFaces
    }
    
    /**
     * Check if a block is transparent
     * 
     * @param blockId The block ID
     * @return true if transparent
     */
    private fun isTransparent(blockId: Int): Boolean {
        // Get block type from ID and check transparency
        val blockType = BlockType.fromId(blockId)
        return blockType.isTransparent
    }
    
    /**
     * Add a block face to the mesh
     * 
     * @param x Block x position (world coordinates)
     * @param y Block y position (world coordinates)
     * @param z Block z position (world coordinates)
     * @param face The face direction
     * @param blockId The block type
     * @param vertexIndex Current vertex index
     */
    private fun addBlockFace(
        x: Float, 
        y: Float, 
        z: Float, 
        face: FaceDirection, 
        blockId: Int, 
        vertexIndex: Int
    ) {
        // Get the block type
        val blockType = BlockType.fromId(blockId)
        
        // Map the block ID and face to the correct texture index from TextureManager
        val textureId = when (face) {
            FaceDirection.UP -> {
                // Different blocks have different top textures
                when (blockId) {
                    BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_top")
                    BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_top")
                    else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
                }
            }
            FaceDirection.DOWN -> {
                // Different blocks have different bottom textures
                when (blockId) {
                    BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_bottom")
                    BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_top")
                    else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
                }
            }
            else -> {
                // Side textures can also vary by block
                when (blockId) {
                    BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_side")
                    BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_side")
                    else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
                }
            }
        }
        
        // Get texture region from texture atlas using the managed texture ID
        val region = textureStitcher.getTextureRegion(textureId)
        
        // Get vertex positions and normals based on face direction
        when (face) {
            FaceDirection.UP -> {
                // Top face (Y+) - Fixed UV mapping orientation
                addVertex(x, y + 1, z, region.u1, region.v1, normalUp)
                addVertex(x + 1, y + 1, z, region.u2, region.v1, normalUp)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v2, normalUp)
                addVertex(x, y + 1, z + 1, region.u1, region.v2, normalUp)
            }
            FaceDirection.DOWN -> {
                // Bottom face (Y-) - Fixed UV mapping orientation
                addVertex(x, y, z, region.u1, region.v2, normalDown)
                addVertex(x, y, z + 1, region.u1, region.v1, normalDown)
                addVertex(x + 1, y, z + 1, region.u2, region.v1, normalDown)
                addVertex(x + 1, y, z, region.u2, region.v2, normalDown)
            }
            FaceDirection.NORTH -> {
                // North face (Z-)
                addVertex(x + 1, y, z, region.u2, region.v2, normalNorth)
                addVertex(x + 1, y + 1, z, region.u2, region.v1, normalNorth)
                addVertex(x, y + 1, z, region.u1, region.v1, normalNorth)
                addVertex(x, y, z, region.u1, region.v2, normalNorth)
            }
            FaceDirection.SOUTH -> {
                // South face (Z+)
                addVertex(x, y, z + 1, region.u1, region.v2, normalSouth)
                addVertex(x, y + 1, z + 1, region.u1, region.v1, normalSouth)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v1, normalSouth)
                addVertex(x + 1, y, z + 1, region.u2, region.v2, normalSouth)
            }
            FaceDirection.EAST -> {
                // East face (X+)
                addVertex(x + 1, y, z + 1, region.u2, region.v2, normalEast)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v1, normalEast)
                addVertex(x + 1, y + 1, z, region.u1, region.v1, normalEast)
                addVertex(x + 1, y, z, region.u1, region.v2, normalEast)
            }
            FaceDirection.WEST -> {
                // West face (X-)
                addVertex(x, y, z, region.u1, region.v2, normalWest)
                addVertex(x, y + 1, z, region.u1, region.v1, normalWest)
                addVertex(x, y + 1, z + 1, region.u2, region.v1, normalWest)
                addVertex(x, y, z + 1, region.u2, region.v2, normalWest)
            }
        }
        
        // Add tangents and bitangents based on face
        when (face) {
            FaceDirection.UP, FaceDirection.DOWN -> {
                // For top and bottom faces, tangent along X axis
                val tangent = floatArrayOf(1.0f, 0.0f, 0.0f)
                val bitangent = if (face == FaceDirection.UP) {
                    floatArrayOf(0.0f, 0.0f, 1.0f)
                } else {
                    floatArrayOf(0.0f, 0.0f, -1.0f)
                }
                
                // Add tangent and bitangent for all vertices
                repeat(4) {
                    tangents.add(tangent[0])
                    tangents.add(tangent[1])
                    tangents.add(tangent[2])
                    
                    biTangents.add(bitangent[0])
                    biTangents.add(bitangent[1])
                    biTangents.add(bitangent[2])
                }
            }
            FaceDirection.NORTH, FaceDirection.SOUTH -> {
                // For north and south faces, tangent along X axis
                val tangent = floatArrayOf(if (face == FaceDirection.NORTH) -1.0f else 1.0f, 0.0f, 0.0f)
                val bitangent = floatArrayOf(0.0f, 1.0f, 0.0f)
                
                // Add tangent and bitangent for all vertices
                repeat(4) {
                    tangents.add(tangent[0])
                    tangents.add(tangent[1])
                    tangents.add(tangent[2])
                    
                    biTangents.add(bitangent[0])
                    biTangents.add(bitangent[1])
                    biTangents.add(bitangent[2])
                }
            }
            FaceDirection.EAST, FaceDirection.WEST -> {
                // For east and west faces, tangent along Z axis
                val tangent = floatArrayOf(0.0f, 0.0f, if (face == FaceDirection.EAST) -1.0f else 1.0f)
                val bitangent = floatArrayOf(0.0f, 1.0f, 0.0f)
                
                // Add tangent and bitangent for all vertices
                repeat(4) {
                    tangents.add(tangent[0])
                    tangents.add(tangent[1])
                    tangents.add(tangent[2])
                    
                    biTangents.add(bitangent[0])
                    biTangents.add(bitangent[1])
                    biTangents.add(bitangent[2])
                }
            }
        }
        
        // Add indices for triangles (ensure correct winding order)
        indices.add(vertexIndex)
        indices.add(vertexIndex + 1)
        indices.add(vertexIndex + 2)
        
        indices.add(vertexIndex)
        indices.add(vertexIndex + 2)
        indices.add(vertexIndex + 3)
    }
    
    /**
     * Get the block texture name based on block ID
     */
    private fun getBlockTextureName(blockId: Int): String {
        return when (blockId) {
            BlockType.STONE.id -> "stone"
            BlockType.DIRT.id -> "dirt"
            BlockType.GRASS.id -> "grass_side" // Default side texture
            BlockType.COBBLESTONE.id -> "cobblestone"
            BlockType.BEDROCK.id -> "bedrock"
            BlockType.SAND.id -> "sand"
            BlockType.GRAVEL.id -> "gravel"
            BlockType.LOG_OAK.id -> "log_oak_side" // Default side texture
            BlockType.LEAVES_OAK.id -> "leaves_oak"
            BlockType.COAL_ORE.id -> "coal_ore"
            BlockType.IRON_ORE.id -> "iron_ore"
            BlockType.GOLD_ORE.id -> "gold_ore"
            BlockType.DIAMOND_ORE.id -> "diamond_ore"
            BlockType.REDSTONE_ORE.id -> "redstone_ore"
            BlockType.LAPIS_ORE.id -> "lapis_ore"
            BlockType.WATER.id -> "water"
            else -> "air" // Fallback for unknown blocks
        }
    }
    
    /**
     * Add a vertex to the mesh
     * 
     * @param x Vertex x position
     * @param y Vertex y position
     * @param z Vertex z position
     * @param u Texture U coordinate
     * @param v Texture V coordinate
     * @param normal Normal vector
     */
    private fun addVertex(x: Float, y: Float, z: Float, u: Float, v: Float, normal: FloatArray) {
        // Add position
        positions.add(x)
        positions.add(y)
        positions.add(z)
        
        // Add texture coordinates
        textCoords.add(u)
        textCoords.add(v)
        
        // Add normal
        normals.add(normal[0])
        normals.add(normal[1])
        normals.add(normal[2])
    }
}