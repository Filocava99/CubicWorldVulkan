package it.filippocavallari.cubicworld.gui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import org.vulkanb.eng.scene.IGuiInstance
import java.io.File

/**
 * GUI implementation for displaying a crosshair at the center of the screen
 */
class CrosshairGui : IGuiInstance {
    
    private var crosshairTextureId: Int = -1
    private var crosshairSize = 32f // Size of the crosshair in pixels
    private var isInitialized = false
    
    init {
        loadCrosshairTexture()
    }
    
    private fun loadCrosshairTexture() {
        try {
            val crosshairFile = File("src/main/resources/gui/crosshair.png")
            if (!crosshairFile.exists()) {
                println("Warning: crosshair.png not found at ${crosshairFile.absolutePath}")
                println("Using simple cross lines instead of texture")
            } else {
                println("Found crosshair.png at ${crosshairFile.absolutePath}")
                // For now, we'll use simple drawing instead of texture loading
                // since proper texture integration requires more complex Vulkan setup
            }
            
            isInitialized = true
            println("Crosshair GUI initialized")
            
        } catch (e: Exception) {
            println("Error initializing crosshair: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun drawGui() {
        if (!isInitialized) {
            return
        }
        
        // Follow the proper ImGui frame lifecycle as shown in the documentation
        ImGui.newFrame()
        
        // Get display size for centering
        val displaySize = ImGui.getIO().displaySize
        
        // Create a full-screen invisible overlay window
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(displaySize.x, displaySize.y)
        
        val windowFlags = ImGuiWindowFlags.NoTitleBar or 
                         ImGuiWindowFlags.NoResize or 
                         ImGuiWindowFlags.NoMove or 
                         ImGuiWindowFlags.NoScrollbar or 
                         ImGuiWindowFlags.NoScrollWithMouse or 
                         ImGuiWindowFlags.NoCollapse or 
                         ImGuiWindowFlags.NoBackground or 
                         ImGuiWindowFlags.NoSavedSettings or 
                         ImGuiWindowFlags.NoInputs or
                         ImGuiWindowFlags.NoFocusOnAppearing or
                         ImGuiWindowFlags.NoBringToFrontOnFocus
        
        if (ImGui.begin("##Crosshair", windowFlags)) {
            val drawList = ImGui.getWindowDrawList()
            
            // Calculate exact center position
            val centerX = displaySize.x / 2.0f
            val centerY = displaySize.y / 2.0f
            
            // Draw crosshair lines with proper centering
            val crosshairHalfSize = crosshairSize / 2.0f
            val crosshairThickness = 2.0f
            val crosshairColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.8f)
            
            // Draw horizontal line (left to right through center)
            drawList.addLine(
                centerX - crosshairHalfSize, centerY,
                centerX + crosshairHalfSize, centerY,
                crosshairColor, crosshairThickness
            )
            
            // Draw vertical line (top to bottom through center)
            drawList.addLine(
                centerX, centerY - crosshairHalfSize,
                centerX, centerY + crosshairHalfSize,
                crosshairColor, crosshairThickness
            )
        }
        ImGui.end()
        
        // Complete the ImGui frame as shown in documentation
        ImGui.endFrame()
        ImGui.render()
    }
}