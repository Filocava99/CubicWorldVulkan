package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.gui.CrosshairGui
import it.filippocavallari.cubicworld.integration.VulkanIntegration
import it.filippocavallari.cubicworld.integration.VulkanChunkMeshBuilder
import it.filippocavallari.cubicworld.models.ModelManager
import it.filippocavallari.cubicworld.textures.TextureAtlasLoader
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.CubicWorld
import it.filippocavallari.cubicworld.world.ChunkManager
import it.filippocavallari.cubicworld.world.CubicChunkManager
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import it.filippocavallari.cubicworld.world.generators.BiodiverseWorldGenerator
import it.filippocavallari.cubicworld.world.generators.WorldGeneratorRegistry
import it.filippocavallari.cubicworld.world.generators.CubicBiodiverseWorldGenerator
import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.interaction.BlockInteractionManager
import it.filippocavallari.cubicworld.physics.PhysicsEngine
import it.filippocavallari.cubicworld.physics.MovementMode
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
    private lateinit var cubicWorld: CubicWorld
    
    // Chunk management system
    private lateinit var chunkManager: ChunkManager
    private lateinit var cubicChunkManager: CubicChunkManager
    
    // Flag to use cubic chunks
    private val useCubicChunks = true
    
    // Vulkan integration component
    private lateinit var vulkanIntegration: VulkanIntegration
    
    // Vulkan scene reference
    private lateinit var vulkanScene: Scene
    
    // Block interaction manager
    private lateinit var blockInteractionManager: BlockInteractionManager
    
    // Physics system
    private lateinit var physicsEngine: PhysicsEngine
    private var movementMode = MovementMode.FLY
    
    // Input state tracking
    private var lastF1KeyState = false
    
    // Constants
    companion object {
        private const val MOUSE_SENSITIVITY = 0.1f
        private const val MOVEMENT_SPEED = 0.08f  // Increased movement speed for navigating the larger world
        private const val WORLD_RENDER_DISTANCE = 2 // 4x4 grid (2 chunks in each direction from center)
        private const val CUBIC_RENDER_DISTANCE = 8 // Cubic chunks can render further due to smaller size
        
        // Mouse cursor state
        private var mouseCaptured = true
    }
    
    // Mouse click tracking
    private var lastLeftClickState = false
    private var lastRightClickState = false
    
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
            
            // Create world generators based on chunk system
            val seed = System.currentTimeMillis()
            println("Using seed for world generation: $seed")
            
            // Create and initialize world
            if (useCubicChunks) {
                println("\n=== USING CUBIC CHUNKS SYSTEM ===")
                println("Chunk size: 16x16x16 blocks")
                println("Memory savings: ~98% compared to traditional chunks")
                println("Features: Infinite height, better occlusion culling")
                println("=================================\n")
                
                // Use CubicChunk-native world generator
                val cubicWorldGenerator = CubicBiodiverseWorldGenerator(seed)
                cubicWorld = CubicWorld(cubicWorldGenerator)
                
                // Create cubic chunk manager
                cubicChunkManager = CubicChunkManager(cubicWorld, vulkanIntegration)
                
                // Add listener to Vulkan integration
                cubicWorld.addChunkListener(object : CubicWorld.ChunkListener {
                    override fun onChunkLoaded(chunk: CubicChunk) {
                        // Handled by chunk manager
                    }
                    
                    override fun onChunkUnloaded(chunk: CubicChunk) {
                        // Handled by chunk manager
                    }
                    
                    override fun onChunkUpdated(chunk: CubicChunk) {
                        println("DEBUG: Chunk updated - regenerating mesh for chunk at (${chunk.position.x}, ${chunk.position.y}, ${chunk.position.z})")
                        vulkanIntegration.createCubicChunkMesh(chunk)
                    }
                })
            } else {
                println("\n=== USING TRADITIONAL CHUNKS SYSTEM ===")
                println("Chunk size: 16x256x16 blocks")
                println("Dynamic loading: 4x4 chunks around player")
                println("========================================\n")
                
                // Use traditional world generator for regular chunks
                val worldGenerator = WorldGeneratorRegistry.create("biodiverse", seed)
                gameWorld = World(worldGenerator)
                
                // Connect world to Vulkan integration
                vulkanIntegration.connectWorld(gameWorld)
                
                // Initialize the chunk manager with the new 4x4 dynamic loading system
                chunkManager = ChunkManager(gameWorld, vulkanIntegration)
            }
            
            // Setup camera
            setupCamera(scene.camera)
            
            // Setup lighting
            setupLighting(scene)
            
            // Setup crosshair GUI with proper ImGui frame management
            setupCrosshairGui(scene)
            
            // Initialize block interaction manager
            setupBlockInteraction()
            
            // Initialize physics engine
            setupPhysicsEngine()
            
            // Set up mouse cursor capture for first-person camera mode
            val windowHandle = window.windowHandle
            // Disable cursor visibility
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
            // Center the cursor
            GLFW.glfwSetCursorPos(windowHandle, (window.width / 2).toDouble(), (window.height / 2).toDouble())
            
            // Initialize the enhanced chunk loading system
            if (useCubicChunks) {
                println("Initializing cubic chunk loading system")
                println("This system uses 16x16x16 chunks for massive memory savings")
                
                // Initialize at spawn point with good starting height
                cubicChunkManager.initialize(0f, 64f, 0f)
                
                println("\n=== CUBIC CHUNK LOADING SYSTEM ===")
                println("Features:")
                println("  ✓ 16x16x16 cubic chunks (vs 16x256x16 traditional)")
                println("  ✓ Spherical loading pattern")
                println("  ✓ ${CubicChunkManager.HORIZONTAL_RENDER_DISTANCE} chunk radius horizontally")
                println("  ✓ ${CubicChunkManager.VERTICAL_RENDER_DISTANCE} chunk radius vertically")
                println("  ✓ Automatic empty chunk culling")
                println("  ✓ Up to ${CubicChunkManager.MAX_LOADED_CHUNKS} chunks loaded simultaneously")
                println("")
                println("Initial chunks loaded: ${cubicChunkManager.getLoadedChunkInfo()}")
                println("===================================\n")
            } else {
                println("Initializing enhanced 4x4 dynamic chunk loading system")
                println("This system will load chunks in a circular pattern and dynamically adjust as you move")
                
                // Initialize the chunk manager at spawn point (0, 0)
                chunkManager.initialize(0, 0)
                
                println("\n=== ENHANCED CHUNK LOADING SYSTEM ===\n")
                println("Features:")
                println("  ✓ 4x4 chunk grid (${WORLD_RENDER_DISTANCE * 2 + 1}x${WORLD_RENDER_DISTANCE * 2 + 1} total area)")
                println("  ✓ Circular loading pattern (center-out priority)")
                println("  ✓ Dynamic loading when approaching chunk borders")
                println("  ✓ Automatic unloading of distant chunks")
                println("  ✓ Memory-efficient chunk management")
                println("")
                println("Initial chunks loaded: ${chunkManager.getLoadedChunkInfo()}")
                println("=====================================\n")
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
        if (useCubicChunks) {
            // Position camera at a good starting point for cubic chunks
            // Spawn above typical terrain height (64) + variation (45) + buffer = ~120
            camera.position.set(8.0f, 120.0f, 8.0f) // Start well above highest terrain
            camera.setRotation(0.5f, 0.0f) // Look slightly down
            
            println("\n=== CAMERA SETUP FOR CUBIC CHUNKS ===")
            println("Camera positioned at: (${camera.position.x}, ${camera.position.y}, ${camera.position.z})")
            println("Cubic chunk world extends infinitely in all directions")
            println("")
            println("Cubic chunk layout:")
            println("  Each chunk: 16x16x16 blocks")
            println("  Horizontal render distance: ${CubicChunkManager.HORIZONTAL_RENDER_DISTANCE} chunks")
            println("  Vertical render distance: ${CubicChunkManager.VERTICAL_RENDER_DISTANCE} chunks")
            println("  Total potential chunks: ~${(CubicChunkManager.HORIZONTAL_RENDER_DISTANCE * 2 + 1) * 
                      (CubicChunkManager.HORIZONTAL_RENDER_DISTANCE * 2 + 1) * 
                      (CubicChunkManager.VERTICAL_RENDER_DISTANCE * 2 + 1)}")
            println("")
            println("Movement controls (FPS-style):")
            println("  W: Move forward")
            println("  S: Move backward")
            println("  A: Strafe left")
            println("  D: Strafe right")
            println("  Space: Move up")
            println("  Shift: Move down")
            println("  Mouse: Look around")
            println("  ESC: Toggle mouse capture")
            println("=====================================\n")
        } else {
            // Original camera setup for traditional chunks
            // Spawn above typical terrain height (64) + variation (45) + buffer = ~120
            camera.position.set(8.0f, 120.0f, 8.0f) // Start well above highest terrain
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
     * Setup the crosshair GUI with proper ImGui frame management
     */
    private fun setupCrosshairGui(scene: Scene) {
        val crosshairGui = CrosshairGui()
        scene.guiInstance = crosshairGui
        println("Crosshair GUI initialized with proper frame management")
    }
    
    /**
     * Setup block interaction system
     */
    private fun setupBlockInteraction() {
        blockInteractionManager = if (useCubicChunks) {
            BlockInteractionManager(cubicWorld = cubicWorld)
        } else {
            BlockInteractionManager(world = gameWorld)
        }
        println("Block interaction system initialized")
        println("Controls:")
        println("  Left Click: Remove block")
        println("  Right Click: Place block")
        println("  Number Keys 1-9: Select block type")
    }
    
    /**
     * Setup physics engine
     */
    private fun setupPhysicsEngine() {
        physicsEngine = if (useCubicChunks) {
            PhysicsEngine(cubicWorld = cubicWorld)
        } else {
            PhysicsEngine(world = gameWorld)
        }
        println("\n=== PHYSICS ENGINE INITIALIZED ===")
        println("Movement modes:")
        println("  FLY MODE (current): Free movement in 3D space")
        println("  PHYSICS MODE: Gravity, collision, jumping")
        println("")
        println("Controls:")
        println("  F1: Toggle between fly and physics mode")
        println("  Space: Jump (physics mode) / Move up (fly mode)")
        println("  Ctrl: Sprint (physics mode)")
        println("===================================")
    }
    
    /**
     * Handle input events
     */
    override fun input(window: Window, scene: Scene, diffTimeMillis: Long, inputConsumed: Boolean) {
        if (inputConsumed) {
            return
        }
        
        val camera = scene.camera
        val deltaTime = diffTimeMillis / 1000.0f
        
        // Handle F1 key to toggle movement mode
        val currentF1State = window.isKeyPressed(GLFW.GLFW_KEY_F1)
        if (currentF1State && !lastF1KeyState) {
            movementMode = if (movementMode == MovementMode.FLY) {
                println("Switched to PHYSICS MODE - Gravity and collision enabled")
                physicsEngine.reset() // Reset velocity when switching modes
                MovementMode.PHYSICS
            } else {
                println("Switched to FLY MODE - Free movement enabled")
                MovementMode.FLY
            }
        }
        lastF1KeyState = currentF1State
        
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
        
        // Handle movement based on current mode
        when (movementMode) {
            MovementMode.FLY -> {
                // Original fly movement
                val move = diffTimeMillis * MOVEMENT_SPEED
                
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
            }
            
            MovementMode.PHYSICS -> {
                // Physics-based movement
                val isSprinting = window.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)
                val moveDirection = Vector3f()
                
                // Calculate movement direction based on camera rotation
                val cameraRotation = camera.rotation
                val forward = Vector3f(
                    kotlin.math.sin(cameraRotation.y),
                    0f,
                    -kotlin.math.cos(cameraRotation.y)
                ).normalize()
                val right = Vector3f(forward).cross(Vector3f(0f, 1f, 0f)).normalize()
                
                // WASD movement - accumulate all pressed directions
                if (window.isKeyPressed(GLFW.GLFW_KEY_W)) {
                    moveDirection.add(forward)
                }
                if (window.isKeyPressed(GLFW.GLFW_KEY_S)) {
                    moveDirection.sub(forward)
                }
                if (window.isKeyPressed(GLFW.GLFW_KEY_A)) {
                    moveDirection.sub(right)
                }
                if (window.isKeyPressed(GLFW.GLFW_KEY_D)) {
                    moveDirection.add(right)
                }
                
                // Don't normalize here - let the physics engine handle it for proper deceleration
                // when no keys are pressed (moveDirection will be zero vector)
                
                // Apply horizontal movement to physics engine
                physicsEngine.moveHorizontal(moveDirection, deltaTime, isSprinting)
                
                // Handle jumping
                if (window.isKeyPressed(GLFW.GLFW_KEY_SPACE)) {
                    physicsEngine.jump()
                }
            }
        }
        
        
        // Handle mouse clicks for block interaction (independent of mouse capture)
        val mouseInput = window.mouseInput
        val currentLeftClick = mouseInput.isLeftButtonPressed
        val currentRightClick = mouseInput.isRightButtonPressed
        
        if (currentLeftClick && !lastLeftClickState) {
            println("DEBUG: Left click detected")
            blockInteractionManager.handleLeftClick(camera)
        }
        if (currentRightClick && !lastRightClickState) {
            println("DEBUG: Right click detected")
            blockInteractionManager.handleRightClick(camera)
        }
        
        lastLeftClickState = currentLeftClick
        lastRightClickState = currentRightClick
        
        // Camera rotation with mouse (only when mouse is captured)
        if (mouseCaptured) {
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
        
        // Handle number keys for block type selection
        handleBlockTypeSelection(window)
    }
    
    /**
     * Handle block type selection with number keys
     */
    private fun handleBlockTypeSelection(window: Window) {
        val blockTypes = listOf(
            BlockType.STONE,
            BlockType.GRASS,
            BlockType.DIRT,
            BlockType.LOG_OAK,
            BlockType.LEAVES_OAK,
            BlockType.SAND,
            BlockType.COBBLESTONE,
            BlockType.COAL_ORE,
            BlockType.WATER
        )
        
        for (i in 1..minOf(9, blockTypes.size)) {
            val key = GLFW.GLFW_KEY_0 + i
            if (window.isKeyPressed(key)) {
                blockInteractionManager.setSelectedBlockType(blockTypes[i - 1])
                // Small delay to prevent multiple selections
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Update game logic
     */
    override fun update(window: Window, scene: Scene, diffTimeMillis: Long) {
        val camera = scene.camera
        val deltaTime = diffTimeMillis / 1000.0f
        
        // Update physics if in physics mode
        if (movementMode == MovementMode.PHYSICS) {
            val isSprinting = window.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)
            physicsEngine.update(camera, deltaTime, isSprinting)
        }
        
        if (useCubicChunks) {
            // Update cubic chunk manager with current player position
            cubicChunkManager.updatePlayerPosition(
                camera.position.x, 
                camera.position.y, 
                camera.position.z
            )
            
            // Update frustum culling and directional chunk visibility
            val cameraForward = Vector3f()
            camera.getViewMatrix().positiveZ(cameraForward).negate() // Get forward direction from view matrix
            vulkanIntegration.updateDirectionalChunkVisibility(cameraForward, camera.position)
            
            // Optional: Print debug info periodically (every 5 seconds)
            if (System.currentTimeMillis() % 5000 < 50) {
                val playerChunkX = CubicChunk.worldToChunk(camera.position.x.toInt())
                val playerChunkY = CubicChunk.worldToChunk(camera.position.y.toInt())
                val playerChunkZ = CubicChunk.worldToChunk(camera.position.z.toInt())
                println("Player at world (${camera.position.x.toInt()}, ${camera.position.y.toInt()}, ${camera.position.z.toInt()}) = chunk ($playerChunkX, $playerChunkY, $playerChunkZ)")
                println(cubicChunkManager.getLoadedChunkInfo())
                
                // Demonstrate frustum culling by getting visible chunks
                scene.updateFrustum()
                val visibleChunks = cubicChunkManager.getVisibleChunks(scene.frustumCuller)
                println("Frustum culling: ${visibleChunks.size} cubic chunks visible out of ${cubicChunkManager.getLoadedChunkInfo().split("loaded: ")[1].split("/")[0].toIntOrNull() ?: 0} loaded")
            }
        } else {
            // Update the world
            gameWorld.updateChunks(diffTimeMillis / 1000.0f)
            
            // Update chunk manager with current player position for dynamic loading
            chunkManager.updatePlayerPosition(camera.position.x, camera.position.z)
            
            // Update directional chunk visibility based on camera direction
            val cameraForward = Vector3f()
            camera.getViewMatrix().positiveZ(cameraForward).negate() // Get forward direction from view matrix
            vulkanIntegration.updateDirectionalChunkVisibility(cameraForward, camera.position)
            
            // Optional: Print debug info periodically (every 5 seconds)
            if (System.currentTimeMillis() % 5000 < 50) {
                val playerChunkX = Chunk.worldToChunk(camera.position.x.toInt())
                val playerChunkZ = Chunk.worldToChunk(camera.position.z.toInt())
                println("Player at world (${camera.position.x.toInt()}, ${camera.position.z.toInt()}) = chunk ($playerChunkX, $playerChunkZ). ${chunkManager.getLoadedChunkInfo()}")
            }
        }
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        println("Cleaning up CubicWorld Engine")
        
        if (useCubicChunks) {
            // Clean up cubic chunk manager
            if (::cubicChunkManager.isInitialized) {
                cubicChunkManager.cleanup()
            }
            
            // Clean up cubic world resources
            if (::cubicWorld.isInitialized) {
                cubicWorld.cleanup()
            }
        } else {
            // Clean up chunk manager
            if (::chunkManager.isInitialized) {
                chunkManager.cleanup()
            }
            
            // Clean up world resources
            if (::gameWorld.isInitialized) {
                gameWorld.cleanup()
            }
        }
        
        // Clean up Vulkan integration
        if (::vulkanIntegration.isInitialized) {
            vulkanIntegration.cleanup()
        }
    }
}