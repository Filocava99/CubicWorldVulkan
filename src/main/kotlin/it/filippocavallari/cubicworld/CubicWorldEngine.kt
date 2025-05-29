package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.integration.VulkanIntegration
import it.filippocavallari.cubicworld.integration.VulkanChunkMeshBuilder
import it.filippocavallari.cubicworld.models.ModelManager
import it.filippocavallari.cubicworld.textures.TextureAtlasLoader
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.ChunkManager
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
    
    // Chunk management system
    private lateinit var chunkManager: ChunkManager
    
    // Vulkan integration component
    private lateinit var vulkanIntegration: VulkanIntegration
    
    // Vulkan scene reference
    private lateinit var vulkanScene: Scene
    
    // Constants
    companion object {
        private const val MOUSE_SENSITIVITY = 0.1f
        private const val MOVEMENT_SPEED = 0.08f  // Increased movement speed for navigating the larger world
        private const val WORLD_RENDER_DISTANCE = 2 // 4x4 grid (2 chunks in each direction from center)
        
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
            
            // Enable directional culling for better performance
            vulkanIntegration.setDirectionalCullingEnabled(true)
            println("Directional face culling enabled for improved performance")
            
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
            
            // Initialize the chunk manager with the new 4x4 dynamic loading system
            chunkManager = ChunkManager(gameWorld, vulkanIntegration)
            
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
            
            // Initialize the enhanced 4x4 chunk loading system
            println("Initializing enhanced 4x4 dynamic chunk loading system")
            println("This system will load chunks in a circular pattern and dynamically adjust as you move")
            
            // Initialize the chunk manager at spawn point (0, 0, 0)
            chunkManager.initialize(0, 0, 0)
            
            println("\n=== ENHANCED CHUNK LOADING SYSTEM ===\n")
            println("Features:")
            val effectiveRenderDistY = WORLD_RENDER_DISTANCE // Assuming Y render distance matches XZ for this print
            println("  ✓ ${WORLD_RENDER_DISTANCE * 2 + 1}x${effectiveRenderDistY * 2 + 1}x${WORLD_RENDER_DISTANCE * 2 + 1} chunk grid")
            println("  ✓ Circular loading pattern (center-out priority)")
            println("  ✓ Dynamic loading when approaching chunk borders")
            println("  ✓ Automatic unloading of distant chunks")
            println("  ✓ Memory-efficient chunk management")
            println("")
            println("Initial chunks loaded: ${chunkManager.getLoadedChunkInfo()}")
            println("=====================================\n")
            
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
    private fun createBasicChunkAt(x: Int, y: Int, z: Int) {
        try {
            println("Creating chunk using biodiverse generator at ($x, $y, $z)")
            println("  Chunk coordinate: ($x, $y, $z)")
            println("  World position: (${x * Chunk.SIZE}, ${y * Chunk.HEIGHT}, ${z * Chunk.SIZE}) to (${x * Chunk.SIZE + Chunk.SIZE -1}, ${y * Chunk.HEIGHT + Chunk.HEIGHT -1}, ${z * Chunk.SIZE + Chunk.SIZE -1})")
            
            // Load the chunk synchronously using the world generator
            val chunk = gameWorld.loadChunkSynchronously(x, y, z)
            
            // Validate chunk coordinates
            validateChunkCoordinates(chunk, x, y, z)
            
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
                println("ERROR: Chunk at ($x, $y, $z) is completely empty!")
            } else {
                println("Chunk at ($x, $y, $z) terrain analysis:")
                println("  Total blocks: $blockCount")
                println("  Height range: $minHeight to $maxHeight")
                println("  Surface area coverage: ${String.format("%.1f", (blockCount.toFloat() / (Chunk.SIZE * Chunk.SIZE)) * 100)}%")
            }
            
            // Create the mesh
            val entityId = vulkanIntegration.createChunkMesh(chunk)
            if (entityId.isEmpty()) {
                println("WARNING: Failed to create mesh for chunk at ($x, $y, $z) - mesh might be empty or failed")
            } else {
                println("SUCCESS: Created mesh entity '$entityId' for chunk at ($x, $y, $z)")
            }
            
        } catch (e: Exception) {
            println("ERROR: Failed to create chunk at ($x, $y, $z): ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Validate that chunk coordinates are correctly set
     */
    private fun validateChunkCoordinates(chunk: Chunk, expectedX: Int, expectedY: Int, expectedZ: Int) {
        if (chunk.position.x != expectedX || chunk.position.y != expectedY || chunk.position.z != expectedZ) {
            println("ERROR: Chunk coordinate mismatch!")
            println("  Expected: ($expectedX, $expectedY, $expectedZ)")
            println("  Actual: (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z})")
        }
        
        val expectedWorldX = expectedX * Chunk.SIZE
        val expectedWorldY = expectedY * Chunk.HEIGHT
        val expectedWorldZ = expectedZ * Chunk.SIZE
        val actualWorldX = chunk.getWorldX()
        val actualWorldY = chunk.getWorldY()
        val actualWorldZ = chunk.getWorldZ()
        
        if (actualWorldX != expectedWorldX || actualWorldY != expectedWorldY || actualWorldZ != expectedWorldZ) {
            println("ERROR: World coordinate calculation error!")
            println("  Expected world position: ($expectedWorldX, $expectedWorldY, $expectedWorldZ)")
            println("  Actual world position: ($actualWorldX, $actualWorldY, $actualWorldZ)")
        } else {
            println("  Coordinates validated: chunk ($expectedX, $expectedY, $expectedZ) -> world ($actualWorldX, $actualWorldY, $actualWorldZ)")
        }
    }
    
    /**
     * Create and load a test chunk synchronously using the flat world generator
     * This method is retained for compatibility but is now redundant
     */
    private fun createAndLoadTestChunk() {
        // Delegate to createBasicChunkAt to avoid code duplication, assuming Y=0 for this test
        createBasicChunkAt(0, 0, 0)
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
                if (x == 0 && z == 0) continue // Assuming Y=0 for this specific loop's context
                
                // Use synchronous loading for initial chunks, assuming Y=0
                val chunk = Chunk(x, 0, z, gameWorld)
                
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
        // Position the camera at the center of the 4x4 grid, elevated for a better view
        // Center of the grid is at (0, 0) in chunk coordinates, which translates to (0, 0) in block coordinates
        // Set height to see the entire 4x4 chunk grid clearly
        camera.position.set(8.0f, 50.0f, 8.0f) // Slightly offset to see the grid better
        // Look down at a moderate angle for a good overview
        camera.setRotation(1.0f, 0.0f)
        
        println("\n=== CAMERA SETUP & EXPECTED LAYOUT ===")
        println("Camera positioned at: (${camera.position.x}, ${camera.position.y}, ${camera.position.z})")
        println("Looking down at 4x4 dynamic chunk grid")
        println("")
        println("Dynamic chunk layout (4x4 grid around player):")
        println("  Chunks will load/unload automatically as you move")
        println("  Total coverage: 4x4 chunks = 64x64 blocks")
        println("  Load distance: ${WORLD_RENDER_DISTANCE} chunks in each direction")
        println("  Circular loading pattern prioritizes closest chunks")
        println("")
        println("Movement controls (FPS-style):")
        println("  W: Move forward (based on horizontal look direction)")
        println("  S: Move backward (based on horizontal look direction)")
        println("  A: Strafe left")
        println("  D: Strafe right")
        println("  Space: Move up (Y+)")
        println("  Shift: Move down (Y-)")
        println("  Mouse: Look around")
        println("  ESC: Toggle mouse capture")
        println("========================================\n")
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
                // Correct axis mapping:
                // - Mouse X movement (horizontal) → Yaw (Y-axis rotation, horizontal camera rotation)
                // - Mouse Y movement (vertical) → Pitch (X-axis rotation, vertical camera rotation)
                camera.addRotation(
                    Math.toRadians((displVec.x * MOUSE_SENSITIVITY).toDouble()).toFloat(),     // Mouse Y → Pitch (vertical rotation)
                    Math.toRadians((displVec.y * MOUSE_SENSITIVITY).toDouble()).toFloat()      // Mouse X → Yaw (horizontal rotation)
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
        // Update the world
        gameWorld.updateChunks(diffTimeMillis / 1000.0f)
        
        // Update chunk manager with current player position for dynamic loading
        val camera = scene.camera
        chunkManager.updatePlayerPosition(camera.position.x, camera.position.y, camera.position.z)
        
        // Update directional chunk visibility based on camera direction
        val cameraForward = Vector3f()
        camera.getViewMatrix().positiveZ(cameraForward).negate() // Get forward direction from view matrix
        vulkanIntegration.updateDirectionalChunkVisibility(cameraForward, camera.position)
        
        // Optional: Print debug info periodically (every 5 seconds)
        if (System.currentTimeMillis() % 5000 < 50) { // Roughly every 5 seconds
            val playerChunkX = Chunk.worldToChunkXZ(camera.position.x.toInt())
            val playerChunkY = Chunk.worldToChunkY(camera.position.y.toInt())
            val playerChunkZ = Chunk.worldToChunkXZ(camera.position.z.toInt())
            println("Player at world (${camera.position.x.toInt()}, ${camera.position.y.toInt()}, ${camera.position.z.toInt()}) = chunk ($playerChunkX, $playerChunkY, $playerChunkZ). ${chunkManager.getLoadedChunkInfo()}")
        }
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        println("Cleaning up CubicWorld Engine")
        
        // Clean up chunk manager
        if (::chunkManager.isInitialized) {
            chunkManager.cleanup()
        }
        
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