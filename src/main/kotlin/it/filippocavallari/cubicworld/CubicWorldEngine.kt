package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.renderer.VulkanRenderer
import it.filippocavallari.cubicworld.window.Window
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback

/**
 * Main engine class for CubicWorld.
 * Handles initialization, game loop, and cleanup.
 */
class CubicWorldEngine {
    
    private lateinit var window: Window
    private lateinit var renderer: VulkanRenderer
    private var isRunning = false
    
    // FPS calculation
    private var fps = 0
    private var fpsCounter = 0
    private var lastFpsTime = 0L
    
    // Time tracking
    private var lastFrameTime = 0.0
    private var delta = 0.0
    
    fun start() {
        init()
        gameLoop()
        cleanup()
    }
    
    private fun init() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set()
        
        // Initialize GLFW
        if (!GLFW.glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }
        
        // Create window
        window = Window("CubicWorld", 1280, 720)
        window.init()
        
        // Create Vulkan renderer
        renderer = VulkanRenderer(window)
        renderer.init()
        
        // Mark engine as running
        isRunning = true
    }
    
    private fun gameLoop() {
        lastFrameTime = GLFW.glfwGetTime()
        lastFpsTime = System.currentTimeMillis()
        
        // Main game loop
        while (isRunning && !window.shouldClose()) {
            // Calculate delta time
            val currentTime = GLFW.glfwGetTime()
            delta = currentTime - lastFrameTime
            lastFrameTime = currentTime
            
            // Update FPS counter
            fpsCounter++
            if (System.currentTimeMillis() - lastFpsTime > 1000) {
                fps = fpsCounter
                fpsCounter = 0
                lastFpsTime = System.currentTimeMillis()
                window.setTitle("CubicWorld | FPS: $fps")
            }
            
            // Process input
            input()
            
            // Update game logic
            update(delta)
            
            // Render frame
            render()
            
            // Poll events
            GLFW.glfwPollEvents()
        }
    }
    
    private fun input() {
        // Process input here
    }
    
    private fun update(delta: Double) {
        // Update game state here
    }
    
    private fun render() {
        renderer.render()
    }
    
    private fun cleanup() {
        // Clean up resources
        renderer.cleanup()
        window.cleanup()
        
        // Terminate GLFW
        GLFW.glfwTerminate()
        GLFW.glfwSetErrorCallback(null)?.free()
    }
}