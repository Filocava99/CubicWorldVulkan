package it.filippocavallari.cubicworld.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import it.filippocavallari.cubicworld.renderer.Material
import it.filippocavallari.cubicworld.renderer.Mesh
import it.filippocavallari.cubicworld.renderer.Shader
import it.filippocavallari.cubicworld.renderer.Texture
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Manages the loading and caching of model definitions from JSON files.
 */
class ModelManager(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    private val defaultShader: Shader
) {
    private val loadedModels = HashMap<String, Model>()
    private val loadedMeshes = HashMap<String, Mesh>()
    private val loadedTextures = HashMap<String, Texture>()
    private val gson: Gson
    
    init {
        // Configure GSON for model loading
        this.gson = GsonBuilder()
            .registerTypeAdapter(Model::class.java, createModelDeserializer())
            .create()
    }
    
    /**
     * Create a custom deserializer for handling model inheritance and references.
     */
    private fun createModelDeserializer(): JsonDeserializer<Model> {
        return JsonDeserializer { json, _, _ ->
            val model = Gson().fromJson(json, Model::class.java)
            
            // Process parent models if specified
            if (!model.parent.isNullOrEmpty()) {
                try {
                    val parentModel = getModel(model.parent!!)
                    model.inheritFromParent(parentModel)
                } catch (e: IOException) {
                    throw RuntimeException("Failed to load parent model: ${model.parent}", e)
                }
            }
            
            // Resolve texture references
            model.resolveTextures(this)
            
            model
        }
    }
    
    /**
     * Get a model by its identifier, loading it if necessary.
     *
     * @param modelId The model identifier (e.g., "block/stone")
     * @return The loaded model
     * @throws IOException If the model file cannot be read
     */
    fun getModel(modelId: String): Model {
        // Check cache first
        loadedModels[modelId]?.let { return it }
        
        // Determine file path
        var filePath = "$modelId.json"
        if (!modelId.contains("/")) {
            filePath = "block/$filePath" // Default to block directory if no path specified
        }
        
        val fullPath = "models/$filePath"
        
        // Load and parse the model file
        val classLoader = javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream(fullPath)
            ?: throw IOException("Model file not found: $fullPath")
        
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val model = gson.fromJson(reader, Model::class.java)
            model.id = modelId
            
            // Cache the model
            loadedModels[modelId] = model
            
            return model
        }
    }
    
    /**
     * Loads a texture if it's not already loaded.
     *
     * @param texturePath The path to the texture
     * @param commandPool Command pool for transfer operations
     * @param queue Queue for transfer operations
     * @return The loaded texture
     */
    fun loadTexture(texturePath: String, commandPool: Long, queue: VkQueue): Texture {
        // Check cache first
        loadedTextures[texturePath]?.let { return it }
        
        // Create and load new texture
        val texture = Texture(device, physicalDevice, "textures/$texturePath.png")
        texture.load(commandPool, queue)
        
        // Cache the texture
        loadedTextures[texturePath] = texture
        
        return texture
    }
    
    /**
     * Creates a material for a model.
     *
     * @param model The model to create a material for
     * @param commandPool Command pool for transfer operations
     * @param queue Queue for transfer operations
     * @return The created material
     */
    fun createMaterial(model: Model, commandPool: Long, queue: VkQueue): Material {
        // Load textures
        val diffuseTexture = model.textures["diffuse"]?.let { loadTexture(it, commandPool, queue) }
        val normalTexture = model.textures["normal"]?.let { loadTexture(it, commandPool, queue) }
        val specularTexture = model.textures["specular"]?.let { loadTexture(it, commandPool, queue) }
        
        // Create material
        return Material(
            shader = defaultShader,
            diffuseTexture = diffuseTexture,
            normalTexture = normalTexture,
            specularTexture = specularTexture
        )
    }
    
    /**
     * Creates a mesh for a model.
     *
     * @param model The model to create a mesh for
     * @param commandPool Command pool for transfer operations
     * @param queue Queue for transfer operations
     * @return The created mesh
     */
    fun createMesh(model: Model, commandPool: Long, queue: VkQueue): Mesh {
        // Check cache first
        loadedMeshes[model.id]?.let { return it }
        
        // Convert model elements to mesh data
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()
        val indices = ArrayList<Int>()
        
        var indexOffset = 0
        
        for (element in model.elements) {
            // Add cube vertices based on the element's from/to coordinates
            val fromX = element.from[0] / 16.0f
            val fromY = element.from[1] / 16.0f
            val fromZ = element.from[2] / 16.0f
            val toX = element.to[0] / 16.0f
            val toY = element.to[1] / 16.0f
            val toZ = element.to[2] / 16.0f
            
            // Add vertices for each face
            for (face in element.faces.values) {
                // Add quad vertices (2 triangles)
                when (face.cullFace) {
                    "north" -> {
                        // Add vertices for north face (X, Z plane at min Y)
                        positions.addAll(listOf(
                            fromX, fromY, fromZ,
                            toX, fromY, fromZ,
                            toX, fromY, toZ,
                            fromX, fromY, toZ
                        ))
                    }
                    "south" -> {
                        // Add vertices for south face (X, Z plane at max Y)
                        positions.addAll(listOf(
                            fromX, toY, fromZ,
                            fromX, toY, toZ,
                            toX, toY, toZ,
                            toX, toY, fromZ
                        ))
                    }
                    "east" -> {
                        // Add vertices for east face (Y, Z plane at max X)
                        positions.addAll(listOf(
                            toX, fromY, fromZ,
                            toX, toY, fromZ,
                            toX, toY, toZ,
                            toX, fromY, toZ
                        ))
                    }
                    "west" -> {
                        // Add vertices for west face (Y, Z plane at min X)
                        positions.addAll(listOf(
                            fromX, fromY, fromZ,
                            fromX, fromY, toZ,
                            fromX, toY, toZ,
                            fromX, toY, fromZ
                        ))
                    }
                    "up" -> {
                        // Add vertices for up face (X, Y plane at max Z)
                        positions.addAll(listOf(
                            fromX, fromY, toZ,
                            toX, fromY, toZ,
                            toX, toY, toZ,
                            fromX, toY, toZ
                        ))
                    }
                    "down" -> {
                        // Add vertices for down face (X, Y plane at min Z)
                        positions.addAll(listOf(
                            fromX, fromY, fromZ,
                            fromX, toY, fromZ,
                            toX, toY, fromZ,
                            toX, fromY, fromZ
                        ))
                    }
                }
                
                // Add normal for this face
                val normal = when (face.cullFace) {
                    "north" -> floatArrayOf(0f, -1f, 0f)
                    "south" -> floatArrayOf(0f, 1f, 0f)
                    "east" -> floatArrayOf(1f, 0f, 0f)
                    "west" -> floatArrayOf(-1f, 0f, 0f)
                    "up" -> floatArrayOf(0f, 0f, 1f)
                    "down" -> floatArrayOf(0f, 0f, -1f)
                    else -> floatArrayOf(0f, 0f, 0f)
                }
                
                // Add normal for each vertex of the face
                for (i in 0 until 4) {
                    normals.addAll(normal.toList())
                }
                
                // Add texture coordinates based on UV values
                val uv = face.uv ?: floatArrayOf(0f, 0f, 16f, 16f)
                val u1 = uv[0] / 16.0f
                val v1 = uv[1] / 16.0f
                val u2 = uv[2] / 16.0f
                val v2 = uv[3] / 16.0f
                
                texCoords.addAll(listOf(
                    u1, v1,
                    u2, v1,
                    u2, v2,
                    u1, v2
                ))
                
                // Add indices for the two triangles of this face
                indices.addAll(listOf(
                    indexOffset, indexOffset + 1, indexOffset + 2,
                    indexOffset, indexOffset + 2, indexOffset + 3
                ))
                
                indexOffset += 4
            }
        }
        
        // Create the mesh
        val mesh = Mesh(
            positions = positions.toFloatArray(),
            normals = normals.toFloatArray(),
            texCoords = texCoords.toFloatArray(),
            indices = indices.toIntArray()
        )
        
        // Upload mesh data to GPU
        mesh.uploadToGPU(device, physicalDevice, commandPool, queue)
        
        // Cache the mesh
        loadedMeshes[model.id] = mesh
        
        return mesh
    }
    
    /**
     * Cleans up all loaded resources.
     */
    fun cleanup() {
        // Clean up textures
        for (texture in loadedTextures.values) {
            texture.cleanup()
        }
        loadedTextures.clear()
        
        // Clean up meshes
        for (mesh in loadedMeshes.values) {
            mesh.cleanup(device)
        }
        loadedMeshes.clear()
        
        // Models don't need cleanup, just clear the map
        loadedModels.clear()
    }
}

/**
 * Represents a model definition loaded from a JSON file.
 */
class Model {
    var id: String = ""
    var parent: String? = null
    var textures: MutableMap<String, String> = mutableMapOf()
    var elements: MutableList<Element> = mutableListOf()
    
    /**
     * Inherits properties from a parent model.
     *
     * @param parentModel The parent model to inherit from
     */
    fun inheritFromParent(parentModel: Model) {
        // Inherit textures (child textures override parent textures)
        val mergedTextures = parentModel.textures.toMutableMap()
        mergedTextures.putAll(textures)
        textures = mergedTextures
        
        // Inherit elements if this model has none
        if (elements.isEmpty()) {
            elements = parentModel.elements.toMutableList()
        }
    }
    
    /**
     * Resolves texture references in the model.
     *
     * @param modelManager The model manager to resolve references
     */
    fun resolveTextures(modelManager: ModelManager) {
        // Resolve texture references (e.g., "#texture" -> actual texture path)
        val resolvedTextures = mutableMapOf<String, String>()
        
        for ((key, value) in textures) {
            var resolvedValue = value
            
            // If the value is a reference to another texture in this model
            if (value.startsWith("#")) {
                val referencedKey = value.substring(1)
                resolvedValue = textures[referencedKey] ?: value
                
                // Continue resolving if it's still a reference
                var iteration = 0
                while (resolvedValue.startsWith("#") && iteration < 10) {
                    val nextKey = resolvedValue.substring(1)
                    resolvedValue = textures[nextKey] ?: resolvedValue
                    iteration++
                }
            }
            
            resolvedTextures[key] = resolvedValue
        }
        
        textures = resolvedTextures
    }
    
    /**
     * Represents an element in a model (usually a cube or part of a model).
     */
    class Element {
        var from: FloatArray = floatArrayOf(0f, 0f, 0f)
        var to: FloatArray = floatArrayOf(16f, 16f, 16f)
        var rotation: Rotation? = null
        var shade: Boolean = true
        var faces: Map<String, Face> = mapOf()
        
        /**
         * Represents rotation of an element.
         */
        class Rotation {
            var origin: FloatArray = floatArrayOf(8f, 8f, 8f)
            var axis: String = "y"
            var angle: Float = 0f
            var rescale: Boolean = false
        }
        
        /**
         * Represents a face of an element.
         */
        class Face {
            var uv: FloatArray? = null
            var texture: String = ""
            var cullFace: String = ""
            var rotation: Int = 0
            var tintindex: Int = -1
        }
    }
}