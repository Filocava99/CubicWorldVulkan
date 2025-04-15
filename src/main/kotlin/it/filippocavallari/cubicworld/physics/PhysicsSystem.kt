package it.filippocavallari.cubicworld.physics

import org.joml.Vector3f

/**
 * Physics material used by colliders.
 * Defines physical properties for collision response.
 */
class PhysicsMaterial(
    var friction: Float = 0.5f,
    var restitution: Float = 0.5f,
    var density: Float = 1.0f
) {
    companion object {
        // Common materials
        val ICE = PhysicsMaterial(0.05f, 0.7f, 0.9f)
        val METAL = PhysicsMaterial(0.3f, 0.5f, 7.8f)
        val WOOD = PhysicsMaterial(0.5f, 0.4f, 0.7f)
        val RUBBER = PhysicsMaterial(0.8f, 0.8f, 1.1f)
        val STONE = PhysicsMaterial(0.6f, 0.3f, 2.7f)
    }
}

/**
 * Global physics system for the game engine.
 * Manages physics simulation and collision detection.
 */
object PhysicsSystem {
    
    // Global physics settings
    var gravity = Vector3f(0f, -9.81f, 0f)
    
    // Physics simulation properties
    var fixedTimeStep = 1.0f / 60.0f
    var maxSubSteps = 3
    
    // Collision detection properties
    var collisionIterations = 5
    var penetrationTolerance = 0.01f
    
    // Physics objects
    private val rigidBodies = mutableListOf<RigidBody>()
    private val colliders = mutableListOf<Collider>()
    
    // Tracking collisions between frames
    private val currentCollisions = mutableMapOf<Pair<Collider, Collider>, Boolean>()
    private val previousCollisions = mutableMapOf<Pair<Collider, Collider>, Boolean>()
    
    /**
     * Registers a rigid body with the physics system.
     * 
     * @param rigidBody The rigid body to register
     */
    fun registerRigidBody(rigidBody: RigidBody) {
        rigidBodies.add(rigidBody)
    }
    
    /**
     * Unregisters a rigid body from the physics system.
     * 
     * @param rigidBody The rigid body to unregister
     */
    fun unregisterRigidBody(rigidBody: RigidBody) {
        rigidBodies.remove(rigidBody)
    }
    
    /**
     * Registers a collider with the physics system.
     * 
     * @param collider The collider to register
     */
    fun registerCollider(collider: Collider) {
        colliders.add(collider)
    }
    
    /**
     * Unregisters a collider from the physics system.
     * 
     * @param collider The collider to unregister
     */
    fun unregisterCollider(collider: Collider) {
        colliders.remove(collider)
    }
    
    /**
     * Updates the physics simulation.
     * 
     * @param deltaTime Time since the last update
     */
    fun update(deltaTime: Float) {
        // Store previous collisions
        previousCollisions.clear()
        previousCollisions.putAll(currentCollisions)
        currentCollisions.clear()
        
        // Perform fixed timestep physics
        var timeRemaining = deltaTime
        var numSubSteps = 0
        
        while (timeRemaining > 0f && numSubSteps < maxSubSteps) {
            val stepTime = kotlin.math.min(timeRemaining, fixedTimeStep)
            
            // Update rigid bodies
            for (rigidBody in rigidBodies) {
                rigidBody.update(stepTime)
            }
            
            // Detect and resolve collisions
            detectCollisions()
            resolveCollisions()
            
            // Update timers
            timeRemaining -= stepTime
            numSubSteps++
        }
        
        // Trigger collision events
        triggerCollisionEvents()
    }
    
    /**
     * Detects collisions between colliders.
     */
    private fun detectCollisions() {
        // Simple O(n²) collision detection
        for (i in 0 until colliders.size) {
            val colliderA = colliders[i]
            
            for (j in i + 1 until colliders.size) {
                val colliderB = colliders[j]
                
                // Skip collisions between static objects or objects on different layers
                val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                
                if (rigidBodyA != null && rigidBodyB != null) {
                    if (rigidBodyA.isStatic && rigidBodyB.isStatic) {
                        continue
                    }
                }
                
                // Check for collision
                val collision = colliderA.intersects(colliderB)
                if (collision) {
                    // Store the collision
                    val pair1 = Pair(colliderA, colliderB)
                    val pair2 = Pair(colliderB, colliderA)
                    
                    currentCollisions[pair1] = true
                    currentCollisions[pair2] = true
                }
            }
        }
    }
    
    /**
     * Resolves collisions between objects.
     */
    private fun resolveCollisions() {
        // For each collision pair
        for ((colliderPair, _) in currentCollisions) {
            val (colliderA, colliderB) = colliderPair
            
            // Skip if either collider is a trigger
            if (colliderA.isTrigger || colliderB.isTrigger) {
                continue
            }
            
            // Get rigid bodies
            val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
            val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
            
            // Skip if no rigid bodies
            if (rigidBodyA == null && rigidBodyB == null) {
                continue
            }
            
            // Calculate collision response
            // This is a simplistic implementation
            val posA = colliderA.getWorldPosition()
            val posB = colliderB.getWorldPosition()
            
            // Calculate collision normal
            val normal = Vector3f(posB).sub(posA).normalize()
            
            // Calculate penetration depth
            // This is a simplified approach - in a real engine, we'd use more sophisticated methods
            val penetrationDepth = when {
                colliderA is SphereCollider && colliderB is SphereCollider -> {
                    val distance = posA.distance(posB)
                    val minDistance = colliderA.radius + colliderB.radius
                    minDistance - distance
                }
                else -> {
                    // For other collider types, we'd need more complex calculations
                    0.1f // placeholder
                }
            }
            
            // Only resolve if penetrating
            if (penetrationDepth > penetrationTolerance) {
                // Calculate separation vector
                val separationVector = Vector3f(normal).mul(penetrationDepth)
                
                // Apply separation based on mass ratios
                if (rigidBodyA != null && !rigidBodyA.isStatic) {
                    val massRatio = if (rigidBodyB != null && !rigidBodyB.isStatic) {
                        rigidBodyB.mass / (rigidBodyA.mass + rigidBodyB.mass)
                    } else {
                        1.0f
                    }
                    
                    rigidBodyA.gameObject.position.sub(Vector3f(separationVector).mul(massRatio))
                }
                
                if (rigidBodyB != null && !rigidBodyB.isStatic) {
                    val massRatio = if (rigidBodyA != null && !rigidBodyA.isStatic) {
                        rigidBodyA.mass / (rigidBodyA.mass + rigidBodyB.mass)
                    } else {
                        1.0f
                    }
                    
                    rigidBodyB.gameObject.position.add(Vector3f(separationVector).mul(massRatio))
                }
                
                // Calculate collision impulse
                if (rigidBodyA != null && rigidBodyB != null && 
                    !rigidBodyA.isStatic && !rigidBodyB.isStatic) {
                    
                    val velA = rigidBodyA.getVelocity()
                    val velB = rigidBodyB.getVelocity()
                    
                    // Relative velocity
                    val relativeVelocity = Vector3f(velB).sub(velA)
                    
                    // Check if objects are moving apart
                    val velocityAlongNormal = relativeVelocity.dot(normal)
                    if (velocityAlongNormal > 0) {
                        continue
                    }
                    
                    // Calculate restitution (bounciness)
                    val restitution = kotlin.math.min(
                        colliderA.material.restitution,
                        colliderB.material.restitution
                    )
                    
                    // Calculate impulse scalar
                    val j = -(1.0f + restitution) * velocityAlongNormal
                    val impulseScalar = j / (rigidBodyA.getInverseMass() + rigidBodyB.getInverseMass())
                    
                    // Apply impulse
                    val impulse = Vector3f(normal).mul(impulseScalar)
                    rigidBodyA.applyImpulse(Vector3f(impulse).mul(-1.0f))
                    rigidBodyB.applyImpulse(impulse)
                    
                    // Apply friction
                    val friction = kotlin.math.sqrt(
                        colliderA.material.friction * colliderB.material.friction
                    )
                    
                    // Tangent is perpendicular to normal
                    val tangent = Vector3f(relativeVelocity).sub(
                        Vector3f(normal).mul(relativeVelocity.dot(normal))
                    )
                    
                    if (tangent.lengthSquared() > 0.0001f) {
                        tangent.normalize()
                        
                        // Calculate friction impulse scalar
                        val jt = -relativeVelocity.dot(tangent) * friction
                        val frictionImpulseScalar = jt / (rigidBodyA.getInverseMass() + rigidBodyB.getInverseMass())
                        
                        // Apply friction impulse
                        val frictionImpulse = Vector3f(tangent).mul(frictionImpulseScalar)
                        rigidBodyA.applyImpulse(Vector3f(frictionImpulse).mul(-1.0f))
                        rigidBodyB.applyImpulse(frictionImpulse)
                    }
                }
            }
        }
    }
    
    /**
     * Triggers collision events for started, ongoing, and ended collisions.
     */
    private fun triggerCollisionEvents() {
        // Detect new collisions (collision enter)
        for ((colliderPair, _) in currentCollisions) {
            val (colliderA, colliderB) = colliderPair
            
            // Check if this is a new collision
            if (!previousCollisions.containsKey(colliderPair)) {
                // Trigger collision enter events
                if (colliderA.isTrigger || colliderB.isTrigger) {
                    // Trigger events
                    colliderA.onTriggerEnter?.invoke(colliderB)
                    colliderB.onTriggerEnter?.invoke(colliderA)
                    
                    // Rigid body callbacks
                    val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                    val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                    
                    rigidBodyA?.onCollisionEnter?.invoke(colliderB)
                    rigidBodyB?.onCollisionEnter?.invoke(colliderA)
                } else {
                    // Collision events
                    colliderA.onCollisionEnter?.invoke(colliderB)
                    colliderB.onCollisionEnter?.invoke(colliderA)
                    
                    // Rigid body callbacks
                    val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                    val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                    
                    rigidBodyA?.onCollisionEnter?.invoke(colliderB)
                    rigidBodyB?.onCollisionEnter?.invoke(colliderA)
                }
            } else {
                // Ongoing collision (collision stay)
                if (colliderA.isTrigger || colliderB.isTrigger) {
                    // Trigger events
                    colliderA.onTriggerStay?.invoke(colliderB)
                    colliderB.onTriggerStay?.invoke(colliderA)
                    
                    // Rigid body callbacks
                    val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                    val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                    
                    rigidBodyA?.onCollisionStay?.invoke(colliderB)
                    rigidBodyB?.onCollisionStay?.invoke(colliderA)
                } else {
                    // Collision events
                    colliderA.onCollisionStay?.invoke(colliderB)
                    colliderB.onCollisionStay?.invoke(colliderA)
                    
                    // Rigid body callbacks
                    val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                    val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                    
                    rigidBodyA?.onCollisionStay?.invoke(colliderB)
                    rigidBodyB?.onCollisionStay?.invoke(colliderA)
                }
            }
        }
        
        // Detect ended collisions (collision exit)
        for ((colliderPair, _) in previousCollisions) {
            // Check if the collision has ended
            if (!currentCollisions.containsKey(colliderPair)) {
                val (colliderA, colliderB) = colliderPair
                
                // Trigger collision exit events
                if (colliderA.isTrigger || colliderB.isTrigger) {
                    // Trigger events
                    colliderA.onTriggerExit?.invoke(colliderB)
                    colliderB.onTriggerExit?.invoke(colliderA)
                    
                    // Rigid body callbacks
                    val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                    val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                    
                    rigidBodyA?.onCollisionExit?.invoke(colliderB)
                    rigidBodyB?.onCollisionExit?.invoke(colliderA)
                } else {
                    // Collision events
                    colliderA.onCollisionExit?.invoke(colliderB)
                    colliderB.onCollisionExit?.invoke(colliderA)
                    
                    // Rigid body callbacks
                    val rigidBodyA = colliderA.gameObject.getComponent<RigidBody>()
                    val rigidBodyB = colliderB.gameObject.getComponent<RigidBody>()
                    
                    rigidBodyA?.onCollisionExit?.invoke(colliderB)
                    rigidBodyB?.onCollisionExit?.invoke(colliderA)
                }
            }
        }
    }
    
    /**
     * Casts a ray and returns the first object hit.
     * 
     * @param origin The origin of the ray
     * @param direction The direction of the ray
     * @param maxDistance The maximum distance of the ray
     * @return The raycast hit result, or null if nothing was hit
     */
    fun raycast(origin: Vector3f, direction: Vector3f, maxDistance: Float = Float.MAX_VALUE): RaycastHit? {
        val normalizedDirection = Vector3f(direction).normalize()
        var closestHit: RaycastHit? = null
        var closestDistance = maxDistance
        
        // Check against all colliders
        for (collider in colliders) {
            when (collider) {
                is SphereCollider -> {
                    val hit = raycastSphere(origin, normalizedDirection, collider, maxDistance)
                    if (hit != null && hit.distance < closestDistance) {
                        closestHit = hit
                        closestDistance = hit.distance
                    }
                }
                is BoxCollider -> {
                    val hit = raycastBox(origin, normalizedDirection, collider, maxDistance)
                    if (hit != null && hit.distance < closestDistance) {
                        closestHit = hit
                        closestDistance = hit.distance
                    }
                }
            }
        }
        
        return closestHit
    }
    
    /**
     * Performs a raycast against a sphere collider.
     * 
     * @param origin The origin of the ray
     * @param direction The normalized direction of the ray
     * @param collider The sphere collider to check against
     * @param maxDistance The maximum distance of the ray
     * @return The raycast hit result, or null if the ray doesn't hit the sphere
     */
    private fun raycastSphere(
        origin: Vector3f,
        direction: Vector3f,
        collider: SphereCollider,
        maxDistance: Float
    ): RaycastHit? {
        val sphereCenter = collider.getWorldPosition()
        val radius = collider.radius
        
        // Vector from ray origin to sphere center
        val m = Vector3f(sphereCenter).sub(origin)
        
        // Project m onto the ray direction
        val b = m.dot(direction)
        
        // Find squared distance from sphere center to the ray
        val c = m.dot(m) - b * b
        
        // If the ray doesn't intersect the sphere at all
        if (c > radius * radius) {
            return null
        }
        
        // Find the intersection points
        val t = kotlin.math.sqrt(radius * radius - c)
        val t1 = b - t
        val t2 = b + t
        
        // Check if intersections are within range
        if (t1 < 0 && t2 < 0) {
            return null
        }
        
        // Use the nearest intersection
        val distance = if (t1 >= 0) t1 else t2
        
        // Check max distance
        if (distance > maxDistance) {
            return null
        }
        
        // Calculate intersection point
        val point = Vector3f(origin).add(Vector3f(direction).mul(distance))
        
        // Calculate normal
        val normal = Vector3f(point).sub(sphereCenter).normalize()
        
        return RaycastHit(
            distance = distance,
            point = point,
            normal = normal,
            collider = collider
        )
    }
    
    /**
     * Performs a raycast against a box collider.
     * 
     * @param origin The origin of the ray
     * @param direction The normalized direction of the ray
     * @param collider The box collider to check against
     * @param maxDistance The maximum distance of the ray
     * @return The raycast hit result, or null if the ray doesn't hit the box
     */
    private fun raycastBox(
        origin: Vector3f,
        direction: Vector3f,
        collider: BoxCollider,
        maxDistance: Float
    ): RaycastHit? {
        // This uses the slab method for ray-box intersection
        
        val min = collider.getMin()
        val max = collider.getMax()
        
        // Ray-AABB intersection using slab method
        var tMin = Float.NEGATIVE_INFINITY
        var tMax = Float.POSITIVE_INFINITY
        
        var normalIndex = 0
        var normalSign = 1.0f
        
        // For each axis
        for (i in 0..2) {
            // Get the current component
            val o = when (i) {
                0 -> origin.x
                1 -> origin.y
                else -> origin.z
            }
            
            val d = when (i) {
                0 -> direction.x
                1 -> direction.y
                else -> direction.z
            }
            
            val minVal = when (i) {
                0 -> min.x
                1 -> min.y
                else -> min.z
            }
            
            val maxVal = when (i) {
                0 -> max.x
                1 -> max.y
                else -> max.z
            }
            
            // Check if ray is parallel to slab
            if (kotlin.math.abs(d) < 0.0001f) {
                // Ray is parallel to slab, check if origin is inside slab
                if (o < minVal || o > maxVal) {
                    return null
                }
            } else {
                // Ray not parallel to slab, compute intersection
                val invD = 1.0f / d
                var t1 = (minVal - o) * invD
                var t2 = (maxVal - o) * invD
                
                // Ensure t1 <= t2
                if (t1 > t2) {
                    val temp = t1
                    t1 = t2
                    t2 = temp
                }
                
                // Update tMin and tMax
                if (t1 > tMin) {
                    tMin = t1
                    normalIndex = i
                    normalSign = if (d < 0.0f) 1.0f else -1.0f
                }
                
                if (t2 < tMax) {
                    tMax = t2
                }
                
                // Ray misses the box
                if (tMin > tMax) {
                    return null
                }
            }
        }
        
        // Check if intersection is beyond max distance
        if (tMin > maxDistance || tMin < 0) {
            return null
        }
        
        // Compute normal
        val normal = Vector3f()
        when (normalIndex) {
            0 -> normal.set(normalSign, 0.0f, 0.0f)
            1 -> normal.set(0.0f, normalSign, 0.0f)
            2 -> normal.set(0.0f, 0.0f, normalSign)
        }
        
        // Compute intersection point
        val point = Vector3f(origin).add(Vector3f(direction).mul(tMin))
        
        return RaycastHit(
            distance = tMin,
            point = point,
            normal = normal,
            collider = collider
        )
    }
}

/**
 * Represents the result of a raycast hit.
 * 
 * @property distance The distance from the ray origin to the hit point
 * @property point The point of intersection
 * @property normal The surface normal at the hit point
 * @property collider The collider that was hit
 */
data class RaycastHit(
    val distance: Float,
    val point: Vector3f,
    val normal: Vector3f,
    val collider: Collider
)