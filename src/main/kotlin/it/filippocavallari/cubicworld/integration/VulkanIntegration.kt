package it.filippocavallari.cubicworld.integration

import it.filippocavallari.cubicworld.data.block.FaceDirection
import it.filippocavallari.cubicworld.models.Model
import it.filippocavallari.cubicworld.models.ModelManager
import it.filippocavallari.cubicworld.textures.TextureAtlasLoader
import it.filippocavallari.cubicworld.textures.TextureRegion
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import it.filippocavallari.cubicworld.integration.VulkanChunkMeshBuilder
import it.filippocavallari.cubicworld.integration.DirectionalVulkanChunkMeshBuilder
import it.filippocavallari.cubicworld.integration.CubicChunkMeshBuilder
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
import java.util.*
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
    private lateinit var directionalChunkMeshBuilder: DirectionalVulkanChunkMeshBuilder
    private lateinit var cubicChunkMeshBuilder: CubicChunkMeshBuilder
    private val chunkMeshCache = ConcurrentHashMap<String, ModelData>()
    private val directionalChunkMeshCache = ConcurrentHashMap<String, Map<FaceDirection, ModelData>>()
    private val cubicChunkMeshCache = ConcurrentHashMap<String, ModelData>()
    
    // Track loaded chunks to manage descriptor pool
    private val loadedChunks = ConcurrentHashMap<String, String>() // chunkId -> modelId
    private val directionalLoadedChunks = ConcurrentHashMap<String, Map<FaceDirection, String>>() // chunkId -> direction -> entityId
    private val chunkLoadOrder = Collections.synchronizedList(mutableListOf<String>()) // Track order for LRU
    private val modelRefCount = ConcurrentHashMap<String, Int>() // modelId -> reference count
    private val MAX_LOADED_CHUNKS = 500 // Increased limit
    
    // Use directional culling by default
    private var useDirectionalCulling = true
    
    // Track loaded models to prevent duplication
    private val loadedModels = ConcurrentHashMap<String, Boolean>() // modelId -> loaded
    
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
        directionalChunkMeshBuilder = DirectionalVulkanChunkMeshBuilder(textureStitcher)
        cubicChunkMeshBuilder = CubicChunkMeshBuilder(textureStitcher)
        
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
            println("- Max loaded chunks: $MAX_LOADED_CHUNKS")
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
     * Check if we can load more chunks without exhausting resources
     */
    private fun canLoadMoreChunks(): Boolean {
        val currentChunks = loadedChunks.size
        if (currentChunks >= MAX_LOADED_CHUNKS) {
            println("Chunk limit reached ($currentChunks/$MAX_LOADED_CHUNKS), will unload old chunks")
            return false
        }
        return true
    }
    
    /**
     * Unload oldest chunks to make room for new ones using LRU strategy
     */
    private fun unloadOldestChunks(count: Int) {
        println("Unloading $count oldest chunks to free resources")
        
        val chunksToRemove = synchronized(chunkLoadOrder) {
            val toRemove = chunkLoadOrder.take(minOf(count, chunkLoadOrder.size))
            chunkLoadOrder.removeAll(toRemove)
            toRemove
        }
        
        chunksToRemove.forEach { chunkId ->
            removeChunkMeshInternal(chunkId)
        }
        
        println("Freed resources from ${chunksToRemove.size} chunks, now have ${loadedChunks.size} loaded chunks")
    }
    
    /**
     * Internal method to remove a chunk mesh
     */
    private fun removeChunkMeshInternal(chunkId: String) {
        if (useDirectionalCulling) {
            // Remove directional meshes
            val directionalEntities = directionalLoadedChunks[chunkId]
            if (directionalEntities != null) {
                for ((direction, entityId) in directionalEntities) {
                    // Find and remove entity from scene
                    var entityFound = false
                    for ((modelId, entities) in vulkanScene.entitiesMap) {
                        val entity = entities?.find { it.getId() == entityId }
                        if (entity != null) {
                            vulkanScene.removeEntity(entity)
                            println("Removed directional entity for chunk $chunkId direction $direction")
                            entityFound = true
                            
                            // Update reference count for the model
                            val refCount = modelRefCount[modelId] ?: 1
                            if (refCount <= 1) {
                                modelRefCount.remove(modelId)
                                loadedModels.remove(modelId)
                                println("Model $modelId no longer in use, marked for cleanup")
                            } else {
                                modelRefCount[modelId] = refCount - 1
                            }
                            break
                        }
                    }
                    
                    if (!entityFound) {
                        println("Warning: Could not find entity $entityId to remove")
                    }
                }
                
                // Remove from tracking
                directionalLoadedChunks.remove(chunkId)
                directionalChunkMeshCache.remove(chunkId)
            }
        } else {
            // Original single mesh removal
            val modelId = loadedChunks[chunkId] ?: return
            
            // Find and remove entity from scene
            val modelData = chunkMeshCache[chunkId]
            val entities = if (modelData != null) vulkanScene.getEntitiesByModelId(modelData.modelId) else null
            val entity = entities?.find { it.getId() == chunkId }
            
            if (entity != null) {
                vulkanScene.removeEntity(entity)
                println("Removed entity for chunk $chunkId")
            }
            
            // Update reference count
            val refCount = modelRefCount[modelId] ?: 1
            if (refCount <= 1) {
                modelRefCount.remove(modelId)
                loadedModels.remove(modelId)
                println("Model $modelId no longer in use, marked for cleanup")
            } else {
                modelRefCount[modelId] = refCount - 1
            }
            
            // Remove from tracking
            loadedChunks.remove(chunkId)
            chunkMeshCache.remove(chunkId)
        }
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
        return if (useDirectionalCulling) {
            createDirectionalChunkMesh(chunk)
        } else {
            createSingleChunkMesh(chunk)
        }
    }
    
    /**
     * Create directional meshes for a chunk (6 meshes, one per face direction)
     */
    private fun createDirectionalChunkMesh(chunk: Chunk): String {
        println("Building directional meshes for chunk at (${chunk.position.x}, ${chunk.position.y})")
        
        // Check if we can load more chunks
        if (!canLoadMoreChunks()) {
            println("Chunk limit reached, unloading old chunks")
            unloadOldestChunks(50)
        }
        
        // Generate directional meshes from the chunk data
        val directionalMeshes = directionalChunkMeshBuilder.buildDirectionalMeshes(chunk)
        
        // Skip if no mesh data (empty chunk)
        if (directionalMeshes.isEmpty()) {
            println("Skipping empty chunk mesh")
            return ""
        }
        
        // Get a unique ID for this chunk
        val chunkId = getChunkId(chunk)
        
        // Check if this exact chunk mesh is already loaded
        val existingMeshes = directionalChunkMeshCache[chunkId]
        if (existingMeshes != null && !chunk.isDirty()) {
            println("Chunk $chunkId is already loaded with directional meshes. Skipping.")
            return chunkId
        }
        
        // Remove existing entities if any
        val existingEntities = directionalLoadedChunks[chunkId]
        if (existingEntities != null) {
            for ((direction, entityId) in existingEntities) {
                val entities = vulkanScene.getEntitiesByModelId(entityId)
                val entity = entities?.find { it.getId() == entityId }
                if (entity != null) {
                    vulkanScene.removeEntity(entity)
                    println("Removed existing entity for chunk $chunkId direction $direction")
                }
            }
        }
        
        // Store the meshes for future reference
        directionalChunkMeshCache[chunkId] = directionalMeshes
        
        // Track loaded entities for this chunk
        val loadedEntities = mutableMapOf<FaceDirection, String>()
        
        // Load each directional mesh
        for ((direction, modelData) in directionalMeshes) {
            // Check if this model was already loaded
            val modelAlreadyLoaded = loadedModels.containsKey(modelData.modelId)
            
            if (!modelAlreadyLoaded) {
                try {
                    println("Loading directional mesh for $direction with ${modelData.meshDataList[0].positions.size / 3} vertices")
                    vulkanRender.loadModels(listOf(modelData))
                    loadedModels[modelData.modelId] = true
                    modelRefCount[modelData.modelId] = 1
                } catch (e: Exception) {
                    println("ERROR: Failed to load directional chunk model: ${e.message}")
                    if (e.message?.contains("-1000069000") == true || 
                        e.message?.contains("descriptor set") == true) {
                        println("Descriptor pool exhausted!")
                        throw RuntimeException("Descriptor pool exhausted", e)
                    }
                    throw e
                }
            } else {
                println("Model ${modelData.modelId} already loaded, reusing")
                modelRefCount[modelData.modelId] = (modelRefCount[modelData.modelId] ?: 0) + 1
            }
            
            // Create entity for this directional mesh
            val entityId = "${chunkId}_${direction.name.lowercase()}"
            val entity = Entity(entityId, modelData.modelId, Vector3f(0.0f, 0.0f, 0.0f))
            
            // Add entity to scene
            vulkanScene.addEntity(entity)
            loadedEntities[direction] = entityId
            
            println("Added entity for chunk $chunkId direction $direction")
        }
        
        // Track this chunk
        directionalLoadedChunks[chunkId] = loadedEntities
        synchronized(chunkLoadOrder) {
            chunkLoadOrder.add(chunkId)
        }
        
        // Mark chunk as clean
        chunk.markClean()
        
        println("Created ${loadedEntities.size} directional entities for chunk $chunkId")
        println("Chunks loaded: ${chunkLoadOrder.size}/$MAX_LOADED_CHUNKS")
        
        return chunkId
    }
    
    /**
     * Create a single mesh for a chunk (original implementation)
     */
    private fun createSingleChunkMesh(chunk: Chunk): String {
        println("Building mesh for chunk at (${chunk.position.x}, ${chunk.position.y})")
        
        // Check if we can load more chunks
        if (!canLoadMoreChunks()) {
            println("Chunk limit reached, unloading old chunks")
            unloadOldestChunks(50) // Unload more chunks at once for efficiency
        }
        
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
        
        // Check if this model was already loaded to the renderer
        val modelAlreadyLoaded = loadedModels.containsKey(modelData.modelId)
        
        // Load model to renderer only if not already loaded
        if (!modelAlreadyLoaded) {
            println("Loading chunk model with ID: ${modelData.modelId}")
            
            // Check if mesh has actual vertices before loading
            if (modelData.meshDataList.isNotEmpty() && modelData.meshDataList[0].positions.isNotEmpty()) {
                try {
                    println("Loading chunk with ${modelData.meshDataList[0].positions.size / 3} vertices")
                    vulkanRender.loadModels(listOf(modelData))
                    
                    // Track that this model is loaded
                    loadedModels[modelData.modelId] = true
                    
                    // Track descriptor set usage
                    loadedChunks[chunkId] = modelData.modelId
                    synchronized(chunkLoadOrder) {
                        chunkLoadOrder.add(chunkId)
                    }
                    modelRefCount[modelData.modelId] = 1
                    
                    println("Chunks loaded: ${loadedChunks.size}/$MAX_LOADED_CHUNKS")
                } catch (e: Exception) {
                    println("ERROR: Failed to load chunk model: ${e.message}")
                    // Check for descriptor pool exhaustion
                    if (e.message?.contains("-1000069000") == true || 
                        e.message?.contains("descriptor set") == true) {
                        println("Descriptor pool exhausted!")
                        throw RuntimeException("Descriptor pool exhausted", e)
                    }
                    throw e
                }
            } else {
                println("WARNING: Trying to load empty chunk mesh. Skipping.")
                return ""
            }
        } else {
            println("Model ${modelData.modelId} already loaded to renderer, reusing")
            // Still track this chunk as using the model
            loadedChunks[chunkId] = modelData.modelId
            synchronized(chunkLoadOrder) {
                chunkLoadOrder.add(chunkId)
            }
            // Increment reference count
            modelRefCount[modelData.modelId] = (modelRefCount[modelData.modelId] ?: 0) + 1
        }
        
        // Create entity for this chunk at origin since mesh vertices are already in world coordinates
        // The mesh vertices are positioned at world coordinates, so entity should be at origin
        val entity = Entity(chunkId, modelData.modelId, Vector3f(0.0f, 0.0f, 0.0f))
        
        println("\n=== CHUNK POSITIONING DEBUG ===")
        println("Chunk coordinate: (${chunk.position.x}, ${chunk.position.y})")
        println("Expected world bounds: (${chunk.getWorldX()}, ${chunk.getWorldZ()}) to (${chunk.getWorldX() + 15}, ${chunk.getWorldZ() + 15})")
        println("Mesh vertices will be positioned at these world coordinates")
        println("Entity position: (0, 0, 0) - no additional offset needed")
        println("================================\n")
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
        removeChunkMesh(chunkId)
    }
    
    /**
     * Remove a chunk mesh from the scene by entity ID
     * 
     * @param entityId The entity ID of the chunk to remove
     */
    fun removeChunkMesh(entityId: String) {
        val chunkId = entityId
        
        // Remove from load order tracking
        synchronized(chunkLoadOrder) {
            chunkLoadOrder.remove(chunkId)
        }
        
        // Use internal removal method
        removeChunkMeshInternal(chunkId)
    }
    
    /**
     * Get a unique ID for a chunk
     * 
     * @param chunk The chunk
     * @return A unique string ID
     */
    private fun getChunkId(chunk: Chunk): String {
        // Use chunk position from the chunk object, not the world position
        // position.x and position.y are the chunk coordinates
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
                // Only create mesh if this chunk isn't already loaded
                val chunkId = getChunkId(chunk)
                if (!loadedChunks.containsKey(chunkId)) {
                    try {
                        createChunkMesh(chunk)
                    } catch (e: Exception) {
                        println("Failed to create mesh for loaded chunk: ${e.message}")
                    }
                }
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
        
        if (useDirectionalCulling) {
            // Remove all directional chunk entities
            for ((chunkId, directionalEntities) in directionalLoadedChunks) {
                for ((direction, entityId) in directionalEntities) {
                    // Search through all model entities to find this entity
                    var found = false
                    for ((modelId, entities) in vulkanScene.entitiesMap) {
                        val entity = entities?.find { it.getId() == entityId }
                        if (entity != null) {
                            vulkanScene.removeEntity(entity)
                            println("Removed directional entity for chunk $chunkId direction $direction during reset")
                            found = true
                            break
                        }
                    }
                    
                    if (!found) {
                        println("Warning: Could not find entity $entityId during reset")
                    }
                }
            }
            
            // Clear directional caches
            directionalChunkMeshCache.clear()
            directionalLoadedChunks.clear()
        } else {
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
            
            // Clear the caches
            chunkMeshCache.clear()
            loadedChunks.clear()
        }
        
        // Clear common caches
        loadedModels.clear()
        modelRefCount.clear()
        chunkLoadOrder.clear()
        
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
    
    /**
     * Toggle between directional and single mesh mode
     */
    fun setDirectionalCullingEnabled(enabled: Boolean) {
        if (useDirectionalCulling != enabled) {
            println("Switching directional culling to: $enabled")
            
            // Reset current rendering
            resetChunkRendering()
            
            // Update flag
            useDirectionalCulling = enabled
        }
    }
    
    /**
     * Update visibility of directional chunk meshes using proper frustum culling
     * This should be called each frame after updating the scene's frustum
     * 
     * @param cameraForward The forward direction vector of the camera (normalized)
     * @param cameraPosition The position of the camera
     */
    fun updateDirectionalChunkVisibility(cameraForward: Vector3f, cameraPosition: Vector3f) {
        if (!useDirectionalCulling) return
        
        // Update frustum culling planes first
        vulkanScene.updateFrustum()
        val frustumCuller = vulkanScene.frustumCuller
        
        var visibleFaces = 0
        var totalFaces = 0
        var culledByFrustum = 0
        
        // For each loaded chunk, calculate which faces should be visible
        for ((chunkId, directionalEntities) in directionalLoadedChunks) {
            // Parse chunk position from ID
            val parts = chunkId.split("_")
            if (parts.size >= 3) {
                val chunkX = parts[1].toIntOrNull() ?: continue
                val chunkZ = parts[2].toIntOrNull() ?: continue
                
                // Calculate chunk bounds
                val chunkMinX = chunkX * Chunk.SIZE.toFloat()
                val chunkMinZ = chunkZ * Chunk.SIZE.toFloat()
                val chunkMinY = 0f  // Assuming ground level, could be improved
                val chunkMaxX = chunkMinX + Chunk.SIZE
                val chunkMaxZ = chunkMinZ + Chunk.SIZE
                val chunkMaxY = chunkMinY + Chunk.HEIGHT // Use full chunk height
                
                // First check if the entire chunk is in the frustum
                val chunkInFrustum = frustumCuller.isChunkInFrustum(
                    chunkMinX, chunkMinY, chunkMinZ,
                    chunkMaxX, chunkMaxY, chunkMaxZ
                )
                
                if (!chunkInFrustum) {
                    // Chunk is completely outside frustum, cull all faces
                    culledByFrustum += directionalEntities.size
                    totalFaces += directionalEntities.size
                    continue
                }
                
                // Chunk is in frustum, now check directional visibility
                val chunkCenterX = chunkMinX + Chunk.SIZE / 2.0f
                val chunkCenterZ = chunkMinZ + Chunk.SIZE / 2.0f
                val chunkCenterY = cameraPosition.y
                
                // Vector from camera to chunk center
                val toChunk = Vector3f(
                    chunkCenterX - cameraPosition.x,
                    chunkCenterY - cameraPosition.y,
                    chunkCenterZ - cameraPosition.z
                )
                
                // Check visibility for each direction within the frustum
                for ((direction, entityId) in directionalEntities) {
                    totalFaces++
                    val isVisible = shouldDirectionBeVisible(direction, cameraForward, toChunk)
                    if (isVisible) {
                        visibleFaces++
                    }
                    
                    // TODO: In a full implementation, we would set a visibility flag on the entity
                    // or communicate with the renderer to skip invisible entities
                }
            }
        }
        
        // Log culling effectiveness periodically
        if (System.currentTimeMillis() % 10000 < 50) { // Every 10 seconds
            val culledPercentage = if (totalFaces > 0) {
                ((totalFaces - visibleFaces) * 100.0f / totalFaces)
            } else 0.0f
            
            val frustumCulledPercentage = if (totalFaces > 0) {
                (culledByFrustum * 100.0f / totalFaces)
            } else 0.0f
            
            println("Culling stats: $visibleFaces/$totalFaces faces visible")
            println("  Frustum culled: $culledByFrustum faces (${String.format("%.1f", frustumCulledPercentage)}%)")
            println("  Directional culled: ${totalFaces - visibleFaces - culledByFrustum} faces (${String.format("%.1f", culledPercentage - frustumCulledPercentage)}%)")
            println("  Total culled: ${String.format("%.1f", culledPercentage)}%")
        }
    }
    
    /**
     * Create a mesh for a cubic chunk and add it to the scene
     * 
     * @param chunk The cubic chunk to create a mesh for
     * @return The entity ID of the created mesh in the scene
     */
    @Synchronized
    fun createCubicChunkMesh(chunk: CubicChunk): String {
        println("Building mesh for cubic chunk at (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z})")
        
        // Skip empty chunks
        if (chunk.isEmpty()) {
            println("Skipping empty cubic chunk")
            return ""
        }
        
        // Check if we can load more chunks
        if (!canLoadMoreChunks()) {
            println("Chunk limit reached, unloading old chunks")
            unloadOldestChunks(50)
        }
        
        // Generate mesh from the cubic chunk data
        val modelData = if (useDirectionalCulling) {
            // For now, use single mesh for cubic chunks
            // TODO: Implement directional meshes for cubic chunks
            cubicChunkMeshBuilder.buildMesh(chunk)
        } else {
            cubicChunkMeshBuilder.buildMesh(chunk)
        }
        
        // Skip if no mesh data (empty chunk)
        if (modelData.meshDataList.isEmpty() || modelData.meshDataList[0].positions.isEmpty()) {
            println("Skipping cubic chunk with no visible faces")
            return ""
        }
        
        println("Cubic mesh generated with ${modelData.meshDataList[0].positions.size / 3} vertices")
        
        // Get a unique ID for this chunk
        val chunkId = getCubicChunkId(chunk)
        
        // Check if this chunk mesh is already loaded
        val existingMesh = cubicChunkMeshCache[chunkId]
        if (existingMesh != null && !chunk.isDirty() && existingMesh.modelId == modelData.modelId) {
            println("Cubic chunk $chunkId is already loaded with the same mesh. Skipping.")
            return chunkId
        }
        
        // Store the mesh for future reference
        cubicChunkMeshCache[chunkId] = modelData
        
        // Check if entity already exists
        val entities = vulkanScene.getEntitiesByModelId(modelData.modelId)
        val existingEntity = entities?.find { it.getId() == chunkId }
        if (existingEntity != null) {
            vulkanScene.removeEntity(existingEntity)
            println("Removed existing entity for cubic chunk $chunkId")
        }
        
        // Check if this model was already loaded to the renderer
        val modelAlreadyLoaded = loadedModels.containsKey(modelData.modelId)
        
        // Load model to renderer only if not already loaded
        if (!modelAlreadyLoaded) {
            try {
                println("Loading cubic chunk with ${modelData.meshDataList[0].positions.size / 3} vertices")
                vulkanRender.loadModels(listOf(modelData))
                
                loadedModels[modelData.modelId] = true
                loadedChunks[chunkId] = modelData.modelId
                synchronized(chunkLoadOrder) {
                    chunkLoadOrder.add(chunkId)
                }
                modelRefCount[modelData.modelId] = 1
                
                println("Cubic chunks loaded: ${loadedChunks.size}/$MAX_LOADED_CHUNKS")
            } catch (e: Exception) {
                println("ERROR: Failed to load cubic chunk model: ${e.message}")
                if (e.message?.contains("-1000069000") == true || 
                    e.message?.contains("descriptor set") == true) {
                    println("Descriptor pool exhausted!")
                    throw RuntimeException("Descriptor pool exhausted", e)
                }
                throw e
            }
        } else {
            println("Model ${modelData.modelId} already loaded to renderer, reusing")
            loadedChunks[chunkId] = modelData.modelId
            synchronized(chunkLoadOrder) {
                chunkLoadOrder.add(chunkId)
            }
            modelRefCount[modelData.modelId] = (modelRefCount[modelData.modelId] ?: 0) + 1
        }
        
        // Create entity for this chunk at origin since mesh vertices are already in world coordinates
        val entity = Entity(chunkId, modelData.modelId, Vector3f(0.0f, 0.0f, 0.0f))
        
        println("\n=== CUBIC CHUNK POSITIONING DEBUG ===")
        println("Chunk coordinate: (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z})")
        println("World bounds: (${chunk.getWorldX()}, ${chunk.getWorldY()}, ${chunk.getWorldZ()}) to " +
                "(${chunk.getWorldX() + 15}, ${chunk.getWorldY() + 15}, ${chunk.getWorldZ() + 15})")
        println("Entity position: (0, 0, 0) - no additional offset needed")
        println("=====================================\n")
        
        // Add entity to scene
        vulkanScene.addEntity(entity)
        println("Added entity for cubic chunk $chunkId with model ID ${modelData.modelId}")
        
        // Mark chunk as clean
        chunk.markClean()
        
        return chunkId
    }
    
    /**
     * Get a unique ID for a cubic chunk
     */
    private fun getCubicChunkId(chunk: CubicChunk): String {
        return "cubic_chunk_${chunk.position.x}_${chunk.position.y}_${chunk.position.z}"
    }
    
    /**
     * Determine if a face direction should be visible based on camera orientation
     */
    private fun shouldDirectionBeVisible(
        direction: FaceDirection,
        cameraForward: Vector3f,
        toChunk: Vector3f
    ): Boolean {
        // Get the normal vector for this face direction
        val faceNormal = when (direction) {
            FaceDirection.UP -> Vector3f(0f, 1f, 0f)
            FaceDirection.DOWN -> Vector3f(0f, -1f, 0f)
            FaceDirection.NORTH -> Vector3f(0f, 0f, -1f)
            FaceDirection.SOUTH -> Vector3f(0f, 0f, 1f)
            FaceDirection.EAST -> Vector3f(1f, 0f, 0f)
            FaceDirection.WEST -> Vector3f(-1f, 0f, 0f)
        }
        
        // For top/bottom faces, use a different strategy
        if (direction == FaceDirection.UP || direction == FaceDirection.DOWN) {
            // Always render top faces if camera is above chunk center
            // Always render bottom faces if camera is below chunk center
            return if (direction == FaceDirection.UP) {
                toChunk.y < 0 // Camera is above chunk
            } else {
                toChunk.y > 0 // Camera is below chunk
            }
        }
        
        // For side faces, check if the face normal points towards the camera
        // If dot product is negative, the face is pointing towards the camera
        val dotProduct = faceNormal.dot(toChunk.normalize())
        
        // Face is visible if it's pointing towards the camera (negative dot product)
        // Add a small threshold to prevent flickering at edges
        return dotProduct < 0.1f
    }
}
