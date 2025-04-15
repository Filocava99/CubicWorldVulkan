package it.filippocavallari.cubicworld.core

import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Base class for all game objects in the world.
 * Provides basic transformation and component-based functionality.
 */
open class GameObject(val id: String = "GameObject") {
    
    // Transform properties
    val position = Vector3f(0f, 0f, 0f)
    val rotation = Vector3f(0f, 0f, 0f)
    val scale = Vector3f(1f, 1f, 1f)
    
    // Components
    private val components = mutableMapOf<Class<out Component>, Component>()
    
    // Model matrix
    private val modelMatrix = Matrix4f()
    private var modelMatrixDirty = true
    
    // Parent-child relationships
    private var parent: GameObject? = null
    private val children = mutableListOf<GameObject>()
    
    /**
     * Adds a component to this game object.
     * 
     * @param component The component to add
     * @return The added component
     */
    fun <T : Component> addComponent(component: T): T {
        components[component.javaClass] = component
        component.gameObject = this
        component.onAttach()
        return component
    }
    
    /**
     * Gets a component of the specified type.
     * 
     * @param clazz The class of the component to get
     * @return The component, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(clazz: Class<T>): T? {
        return components[clazz] as T?
    }
    
    /**
     * Gets a component of the specified type.
     * 
     * @return The component, or null if not found
     */
    inline fun <reified T : Component> getComponent(): T? {
        return getComponent(T::class.java)
    }
    
    /**
     * Removes a component of the specified type.
     * 
     * @param clazz The class of the component to remove
     * @return True if the component was removed, false if not found
     */
    fun <T : Component> removeComponent(clazz: Class<T>): Boolean {
        val component = components.remove(clazz) ?: return false
        component.onDetach()
        return true
    }
    
    /**
     * Adds a child game object.
     * 
     * @param child The child to add
     */
    fun addChild(child: GameObject) {
        children.add(child)
        child.parent = this
    }
    
    /**
     * Removes a child game object.
     * 
     * @param child The child to remove
     * @return True if the child was removed, false if not found
     */
    fun removeChild(child: GameObject): Boolean {
        if (children.remove(child)) {
            child.parent = null
            return true
        }
        return false
    }
    
    /**
     * Updates this game object and all its components.
     * 
     * @param deltaTime Time since the last update
     */
    fun update(deltaTime: Float) {
        // Update components
        components.values.forEach { it.update(deltaTime) }
        
        // Update children
        children.forEach { it.update(deltaTime) }
    }
    
    /**
     * Gets the model matrix for this game object.
     * 
     * @return The model matrix
     */
    fun getModelMatrix(): Matrix4f {
        if (modelMatrixDirty) {
            updateModelMatrix()
        }
        return modelMatrix
    }
    
    /**
     * Updates the model matrix based on position, rotation, and scale.
     */
    private fun updateModelMatrix() {
        modelMatrix.identity()
            .translate(position)
            .rotateX(Math.toRadians(rotation.x.toDouble()).toFloat())
            .rotateY(Math.toRadians(rotation.y.toDouble()).toFloat())
            .rotateZ(Math.toRadians(rotation.z.toDouble()).toFloat())
            .scale(scale)
        
        modelMatrixDirty = false
    }
    
    /**
     * Sets the position of this game object.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    fun setPosition(x: Float, y: Float, z: Float) {
        position.set(x, y, z)
        modelMatrixDirty = true
    }
    
    /**
     * Sets the rotation of this game object in degrees.
     * 
     * @param x Rotation around X axis
     * @param y Rotation around Y axis
     * @param z Rotation around Z axis
     */
    fun setRotation(x: Float, y: Float, z: Float) {
        rotation.set(x, y, z)
        modelMatrixDirty = true
    }
    
    /**
     * Sets the scale of this game object.
     * 
     * @param x Scale on X axis
     * @param y Scale on Y axis
     * @param z Scale on Z axis
     */
    fun setScale(x: Float, y: Float, z: Float) {
        scale.set(x, y, z)
        modelMatrixDirty = true
    }
    
    /**
     * Sets a uniform scale on all axes.
     * 
     * @param scale Scale factor
     */
    fun setScale(scale: Float) {
        this.scale.set(scale, scale, scale)
        modelMatrixDirty = true
    }
}