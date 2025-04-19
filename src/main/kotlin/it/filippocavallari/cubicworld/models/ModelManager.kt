package it.filippocavallari.cubicworld.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import it.filippocavallari.cubicworld.integration.ModelConverter
import it.filippocavallari.cubicworld.textures.TextureStitcher
import org.vulkanb.eng.scene.ModelData
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors

/**
 * Manages models for the game, including loading and caching models.
 */
class ModelManager(
    private val modelsPath: String
) {
    // Cache of loaded models
    private val models = HashMap<String, Model>()
    
    // Gson parser for JSON
    private val gson: Gson = GsonBuilder().create()
    
    /**
     * Load all models from the models directory and convert to Vulkan format
     */
    fun loadModels(textureStitcher: TextureStitcher): List<ModelData> {
        // Clear existing models
        models.clear()
        
        val modelDataList = ArrayList<ModelData>()
        val modelDir = Paths.get(modelsPath)
        
        try {
            // Find all model JSON files
            val modelFiles = Files.walk(modelDir)
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".json") }
                .collect(Collectors.toList())
            
            println("Found ${modelFiles.size} model files")
            
            // Load each model
            for (modelFile in modelFiles) {
                val model = loadModel(modelFile, textureStitcher)
                val modelId = getModelId(modelFile)
                
                if (model != null) {
                    models[modelId] = model
                    
                    // Convert to engine ModelData format
                    val modelData = ModelConverter.convertToVulkanModelData(model, textureStitcher)
                    modelDataList.add(modelData)
                    
                    println("Loaded model: $modelId")
                }
            }
        } catch (e: Exception) {
            println("Error loading models: ${e.message}")
            e.printStackTrace()
        }
        
        println("Loaded ${models.size} models")
        
        return modelDataList
    }
    
    /**
     * Load a specific model from file
     */
    fun loadModel(filePath: Path, textureStitcher: TextureStitcher): Model? {
        try {
            // Read the model file
            FileReader(filePath.toFile()).use { reader ->
                val model = gson.fromJson(reader, Model::class.java)
                
                // Set the model ID
                model.id = getModelId(filePath)
                
                // Fix texture paths: block textures should look in the root folder
                // Modify textures to use correct paths
                model.fixTexturePaths()
                
                // If the model has a parent, load and inherit from it
                if (model.parent != null) {
                    val parentModel = getOrLoadParentModel(model.parent!!, textureStitcher)
                    if (parentModel != null) {
                        model.inheritFromParent(parentModel)
                    }
                }
                
                // Resolve texture references
                model.resolveTextureReferences(textureStitcher)
                
                return model
            }
        } catch (e: Exception) {
            println("Error loading model from $filePath: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Get a loaded model by ID, or null if not loaded
     */
    fun getModel(id: String): Model? {
        return models[id]
    }
    
    /**
     * Get or load a parent model
     */
    private fun getOrLoadParentModel(parentId: String, textureStitcher: TextureStitcher): Model? {
        // Check if already loaded
        if (models.containsKey(parentId)) {
            return models[parentId]
        }
        
        // Try to load the parent model
        val parentPath = Paths.get(modelsPath, "$parentId.json")
        if (Files.exists(parentPath)) {
            val parentModel = loadModel(parentPath, textureStitcher)
            if (parentModel != null) {
                models[parentId] = parentModel
                return parentModel
            }
        }
        
        println("Warning: Parent model not found: $parentId")
        return null
    }
    
    /**
     * Extract model ID from file path
     */
    private fun getModelId(filePath: Path): String {
        // Get the filename without extension
        val fileName = filePath.fileName.toString()
        val nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'))
        
        // Get the directory relative to models path
        val relativePath = Paths.get(modelsPath).relativize(filePath.parent)
        
        // Combine to form ID like "block/stone"
        return if (relativePath.toString().isEmpty()) {
            nameWithoutExt
        } else {
            relativePath.toString().replace('\\', '/') + "/" + nameWithoutExt
        }
    }
}