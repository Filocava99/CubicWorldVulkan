package it.filippocavallari.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import it.filippocavallari.textures.TextureStitcher
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Manages the loading and caching of model definitions from JSON files.
 */
class ModelManager(
    private val modelsDirectory: String,
    private val textureStitcher: TextureStitcher
) {
    private val loadedModels = HashMap<String, Model>()
    private val gson: Gson
    
    // Model renderers

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
        return JsonDeserializer { json, typeOfT, context ->
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
            model.resolveTextureReferences(textureStitcher)
            
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
        
        val fullPath = Paths.get(modelsDirectory, filePath)
        
        // Load and parse the model file
        FileReader(fullPath.toFile()).use { reader ->
            val model = gson.fromJson(reader, Model::class.java)
            model.id = modelId
            
            // Cache the model
            loadedModels[modelId] = model
            
            return model
        }
    }
    
    /**
     * Get all available models in the models directory.
     *
     * @return A Map of model IDs to Models
     * @throws IOException If there's an error reading the models directory
     */
    fun getAllModels(): Map<String, Model> {
        if (loadedModels.isEmpty()) {
            // Scan directory for model files
            Files.walk(Paths.get(modelsDirectory))
                .filter { path -> path.toString().endsWith(".json") }
                .forEach { path ->
                    val relativePath = Paths.get(modelsDirectory).relativize(path).toString()
                    val modelId = relativePath.substring(0, relativePath.length - 5) // Remove .json
                    try {
                        getModel(modelId) // This will cache the model
                    } catch (e: IOException) {
                        System.err.println("Error loading model: $modelId - ${e.message}")
                    }
                }
        }
        
        return loadedModels
    }
}