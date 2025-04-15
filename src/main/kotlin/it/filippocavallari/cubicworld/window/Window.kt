package it.filippocavallari.cubicworld.window

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil

/**
 * Window class encapsulating GLFW window functionality.
 * Sets up a window specifically configured for Vulkan rendering.
 */
class Window(
    private var title: String,
    private var width: Int,
    private var height: Int,
    private var vsync: Boolean = false
) {
    
    private var windowHandle: Long = 0
    
    // Window properties
    val aspectRatio: Float
        get() = width.toFloat() / height.toFloat()
    
    fun init() {
        // Configure GLFW for Vulkan
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API) // Important: Prevent GLFW from creating an OpenGL context
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
        
        // Create window
        windowHandle = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
        if (windowHandle == MemoryUtil.NULL) {
            throw RuntimeException("Failed to create GLFW window")
        }
        
        // Setup resize callback
        GLFW.glfwSetFramebufferSizeCallback(windowHandle) { _, width, height ->
            this.width = width
            this.height = height
            // Signal to renderer that window has been resized
        }
        
        // Center the window
        val vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor())
        if (vidMode != null) {
            val x = (vidMode.width() - width) / 2
            val y = (vidMode.height() - height) / 2
            GLFW.glfwSetWindowPos(windowHandle, x, y)
        }
        
        // Check Vulkan support
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw RuntimeException("Vulkan is not supported")
        }
    }
    
    fun setTitle(title: String) {
        this.title = title
        GLFW.glfwSetWindowTitle(windowHandle, title)
    }
    
    fun shouldClose(): Boolean {
        return GLFW.glfwWindowShouldClose(windowHandle)
    }
    
    fun isKeyPressed(keyCode: Int): Boolean {
        return GLFW.glfwGetKey(windowHandle, keyCode) == GLFW.GLFW_PRESS
    }
    
    fun cleanup() {
        GLFW.glfwDestroyWindow(windowHandle)
    }
    
    fun getWindowHandle(): Long {
        return windowHandle
    }
    
    fun getWidth(): Int {
        return width
    }
    
    fun getHeight(): Int {
        return height
    }
    
    fun setVSync(vsync: Boolean) {
        this.vsync = vsync
    }
    
    fun isVSyncEnabled(): Boolean {
        return vsync
    }
}