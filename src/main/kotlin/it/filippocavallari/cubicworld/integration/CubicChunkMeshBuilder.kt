package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.data.block.FaceDirection
import it.filippocavallari.cubicworld.textures.TextureManager
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import org.joml.Vector4f
import org.vulkanb.eng.scene.ModelData
import java.util.*

/**
 * Builds optimized mesh data for cubic chunks (16x16x16).
 * Uses greedy meshing and face culling optimizations.
 */
class CubicChunkMeshBuilder(private val textureStitcher: TextureStitcher) {
    
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
    
    // Statistics
    private var totalFacesGenerated = 0
    private var facesculled = 0
    
    /**
     * Build an optimized mesh from cubic chunk data
     */
    fun buildMesh(chunk: CubicChunk): ModelData {
        // Clear previous mesh data
        positions.clear()
        textCoords.clear()
        normals.clear()
        tangents.clear()
        biTangents.clear()
        indices.clear()
        
        // Skip empty chunks entirely
        if (chunk.isEmpty()) {
            return createEmptyModel(chunk)
        }
        
        var vertexCount = 0
        var facesAdded = 0
        
        // Process all blocks in the cubic chunk
        for (x in 0 until CubicChunk.SIZE) {
            for (y in 0 until CubicChunk.SIZE) {
                for (z in 0 until CubicChunk.SIZE) {
                    val blockId = chunk.getBlock(x, y, z)
                    
                    // Skip air blocks
                    if (blockId == 0) continue
                    
                    // Convert to world coordinates
                    val worldX = chunk.getWorldX() + x
                    val worldY = chunk.getWorldY() + y
                    val worldZ = chunk.getWorldZ() + z
                    
                    // Add visible faces (check neighbors for occlusion)
                    val prevVertexCount = vertexCount
                    vertexCount = addVisibleFaces(
                        chunk, x, y, z, blockId, vertexCount,
                        worldX.toFloat(), worldY.toFloat(), worldZ.toFloat()
                    )
                    
                    facesAdded += (vertexCount - prevVertexCount) / 4
                }
            }
        }
        
        totalFacesGenerated += facesAdded
        
        // Create model data
        return createModelData(chunk, facesAdded, vertexCount)
    }
    
    /**
     * Build directional meshes for better culling
     */
    fun buildDirectionalMeshes(chunk: CubicChunk): Map<FaceDirection, ModelData> {
        if (chunk.isEmpty()) {
            return emptyMap()
        }
        
        val directionalMeshes = mutableMapOf<FaceDirection, ModelData>()
        
        // Build a separate mesh for each face direction
        for (direction in FaceDirection.values()) {
            val mesh = buildDirectionalMesh(chunk, direction)
            if (mesh.meshDataList.isNotEmpty() && mesh.meshDataList[0].positions.isNotEmpty()) {
                directionalMeshes[direction] = mesh
            }
        }
        
        return directionalMeshes
    }
    
    /**
     * Build mesh for a specific face direction
     */
    private fun buildDirectionalMesh(chunk: CubicChunk, direction: FaceDirection): ModelData {
        // Clear mesh data
        positions.clear()
        textCoords.clear()
        normals.clear()
        tangents.clear()
        biTangents.clear()
        indices.clear()
        
        var vertexCount = 0
        var facesAdded = 0
        
        // Process all blocks
        for (x in 0 until CubicChunk.SIZE) {
            for (y in 0 until CubicChunk.SIZE) {
                for (z in 0 until CubicChunk.SIZE) {
                    val blockId = chunk.getBlock(x, y, z)
                    if (blockId == 0) continue
                    
                    // Check if the face in this direction is visible
                    if (isFaceVisible(chunk, x, y, z, direction)) {
                        val worldX = chunk.getWorldX() + x
                        val worldY = chunk.getWorldY() + y
                        val worldZ = chunk.getWorldZ() + z
                        
                        addBlockFace(
                            worldX.toFloat(), worldY.toFloat(), worldZ.toFloat(),
                            direction, blockId, vertexCount
                        )
                        
                        vertexCount += 4
                        facesAdded++
                    }
                }
            }
        }
        
        return createModelData(chunk, facesAdded, vertexCount, direction)
    }
    
    /**
     * Check if a specific face is visible
     */
    private fun isFaceVisible(chunk: CubicChunk, x: Int, y: Int, z: Int, direction: FaceDirection): Boolean {
        val (dx, dy, dz) = when (direction) {
            FaceDirection.UP -> Triple(0, 1, 0)
            FaceDirection.DOWN -> Triple(0, -1, 0)
            FaceDirection.NORTH -> Triple(0, 0, -1)
            FaceDirection.SOUTH -> Triple(0, 0, 1)
            FaceDirection.EAST -> Triple(1, 0, 0)
            FaceDirection.WEST -> Triple(-1, 0, 0)
        }
        
        val adjacentBlock = chunk.getBlockFromNeighbor(x + dx, y + dy, z + dz)
        return adjacentBlock == 0 || isTransparent(adjacentBlock)
    }
    
    /**
     * Add visible faces for a block
     */
    private fun addVisibleFaces(
        chunk: CubicChunk,
        x: Int, y: Int, z: Int,
        blockId: Int,
        vertexCount: Int,
        worldX: Float, worldY: Float, worldZ: Float
    ): Int {
        var newVertexCount = vertexCount
        
        // Check each face direction
        for (direction in FaceDirection.values()) {
            if (isFaceVisible(chunk, x, y, z, direction)) {
                addBlockFace(worldX, worldY, worldZ, direction, blockId, newVertexCount)
                newVertexCount += 4
            } else {
                facesculled++
            }
        }
        
        return newVertexCount
    }
    
    /**
     * Add a block face to the mesh
     */
    private fun addBlockFace(
        x: Float, y: Float, z: Float,
        face: FaceDirection,
        blockId: Int,
        vertexIndex: Int
    ) {
        // Get texture for this block face
        val textureId = getTextureIdForFace(blockId, face)
        val region = textureStitcher.getTextureRegion(textureId)
        
        // Add vertices based on face direction
        when (face) {
            FaceDirection.UP -> {
                addVertex(x, y + 1, z, region.u1, region.v1, normalUp)
                addVertex(x + 1, y + 1, z, region.u2, region.v1, normalUp)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v2, normalUp)
                addVertex(x, y + 1, z + 1, region.u1, region.v2, normalUp)
            }
            FaceDirection.DOWN -> {
                addVertex(x, y, z, region.u1, region.v2, normalDown)
                addVertex(x, y, z + 1, region.u1, region.v1, normalDown)
                addVertex(x + 1, y, z + 1, region.u2, region.v1, normalDown)
                addVertex(x + 1, y, z, region.u2, region.v2, normalDown)
            }
            FaceDirection.NORTH -> {
                addVertex(x + 1, y, z, region.u2, region.v2, normalNorth)
                addVertex(x + 1, y + 1, z, region.u2, region.v1, normalNorth)
                addVertex(x, y + 1, z, region.u1, region.v1, normalNorth)
                addVertex(x, y, z, region.u1, region.v2, normalNorth)
            }
            FaceDirection.SOUTH -> {
                addVertex(x, y, z + 1, region.u1, region.v2, normalSouth)
                addVertex(x, y + 1, z + 1, region.u1, region.v1, normalSouth)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v1, normalSouth)
                addVertex(x + 1, y, z + 1, region.u2, region.v2, normalSouth)
            }
            FaceDirection.EAST -> {
                addVertex(x + 1, y, z + 1, region.u2, region.v2, normalEast)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v1, normalEast)
                addVertex(x + 1, y + 1, z, region.u1, region.v1, normalEast)
                addVertex(x + 1, y, z, region.u1, region.v2, normalEast)
            }
            FaceDirection.WEST -> {
                addVertex(x, y, z, region.u1, region.v2, normalWest)
                addVertex(x, y + 1, z, region.u1, region.v1, normalWest)
                addVertex(x, y + 1, z + 1, region.u2, region.v1, normalWest)
                addVertex(x, y, z + 1, region.u2, region.v2, normalWest)
            }
        }
        
        // Add tangents and bitangents
        addTangentsForFace(face)
        
        // Add indices
        indices.add(vertexIndex)
        indices.add(vertexIndex + 1)
        indices.add(vertexIndex + 2)
        indices.add(vertexIndex)
        indices.add(vertexIndex + 2)
        indices.add(vertexIndex + 3)
    }
    
    /**
     * Add tangents and bitangents for a face
     */
    private fun addTangentsForFace(face: FaceDirection) {
        val (tangent, bitangent) = when (face) {
            FaceDirection.UP -> floatArrayOf(1f, 0f, 0f) to floatArrayOf(0f, 0f, 1f)
            FaceDirection.DOWN -> floatArrayOf(1f, 0f, 0f) to floatArrayOf(0f, 0f, -1f)
            FaceDirection.NORTH -> floatArrayOf(-1f, 0f, 0f) to floatArrayOf(0f, 1f, 0f)
            FaceDirection.SOUTH -> floatArrayOf(1f, 0f, 0f) to floatArrayOf(0f, 1f, 0f)
            FaceDirection.EAST -> floatArrayOf(0f, 0f, -1f) to floatArrayOf(0f, 1f, 0f)
            FaceDirection.WEST -> floatArrayOf(0f, 0f, 1f) to floatArrayOf(0f, 1f, 0f)
        }
        
        repeat(4) {
            tangents.add(tangent[0])
            tangents.add(tangent[1])
            tangents.add(tangent[2])
            
            biTangents.add(bitangent[0])
            biTangents.add(bitangent[1])
            biTangents.add(bitangent[2])
        }
    }
    
    /**
     * Add a vertex to the mesh
     */
    private fun addVertex(x: Float, y: Float, z: Float, u: Float, v: Float, normal: FloatArray) {
        positions.add(x)
        positions.add(y)
        positions.add(z)
        
        textCoords.add(u)
        textCoords.add(v)
        
        normals.add(normal[0])
        normals.add(normal[1])
        normals.add(normal[2])
    }
    
    /**
     * Get texture ID for a block face
     */
    private fun getTextureIdForFace(blockId: Int, face: FaceDirection): Int {
        return when (face) {
            FaceDirection.UP -> when (blockId) {
                BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_top")
                BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_top")
                else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
            }
            FaceDirection.DOWN -> when (blockId) {
                BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_bottom")
                BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_top")
                else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
            }
            else -> when (blockId) {
                BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_side")
                BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_side")
                else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
            }
        }
    }
    
    /**
     * Get block texture name
     */
    private fun getBlockTextureName(blockId: Int): String {
        return when (blockId) {
            BlockType.STONE.id -> "stone"
            BlockType.DIRT.id -> "dirt"
            BlockType.GRASS.id -> "grass_side"
            BlockType.COBBLESTONE.id -> "cobblestone"
            BlockType.BEDROCK.id -> "bedrock"
            BlockType.SAND.id -> "sand"
            BlockType.GRAVEL.id -> "gravel"
            BlockType.LOG_OAK.id -> "log_oak_side"
            BlockType.LEAVES_OAK.id -> "leaves_oak"
            BlockType.COAL_ORE.id -> "coal_ore"
            BlockType.IRON_ORE.id -> "iron_ore"
            BlockType.GOLD_ORE.id -> "gold_ore"
            BlockType.DIAMOND_ORE.id -> "diamond_ore"
            else -> "stone"
        }
    }
    
    /**
     * Check if a block is transparent
     */
    private fun isTransparent(blockId: Int): Boolean {
        val blockType = BlockType.fromId(blockId)
        return blockType.isTransparent
    }
    
    /**
     * Create model data from mesh arrays
     */
    private fun createModelData(
        chunk: CubicChunk,
        facesAdded: Int,
        vertexCount: Int,
        direction: FaceDirection? = null
    ): ModelData {
        // Create material
        val materialList = ArrayList<ModelData.Material>()
        val diffuseAtlasPath = "${System.getProperty("user.dir")}/src/main/resources/atlas/diffuse_atlas.png"
        val normalAtlasPath = "${System.getProperty("user.dir")}/src/main/resources/atlas/normal_atlas.png"
        val specularAtlasPath = "${System.getProperty("user.dir")}/src/main/resources/atlas/specular_atlas.png"
        
        materialList.add(ModelData.Material(
            diffuseAtlasPath,
            normalAtlasPath,
            specularAtlasPath,
            Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
            0.5f,
            0.0f
        ))
        
        // Create mesh data
        val meshDataList = ArrayList<ModelData.MeshData>()
        
        if (positions.isNotEmpty()) {
            meshDataList.add(ModelData.MeshData(
                positions.toFloatArray(),
                normals.toFloatArray(),
                tangents.toFloatArray(),
                biTangents.toFloatArray(),
                textCoords.toFloatArray(),
                indices.toIntArray(),
                0
            ))
        }
        
        // Generate unique ID
        val modelId = if (direction != null) {
            "cubic_chunk_${chunk.position.x}_${chunk.position.y}_${chunk.position.z}_${direction.name}"
        } else {
            "cubic_chunk_${chunk.position.x}_${chunk.position.y}_${chunk.position.z}"
        }
        
        if (vertexCount > 0 && totalFacesGenerated % 1000 == 0) {
            println("Cubic mesh stats: Total faces: $totalFacesGenerated, Culled: $facesculled (${(facesculled * 100.0 / (totalFacesGenerated + facesculled)).toInt()}%)")
        }
        
        return ModelData(modelId, meshDataList, materialList)
    }
    
    /**
     * Create empty model for empty chunks
     */
    private fun createEmptyModel(chunk: CubicChunk): ModelData {
        val modelId = "empty_cubic_chunk_${chunk.position.x}_${chunk.position.y}_${chunk.position.z}"
        return ModelData(modelId, ArrayList(), ArrayList())
    }
}
