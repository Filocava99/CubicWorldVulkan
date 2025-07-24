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
 * Builds separate meshes for each face direction of a chunk.
 * This allows for directional frustum culling where we can skip rendering
 * faces that are facing away from the camera.
 */
class DirectionalVulkanChunkMeshBuilder(private val textureStitcher: TextureStitcher) {
    
    // Data class to hold mesh data for a specific direction
    data class DirectionalMeshData(
        val positions: ArrayList<Float> = ArrayList(),
        val textCoords: ArrayList<Float> = ArrayList(),
        val normals: ArrayList<Float> = ArrayList(),
        val tangents: ArrayList<Float> = ArrayList(),
        val biTangents: ArrayList<Float> = ArrayList(),
        val indices: ArrayList<Int> = ArrayList(),
        var vertexCount: Int = 0
    )
    
    // Face normal vectors
    private val normalUp = floatArrayOf(0.0f, 1.0f, 0.0f)
    private val normalDown = floatArrayOf(0.0f, -1.0f, 0.0f)
    private val normalNorth = floatArrayOf(0.0f, 0.0f, -1.0f)
    private val normalSouth = floatArrayOf(0.0f, 0.0f, 1.0f)
    private val normalEast = floatArrayOf(1.0f, 0.0f, 0.0f)
    private val normalWest = floatArrayOf(-1.0f, 0.0f, 0.0f)
    
    companion object {
        const val MAX_VERTICES_PER_DIRECTION = 16384  // Limit per direction mesh
        const val VERTICES_PER_FACE = 4
        const val INDICES_PER_FACE = 6
    }
    
    /**
     * Build separate meshes for each face direction
     * 
     * @param chunk The chunk to build meshes for
     * @return Map of FaceDirection to ModelData
     */
    fun buildDirectionalMeshes(chunk: Chunk): Map<FaceDirection, ModelData> {
        // Initialize mesh data for each direction
        val meshDataMap = mapOf(
            FaceDirection.UP to DirectionalMeshData(),
            FaceDirection.DOWN to DirectionalMeshData(),
            FaceDirection.NORTH to DirectionalMeshData(),
            FaceDirection.SOUTH to DirectionalMeshData(),
            FaceDirection.EAST to DirectionalMeshData(),
            FaceDirection.WEST to DirectionalMeshData()
        )
        
        // Find the actual maximum height in this chunk
        var actualMaxHeight = 0
        var totalBlocksFound = 0
        
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
        
        // Add padding for decorations
        actualMaxHeight = minOf(actualMaxHeight + 32, Chunk.HEIGHT - 1)
        
        println("Directional mesh building for chunk ${chunk.position.x},${chunk.position.y}: Found $totalBlocksFound blocks, max height $actualMaxHeight")
        
        // Process all blocks
        for (x in 0 until Chunk.SIZE) {
            for (y in 0..actualMaxHeight) {
                for (z in 0 until Chunk.SIZE) {
                    val blockId = chunk.getBlock(x, y, z)
                    
                    // Skip air blocks
                    if (blockId == 0) continue
                    
                    // Convert to world coordinates
                    val worldX = chunk.getWorldX() + x
                    val worldZ = chunk.getWorldZ() + z
                    
                    // Add visible faces to appropriate directional meshes
                    addVisibleFacesToDirectionalMeshes(
                        chunk, x, y, z, blockId, 
                        worldX.toFloat(), y.toFloat(), worldZ.toFloat(),
                        meshDataMap
                    )
                }
            }
        }
        
        // Convert mesh data to ModelData for each direction
        val result = mutableMapOf<FaceDirection, ModelData>()
        
        for ((direction, meshData) in meshDataMap) {
            if (meshData.positions.isEmpty()) {
                // Skip empty meshes
                continue
            }
            
            println("Direction $direction: ${meshData.vertexCount} vertices, ${meshData.indices.size} indices")
            
            // Convert to arrays
            val posArray = meshData.positions.toFloatArray()
            val texCoordsArray = meshData.textCoords.toFloatArray()
            val normalsArray = meshData.normals.toFloatArray()
            val tangentsArray = meshData.tangents.toFloatArray()
            val biTangentsArray = meshData.biTangents.toFloatArray()
            val indicesArray = meshData.indices.toIntArray()
            
            // Create material list (same for all directions)
            val materialList = createMaterialList()
            
            // Create mesh data
            val meshDataList = ArrayList<ModelData.MeshData>()
            meshDataList.add(ModelData.MeshData(
                posArray,
                normalsArray,
                tangentsArray,
                biTangentsArray,
                texCoordsArray,
                indicesArray,
                0  // Material index
            ))
            
            // Generate unique ID for this directional mesh
            val modelId = "chunk_${chunk.position.x}_${chunk.position.y}_${direction.name.lowercase()}"
            
            // Create ModelData
            result[direction] = ModelData(modelId, meshDataList, materialList)
        }
        
        println("Created ${result.size} directional meshes for chunk ${chunk.position.x},${chunk.position.y}")
        
        return result
    }
    
    /**
     * Add visible faces to the appropriate directional meshes
     */
    private fun addVisibleFacesToDirectionalMeshes(
        chunk: Chunk,
        x: Int, y: Int, z: Int,
        blockId: Int,
        worldX: Float, worldY: Float, worldZ: Float,
        meshDataMap: Map<FaceDirection, DirectionalMeshData>
    ) {
        // Check and add top face
        if (isBlockFaceVisible(chunk, x, y + 1, z)) {
            addBlockFaceToMesh(
                worldX, worldY, worldZ,
                FaceDirection.UP,
                blockId,
                meshDataMap[FaceDirection.UP]!!
            )
        }
        
        // Check and add bottom face
        if (isBlockFaceVisible(chunk, x, y - 1, z)) {
            addBlockFaceToMesh(
                worldX, worldY, worldZ,
                FaceDirection.DOWN,
                blockId,
                meshDataMap[FaceDirection.DOWN]!!
            )
        }
        
        // Check and add north face
        if (isBlockFaceVisible(chunk, x, y, z - 1)) {
            addBlockFaceToMesh(
                worldX, worldY, worldZ,
                FaceDirection.NORTH,
                blockId,
                meshDataMap[FaceDirection.NORTH]!!
            )
        }
        
        // Check and add south face
        if (isBlockFaceVisible(chunk, x, y, z + 1)) {
            addBlockFaceToMesh(
                worldX, worldY, worldZ,
                FaceDirection.SOUTH,
                blockId,
                meshDataMap[FaceDirection.SOUTH]!!
            )
        }
        
        // Check and add east face
        if (isBlockFaceVisible(chunk, x + 1, y, z)) {
            addBlockFaceToMesh(
                worldX, worldY, worldZ,
                FaceDirection.EAST,
                blockId,
                meshDataMap[FaceDirection.EAST]!!
            )
        }
        
        // Check and add west face
        if (isBlockFaceVisible(chunk, x - 1, y, z)) {
            addBlockFaceToMesh(
                worldX, worldY, worldZ,
                FaceDirection.WEST,
                blockId,
                meshDataMap[FaceDirection.WEST]!!
            )
        }
    }
    
    /**
     * Check if a block face is visible
     */
    private fun isBlockFaceVisible(chunk: Chunk, x: Int, y: Int, z: Int): Boolean {
        // If y is out of bounds, handle appropriately
        if (y < 0) return false
        if (y >= Chunk.HEIGHT) return true
        
        // If position is outside the chunk, get block from neighboring chunk
        if (x < 0 || x >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            val worldX = chunk.getWorldX() + x
            val worldZ = chunk.getWorldZ() + z
            val adjacentBlock = chunk.world?.getBlock(worldX, y, worldZ) ?: 0
            return adjacentBlock == 0 || isTransparent(adjacentBlock)
        }
        
        // Check if the adjacent block is air or transparent
        val blockType = chunk.getBlock(x, y, z)
        return blockType == 0 || isTransparent(blockType)
    }
    
    /**
     * Check if a block is transparent
     */
    private fun isTransparent(blockId: Int): Boolean {
        val blockType = BlockType.fromId(blockId)
        return blockType.isTransparent
    }
    
    /**
     * Add a block face to the specific directional mesh
     */
    private fun addBlockFaceToMesh(
        x: Float, y: Float, z: Float,
        face: FaceDirection,
        blockId: Int,
        meshData: DirectionalMeshData
    ) {
        // Check vertex limit for this direction
        if (meshData.vertexCount + VERTICES_PER_FACE > MAX_VERTICES_PER_DIRECTION) {
            println("WARNING: Direction ${face.name} mesh approaching vertex limit")
            return
        }
        
        // Get texture for this face
        val textureId = getTextureIdForFace(blockId, face)
        val region = textureStitcher.getTextureRegion(textureId)
        
        // Current vertex index for this mesh
        val vertexIndex = meshData.vertexCount
        
        // Add vertices based on face direction
        when (face) {
            FaceDirection.UP -> {
                addVertex(meshData, x, y + 1, z, region.u1, region.v1, normalUp)
                addVertex(meshData, x + 1, y + 1, z, region.u2, region.v1, normalUp)
                addVertex(meshData, x + 1, y + 1, z + 1, region.u2, region.v2, normalUp)
                addVertex(meshData, x, y + 1, z + 1, region.u1, region.v2, normalUp)
                addTangentsForFace(meshData, face)
            }
            FaceDirection.DOWN -> {
                addVertex(meshData, x, y, z, region.u1, region.v2, normalDown)
                addVertex(meshData, x, y, z + 1, region.u1, region.v1, normalDown)
                addVertex(meshData, x + 1, y, z + 1, region.u2, region.v1, normalDown)
                addVertex(meshData, x + 1, y, z, region.u2, region.v2, normalDown)
                addTangentsForFace(meshData, face)
            }
            FaceDirection.NORTH -> {
                addVertex(meshData, x + 1, y, z, region.u2, region.v2, normalNorth)
                addVertex(meshData, x + 1, y + 1, z, region.u2, region.v1, normalNorth)
                addVertex(meshData, x, y + 1, z, region.u1, region.v1, normalNorth)
                addVertex(meshData, x, y, z, region.u1, region.v2, normalNorth)
                addTangentsForFace(meshData, face)
            }
            FaceDirection.SOUTH -> {
                addVertex(meshData, x, y, z + 1, region.u1, region.v2, normalSouth)
                addVertex(meshData, x, y + 1, z + 1, region.u1, region.v1, normalSouth)
                addVertex(meshData, x + 1, y + 1, z + 1, region.u2, region.v1, normalSouth)
                addVertex(meshData, x + 1, y, z + 1, region.u2, region.v2, normalSouth)
                addTangentsForFace(meshData, face)
            }
            FaceDirection.EAST -> {
                addVertex(meshData, x + 1, y, z + 1, region.u2, region.v2, normalEast)
                addVertex(meshData, x + 1, y + 1, z + 1, region.u2, region.v1, normalEast)
                addVertex(meshData, x + 1, y + 1, z, region.u1, region.v1, normalEast)
                addVertex(meshData, x + 1, y, z, region.u1, region.v2, normalEast)
                addTangentsForFace(meshData, face)
            }
            FaceDirection.WEST -> {
                addVertex(meshData, x, y, z, region.u1, region.v2, normalWest)
                addVertex(meshData, x, y + 1, z, region.u1, region.v1, normalWest)
                addVertex(meshData, x, y + 1, z + 1, region.u2, region.v1, normalWest)
                addVertex(meshData, x, y, z + 1, region.u2, region.v2, normalWest)
                addTangentsForFace(meshData, face)
            }
        }
        
        // Add indices
        meshData.indices.add(vertexIndex)
        meshData.indices.add(vertexIndex + 1)
        meshData.indices.add(vertexIndex + 2)
        
        meshData.indices.add(vertexIndex)
        meshData.indices.add(vertexIndex + 2)
        meshData.indices.add(vertexIndex + 3)
        
        // Update vertex count
        meshData.vertexCount += 4
    }
    
    /**
     * Add a vertex to the mesh data
     */
    private fun addVertex(
        meshData: DirectionalMeshData,
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        normal: FloatArray
    ) {
        // Add position
        meshData.positions.add(x)
        meshData.positions.add(y)
        meshData.positions.add(z)
        
        // Add texture coordinates
        meshData.textCoords.add(u)
        meshData.textCoords.add(v)
        
        // Add normal
        meshData.normals.add(normal[0])
        meshData.normals.add(normal[1])
        meshData.normals.add(normal[2])
    }
    
    /**
     * Add tangents and bitangents for a face
     */
    private fun addTangentsForFace(meshData: DirectionalMeshData, face: FaceDirection) {
        when (face) {
            FaceDirection.UP, FaceDirection.DOWN -> {
                val tangent = floatArrayOf(1.0f, 0.0f, 0.0f)
                val bitangent = if (face == FaceDirection.UP) {
                    floatArrayOf(0.0f, 0.0f, 1.0f)
                } else {
                    floatArrayOf(0.0f, 0.0f, -1.0f)
                }
                
                repeat(4) {
                    meshData.tangents.add(tangent[0])
                    meshData.tangents.add(tangent[1])
                    meshData.tangents.add(tangent[2])
                    
                    meshData.biTangents.add(bitangent[0])
                    meshData.biTangents.add(bitangent[1])
                    meshData.biTangents.add(bitangent[2])
                }
            }
            FaceDirection.NORTH, FaceDirection.SOUTH -> {
                val tangent = floatArrayOf(if (face == FaceDirection.NORTH) -1.0f else 1.0f, 0.0f, 0.0f)
                val bitangent = floatArrayOf(0.0f, 1.0f, 0.0f)
                
                repeat(4) {
                    meshData.tangents.add(tangent[0])
                    meshData.tangents.add(tangent[1])
                    meshData.tangents.add(tangent[2])
                    
                    meshData.biTangents.add(bitangent[0])
                    meshData.biTangents.add(bitangent[1])
                    meshData.biTangents.add(bitangent[2])
                }
            }
            FaceDirection.EAST, FaceDirection.WEST -> {
                val tangent = floatArrayOf(0.0f, 0.0f, if (face == FaceDirection.EAST) -1.0f else 1.0f)
                val bitangent = floatArrayOf(0.0f, 1.0f, 0.0f)
                
                repeat(4) {
                    meshData.tangents.add(tangent[0])
                    meshData.tangents.add(tangent[1])
                    meshData.tangents.add(tangent[2])
                    
                    meshData.biTangents.add(bitangent[0])
                    meshData.biTangents.add(bitangent[1])
                    meshData.biTangents.add(bitangent[2])
                }
            }
        }
    }
    
    /**
     * Get texture ID for a specific block face
     */
    private fun getTextureIdForFace(blockId: Int, face: FaceDirection): Int {
        return when (face) {
            FaceDirection.UP -> {
                when (blockId) {
                    BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_top")
                    BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_top")
                    else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
                }
            }
            FaceDirection.DOWN -> {
                when (blockId) {
                    BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_bottom")
                    BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_top")
                    else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
                }
            }
            else -> {
                when (blockId) {
                    BlockType.GRASS.id -> TextureManager.getTextureIndex("grass_side")
                    BlockType.LOG_OAK.id -> TextureManager.getTextureIndex("log_oak_side")
                    else -> TextureManager.getTextureIndex(getBlockTextureName(blockId))
                }
            }
        }
    }
    
    /**
     * Get the block texture name based on block ID
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
            BlockType.REDSTONE_ORE.id -> "redstone_ore"
            BlockType.LAPIS_ORE.id -> "lapis_ore"
            BlockType.WATER.id -> "water"
            else -> "air" // Fallback for unknown blocks
        }
    }
    
    /**
     * Create material list for all meshes
     */
    private fun createMaterialList(): ArrayList<ModelData.Material> {
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
        
        return materialList
    }
}
