package it.filippocavallari.cubicworld.physics

import it.filippocavallari.cubicworld.core.Component
import org.joml.Vector3f

/**
 * Physics component for game objects.
 * Handles physics simulation including velocity, acceleration, and collisions.
 */
class RigidBody : Component() {
    
    // Physical properties
    private val velocity = Vector3f(0f, 0f, 0f)
    private val acceleration = Vector3f(0f, 0f, 0f)
    private val forces = Vector3f(0f, 0f, 0f)
    
    // Body properties
    var mass = 1.0f
        set(value) {
            field = if (value <= 0f) 0.0001f else value
            updateInverseMass()
        }
    
    var isStatic = false
        set(value) {
            field = value
            updateInverseMass()
        }
    
    private var inverseMass = 1.0f
    
    // Damping to simulate drag/friction
    var linearDamping = 0.99f
    
    // Gravity affects this body
    var useGravity = true
    
    // Physics engine callbacks
    var onCollisionEnter: ((Collider) -> Unit)? = null
    var onCollisionStay: ((Collider) -> Unit)? = null
    var onCollisionExit: ((Collider) -> Unit)? = null
    
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
     * Updates the physics simulation.
     * 
     * @param deltaTime Time since the last update
     */
    override fun update(deltaTime: Float) {
        if (isStatic) return
        
        // Apply gravity
        if (useGravity) {
            applyForce(PhysicsSystem.gravity.mul(mass, Vector3f()))
        }
        
        // Calculate acceleration from forces
        acceleration.set(forces).mul(inverseMass)
        
        // Update velocity
        velocity.add(acceleration.x * deltaTime, acceleration.y * deltaTime, acceleration.z * deltaTime)
        
        // Apply damping
        velocity.mul(Math.pow(linearDamping.toDouble(), deltaTime.toDouble()).toFloat())
        
        // Update position
        gameObject.position.add(
            velocity.x * deltaTime,
            velocity.y * deltaTime,
            velocity.z * deltaTime
        )
        
        // Reset forces for next frame
        forces.set(0f, 0f, 0f)
    }
    
    /**
     * Applies a force to the rigid body.
     * 
     * @param force The force to apply
     */
    fun applyForce(force: Vector3f) {
        if (isStatic) return
        forces.add(force)
    }
    
    /**
     * Applies an impulse (instantaneous change in velocity) to the rigid body.
     * 
     * @param impulse The impulse to apply
     */
    fun applyImpulse(impulse: Vector3f) {
        if (isStatic) return
        velocity.add(impulse.x * inverseMass, impulse.y * inverseMass, impulse.z * inverseMass)
    }
    
    /**
     * Gets the current velocity.
     * 
     * @return The velocity vector
     */
    fun getVelocity(): Vector3f {
        return Vector3f(velocity)
    }
    
    /**
     * Sets the velocity directly.
     * 
     * @param velocity The new velocity
     */
    fun setVelocity(velocity: Vector3f) {
        this.velocity.set(velocity)
    }
    
    /**
     * Gets the current acceleration.
     * 
     * @return The acceleration vector
     */
    fun getAcceleration(): Vector3f {
        return Vector3f(acceleration)
    }
    
    /**
     * Gets the inverse mass.
     * Used for physics calculations.
     * 
     * @return The inverse mass
     */
    fun getInverseMass(): Float {
        return inverseMass
    }
    
    /**
     * Updates the inverse mass based on the mass and static flag.
     */
    private fun updateInverseMass() {
        inverseMass = if (isStatic || mass <= 0f) 0f else 1f / mass
    }
}