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
import org.lwjgl.glfw.GLFW
import org.vulkanb.eng.scene.Entity
import org.vulkanb.eng.scene.Light
import org.vulkanb.eng.scene.Camera
import org.vulkanb.eng.scene.ModelData
import java.util.concurrent.ConcurrentHashMap

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
        private const val MOVEMENT_SPEED = 0.01f
        private const val WORLD_RENDER_DISTANCE = 8 // Chunks rendered in each direction
        
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
            
            // Create and load chunks in a grid around the player
            println("Generating a diverse biome world with ${WORLD_RENDER_DISTANCE*2 + 1}x${WORLD_RENDER_DISTANCE*2 + 1} chunks")
            
            // Start with just the center chunk and adjacent chunks for performance
            createBasicChunkAt(0, 0)  // Origin
            createBasicChunkAt(0, 1)  // North
            createBasicChunkAt(1, 0)  // East
            createBasicChunkAt(-1, 0) // West
            createBasicChunkAt(0, -1) // South
            
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
        println("Creating chunk using biodiverse generator at $x, $z (world position: ${x * Chunk.SIZE}, ${z * Chunk.SIZE})")
        
        // Load the chunk synchronously using the world generator
        val chunk = gameWorld.loadChunkSynchronously(x, z)
        
        // Create the mesh
        vulkanIntegration.createChunkMesh(chunk)
        
        // Print a simple height sample for debugging
        val sampleHeight = chunk.getBlock(Chunk.SIZE / 2, 100, Chunk.SIZE / 2)
        println("Created terrain chunk at $x, $z - Sample height at center: $sampleHeight")
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
        // Position the camera on top of the grass surface (y = GRASS_LEVEL + 2)
        // Center of the 4 chunks is at (16, 0, 16)
        // The surface is at y = 14 + 2 (player height) = 16
        camera.position.set(16.0f, 16.0f, 16.0f)
        // Look slightly downward for better perspective
        camera.setRotation(0.3f, 0.0f)
        
        println("Camera positioned on top of the surface at (${camera.position.x}, ${camera.position.y}, ${camera.position.z})")
        println("Looking forward with slight downward angle")
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
                // Fixed direction: positive displVec.x means moving mouse up, so camera should look up (positive rotation)
                // For horizontal movement, negative displVec.y means moving mouse right, so camera should look right
                camera.addRotation(
                    Math.toRadians((displVec.x * MOUSE_SENSITIVITY).toDouble()).toFloat(),
                    Math.toRadians((-displVec.y * MOUSE_SENSITIVITY).toDouble()).toFloat()
                )
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