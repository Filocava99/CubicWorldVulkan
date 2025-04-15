package it.filippocavallari.cubicworld.core

/**
 * Base class for all components that can be attached to game objects.
 * Components provide specific functionality to game objects.
 */
abstract class Component {
    
    /**
     * The game object this component is attached to.
     * Set when the component is added to a game object.
     */
    lateinit var gameObject: GameObject
    
    /**
     * Called when the component is attached to a game object.
     */
    open fun onAttach() {}
    
    /**
     * Called when the component is detached from a game object.
     */
    open fun onDetach() {}
    
    /**
     * Called every frame to update the component.
     * 
     * @param deltaTime Time since the last update
     */
    open fun update(deltaTime: Float) {}
}