package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.integration.VulkanIntegration
import it.filippocavallari.cubicworld.integration.VulkanChunkMeshBuilder
import it.filippocavallari.cubicworld.models.ModelManager
import it.filippocavallari.cubicworld.textures.TextureAtlasLoader
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.BiodiverseWorldGenerator
import it.filippocavallari.cubicworld.world.generators.WorldGeneratorRegistry
import it.filippocavallari.cubicworld.data.block.BlockType
import org.vulkanb.eng.Engine
import org.vulkanb.eng.IAppLogic
import org.vulkanb.eng.Window
import org.vulkanb.eng.graph.Render
import org.vulkanb.eng.scene.Scene
import org.joml.Vector3f
import org.joml.Vector2f
import org.vulkanb.eng.scene.Entity
import org.vulkanb.eng.scene.Light
import org.vulkanb.eng.scene.Camera
import org.vulkanb.eng.scene.ModelData
import java.util.concurrent.ConcurrentHashMap
import org.lwjgl.glfw.GLFW

/**
 * Main engine class for CubicWorld.
 * Integrates the Vulkan engine with the game-specific components.
 */
class CubicWorldEngine : IAppLogic {
    // Engine instances
    private lateinit var engine: Engine
    
    // Game world
    private lateinit var gameWorld: World
    
    // Vulkan integration component
    private lateinit var vulkanIntegration: VulkanIntegration
    
    // Vulkan scene reference
    private lateinit var vulkanScene: Scene
    
    // Constants
    companion object {
        private const val MOUSE_SENSITIVITY = 0.1f
        private const val MOVEMENT_SPEED = 0.05f  // Increased movement speed for navigating the larger world
        private const val WORLD_RENDER_DISTANCE = 16 // Increased render distance for the larger grid
        
        // Mouse cursor state
        private var mouseCaptured = true
    }
    
    /**
     * Start the engine
     */
    fun start() {
        try {
            engine = Engine("CubicWorld Voxel Game", this)
            engine.start()
        } catch (e: Exception) {
            e.printStackTrace()
            System.exit(-1)
        }
    }
    
    /**
     * Initialize the engine and game components
     */
    override fun init(window: Window, scene: Scene, render: Render) {
        println("Initializing CubicWorld Engine with minimal resources")
        
        try {
            // Store the scene reference
            this.vulkanScene = scene
            
            // Create Vulkan integration component
            vulkanIntegration = VulkanIntegration()
            
            // Initialize Vulkan integration
            vulkanIntegration.initialize(render, scene)
            
            // Initialize the world generator registry
            WorldGeneratorRegistry.initialize()
            
            // Create biodiverse world generator with random seed
            val seed = System.currentTimeMillis()
            println("Using seed for world generation: $seed")
            val worldGenerator = WorldGeneratorRegistry.create("biodiverse", seed)
            
            // Create and initialize world
            gameWorld = World(worldGenerator)
            
            // Connect world to Vulkan integration
            vulkanIntegration.connectWorld(gameWorld)
            
            // Setup camera
            setupCamera(scene.camera)
            
            // Setup lighting
            setupLighting(scene)
            
            // Set up mouse cursor capture for first-person camera mode
            val windowHandle = window.windowHandle
            // Disable cursor visibility
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
            // Center the cursor
            GLFW.glfwSetCursorPos(windowHandle, (window.width / 2).toDouble(), (window.height / 2).toDouble())
            
            // Create and load chunks in a smaller initial grid to avoid overwhelming the system
            println("Generating a 3x3 grid of chunks centered at the origin")
            println("This will generate 9 chunks for initial testing to verify seamless transitions")
            
            // Define grid parameters - start small to test seamless chunk boundaries
            val gridSize = 3
            val halfGrid = gridSize / 2
            var chunksGenerated = 0
            val totalChunks = gridSize * gridSize
            
            // Define the range for chunk coordinates (-1 to 1 for both X and Z)
            val startCoord = -halfGrid
            val endCoord = halfGrid
            
            // Generate chunks in a grid pattern with buffer management
            var successfulChunks = 0
            var failedChunks = 0
            val chunkGenerationOrder = mutableListOf<Pair<Int, Int>>()
            
            // Generate chunks in a spiral pattern from center outward for better visibility
            for (radius in 0..halfGrid) {
                for (x in -radius..radius) {
                    for (z in -radius..radius) {
                        // Only add chunks on the edge of this radius
                        if (kotlin.math.abs(x) == radius || kotlin.math.abs(z) == radius) {
                            if (x in startCoord..endCoord && z in startCoord..endCoord) {
                                chunkGenerationOrder.add(Pair(x, z))
                            }
                        }
                    }
                }
            }
            
            println("Generating ${chunkGenerationOrder.size} chunks in spiral order from center")
            
            // Generate chunks with improved error handling and validation
            for ((x, z) in chunkGenerationOrder) {
                try {
                    println("\n=== Creating chunk at ($x, $z) ===")
                    println("World position will be: (${x * 16}, ${z * 16}) to (${x * 16 + 15}, ${z * 16 + 15})")
                    
                    // Create the chunk
                    createBasicChunkAt(x, z)
                    successfulChunks++
                    
                    // Larger delay to allow proper processing and avoid overwhelming Vulkan
                    Thread.sleep(200) // 200ms delay between chunks
                    
                } catch (e: Exception) {
                    println("Failed to create chunk at ($x, $z): ${e.message}")
                    e.printStackTrace()
                    failedChunks++
                    
                    // If we're getting descriptor pool errors, we need to stop
                    if (e.message?.contains("descriptor set") == true || 
                        e.message?.contains("-1000069000") == true ||
                        e.message?.contains("VK_ERROR") == true) {
                        println("ERROR: Vulkan error encountered after $successfulChunks chunks")
                        println("Stopping chunk generation to prevent further errors")
                        break
                    }
                }
                
                // Update progress counter (report every chunk for small grid)
                val chunksGenerated = successfulChunks + failedChunks
                println("Progress: $chunksGenerated/${chunkGenerationOrder.size} chunks attempted")
                println("  Successful: $successfulChunks, Failed: $failedChunks")
            }
            
            println("\nChunk generation complete:")
            println("  Total attempted: ${successfulChunks + failedChunks}")
            println("  Successful: $successfulChunks")
            println("  Failed: $failedChunks")
            
            if (failedChunks > 0) {
                println("\nWARNING: Some chunks failed to load. This is likely due to:")
                println("  - Descriptor pool exhaustion (VK_ERROR_FRAGMENTED_POOL)")
                println("  - Vertex/Index buffer overflow")
                println("  Consider reducing chunk complexity or implementing chunk LOD")
            }
            
            // Initialize is complete
            println("CubicWorld Engine initialized successfully")
        } catch (e: Exception) {
            println("Error during initialization: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Create a chunk at the specified coordinates using the terrain generator
     */
    private fun createBasicChunkAt(x: Int, z: Int) {
        try {
            println("Creating chunk using biodiverse generator at ($x, $z)")
            println("  Chunk coordinate: ($x, $z)")
            println("  World position: (${x * Chunk.SIZE}, ${z * Chunk.SIZE}) to (${x * Chunk.SIZE + 15}, ${z * Chunk.SIZE + 15})")
            
            // Load the chunk synchronously using the world generator
            val chunk = gameWorld.loadChunkSynchronously(x, z)
            
            // Validate chunk coordinates
            validateChunkCoordinates(chunk, x, z)
            
            // Verify the chunk was created properly
            var blockCount = 0
            var maxHeight = 0
            var minHeight = Chunk.HEIGHT
            val heightMap = mutableMapOf<Int, Int>()
            
            for (bx in 0 until Chunk.SIZE) {
                for (bz in 0 until Chunk.SIZE) {
                    for (by in 0 until Chunk.HEIGHT) {
                        if (chunk.getBlock(bx, by, bz) != 0) {
                            blockCount++
                            if (by > maxHeight) maxHeight = by
                            if (by < minHeight) minHeight = by
                            heightMap[by] = (heightMap[by] ?: 0) + 1
                        }
                    }
                }
            }
            
            if (blockCount == 0) {
                println("ERROR: Chunk at ($x, $z) is completely empty!")
            } else {
                println("Chunk at ($x, $z) terrain analysis:")
                println("  Total blocks: $blockCount")
                println("  Height range: $minHeight to $maxHeight")
                println("  Surface area coverage: ${String.format("%.1f", (blockCount.toFloat() / (Chunk.SIZE * Chunk.SIZE)) * 100)}%")
            }
            
            // Create the mesh
            val entityId = vulkanIntegration.createChunkMesh(chunk)
            if (entityId.isEmpty()) {
                println("WARNING: Failed to create mesh for chunk at ($x, $z) - mesh might be empty or failed")
            } else {
                println("SUCCESS: Created mesh entity '$entityId' for chunk at ($x, $z)")
            }
            
        } catch (e: Exception) {
            println("ERROR: Failed to create chunk at ($x, $z): ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Validate that chunk coordinates are correctly set
     */
    private fun validateChunkCoordinates(chunk: Chunk, expectedX: Int, expectedZ: Int) {
        if (chunk.position.x != expectedX || chunk.position.y != expectedZ) {
            println("ERROR: Chunk coordinate mismatch!")
            println("  Expected: ($expectedX, $expectedZ)")
            println("  Actual: (${chunk.position.x}, ${chunk.position.y})")
        }
        
        val expectedWorldX = expectedX * Chunk.SIZE
        val expectedWorldZ = expectedZ * Chunk.SIZE
        val actualWorldX = chunk.getWorldX()
        val actualWorldZ = chunk.getWorldZ()
        
        if (actualWorldX != expectedWorldX || actualWorldZ != expectedWorldZ) {
            println("ERROR: World coordinate calculation error!")
            println("  Expected world position: ($expectedWorldX, $expectedWorldZ)")
            println("  Actual world position: ($actualWorldX, $actualWorldZ)")
        } else {
            println("  Coordinates validated: chunk ($expectedX, $expectedZ) -> world ($actualWorldX, $actualWorldZ)")
        }
    }
    
    /**
     * Create and load a test chunk synchronously using the flat world generator
     * This method is retained for compatibility but is now redundant
     */
    private fun createAndLoadTestChunk() {
        // Delegate to createBasicChunkAt to avoid code duplication
        createBasicChunkAt(0, 0)
    }
    
    /**
     * Load initial chunks around origin - simpler version that loads synchronously
     * to avoid threading issues
     */
    private fun loadInitialChunks() {
        println("Loading initial chunks")
        
        // Limit to just a few chunks for testing
        for (x in 0..1) {
            for (z in 0..1) {
                // Skip the origin since we already loaded it
                if (x == 0 && z == 0) continue
                
                // Use synchronous loading for initial chunks
                val chunk = Chunk(x, z, gameWorld)
                
                // Fill with some basic test blocks
                for (bx in 0 until Chunk.SIZE) {
                    for (bz in 0 until Chunk.SIZE) {
                        chunk.setBlock(bx, 0, bz, BlockType.GRASS.id)
                    }
                }
                
                // Mark the chunk as dirty to ensure mesh building
                chunk.markDirty()
                
                // Add the chunk directly without async processing
                gameWorld.addChunkDirectly(chunk)
                
                // Create the mesh
                vulkanIntegration.createChunkMesh(chunk)
            }
        }
    }
    
    /**
     * Setup the camera
     */
    private fun setupCamera(camera: Camera) {
        // Position the camera at the center of the 3x3 grid, elevated for a better view
        // Center of the grid is at (0, 0) in chunk coordinates, which translates to (0, 0) in block coordinates
        // Set height to see the entire 3x3 chunk grid clearly
        camera.position.set(0.0f, 45.0f, 0.0f)
        // Look down at a moderate angle for a good overview
        camera.setRotation(1.2f, 0.0f)
        
        println("Camera positioned above the center of the 3x3 chunk grid at (${camera.position.x}, ${camera.position.y}, ${camera.position.z})")
        println("Set to overview angle to observe seamless chunk transitions")
        println("Expected to see chunks from (-16,-16) to (31,31) in world coordinates")
    }
    
    /**
     * Setup lighting for the scene
     */
    private fun setupLighting(scene: Scene) {
        // Set ambient light
        scene.ambientLight.set(0.3f, 0.3f, 0.3f, 1.0f)
        
        // Add a directional light (sun)
        val directionalLight = Light()
        directionalLight.color.set(1.0f, 1.0f, 0.8f, 1.0f)
        directionalLight.position.set(0.0f, 1.0f, 0.5f, 0.0f) // Direction is -position
        directionalLight.position.normalize()
        directionalLight.position.w = 0.0f // Directional light
        
        // Set scene lights
        scene.lights = arrayOf(directionalLight)
    }
    
    /**
     * Handle input events
     */
    override fun input(window: Window, scene: Scene, diffTimeMillis: Long, inputConsumed: Boolean) {
        if (inputConsumed) {
            return
        }
        
        val camera = scene.camera
        val move = diffTimeMillis * MOVEMENT_SPEED
        
        // Handle escape key to toggle mouse capture
        if (window.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            // Toggle mouse capture state
            mouseCaptured = !mouseCaptured
            
            // Update cursor mode based on the toggle state
            if (mouseCaptured) {
                // Disable cursor for camera control
                GLFW.glfwSetInputMode(window.windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
            } else {
                // Show normal cursor
                GLFW.glfwSetInputMode(window.windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            }
            
            // Small delay to prevent multiple toggles
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        
        // Camera movement with WASD
        if (window.isKeyPressed(GLFW.GLFW_KEY_W)) {
            camera.moveForward(move)
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_S)) {
            camera.moveBackwards(move)
        }
        
        if (window.isKeyPressed(GLFW.GLFW_KEY_A)) {
            camera.moveLeft(move)
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_D)) {
            camera.moveRight(move)
        }
        
        if (window.isKeyPressed(GLFW.GLFW_KEY_SPACE)) {
            camera.moveUp(move)
        } else if (window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
            camera.moveDown(move)
        }
        
        // Camera rotation with mouse (only when mouse is captured)
        if (mouseCaptured) {
            val mouseInput = window.mouseInput
            val displVec = mouseInput.displVec
            
            // Only process mouse movement if there's actual displacement
            if (displVec.x != 0.0f || displVec.y != 0.0f) {
                // Apply mouse movements to camera rotation
                // EXPLICITLY INVERT the horizontal component (yaw) here
                camera.addRotation(
                    Math.toRadians((displVec.x * MOUSE_SENSITIVITY).toDouble()).toFloat(),     // Pitch (up/down)
                    Math.toRadians((-displVec.y * MOUSE_SENSITIVITY).toDouble()).toFloat()     // Yaw (INVERTED)
                )
                
                // Reset cursor position to center after processing movement
                // This prevents the cursor from hitting the window edges
                mouseInput.resetCursorPosition()
            }
        }
    }
    
    /**
     * Update game logic
     */
    override fun update(window: Window, scene: Scene, diffTimeMillis: Long) {
        // Simplified update - just update the world
        gameWorld.updateChunks(diffTimeMillis / 1000.0f)
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        println("Cleaning up CubicWorld Engine")
        
        // Clean up Vulkan integration
        if (::vulkanIntegration.isInitialized) {
            vulkanIntegration.cleanup()
        }
        
        // Clean up world resources
        if (::gameWorld.isInitialized) {
            gameWorld.cleanup()
        }
    }
}