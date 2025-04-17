package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.chunk.Chunk
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import org.vulkanb.eng.Engine
import org.vulkanb.eng.IAppLogic
import org.vulkanb.eng.Window
import org.vulkanb.eng.graph.Render
import org.vulkanb.eng.scene.Camera
import org.vulkanb.eng.scene.Entity
import org.vulkanb.eng.scene.Light
import org.vulkanb.eng.scene.Scene
import org.vulkanb.eng.scene.ModelData

/**
 * Minimal test class to demonstrate a single block rendering without complex textures
 */
class MinimalTest : IAppLogic {
    private lateinit var engine: Engine
    
    // Simple test block model
    private lateinit var testBlockModel: ModelData
    
    fun start() {
        try {
            println("Starting minimal test with JVM stack size: ${Thread.currentThread().stackSize}MB")
            engine = Engine("Minimal Test", this)
            engine.start()
        } catch (e: Exception) {
            println("Error starting minimal test: ${e.message}")
            e.printStackTrace()
            System.exit(-1)
        }
    }
    
    override fun init(window: Window, scene: Scene, render: Render) {
        println("Initializing minimal test")
        
        // Setup camera
        val camera = scene.camera
        camera.position.set(0.0f, 5.0f, -10.0f)
        camera.setRotation(0.0f, 0.0f)
        
        // Setup lighting - very basic
        scene.ambientLight.set(0.5f, 0.5f, 0.5f, 1.0f)
        
        // Create a single test cube directly without complex textures
        val modelId = "test_cube"
        
        // Create an extremely simple cube with just vertices and no textures
        testBlockModel = createSimpleCubeModel(modelId)
        
        // Load model
        render.loadModels(listOf(testBlockModel))
        
        // Create entity
        val entity = Entity("test_entity", modelId, Vector3f(0.0f, 0.0f, 0.0f))
        
        // Add entity to scene
        scene.addEntity(entity)
        
        println("Minimal test initialized")
    }
    
    /**
     * Create a very simple cube model with minimal memory requirements
     */
    private fun createSimpleCubeModel(modelId: String): ModelData {
        // Create a simple cube with just positions and minimal colors
        val positions = floatArrayOf(
            // Front face
            -0.5f, -0.5f,  0.5f,
             0.5f, -0.5f,  0.5f,
             0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            
            // Back face
            -0.5f, -0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f,
             0.5f,  0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,
            
            // Top face
            -0.5f,  0.5f, -0.5f,
            -0.5f,  0.5f,  0.5f,
             0.5f,  0.5f,  0.5f,
             0.5f,  0.5f, -0.5f,
            
            // Bottom face
            -0.5f, -0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,
             0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f,
            
            // Right face
             0.5f, -0.5f, -0.5f,
             0.5f,  0.5f, -0.5f,
             0.5f,  0.5f,  0.5f,
             0.5f, -0.5f,  0.5f,
            
            // Left face
            -0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f, -0.5f
        )
        
        // Very simple normals - just basic directions
        val normals = floatArrayOf(
            // Front face - normal points to Z+
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            
            // Back face - normal points to Z-
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            
            // Top face - normal points to Y+
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            
            // Bottom face - normal points to Y-
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            0.0f, -1.0f, 0.0f,
            
            // Right face - normal points to X+
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            
            // Left face - normal points to X-
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f,
            -1.0f, 0.0f, 0.0f
        )
        
        // Minimal tangents and bitangents - not actually used for rendering in this test
        val tangents = FloatArray(positions.size) { 1.0f }
        val biTangents = FloatArray(positions.size) { 1.0f }
        
        // Simple texture coordinates - not actually used
        val textCoords = FloatArray(48) { 0.0f }
        
        // Indices for the cube faces
        val indices = intArrayOf(
            // Front face
            0, 1, 2, 0, 2, 3,
            
            // Back face
            4, 5, 6, 4, 6, 7,
            
            // Top face
            8, 9, 10, 8, 10, 11,
            
            // Bottom face
            12, 13, 14, 12, 14, 15,
            
            // Right face
            16, 17, 18, 16, 18, 19,
            
            // Left face
            20, 21, 22, 20, 22, 23
        )
        
        // Create a default material
        val materials = listOf(ModelData.Material())
        
        // Create mesh data
        val meshes = listOf(
            ModelData.MeshData(
                positions,
                normals,
                tangents,
                biTangents,
                textCoords,
                indices,
                0 // Material index
            )
        )
        
        // Create and return model data
        return ModelData(modelId, meshes, materials)
    }
    
    override fun input(window: Window, scene: Scene, diffTimeMillis: Long, inputConsumed: Boolean) {
        if (inputConsumed) return
        
        val camera = scene.camera
        val move = diffTimeMillis * 0.005f
        
        // Simple camera movement
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
        
        // Mouse look
        val mouseInput = window.mouseInput
        if (mouseInput.isRightButtonPressed) {
            val displVec = mouseInput.displVec
            camera.addRotation(
                Math.toRadians((-displVec.x * 0.1).toDouble()).toFloat(),
                Math.toRadians((-displVec.y * 0.1).toDouble()).toFloat()
            )
        }
    }
    
    override fun update(window: Window, scene: Scene, diffTimeMillis: Long) {
        // No complex updates in minimal test
    }
    
    override fun cleanup() {
        println("Cleaning up minimal test")
    }
}

// Add a simple extension to get stack size
private val Thread.stackSize: Int
    get() {
        return try {
            val field = Thread::class.java.getDeclaredField("stackSize")
            field.isAccessible = true
            (field.get(this) as Number).toInt() / (1024 * 1024)
        } catch (e: Exception) {
            -1 // Couldn't determine stack size
        }
    }

// Main function to launch this test
fun main() {
    val test = MinimalTest()
    test.start()
}
