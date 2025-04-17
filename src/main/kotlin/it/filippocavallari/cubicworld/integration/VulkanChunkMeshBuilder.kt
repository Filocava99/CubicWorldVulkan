package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.data.block.FaceDirection
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
        
        // Iterate through all blocks in the chunk
        for (x in 0 until Chunk.SIZE) {
            for (y in 0 until Chunk.HEIGHT) {
                for (z in 0 until Chunk.SIZE) {
                    val blockId = chunk.getBlock(x, y, z)
                    
                    // Skip air blocks
                    if (blockId == 0) continue
                    
                    // Add faces that are exposed to air or transparent blocks
                    vertexCount = addVisibleFaces(chunk, x, y, z, blockId, vertexCount)
                }
            }
        }
        
        // Convert lists to arrays for ModelData
        val posArray = FloatArray(positions.size)
        positions.forEachIndexed { index, value -> posArray[index] = value }
        
        val texCoordsArray = FloatArray(textCoords.size)
        textCoords.forEachIndexed { index, value -> texCoordsArray[index] = value }
        
        val normalsArray = FloatArray(normals.size)
        normals.forEachIndexed { index, value -> normalsArray[index] = value }
        
        val tangentsArray = FloatArray(tangents.size)
        tangents.forEachIndexed { index, value -> tangentsArray[index] = value }
        
        val biTangentsArray = FloatArray(biTangents.size)
        biTangents.forEachIndexed { index, value -> biTangentsArray[index] = value }
        
        val indicesArray = IntArray(indices.size)
        indices.forEachIndexed { index, value -> indicesArray[index] = value }
        
        // Create material list
        val materialList = ArrayList<ModelData.Material>()
        
        // Default material - references texture atlas
        materialList.add(ModelData.Material(
            "src/main/resources/atlas/diffuse_atlas.png",  // Texture path
            "src/main/resources/atlas/normal_atlas.png",   // Normal map path
            "src/main/resources/atlas/specular_atlas.png", // Specular map path
            Vector4f(1.0f, 1.0f, 1.0f, 1.0f), // Default color
            0.5f,                 // Roughness
            0.0f                  // Metallic factor
        ))
        
        // Create mesh data list
        val meshDataList = ArrayList<ModelData.MeshData>()
        
        // Only add mesh if we have vertices
        if (positions.isNotEmpty()) {
            meshDataList.add(ModelData.MeshData(
                posArray,
                normalsArray,
                tangentsArray,
                biTangentsArray,
                texCoordsArray,
                indicesArray,
                0  // Material index
            ))
        }
        
        // Generate a unique ID for this mesh
        val modelId = "chunk_${chunk.position.x}_${chunk.position.y}"
        println("Creating model with ID: $modelId")
        
        // Print debug information about the mesh
        println("Created mesh for chunk: $modelId")
        println("  Vertex count: ${positions.size / 3}")
        println("  Index count: ${indices.size}")
        println("  Mesh data list size: ${meshDataList.size}")
        
        // Return the model data
        return ModelData(modelId, meshDataList, materialList)
    }
    
    /**
     * Add visible faces for a block
     * 
     * @param chunk The chunk
     * @param x Block x position
     * @param y Block y position
     * @param z Block z position
     * @param blockId The block type
     * @param vertexCount Current vertex count
     * @return New vertex count
     */
    private fun addVisibleFaces(
        chunk: Chunk, 
        x: Int, 
        y: Int, 
        z: Int, 
        blockId: Int, 
        vertexCount: Int
    ): Int {
        var newVertexCount = vertexCount
        
        // Check and add top face
        if (isBlockFaceVisible(chunk, x, y + 1, z)) {
            addBlockFace(
                x.toFloat(), y.toFloat(), z.toFloat(),
                FaceDirection.UP,
                blockId,
                newVertexCount
            )
            newVertexCount += 4  // 4 vertices per face
        }
        
        // Check and add bottom face
        if (isBlockFaceVisible(chunk, x, y - 1, z)) {
            addBlockFace(
                x.toFloat(), y.toFloat(), z.toFloat(),
                FaceDirection.DOWN,
                blockId,
                newVertexCount
            )
            newVertexCount += 4
        }
        
        // Check and add north face
        if (isBlockFaceVisible(chunk, x, y, z - 1)) {
            addBlockFace(
                x.toFloat(), y.toFloat(), z.toFloat(),
                FaceDirection.NORTH,
                blockId,
                newVertexCount
            )
            newVertexCount += 4
        }
        
        // Check and add south face
        if (isBlockFaceVisible(chunk, x, y, z + 1)) {
            addBlockFace(
                x.toFloat(), y.toFloat(), z.toFloat(),
                FaceDirection.SOUTH,
                blockId,
                newVertexCount
            )
            newVertexCount += 4
        }
        
        // Check and add east face
        if (isBlockFaceVisible(chunk, x + 1, y, z)) {
            addBlockFace(
                x.toFloat(), y.toFloat(), z.toFloat(),
                FaceDirection.EAST,
                blockId,
                newVertexCount
            )
            newVertexCount += 4
        }
        
        // Check and add west face
        if (isBlockFaceVisible(chunk, x - 1, y, z)) {
            addBlockFace(
                x.toFloat(), y.toFloat(), z.toFloat(),
                FaceDirection.WEST,
                blockId,
                newVertexCount
            )
            newVertexCount += 4
        }
        
        return newVertexCount
    }
    
    /**
     * Check if a block face is visible (not obscured by another block)
     * 
     * @param chunk The chunk
     * @param x Adjacent block x
     * @param y Adjacent block y
     * @param z Adjacent block z
     * @return true if face is visible
     */
    private fun isBlockFaceVisible(chunk: Chunk, x: Int, y: Int, z: Int): Boolean {
        // If position is outside the chunk, get block from neighboring chunk
        if (x < 0 || x >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            // Convert to world coordinates
            val worldX = chunk.getWorldX() + x
            val worldZ = chunk.getWorldZ() + z
            
            // Get the block from the world (may return 0 if chunk not loaded)
            val blockType = chunk.world?.getBlockType(worldX, y, worldZ) ?: 0
            return blockType == 0 || isTransparent(blockType)
        }
        
        // If y is out of bounds, return visible for y < 0 and invisible for y > max
        if (y < 0) return false  // No blocks below bedrock
        if (y >= Chunk.HEIGHT) return true  // Air above max height
        
        // Check if the adjacent block is air or transparent
        return chunk.getBlock(x, y, z) == 0 || isTransparent(chunk.getBlock(x, y, z))
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
     * @param x Block x position
     * @param y Block y position
     * @param z Block z position
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
        
        // Get texture ID for this face
        val textureId = when (face) {
            FaceDirection.UP -> blockType.topTextureIndex
            FaceDirection.DOWN -> blockType.bottomTextureIndex
            else -> blockType.sideTextureIndex
        }
        
        // Get texture region from texture atlas
        val region = textureStitcher.getTextureRegion(textureId)
        
        // Get vertex positions and normals based on face direction
        when (face) {
            FaceDirection.UP -> {
                // Top face (Y+)
                addVertex(x, y + 1, z, region.u1, region.v2, normalUp)
                addVertex(x + 1, y + 1, z, region.u2, region.v2, normalUp)
                addVertex(x + 1, y + 1, z + 1, region.u2, region.v1, normalUp)
                addVertex(x, y + 1, z + 1, region.u1, region.v1, normalUp)
            }
            FaceDirection.DOWN -> {
                // Bottom face (Y-)
                addVertex(x, y, z, region.u1, region.v1, normalDown)
                addVertex(x, y, z + 1, region.u1, region.v2, normalDown)
                addVertex(x + 1, y, z + 1, region.u2, region.v2, normalDown)
                addVertex(x + 1, y, z, region.u2, region.v1, normalDown)
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
        
        // Add indices for triangles
        indices.add(vertexIndex)
        indices.add(vertexIndex + 1)
        indices.add(vertexIndex + 2)
        
        indices.add(vertexIndex)
        indices.add(vertexIndex + 2)
        indices.add(vertexIndex + 3)
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