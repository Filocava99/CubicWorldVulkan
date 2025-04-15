package it.filippocavallari.cubicworld.physics

import org.joml.Vector3f
import kotlin.math.abs

/**
 * Box collider component for game objects.
 * Represents a box-shaped collision volume.
 */
class BoxCollider : Collider() {
    
    // Half-extents of the box
    var size = Vector3f(1f, 1f, 1f)
        set(value) {
            field.set(value)
            halfSize.set(value).mul(0.5f)
        }
    
    // Cached half-size for faster calculations
    private val halfSize = Vector3f(0.5f, 0.5f, 0.5f)
    
    /**
     * Gets the minimum point of the box in world space.
     * 
     * @return The minimum point
     */
    fun getMin(): Vector3f {
        val worldPosition = getWorldPosition()
        return Vector3f(
            worldPosition.x - halfSize.x,
            worldPosition.y - halfSize.y,
            worldPosition.z - halfSize.z
        )
    }
    
    /**
     * Gets the maximum point of the box in world space.
     * 
     * @return The maximum point
     */
    fun getMax(): Vector3f {
        val worldPosition = getWorldPosition()
        return Vector3f(
            worldPosition.x + halfSize.x,
            worldPosition.y + halfSize.y,
            worldPosition.z + halfSize.z
        )
    }
    
    /**
     * Checks if this box collider intersects with another collider.
     * 
     * @param other The other collider to check against
     * @return True if the colliders intersect
     */
    override fun intersects(other: Collider): Boolean {
        return when (other) {
            is BoxCollider -> intersectsBox(other)
            is SphereCollider -> intersectsSphere(other)
            else -> false
        }
    }
    
    /**
     * Checks if this box collider intersects with another box collider.
     * 
     * @param other The other box collider to check against
     * @return True if the colliders intersect
     */
    private fun intersectsBox(other: BoxCollider): Boolean {
        val thisMin = getMin()
        val thisMax = getMax()
        val otherMin = other.getMin()
        val otherMax = other.getMax()
        
        // Check if the boxes overlap on all axes
        return (thisMin.x <= otherMax.x && thisMax.x >= otherMin.x) &&
               (thisMin.y <= otherMax.y && thisMax.y >= otherMin.y) &&
               (thisMin.z <= otherMax.z && thisMax.z >= otherMin.z)
    }
    
    /**
     * Checks if this box collider intersects with a sphere collider.
     * 
     * @param other The sphere collider to check against
     * @return True if the colliders intersect
     */
    private fun intersectsSphere(other: SphereCollider): Boolean {
        // Find the closest point on the box to the sphere center
        val closestPoint = closestPoint(other.getWorldPosition())
        
        // Check if the closest point is within the sphere radius
        val sphereCenter = other.getWorldPosition()
        val distanceSquared = closestPoint.distanceSquared(sphereCenter)
        
        return distanceSquared <= other.radius * other.radius
    }
    
    /**
     * Gets the closest point on the box to a given point.
     * 
     * @param point The point to find the closest point to
     * @return The closest point on the box
     */
    override fun closestPoint(point: Vector3f): Vector3f {
        val worldPosition = getWorldPosition()
        val closestPoint = Vector3f(point)
        
        // For each axis, clamp the point to be within the box
        val min = getMin()
        val max = getMax()
        
        closestPoint.x = kotlin.math.max(min.x, kotlin.math.min(closestPoint.x, max.x))
        closestPoint.y = kotlin.math.max(min.y, kotlin.math.min(closestPoint.y, max.y))
        closestPoint.z = kotlin.math.max(min.z, kotlin.math.min(closestPoint.z, max.z))
        
        return closestPoint
    }
}