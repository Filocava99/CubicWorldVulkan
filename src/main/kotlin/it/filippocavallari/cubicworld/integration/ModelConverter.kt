package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.models.Model
import it.filippocavallari.cubicworld.textures.TextureStitcher
import org.joml.Vector4f
import org.vulkanb.eng.scene.ModelData
import java.util.ArrayList

/**
 * Converts between Kotlin model formats and Vulkan engine model formats.
 * This class extends the functionality of VulkanModelConverter to work 
 * directly with the Vulkan engine's model format.
 */
object ModelConverter {
    
    /**
     * Convert a Kotlin Model to Vulkan ModelData
     *
     * @param model The model to convert
     * @param textureStitcher The texture stitcher for texture UV mapping
     * @return The converted ModelData
     */
    fun convertToVulkanModelData(model: Model, textureStitcher: TextureStitcher): ModelData {
        // Lists to store the mesh data
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val textCoords = ArrayList<Float>()
        val indices = ArrayList<Int>()
        val tangents = ArrayList<Float>()  // Required by ModelData
        val biTangents = ArrayList<Float>() // Required by ModelData
        
        // Process each element (cube) in the model
        for (element in model.elements) {
            processElement(element, textureStitcher, positions, textCoords, normals, tangents, biTangents, indices, model)
        }
        
        // Convert to arrays
        val positionsArray = FloatArray(positions.size)
        positions.forEachIndexed { index, value -> positionsArray[index] = value }
        
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
        
        // Create material data
        val materialList = ArrayList<ModelData.Material>()
        
        // Add a default material
        materialList.add(ModelData.Material(
            "src/main/resources/atlas/diffuse_atlas.png",  // Texture path
            "src/main/resources/atlas/normal_atlas.png",   // Normal map path
            "src/main/resources/atlas/specular_atlas.png", // Specular map path
            Vector4f(1.0f, 1.0f, 1.0f, 1.0f), // Diffuse color
            0.5f,                 // Roughness factor
            0.0f                  // Metallic factor
        ))
        
        // Create mesh data
        val meshDataList = ArrayList<ModelData.MeshData>()
        meshDataList.add(ModelData.MeshData(
            positionsArray,
            normalsArray,
            tangentsArray,
            biTangentsArray,
            texCoordsArray,
            indicesArray,
            0  // Material index (using our default material)
        ))
        
        // Create and return ModelData
        return ModelData(model.id, meshDataList, materialList)
    }
    
    /**
     * Process a model element (cube) and extract its vertex data
     */
    private fun processElement(
        element: it.filippocavallari.cubicworld.models.Element,
        textureStitcher: TextureStitcher,
        positions: MutableList<Float>,
        textCoords: MutableList<Float>,
        normals: MutableList<Float>,
        tangents: MutableList<Float>,
        biTangents: MutableList<Float>,
        indices: MutableList<Int>,
        model: Model  // Added model parameter
    ) {
        // The start index for this element's vertices
        val startIndex = positions.size / 3
        
        // Get the cube dimensions and position
        val fromX = element.from[0] / 16.0f
        val fromY = element.from[1] / 16.0f
        val fromZ = element.from[2] / 16.0f
        
        val toX = element.to[0] / 16.0f
        val toY = element.to[1] / 16.0f
        val toZ = element.to[2] / 16.0f
        
        // Add vertices for each face of the cube
        if (element.faces.containsKey("north")) {
            val face = element.faces["north"]!!
            addFace(
                fromX, fromY, fromZ, 
                toX - fromX, 0.0f, 0.0f,
                0.0f, toY - fromY, 0.0f,
                0.0f, 0.0f, -1.0f, 
                face, 
                textureStitcher,
                positions, textCoords, normals, tangents, biTangents,
                model
            )
        }
        
        if (element.faces.containsKey("south")) {
            val face = element.faces["south"]!!
            addFace(
                fromX, fromY, toZ, 
                toX - fromX, 0.0f, 0.0f,
                0.0f, toY - fromY, 0.0f,
                0.0f, 0.0f, 1.0f, 
                face, 
                textureStitcher,
                positions, textCoords, normals, tangents, biTangents,
                model
            )
        }
        
        if (element.faces.containsKey("east")) {
            val face = element.faces["east"]!!
            addFace(
                toX, fromY, fromZ, 
                0.0f, 0.0f, toZ - fromZ,
                0.0f, toY - fromY, 0.0f,
                1.0f, 0.0f, 0.0f, 
                face, 
                textureStitcher,
                positions, textCoords, normals, tangents, biTangents,
                model
            )
        }
        
        if (element.faces.containsKey("west")) {
            val face = element.faces["west"]!!
            addFace(
                fromX, fromY, fromZ, 
                0.0f, 0.0f, toZ - fromZ,
                0.0f, toY - fromY, 0.0f,
                -1.0f, 0.0f, 0.0f, 
                face, 
                textureStitcher,
                positions, textCoords, normals, tangents, biTangents,
                model
            )
        }
        
        if (element.faces.containsKey("up")) {
            val face = element.faces["up"]!!
            addFace(
                fromX, toY, fromZ, 
                toX - fromX, 0.0f, 0.0f,
                0.0f, 0.0f, toZ - fromZ,
                0.0f, 1.0f, 0.0f, 
                face, 
                textureStitcher,
                positions, textCoords, normals, tangents, biTangents,
                model
            )
        }
        
        if (element.faces.containsKey("down")) {
            val face = element.faces["down"]!!
            addFace(
                fromX, fromY, fromZ, 
                toX - fromX, 0.0f, 0.0f,
                0.0f, 0.0f, toZ - fromZ,
                0.0f, -1.0f, 0.0f, 
                face, 
                textureStitcher,
                positions, textCoords, normals, tangents, biTangents,
                model
            )
        }
        
        // Add indices for all faces
        // Each face has 4 vertices, so we need 6 indices to form 2 triangles
        val currentIndex = positions.size / 3
        for (i in startIndex until currentIndex step 4) {
            // First triangle
            indices.add(i)
            indices.add(i + 1)
            indices.add(i + 2)
            
            // Second triangle
            indices.add(i)
            indices.add(i + 2)
            indices.add(i + 3)
        }
    }
    
    /**
     * Add a face to the vertex lists
     */
    private fun addFace(
        x: Float, y: Float, z: Float,
        sizeX: Float, sizeY: Float, sizeZ: Float,
        upX: Float, upY: Float, upZ: Float,
        normalX: Float, normalY: Float, normalZ: Float,
        face: it.filippocavallari.cubicworld.models.Face,
        textureStitcher: TextureStitcher,
        positions: MutableList<Float>,
        textCoords: MutableList<Float>,
        normals: MutableList<Float>,
        tangents: MutableList<Float>,
        biTangents: MutableList<Float>,
        model: Model  // Added model parameter
    ) {
        // Calculate the four corners of the face
        // Bottom-left
        positions.add(x)
        positions.add(y)
        positions.add(z)
        
        // Bottom-right
        positions.add(x + sizeX)
        positions.add(y + sizeY)
        positions.add(z + sizeZ)
        
        // Top-right
        positions.add(x + sizeX + upX)
        positions.add(y + sizeY + upY)
        positions.add(z + sizeZ + upZ)
        
        // Top-left
        positions.add(x + upX)
        positions.add(y + upY)
        positions.add(z + upZ)
        
        // Add texture coordinates
        if (face.texture.startsWith("#")) {
            // Get the texture region from the stitcher using the resolved ID
            val textureRegion = if (face.textureId >= 0) {
                textureStitcher.getTextureRegion(face.textureId)
            } else {
                // If textureId is not set, try to get it directly from the texture path
                if (face.texture.startsWith("#")) {
                    val key = face.texture.substring(1)
                    val modelTexturePath = model.textures[key]
                    if (modelTexturePath != null && !modelTexturePath.startsWith("#")) {
                        val index = textureStitcher.getTextureIndex(modelTexturePath)
                        if (index >= 0) {
                            face.textureId = index  // Update for future reference
                            textureStitcher.getTextureRegion(index)
                        } else {
                            // println("Fallback: Texture not found: ${modelTexturePath} for model ${model.id}")
                            textureStitcher.getTextureRegion(0)
                        }
                    } else {
                        // println("Fallback: Invalid texture reference: ${face.texture} for model ${model.id}")
                        textureStitcher.getTextureRegion(0)
                    }
                } else {
                    // Direct texture path (rare case)
                    val index = textureStitcher.getTextureIndex(face.texture)
                    if (index >= 0) {
                        face.textureId = index  // Update for future reference
                        textureStitcher.getTextureRegion(index)
                    } else {
                        // println("Fallback: Texture not found: ${face.texture} for model ${model.id}")
                        textureStitcher.getTextureRegion(0)
                    }
                }
            }
            
            if (face.uv != null) {
                // Use specified UV coordinates
                val minU = textureRegion.u1 + (textureRegion.u2 - textureRegion.u1) * (face.uv!![0] / 16.0f)
                val minV = textureRegion.v1 + (textureRegion.v2 - textureRegion.v1) * (face.uv!![1] / 16.0f)
                val maxU = textureRegion.u1 + (textureRegion.u2 - textureRegion.u1) * (face.uv!![2] / 16.0f)
                val maxV = textureRegion.v1 + (textureRegion.v2 - textureRegion.v1) * (face.uv!![3] / 16.0f)
                
                textCoords.add(minU)
                textCoords.add(maxV)
                
                textCoords.add(maxU)
                textCoords.add(maxV)
                
                textCoords.add(maxU)
                textCoords.add(minV)
                
                textCoords.add(minU)
                textCoords.add(minV)
            } else {
                // Map the whole texture region
                textCoords.add(textureRegion.u1)
                textCoords.add(textureRegion.v2)
                
                textCoords.add(textureRegion.u2)
                textCoords.add(textureRegion.v2)
                
                textCoords.add(textureRegion.u2)
                textCoords.add(textureRegion.v1)
                
                textCoords.add(textureRegion.u1)
                textCoords.add(textureRegion.v1)
            }
        } else {
            // Default UV mapping (0-1)
            textCoords.add(0.0f)
            textCoords.add(1.0f)
            
            textCoords.add(1.0f)
            textCoords.add(1.0f)
            
            textCoords.add(1.0f)
            textCoords.add(0.0f)
            
            textCoords.add(0.0f)
            textCoords.add(0.0f)
        }
        
        // Add normals
        for (i in 0..3) {
            normals.add(normalX)
            normals.add(normalY)
            normals.add(normalZ)
        }
        
        // Calculate tangent and bitangent vectors
        // Tangent is in the direction of texture U coordinate
        val tangent = calculateTangent(normalX, normalY, normalZ)
        // Bitangent is perpendicular to both normal and tangent
        val bitangent = calculateBitangent(normalX, normalY, normalZ, tangent[0], tangent[1], tangent[2])
        
        // Add tangents
        for (i in 0..3) {
            tangents.add(tangent[0])
            tangents.add(tangent[1])
            tangents.add(tangent[2])
        }
        
        // Add bitangents
        for (i in 0..3) {
            biTangents.add(bitangent[0])
            biTangents.add(bitangent[1])
            biTangents.add(bitangent[2])
        }
    }
    
    /**
     * Calculate a tangent vector perpendicular to the normal
     */
    private fun calculateTangent(normalX: Float, normalY: Float, normalZ: Float): FloatArray {
        // Create a tangent vector perpendicular to the normal
        // Using cross product with world up (0,1,0) unless normal is parallel to up
        val tangent = FloatArray(3)
        
        if (Math.abs(normalY) > 0.99f) {
            // Normal is pointing up or down, use world forward as reference
            tangent[0] = 0.0f  // x
            tangent[1] = 0.0f  // y
            tangent[2] = 1.0f  // z
        } else {
            // Cross product with world up
            tangent[0] = normalZ  // x
            tangent[1] = 0.0f     // y
            tangent[2] = -normalX // z
            
            // Normalize
            val length = Math.sqrt((tangent[0] * tangent[0] + tangent[2] * tangent[2]).toDouble()).toFloat()
            if (length > 0) {
                tangent[0] /= length
                tangent[2] /= length
            }
        }
        
        return tangent
    }
    
    /**
     * Calculate a bitangent vector perpendicular to both normal and tangent
     */
    private fun calculateBitangent(
        normalX: Float, normalY: Float, normalZ: Float,
        tangentX: Float, tangentY: Float, tangentZ: Float
    ): FloatArray {
        // Cross product of normal and tangent
        val bitangent = FloatArray(3)
        
        bitangent[0] = normalY * tangentZ - normalZ * tangentY
        bitangent[1] = normalZ * tangentX - normalX * tangentZ
        bitangent[2] = normalX * tangentY - normalY * tangentX
        
        // Normalize
        val length = Math.sqrt((bitangent[0] * bitangent[0] + 
                               bitangent[1] * bitangent[1] + 
                               bitangent[2] * bitangent[2]).toDouble()).toFloat()
        if (length > 0) {
            bitangent[0] /= length
            bitangent[1] /= length
            bitangent[2] /= length
        }
        
        return bitangent
    }
}
