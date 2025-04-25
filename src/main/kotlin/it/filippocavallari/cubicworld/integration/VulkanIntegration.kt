package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.models.Model
import it.filippocavallari.cubicworld.models.ModelManager
import it.filippocavallari.cubicworld.textures.TextureAtlasLoader
import it.filippocavallari.cubicworld.textures.TextureRegion
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.integration.VulkanChunkMeshBuilder
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import org.vulkanb.eng.graph.Render
import org.vulkanb.eng.graph.TextureCache
import org.vulkanb.eng.scene.Entity
import org.vulkanb.eng.scene.ModelData
import org.vulkanb.eng.scene.Scene
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Main integration class that bridges the Kotlin voxel game components with the Java Vulkan engine.
 * This class provides methods to:
 * 1. Initialize textures and models
 * 2. Convert between Kotlin and Java model formats
 * 3. Handle world integration with the Vulkan scene
 */
class VulkanIntegration {
    // Texture components
    private lateinit var textureStitcher: TextureStitcher
    private lateinit var textureDescriptors: LongArray
    
    // Model components
    private lateinit var modelManager: ModelManager
    
    // Chunk mesh building and caching
    private lateinit var chunkMeshBuilder: VulkanChunkMeshBuilder
    private val chunkMeshCache = ConcurrentHashMap<String, ModelData>()
    
    // Java Vulkan engine components (references only)
    private lateinit var vulkanRender: Render
    private lateinit var vulkanScene: Scene
    
    /**
     * Initialize the integration with required components
     * 
     * @param render The Vulkan render system
     * @param scene The Vulkan scene
     */
    fun initialize(render: Render, scene: Scene) {
        this.vulkanRender = render
        this.vulkanScene = scene
        
        // Create a simple placeholder texture stitcher
        textureStitcher = createMinimalTextureStitcher()
        
        // Initialize chunk mesh builder
        chunkMeshBuilder = VulkanChunkMeshBuilder(textureStitcher)
        
        // Skip model loading entirely for now
        println("VulkanIntegration initialized with minimal resources")
        
        // Print some debug info about available buffers
        try {
            // Try to access buffer sizes through reflection
            val globalBuffersField = render.javaClass.getDeclaredField("globalBuffers")
            globalBuffersField.isAccessible = true
            val globalBuffers = globalBuffersField.get(render)
            
            val verticesBufferField = globalBuffers.javaClass.getDeclaredField("verticesBuffer")
            val indicesBufferField = globalBuffers.javaClass.getDeclaredField("indicesBuffer")
            verticesBufferField.isAccessible = true
            indicesBufferField.isAccessible = true
            
            val verticesBuffer = verticesBufferField.get(globalBuffers)
            val indicesBuffer = indicesBufferField.get(globalBuffers)
            
            val getRequestedSizeMethod = verticesBuffer.javaClass.getDeclaredMethod("getRequestedSize")
            val verticesSize = getRequestedSizeMethod.invoke(verticesBuffer) as Long
            val indicesSize = getRequestedSizeMethod.invoke(indicesBuffer) as Long
            
            println("Global buffer sizes:")
            println("- Vertices buffer: ${verticesSize / (1024 * 1024)} MB")
            println("- Indices buffer: ${indicesSize / (1024 * 1024)} MB")
        } catch (e: Exception) {
            println("Could not access buffer information: ${e.message}")
        }
    }
    
    /**
     * Create a minimal texture stitcher with basic placeholders
     */
    private fun createMinimalTextureStitcher(): TextureStitcher {
        val textureDir = "${System.getProperty("user.dir")}/src/main/resources/textures"
        val stitcher = TextureStitcher(textureDir)
        
        try {
            // Initialize the texture stitcher with a tile size of 16x16
            // Using a more standard size like 64 or 128
            stitcher.build(64)
            
            // Create the atlas directory if it doesn't exist
            val outputDir = "${System.getProperty("user.dir")}/src/main/resources/atlas"
            val outputDirFile = java.io.File(outputDir)
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs()
            }
            
            // Save the atlases
            stitcher.saveAtlases(outputDir)
            
            // Verify the textures have been created
            val diffuseAtlas = java.io.File("$outputDir/diffuse_atlas.png")
            val normalAtlas = java.io.File("$outputDir/normal_atlas.png")
            val specularAtlas = java.io.File("$outputDir/specular_atlas.png")
            
            println("Texture atlas files created:")
            println("Diffuse atlas exists: ${diffuseAtlas.exists()} (size: ${if (diffuseAtlas.exists()) diffuseAtlas.length() else 0} bytes)")
            println("Normal atlas exists: ${normalAtlas.exists()} (size: ${if (normalAtlas.exists()) normalAtlas.length() else 0} bytes)")
            println("Specular atlas exists: ${specularAtlas.exists()} (size: ${if (specularAtlas.exists()) specularAtlas.length() else 0} bytes)")
            
            // Debug mapping for stone texture
            val stoneIndex = stitcher.getTextureIndex("stone")
            println("Stone texture index: $stoneIndex")
            if (stoneIndex >= 0) {
                val region = stitcher.getTextureRegion(stoneIndex)
                println("Stone texture region: u1=${region.u1}, v1=${region.v1}, u2=${region.u2}, v2=${region.v2}")
            }
            
            println("Created and saved minimal texture atlas")
        } catch (e: Exception) {
            println("Error creating texture stitcher: ${e.message}")
            e.printStackTrace()
        }
        
        return stitcher
    }
    
    /**
     * Initialize the texture atlas for the game
     */
    private fun initTextureAtlas() {
        println("Initializing texture atlas for Vulkan integration")
        
        val textureDir = "src/main/resources/textures"
        val outputDir = "src/main/resources/atlas"
        
        // Create texture stitcher to combine textures into an atlas
        textureStitcher = TextureAtlasLoader.loadAndStitchTextures(textureDir, outputDir, 16)
        
        // Load texture atlases into Vulkan
        loadTextureAtlasToVulkan()
        
        println("Texture atlas initialized with ${textureStitcher.totalTextures} textures")
    }
    
    /**
     * Load texture atlases to the Vulkan engine
     */
    private fun loadTextureAtlasToVulkan() {
        try {
            // Since we cannot directly access the private fields in Render class,
            // we'd need proper accessor methods. For now, let's create stub references
            // In a real implementation, Render would need to expose these via getter methods
            // Using dummy values for demonstration
            // In a real implementation, we'd use proper accessor methods
            val device = null as VkDevice? // Dummy device
            val physDevice = 0L // VkPhysicalDevice handle
            val instance = null as VkInstance? // Dummy instance
            val commandPool = 0L // VkCommandPool handle
            val graphicsQueue = null as VkQueue? // Dummy queue
            
            // Since we can't actually run with stub objects, let's just create a dummy implementation
            // In a real implementation, this would use the proper Vulkan APIs
            println("Would load texture atlas to Vulkan (stub implementation)")
            textureDescriptors = longArrayOf(1L, 2L) // Dummy descriptor handles
            
            // Register descriptors with the texture cache
            registerTextureDescriptors()
            
            println("Texture atlases loaded to Vulkan")
        } catch (e: Exception) {
            println("Error loading texture atlas to Vulkan: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Register texture descriptors with the Vulkan engine's texture cache
     */
    private fun registerTextureDescriptors() {
        // This should be implemented based on the TextureCache implementation
        // We will need to extend TextureCache.java to support our texture atlas approach
        // For now, we'll just log the presence of the descriptors
        println("Texture descriptors available: ${textureDescriptors.size}")
    }
    
    /**
     * Initialize the model manager
     */
    private fun initModelManager() {
        println("Initializing model manager")
        
        // Create model manager
        modelManager = ModelManager("src/main/resources/models")
        
        // Load and register models with Vulkan - start with just a few basic models
        // For development, we'll limit how many models we load initially
        val modelDataList = modelManager.loadModels(textureStitcher).take(5)
        
        if (modelDataList.isNotEmpty()) {
            // We need to convert our model data to the format expected by the Vulkan engine
            val vulkanModels = convertToVulkanModelData(modelDataList)
            
            // Load models in smaller batches to avoid stack overflow
            val batchSize = 3
            for (i in vulkanModels.indices step batchSize) {
                val endIndex = minOf(i + batchSize, vulkanModels.size)
                val batch = vulkanModels.subList(i, endIndex)
                vulkanRender.loadModels(batch)
                println("Loaded batch ${i/batchSize + 1} with ${batch.size} models")
            }
            
            println("Loaded ${vulkanModels.size} models into Vulkan engine")
        } else {
            println("No models were loaded")
        }
    }
    
    /**
     * Convert our Kotlin model data to Vulkan Java model data
     */
    private fun convertToVulkanModelData(modelDataList: List<org.vulkanb.eng.scene.ModelData>): List<org.vulkanb.eng.scene.ModelData> {
        // In this implementation, our VulkanModelConverter already outputs the correct format,
        // so we don't need to do additional conversion here. Just return the list.
        return modelDataList
    }
    
    /**
     * Create a mesh for a chunk and add it to the scene
     * This should only be called from the main thread.
     * 
     * @param chunk The chunk to create a mesh for
     * @return The entity ID of the created mesh in the scene
     */
    @Synchronized
    fun createChunkMesh(chunk: Chunk): String {
        println("Building mesh for chunk at (${chunk.position.x}, ${chunk.position.y})")
        
        // Generate a mesh from the chunk data
        val modelData = chunkMeshBuilder.buildMesh(chunk)
        
        // Skip if no mesh data (empty chunk)
        if (modelData.meshDataList.isEmpty() || modelData.meshDataList[0].positions.size == 0) {
            println("Skipping empty chunk mesh")
            return ""
        } else {
            println("Mesh generated with ${modelData.meshDataList[0].positions.size / 3} vertices")
        }
        
        // Get a unique ID for this chunk
        val chunkId = getChunkId(chunk)
        
        // Check if this exact chunk mesh is already loaded and hasn't changed
        val existingMesh = chunkMeshCache[chunkId]
        if (existingMesh != null && !chunk.isDirty() && existingMesh.modelId == modelData.modelId) {
            // The chunk is already loaded with the same mesh, no need to reload
            println("Chunk $chunkId is already loaded with the same mesh. Skipping.")
            return chunkId
        }
        
        // Store the mesh for future reference
        chunkMeshCache[chunkId] = modelData
        
        // Check if entity already exists
        val entities = vulkanScene.getEntitiesByModelId(modelData.modelId)
        val existingEntity = entities?.find { it.getId() == chunkId }
        if (existingEntity != null) {
            // Remove existing entity
            vulkanScene.removeEntity(existingEntity)
            println("Removed existing entity for chunk $chunkId")
        }
        
        // Load model to renderer
        println("Loading chunk model with ID: ${modelData.modelId}")
        
        // Check if mesh has actual vertices before loading
        if (modelData.meshDataList.isNotEmpty() && modelData.meshDataList[0].positions.isNotEmpty()) {
            println("Loading chunk with ${modelData.meshDataList[0].positions.size / 3} vertices")
            vulkanRender.loadModels(listOf(modelData))
        } else {
            println("WARNING: Trying to load empty chunk mesh. Skipping.")
            return ""
        }
        
        // Create entity for this chunk with correct world position
        val worldX = chunk.getWorldX().toFloat()
        val worldZ = chunk.getWorldZ().toFloat()
        val entity = Entity(chunkId, modelData.modelId, Vector3f(worldX, 0.0f, worldZ))
        
        println("Placing chunk entity at world position: ($worldX, 0.0, $worldZ)")
        
        // Add entity to scene
        vulkanScene.addEntity(entity)
        println("Added entity for chunk $chunkId with model ID ${modelData.modelId}")
        
        // Mark chunk as clean
        chunk.markClean()
        
        // Verify entity was added
        val entitiesAfterAdd = vulkanScene.getEntitiesByModelId(modelData.modelId)
        if (entitiesAfterAdd == null) {
            println("WARNING: No entities found for model ID ${modelData.modelId} after adding entity")
        } else {
            println("Verified: ${entitiesAfterAdd.size} entities for model ID ${modelData.modelId}")
        }
        
        return chunkId
    }
    
    /**
     * Update a chunk mesh when the chunk has changed
     * 
     * @param chunk The chunk to update
     */
    fun updateChunkMesh(chunk: Chunk) {
        // Only update if the chunk is marked as dirty
        if (chunk.isDirty()) {
            // Re-create the chunk mesh
            createChunkMesh(chunk)
        }
    }
    
    /**
     * Remove a chunk mesh from the scene
     * 
     * @param chunk The chunk to remove
     */
    fun removeChunkMesh(chunk: Chunk) {
        val chunkId = getChunkId(chunk)
        // Find entity in the scene
        // Retrieve the modelData from the cache
        val modelData = chunkMeshCache[chunkId]
        val entities = if (modelData != null) vulkanScene.getEntitiesByModelId(modelData.modelId) else null
        val entity = entities?.find { it.getId() == chunkId }
        
        if (entity != null) {
            vulkanScene.removeEntity(entity)
        }
        
        // Remove from cache
        chunkMeshCache.remove(chunkId)
    }
    
    /**
     * Get a unique ID for a chunk
     * 
     * @param chunk The chunk
     * @return A unique string ID
     */
    private fun getChunkId(chunk: Chunk): String {
        return "chunk_${chunk.position.x}_${chunk.position.y}"
    }
    
    /**
     * Connect a World to the integration,
     * setting up listeners for chunk loading/unloading
     * 
     * @param world The world to integrate
     */
    fun connectWorld(world: World) {
        // Add listener for chunk events
        world.addChunkListener(object : World.ChunkListener {
            override fun onChunkLoaded(chunk: Chunk) {
                // Create mesh for the chunk and add to scene
                createChunkMesh(chunk)
            }
            
            override fun onChunkUnloaded(chunk: Chunk) {
                // Remove chunk entity from scene
                removeChunkMesh(chunk)
            }
            
            override fun onChunkUpdated(chunk: Chunk) {
                // Update chunk mesh
                updateChunkMesh(chunk)
            }
        })
        
        println("Connected world to Vulkan integration")
    }
    
    /**
     * Reset the world's chunk rendering - useful when changing worlds
     * or when the buffer gets too full
     */
    fun resetChunkRendering() {
        println("Resetting chunk rendering system")
        
        // Remove all chunk entities from scene
        val chunksToRemove = ArrayList<String>()
        
        // Collect all chunk IDs
        chunkMeshCache.keys.forEach { chunkId ->
            chunksToRemove.add(chunkId)
        }
        
        // Remove each chunk entity
        chunksToRemove.forEach { chunkId ->
            val modelData = chunkMeshCache[chunkId]
            val entities = if (modelData != null) vulkanScene.getEntitiesByModelId(modelData.modelId) else null
            val entity = entities?.find { it.getId() == chunkId }
            
            if (entity != null) {
                vulkanScene.removeEntity(entity)
                println("Removed entity for chunk $chunkId during reset")
            }
        }
        
        // Clear the cache
        chunkMeshCache.clear()
        
        // Force recreation of the next world update cycle
        println("Chunk rendering system reset complete")
    }
    
    /**
     * Clean up resources used by the integration
     */
    fun cleanup() {
        // Cleanup texture resources
        if (::textureStitcher.isInitialized && this::vulkanRender.isInitialized) {
            // Since we can't access private device field, we'd need proper accessor method
            // In a real implementation, we'd use something like vulkanRender.getDevice().getVkDevice()
            // For now, commenting out this cleanup call
            // TextureAtlasLoader.cleanup(device)
            println("Texture resources cleanup (stub)")
        }
        
        // Reset the chunk rendering system
        resetChunkRendering()
        
        println("VulkanIntegration cleaned up")
    }
}
