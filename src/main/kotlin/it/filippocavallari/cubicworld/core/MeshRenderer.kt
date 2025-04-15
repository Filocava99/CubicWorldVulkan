package it.filippocavallari.cubicworld.core

import it.filippocavallari.cubicworld.renderer.Material
import it.filippocavallari.cubicworld.renderer.Mesh

/**
 * Component that renders a mesh with a material.
 */
class MeshRenderer : Component() {
    
    // The mesh to render
    var mesh: Mesh? = null
    
    // The material to use for rendering
    var material: Material? = null
    
    /**
     * Called when the component is attached to a game object.
     */
    override fun onAttach() {
        // Register with renderer system
    }
    
    /**
     * Called when the component is detached from a game object.
     */
    override fun onDetach() {
        // Unregister from renderer system
    }
    
    /**
     * Updates the component.
     * 
     * @param deltaTime Time since the last update
     */
    override fun update(deltaTime: Float) {
        // Rendering is handled by the renderer system
    }
}