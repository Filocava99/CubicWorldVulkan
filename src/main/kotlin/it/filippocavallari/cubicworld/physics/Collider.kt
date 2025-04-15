package it.filippocavallari.cubicworld.physics

import it.filippocavallari.cubicworld.core.Component
import org.joml.Vector3f

/**
 * Collision component for game objects.
 * Defines the shape used for collision detection.
 */
abstract class Collider : Component() {
    
    // Collider properties
    var isTrigger = false
    
    // Offset from the center of the game object
    val offset = Vector3f(0f, 0f, 0f)
    
    // Physics material
    var material = PhysicsMaterial()
    
    // Collision callbacks
    var onCollisionEnter: ((Collider) -> Unit)? = null
    var onCollisionStay: ((Collider) -> Unit)? = null
    var onCollisionExit: ((Collider) -> Unit)? = null
    
    // Trigger callbacks
    var onTriggerEnter: ((Collider) -> Unit)? = null
    var onTriggerStay: ((Collider) -> Unit)? = null
    var onTriggerExit: ((Collider) -> Unit)? = null
    
    /**
     * Called when the component is attached to a game object.
     */
    override fun onAttach() {
        // Register with physics system
    }
    
    /**
     * Called when the component is detached from a game object.
     */
    override fun onDetach() {
        // Unregister from physics system
    }
    
    /**
     * Gets the world position of the collider.
     * 
     * @return The world position
     */
    fun getWorldPosition(): Vector3f {
        return Vector3f(gameObject.position).add(offset)
    }
    
    /**
     * Checks if this collider intersects with another collider.
     * 
     * @param other The other collider to check against
     * @return True if the colliders intersect
     */
    abstract fun intersects(other: Collider): Boolean
    
    /**
     * Gets the closest point on the collider to a given point.
     * 
     * @param point The point to find the closest point to
     * @return The closest point on the collider
     */
    abstract fun closestPoint(point: Vector3f): Vector3f
}