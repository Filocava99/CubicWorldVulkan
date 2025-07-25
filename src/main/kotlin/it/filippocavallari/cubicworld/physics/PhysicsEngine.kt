package it.filippocavallari.cubicworld.physics

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.CubicWorld
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.chunk.CubicChunk
import org.joml.Vector3f
import org.vulkanb.eng.scene.Camera

enum class MovementMode {
    FLY,
    PHYSICS
}

class PhysicsEngine(
    private val world: World? = null,
    private val cubicWorld: CubicWorld? = null
) {
    companion object {
        private const val GRAVITY = -9.8f * 2.0f // Stronger gravity for game feel
        private const val TERMINAL_VELOCITY = -20.0f
        private const val GROUND_FRICTION = 0.2f // Much lower friction for more responsive movement
        private const val AIR_FRICTION = 0.5f // Reduced air friction for less floaty feel
        private const val JUMP_VELOCITY = 8.0f
        private const val PLAYER_HEIGHT = 2.0f // Player is exactly 2 blocks tall
        private const val CAMERA_HEIGHT_OFFSET = 1.0f // Camera is 1 block above ground (at eye level)
        private const val PLAYER_WIDTH = 0.6f
        private const val COLLISION_TOLERANCE = 0.001f // Very small tolerance to prevent floating point issues
        private const val STEP_HEIGHT = 0.5f
        private const val SPRINT_MULTIPLIER = 2.5f // Increased sprint multiplier
        private const val WALK_SPEED = 8.0f // Doubled base walk speed
        private const val GROUND_SNAP_DISTANCE = 0.1f // Distance to snap to ground when very close
        private const val MAX_HORIZONTAL_SPEED = 12.0f // Cap to prevent excessive speed
        private const val ACCELERATION_FACTOR = 15.0f // How quickly player reaches target speed
        private const val DECELERATION_FACTOR = 12.0f // How quickly player stops when no input
    }
    
    private val velocity = Vector3f()
    private var isOnGround = false
    private var canJump = false
    private var groundY = 0f // Track the exact ground level
    
    fun update(camera: Camera, deltaTime: Float, isSprinting: Boolean) {
        val position = camera.position
        
        // Apply gravity if not on ground
        if (!isOnGround) {
            velocity.y += GRAVITY * deltaTime
            
            // Clamp to terminal velocity
            if (velocity.y < TERMINAL_VELOCITY) {
                velocity.y = TERMINAL_VELOCITY
            }
        }
        
        // Apply friction/deceleration - more responsive stopping
        if (isOnGround) {
            // Ground deceleration - quick stopping
            velocity.x *= GROUND_FRICTION
            velocity.z *= GROUND_FRICTION
        } else {
            // Air deceleration - some control in air
            velocity.x *= AIR_FRICTION
            velocity.z *= AIR_FRICTION
        }
        
        // Calculate potential new position
        val deltaX = velocity.x * deltaTime
        val deltaY = velocity.y * deltaTime
        val deltaZ = velocity.z * deltaTime
        
        // Handle X-axis movement with collision detection
        val testPosX = Vector3f(position.x + deltaX, position.y, position.z)
        if (!checkPlayerCollision(testPosX)) {
            position.x += deltaX
        } else {
            velocity.x = 0f
        }
        
        // Handle Z-axis movement with collision detection  
        val testPosZ = Vector3f(position.x, position.y, position.z + deltaZ)
        if (!checkPlayerCollision(testPosZ)) {
            position.z += deltaZ
        } else {
            velocity.z = 0f
        }
        
        // Handle Y-axis movement with special ground handling
        val newY = position.y + deltaY
        val testPosY = Vector3f(position.x, newY, position.z)
        
        // Find the ground level at current position
        val currentGroundY = findGroundLevel(position.x, position.z)
        val playerBottomY = newY - CAMERA_HEIGHT_OFFSET // Bottom of player
        
        if (velocity.y <= 0) { // Falling or stationary
            if (playerBottomY <= currentGroundY + GROUND_SNAP_DISTANCE) {
                // Player is on or very close to ground - snap to ground
                groundY = currentGroundY
                position.y = groundY + CAMERA_HEIGHT_OFFSET
                velocity.y = 0f
                isOnGround = true
                canJump = true
            } else if (!checkPlayerCollision(testPosY)) {
                // Still falling - no collision
                position.y = newY
                isOnGround = false
                canJump = false
            } else {
                // Hit something - stop vertical movement
                velocity.y = 0f
                if (playerBottomY <= currentGroundY + COLLISION_TOLERANCE) {
                    // Hit ground
                    groundY = currentGroundY
                    position.y = groundY + CAMERA_HEIGHT_OFFSET
                    isOnGround = true
                    canJump = true
                }
            }
        } else { // Moving upward (jumping)
            if (!checkPlayerCollision(testPosY)) {
                position.y = newY
                isOnGround = false
                canJump = false
            } else {
                // Hit ceiling
                velocity.y = 0f
            }
        }
        
        camera.recalculate()
    }
    
    fun moveHorizontal(direction: Vector3f, deltaTime: Float, isSprinting: Boolean) {
        val targetSpeed = if (isSprinting) WALK_SPEED * SPRINT_MULTIPLIER else WALK_SPEED
        val acceleration = if (isOnGround) ACCELERATION_FACTOR else ACCELERATION_FACTOR * 0.5f
        
        if (direction.lengthSquared() > 0) {
            // Player is trying to move - accelerate towards target velocity
            val targetVelocity = Vector3f(direction).normalize().mul(targetSpeed)
            
            // Lerp towards target velocity for smooth acceleration
            val lerpFactor = acceleration * deltaTime
            velocity.x = lerp(velocity.x, targetVelocity.x, lerpFactor)
            velocity.z = lerp(velocity.z, targetVelocity.z, lerpFactor)
        } else {
            // No input - decelerate quickly for responsive stopping
            val decelerationFactor = if (isOnGround) DECELERATION_FACTOR else DECELERATION_FACTOR * 0.3f
            val lerpFactor = decelerationFactor * deltaTime
            velocity.x = lerp(velocity.x, 0f, lerpFactor)
            velocity.z = lerp(velocity.z, 0f, lerpFactor)
        }
        
        // Cap horizontal speed to prevent excessive velocity
        val horizontalSpeed = kotlin.math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        if (horizontalSpeed > MAX_HORIZONTAL_SPEED) {
            val scale = MAX_HORIZONTAL_SPEED / horizontalSpeed
            velocity.x *= scale
            velocity.z *= scale
        }
    }
    
    private fun lerp(start: Float, end: Float, factor: Float): Float {
        return start + (end - start) * kotlin.math.min(factor, 1.0f)
    }
    
    fun jump() {
        if (canJump && isOnGround) {
            velocity.y = JUMP_VELOCITY
            isOnGround = false
            canJump = false
        }
    }
    
    private fun checkPlayerCollision(cameraPosition: Vector3f): Boolean {
        // Player bounding box: camera is at eye level (1 block above ground)
        // Player extends from (camera.y - 1) to (camera.y + 1)
        val playerBottomY = cameraPosition.y - CAMERA_HEIGHT_OFFSET
        val playerTopY = cameraPosition.y + CAMERA_HEIGHT_OFFSET
        
        val minX = cameraPosition.x - PLAYER_WIDTH / 2
        val maxX = cameraPosition.x + PLAYER_WIDTH / 2
        val minZ = cameraPosition.z - PLAYER_WIDTH / 2
        val maxZ = cameraPosition.z + PLAYER_WIDTH / 2
        
        // Sample points around the player's 2-block-tall bounding box
        val samplePoints = listOf(
            // Bottom corners
            Vector3f(minX, playerBottomY, minZ), Vector3f(maxX, playerBottomY, minZ),
            Vector3f(minX, playerBottomY, maxZ), Vector3f(maxX, playerBottomY, maxZ),
            // Top corners  
            Vector3f(minX, playerTopY, minZ), Vector3f(maxX, playerTopY, minZ),
            Vector3f(minX, playerTopY, maxZ), Vector3f(maxX, playerTopY, maxZ),
            // Center points
            Vector3f(cameraPosition.x, playerBottomY, cameraPosition.z), // Center bottom
            Vector3f(cameraPosition.x, playerTopY, cameraPosition.z),    // Center top
            Vector3f(cameraPosition.x, cameraPosition.y, cameraPosition.z) // Camera center
        )
        
        for (point in samplePoints) {
            if (isBlockSolid(point)) {
                return true
            }
        }
        
        return false
    }
    
    private fun findGroundLevel(x: Float, z: Float): Float {
        // Find the highest solid block at this x,z position
        val blockX = x.toInt()
        val blockZ = z.toInt()
        
        // Handle negative coordinates properly  
        val floorX = if (x < 0) blockX - 1 else blockX
        val floorZ = if (z < 0) blockZ - 1 else blockZ
        
        // Search downward from a reasonable height to find ground
        val searchStartY = 100 // Start search from here
        
        for (y in searchStartY downTo -50) {
            val testPos = Vector3f(x, y.toFloat(), z)
            if (isBlockSolid(testPos)) {
                return y.toFloat() + 1.0f // Ground level is top of the solid block
            }
        }
        
        return 0f // Default ground level if nothing found
    }
    
    private fun isBlockSolid(worldPos: Vector3f): Boolean {
        val blockX = worldPos.x.toInt()
        val blockY = worldPos.y.toInt()
        val blockZ = worldPos.z.toInt()
        
        // Handle negative coordinates properly
        val floorX = if (worldPos.x < 0) blockX - 1 else blockX
        val floorY = if (worldPos.y < 0) blockY - 1 else blockY
        val floorZ = if (worldPos.z < 0) blockZ - 1 else blockZ
        
        return when {
            cubicWorld != null -> {
                val chunkX = CubicChunk.worldToChunk(floorX)
                val chunkY = CubicChunk.worldToChunk(floorY)
                val chunkZ = CubicChunk.worldToChunk(floorZ)
                
                val chunk = cubicWorld.getChunk(chunkX, chunkY, chunkZ)
                if (chunk != null) {
                    val localX = floorX - chunkX * CubicChunk.SIZE
                    val localY = floorY - chunkY * CubicChunk.SIZE
                    val localZ = floorZ - chunkZ * CubicChunk.SIZE
                    
                    if (localX in 0 until CubicChunk.SIZE && 
                        localY in 0 until CubicChunk.SIZE && 
                        localZ in 0 until CubicChunk.SIZE) {
                        val blockId = chunk.getBlock(localX, localY, localZ)
                        blockId != 0 && blockId != BlockType.WATER.id
                    } else false
                } else false
            }
            world != null -> {
                val chunkX = Chunk.worldToChunk(floorX)
                val chunkZ = Chunk.worldToChunk(floorZ)
                
                val chunk = world.getChunk(chunkX, chunkZ)
                if (chunk != null && floorY >= 0 && floorY < Chunk.HEIGHT) {
                    val localX = floorX - chunkX * Chunk.SIZE
                    val localZ = floorZ - chunkZ * Chunk.SIZE
                    
                    if (localX in 0 until Chunk.SIZE && localZ in 0 until Chunk.SIZE) {
                        val blockId = chunk.getBlock(localX, floorY, localZ)
                        blockId != 0 && blockId != BlockType.WATER.id
                    } else false
                } else false
            }
            else -> false
        }
    }
    
    fun getVelocity(): Vector3f = Vector3f(velocity)
    fun isOnGround(): Boolean = isOnGround
    fun setVelocity(newVelocity: Vector3f) {
        velocity.set(newVelocity)
    }
    
    fun reset() {
        velocity.set(0f, 0f, 0f)
        isOnGround = false
        canJump = false
        groundY = 0f
    }
}