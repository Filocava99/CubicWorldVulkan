package it.filippocavallari.cubicworld.input

import it.filippocavallari.cubicworld.window.Window
import org.lwjgl.glfw.GLFW.*

/**
 * Manages user input from keyboard and mouse.
 */
object InputManager {
    
    // Key states
    private val keyPressed = BooleanArray(GLFW_KEY_LAST + 1)
    private val keyDown = BooleanArray(GLFW_KEY_LAST + 1)
    private val keyReleased = BooleanArray(GLFW_KEY_LAST + 1)
    
    // Mouse states
    private val mouseButtonPressed = BooleanArray(GLFW_MOUSE_BUTTON_LAST + 1)
    private val mouseButtonDown = BooleanArray(GLFW_MOUSE_BUTTON_LAST + 1)
    private val mouseButtonReleased = BooleanArray(GLFW_MOUSE_BUTTON_LAST + 1)
    
    // Mouse position
    private var mouseX = 0.0
    private var mouseY = 0.0
    private var lastMouseX = 0.0
    private var lastMouseY = 0.0
    private var mouseDeltaX = 0.0
    private var mouseDeltaY = 0.0
    private var scrollX = 0.0
    private var scrollY = 0.0
    
    /**
     * Initializes the input manager with callbacks for the window.
     * 
     * @param window The window to register callbacks for
     */
    fun init(window: Window) {
        val windowHandle = window.getWindowHandle()
        
        // Set up key callback
        glfwSetKeyCallback(windowHandle) { _, key, _, action, _ ->
            if (key < 0 || key >= keyPressed.size) return@glfwSetKeyCallback
            
            when (action) {
                GLFW_PRESS -> {
                    keyPressed[key] = true
                    keyDown[key] = true
                }
                GLFW_RELEASE -> {
                    keyReleased[key] = true
                    keyDown[key] = false
                }
            }
        }
        
        // Set up mouse button callback
        glfwSetMouseButtonCallback(windowHandle) { _, button, action, _ ->
            if (button < 0 || button >= mouseButtonPressed.size) return@glfwSetMouseButtonCallback
            
            when (action) {
                GLFW_PRESS -> {
                    mouseButtonPressed[button] = true
                    mouseButtonDown[button] = true
                }
                GLFW_RELEASE -> {
                    mouseButtonReleased[button] = true
                    mouseButtonDown[button] = false
                }
            }
        }
        
        // Set up cursor position callback
        glfwSetCursorPosCallback(windowHandle) { _, xpos, ypos ->
            lastMouseX = mouseX
            lastMouseY = mouseY
            mouseX = xpos
            mouseY = ypos
            mouseDeltaX = mouseX - lastMouseX
            mouseDeltaY = mouseY - lastMouseY
        }
        
        // Set up scroll callback
        glfwSetScrollCallback(windowHandle) { _, xoffset, yoffset ->
            scrollX = xoffset
            scrollY = yoffset
        }
    }
    
    /**
     * Updates the input state at the end of a frame.
     * Resets single-frame states like pressed and released.
     */
    fun update() {
        // Reset key pressed/released states
        for (i in keyPressed.indices) {
            keyPressed[i] = false
            keyReleased[i] = false
        }
        
        // Reset mouse button pressed/released states
        for (i in mouseButtonPressed.indices) {
            mouseButtonPressed[i] = false
            mouseButtonReleased[i] = false
        }
        
        // Reset scroll values
        scrollX = 0.0
        scrollY = 0.0
        
        // Reset mouse delta
        mouseDeltaX = 0.0
        mouseDeltaY = 0.0
    }
    
    /**
     * Checks if a key was pressed this frame.
     * 
     * @param key The key code to check
     * @return True if the key was pressed this frame
     */
    fun isKeyPressed(key: Int): Boolean {
        return if (key < 0 || key >= keyPressed.size) false else keyPressed[key]
    }
    
    /**
     * Checks if a key is currently down.
     * 
     * @param key The key code to check
     * @return True if the key is currently down
     */
    fun isKeyDown(key: Int): Boolean {
        return if (key < 0 || key >= keyDown.size) false else keyDown[key]
    }
    
    /**
     * Checks if a key was released this frame.
     * 
     * @param key The key code to check
     * @return True if the key was released this frame
     */
    fun isKeyReleased(key: Int): Boolean {
        return if (key < 0 || key >= keyReleased.size) false else keyReleased[key]
    }
    
    /**
     * Checks if a mouse button was pressed this frame.
     * 
     * @param button The mouse button code to check
     * @return True if the mouse button was pressed this frame
     */
    fun isMouseButtonPressed(button: Int): Boolean {
        return if (button < 0 || button >= mouseButtonPressed.size) false else mouseButtonPressed[button]
    }
    
    /**
     * Checks if a mouse button is currently down.
     * 
     * @param button The mouse button code to check
     * @return True if the mouse button is currently down
     */
    fun isMouseButtonDown(button: Int): Boolean {
        return if (button < 0 || button >= mouseButtonDown.size) false else mouseButtonDown[button]
    }
    
    /**
     * Checks if a mouse button was released this frame.
     * 
     * @param button The mouse button code to check
     * @return True if the mouse button was released this frame
     */
    fun isMouseButtonReleased(button: Int): Boolean {
        return if (button < 0 || button >= mouseButtonReleased.size) false else mouseButtonReleased[button]
    }
    
    /**
     * Gets the current mouse X position.
     * 
     * @return The mouse X position
     */
    fun getMouseX(): Double {
        return mouseX
    }
    
    /**
     * Gets the current mouse Y position.
     * 
     * @return The mouse Y position
     */
    fun getMouseY(): Double {
        return mouseY
    }
    
    /**
     * Gets the mouse X delta since the last frame.
     * 
     * @return The mouse X delta
     */
    fun getMouseDeltaX(): Double {
        return mouseDeltaX
    }
    
    /**
     * Gets the mouse Y delta since the last frame.
     * 
     * @return The mouse Y delta
     */
    fun getMouseDeltaY(): Double {
        return mouseDeltaY
    }
    
    /**
     * Gets the scroll X value this frame.
     * 
     * @return The scroll X value
     */
    fun getScrollX(): Double {
        return scrollX
    }
    
    /**
     * Gets the scroll Y value this frame.
     * 
     * @return The scroll Y value
     */
    fun getScrollY(): Double {
        return scrollY
    }
}