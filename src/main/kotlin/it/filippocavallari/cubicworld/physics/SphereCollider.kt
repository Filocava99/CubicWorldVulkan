package it.filippocavallari.cubicworld.physics

import org.joml.Vector3f

/**
 * Sphere collider component for game objects.
 * Represents a spherical collision volume.
 */
class SphereCollider : Collider() {
    
    // Radius of the sphere
    var radius = 0.5f
    
    /**
     * Checks if this sphere collider intersects with another collider.
     * 
     * @param other The other collider to check against
     * @return True if the colliders intersect
     */
    override fun intersects(other: Collider): Boolean {
        return when (other) {
            is SphereCollider -> intersectsSphere(other)
            is BoxCollider -> intersectsBox(other)
            else -> false
        }
    }
    
    /**
     * Checks if this sphere collider intersects with another sphere collider.
     * 
     * @param other The other sphere collider to check against
     * @return True if the colliders intersect
     */
    private fun intersectsSphere(other: SphereCollider): Boolean {
        val thisCenter = getWorldPosition()
        val otherCenter = other.getWorldPosition()
        
        val distanceSquared = thisCenter.distanceSquared(otherCenter)
        val radiusSum = radius + other.radius
        
        return distanceSquared <= radiusSum * radiusSum
    }
    
    /**
     * Checks if this sphere collider intersects with a box collider.
     * 
     * @param other The box collider to check against
     * @return True if the colliders intersect
     */
    private fun intersectsBox(other: BoxCollider): Boolean {
        // Find the closest point on the box to the sphere center
        val closestPoint = other.closestPoint(getWorldPosition())
        
        // Check if the closest point is within the sphere radius
        val sphereCenter = getWorldPosition()
        val distanceSquared = closestPoint.distanceSquared(sphereCenter)
        
        return distanceSquared <= radius * radius
    }
    
    /**
     * Gets the closest point on the sphere to a given point.
     * 
     * @param point The point to find the closest point to
     * @return The closest point on the sphere
     */
    override fun closestPoint(point: Vector3f): Vector3f {
        val sphereCenter = getWorldPosition()
        
        // If the point is inside the sphere, return the point
        if (sphereCenter.distanceSquared(point) <= radius * radius) {
            return Vector3f(point)
        }
        
        // Otherwise, return the point on the sphere's surface in the direction of the point
        val direction = Vector3f(point).sub(sphereCenter).normalize()
        return Vector3f(sphereCenter).add(direction.mul(radius))
    }
}