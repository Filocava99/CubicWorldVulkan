package org.vulkanb.eng;

import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInput {

    private final Vector2f currentPos;
    private final Vector2f displVec;
    private final Vector2f previousPos;
    private boolean inWindow;
    private boolean leftButtonPressed;
    private boolean rightButtonPressed;
    private final long windowHandle;
    private boolean resetRequired = false;
    private int windowWidth, windowHeight;

    public MouseInput(long windowHandle) {
        this.windowHandle = windowHandle;
        previousPos = new Vector2f(-1, -1);
        currentPos = new Vector2f();
        displVec = new Vector2f();
        leftButtonPressed = false;
        rightButtonPressed = false;
        inWindow = false;

        // Get window dimensions
        int[] width = new int[1], height = new int[1];
        glfwGetWindowSize(windowHandle, width, height);
        windowWidth = width[0];
        windowHeight = height[0];

        glfwSetCursorPosCallback(windowHandle, (handle, xpos, ypos) -> {
            currentPos.x = (float) xpos;
            currentPos.y = (float) ypos;
        });
        glfwSetCursorEnterCallback(windowHandle, (handle, entered) -> inWindow = entered);
        glfwSetMouseButtonCallback(windowHandle, (handle, button, action, mode) -> {
            if (button == GLFW_MOUSE_BUTTON_1) {
                leftButtonPressed = action == GLFW_PRESS;
            }
            if (button == GLFW_MOUSE_BUTTON_2) {
                rightButtonPressed = action == GLFW_PRESS;
            }
        });
        glfwSetWindowSizeCallback(windowHandle, (handle, w, h) -> {
            windowWidth = w;
            windowHeight = h;
        });
    }

    public Vector2f getCurrentPos() {
        return currentPos;
    }

    public Vector2f getDisplVec() {
        return displVec;
    }

    public void input() {
        displVec.x = 0;
        displVec.y = 0;
        
        if (previousPos.x >= 0 && previousPos.y >= 0 && inWindow) {
            // Calculate basic displacement - standard calculation
            // No inversions here - we'll do that in the engine class
            displVec.x = currentPos.y - previousPos.y;  // Vertical movement
            displVec.y = currentPos.x - previousPos.x;  // Horizontal movement (normal)
        }
        
        // Store current position for next frame
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
        
        // Reset cursor to center if needed - prevents hitting window edges
        if (resetRequired) {
            glfwSetCursorPos(windowHandle, windowWidth / 2.0, windowHeight / 2.0);
            currentPos.x = windowWidth / 2.0f;
            currentPos.y = windowHeight / 2.0f;
            previousPos.x = currentPos.x;
            previousPos.y = currentPos.y;
            resetRequired = false;
        }
    }
    
    // Call this to reset cursor position after processing input
    public void resetCursorPosition() {
        resetRequired = true;
    }

    public boolean isLeftButtonPressed() {
        return leftButtonPressed;
    }

    public boolean isRightButtonPressed() {
        return rightButtonPressed;
    }
}
