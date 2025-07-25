package it.filippocavallari.cubicworld.interaction

import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.CubicWorld
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.*

/**
 * Handles block interaction logic including raycasting to find target blocks
 * and determining placement faces.
 */
class BlockInteraction {
    companion object {
        private const val MAX_REACH_DISTANCE = 20.0f // Further increased reach distance
        private const val RAY_STEP = 0.1f // Larger steps for better performance
    }
    
    /**
     * Result of a block raycast
     */
    data class RaycastResult(
        val hit: Boolean,
        val blockPosition: Vector3i? = null,
        val hitPosition: Vector3f? = null,
        val blockFace: BlockFace? = null,
        val adjacentPosition: Vector3i? = null // Position where a new block would be placed
    )
    
    /**
     * Represents the face of a block that was hit
     */
    enum class BlockFace(val normal: Vector3f) {
        TOP(Vector3f(0f, 1f, 0f)),
        BOTTOM(Vector3f(0f, -1f, 0f)),
        NORTH(Vector3f(0f, 0f, -1f)),
        SOUTH(Vector3f(0f, 0f, 1f)),
        EAST(Vector3f(1f, 0f, 0f)),
        WEST(Vector3f(-1f, 0f, 0f))
    }
    
    /**
     * Cast a ray from the camera to find the target block for traditional chunks
     */
    fun raycastBlock(world: World, rayOrigin: Vector3f, rayDirection: Vector3f): RaycastResult {
        val normalizedDirection = Vector3f(rayDirection).normalize()
        val currentPos = Vector3f(rayOrigin)
        val step = Vector3f(normalizedDirection).mul(RAY_STEP)
        
        var totalDistance = 0f
        var lastAirPosition: Vector3i? = null
        
        while (totalDistance < MAX_REACH_DISTANCE) {
            val blockPos = Vector3i(
                floor(currentPos.x).toInt(),
                floor(currentPos.y).toInt(),
                floor(currentPos.z).toInt()
            )
            
            val blockId = world.getBlock(blockPos.x, blockPos.y, blockPos.z)
            
            if (blockId != 0) { // Hit a solid block
                val hitPosition = Vector3f(currentPos)
                val blockFace = determineHitFace(hitPosition, blockPos)
                val adjacentPos = lastAirPosition ?: getAdjacentPosition(blockPos, blockFace)
                
                return RaycastResult(
                    hit = true,
                    blockPosition = blockPos,
                    hitPosition = hitPosition,
                    blockFace = blockFace,
                    adjacentPosition = adjacentPos
                )
            }
            
            lastAirPosition = Vector3i(blockPos)
            currentPos.add(step)
            totalDistance += RAY_STEP
        }
        
        return RaycastResult(hit = false)
    }
    
    /**
     * Cast a ray from the camera to find the target block for cubic chunks
     */
    fun raycastCubicBlock(world: CubicWorld, rayOrigin: Vector3f, rayDirection: Vector3f): RaycastResult {
        println("DEBUG: Starting raycast from (${rayOrigin.x}, ${rayOrigin.y}, ${rayOrigin.z}) in direction (${rayDirection.x}, ${rayDirection.y}, ${rayDirection.z})")
        
        val normalizedDirection = Vector3f(rayDirection).normalize()
        val currentPos = Vector3f(rayOrigin)
        val step = Vector3f(normalizedDirection).mul(RAY_STEP)
        
        var totalDistance = 0f
        var lastAirPosition: Vector3i? = null
        var stepsChecked = 0
        
        while (totalDistance < MAX_REACH_DISTANCE) {
            val blockPos = Vector3i(
                floor(currentPos.x).toInt(),
                floor(currentPos.y).toInt(),
                floor(currentPos.z).toInt()
            )
            
            val blockId = world.getBlock(blockPos.x, blockPos.y, blockPos.z)
            
            if (blockId != 0) { // Hit a solid block
                val hitPosition = Vector3f(currentPos)
                val blockFace = determineHitFace(hitPosition, blockPos)
                val adjacentPos = lastAirPosition ?: getAdjacentPosition(blockPos, blockFace)
                
                return RaycastResult(
                    hit = true,
                    blockPosition = blockPos,
                    hitPosition = hitPosition,
                    blockFace = blockFace,
                    adjacentPosition = adjacentPos
                )
            }
            
            lastAirPosition = Vector3i(blockPos)
            currentPos.add(step)
            totalDistance += RAY_STEP
        }
        
        return RaycastResult(hit = false)
    }
    
    /**
     * Determine which face of a block was hit based on the hit position
     */
    private fun determineHitFace(hitPosition: Vector3f, blockPosition: Vector3i): BlockFace {
        // Calculate the relative position within the block (0.0 to 1.0)
        val relativeX = hitPosition.x - blockPosition.x
        val relativeY = hitPosition.y - blockPosition.y
        val relativeZ = hitPosition.z - blockPosition.z
        
        // Determine which face is closest to the hit point
        val distanceToTop = 1.0f - relativeY
        val distanceToBottom = relativeY
        val distanceToEast = 1.0f - relativeX
        val distanceToWest = relativeX
        val distanceToSouth = 1.0f - relativeZ
        val distanceToNorth = relativeZ
        
        val minDistance = minOf(
            distanceToTop, distanceToBottom,
            distanceToEast, distanceToWest,
            distanceToSouth, distanceToNorth
        )
        
        return when (minDistance) {
            distanceToTop -> BlockFace.TOP
            distanceToBottom -> BlockFace.BOTTOM
            distanceToEast -> BlockFace.EAST
            distanceToWest -> BlockFace.WEST
            distanceToSouth -> BlockFace.SOUTH
            else -> BlockFace.NORTH
        }
    }
    
    /**
     * Get the position adjacent to a block based on the face that was hit
     */
    private fun getAdjacentPosition(blockPosition: Vector3i, face: BlockFace): Vector3i {
        return Vector3i(blockPosition).add(
            face.normal.x.toInt(),
            face.normal.y.toInt(),
            face.normal.z.toInt()
        )
    }
    
    /**
     * Get the direction vector from camera rotation
     */
    fun getDirectionFromRotation(pitch: Float, yaw: Float): Vector3f {
        val direction = Vector3f()
        // Match camera coordinate system: Z-negative is forward, X-positive is right
        direction.x = cos(pitch) * sin(yaw)
        direction.y = -sin(pitch)
        direction.z = -cos(pitch) * cos(yaw)  // Negated to match camera forward direction
        
        return direction.normalize()
    }
}