package it.filippocavallari.cubicworld.interaction

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.world.World
import it.filippocavallari.cubicworld.world.CubicWorld
import org.joml.Vector2f
import org.joml.Vector3f
import org.vulkanb.eng.scene.Camera

/**
 * Manages block interactions including placement and destruction
 */
class BlockInteractionManager(
    private val world: World? = null,
    private val cubicWorld: CubicWorld? = null
) {
    private val blockInteraction = BlockInteraction()
    private var selectedBlockType = BlockType.STONE.id // Default block to place
    
    /**
     * Handle left mouse click - remove block
     */
    fun handleLeftClick(camera: Camera): Boolean {
        println("DEBUG: Starting left click block removal")
        val result = performRaycast(camera)
        
        println("DEBUG: Raycast result - hit: ${result.hit}, blockPos: ${result.blockPosition}")
        if (result.hit && result.blockPosition != null) {
            // Get the current block ID before removal
            val oldBlockId = when {
                world != null -> world.getBlock(result.blockPosition.x, result.blockPosition.y, result.blockPosition.z)
                cubicWorld != null -> cubicWorld.getBlock(result.blockPosition.x, result.blockPosition.y, result.blockPosition.z)
                else -> 0
            }
            
            println("DEBUG: About to remove block ID $oldBlockId at (${result.blockPosition.x}, ${result.blockPosition.y}, ${result.blockPosition.z})")
            
            // Remove the block by setting it to air (0)
            when {
                world != null -> {
                    world.setBlock(
                        result.blockPosition.x,
                        result.blockPosition.y,
                        result.blockPosition.z,
                        0 // Air
                    )
                }
                cubicWorld != null -> {
                    cubicWorld.setBlock(
                        result.blockPosition.x,
                        result.blockPosition.y,
                        result.blockPosition.z,
                        0 // Air
                    )
                }
            }
            
            // Verify the block was actually changed
            val newBlockId = when {
                world != null -> world.getBlock(result.blockPosition.x, result.blockPosition.y, result.blockPosition.z)
                cubicWorld != null -> cubicWorld.getBlock(result.blockPosition.x, result.blockPosition.y, result.blockPosition.z)
                else -> -1
            }
            
            println("DEBUG: Block change verification - Old: $oldBlockId, New: $newBlockId")
            println("✓ Block removed at (${result.blockPosition.x}, ${result.blockPosition.y}, ${result.blockPosition.z})")
            return true
        }
        
        return false
    }
    
    /**
     * Handle right mouse click - place block
     */
    fun handleRightClick(camera: Camera): Boolean {
        println("DEBUG: Starting right click block placement")
        val result = performRaycast(camera)
        
        println("DEBUG: Raycast result - hit: ${result.hit}, blockPos: ${result.blockPosition}, adjacentPos: ${result.adjacentPosition}")
        if (result.hit && result.adjacentPosition != null) {
            // Check if the placement position is not occupied
            val currentBlock = when {
                world != null -> world.getBlock(
                    result.adjacentPosition.x,
                    result.adjacentPosition.y,
                    result.adjacentPosition.z
                )
                cubicWorld != null -> cubicWorld.getBlock(
                    result.adjacentPosition.x,
                    result.adjacentPosition.y,
                    result.adjacentPosition.z
                )
                else -> 1 // Assume occupied if no world
            }
            
            println("DEBUG: Adjacent position block ID: $currentBlock")
            if (currentBlock == 0) { // Only place if position is air
                println("DEBUG: Placing block type $selectedBlockType at (${result.adjacentPosition.x}, ${result.adjacentPosition.y}, ${result.adjacentPosition.z})")
                
                when {
                    world != null -> {
                        world.setBlock(
                            result.adjacentPosition.x,
                            result.adjacentPosition.y,
                            result.adjacentPosition.z,
                            selectedBlockType
                        )
                    }
                    cubicWorld != null -> {
                        cubicWorld.setBlock(
                            result.adjacentPosition.x,
                            result.adjacentPosition.y,
                            result.adjacentPosition.z,
                            selectedBlockType
                        )
                    }
                }
                
                // Verify the block was actually placed
                val verifyBlock = when {
                    world != null -> world.getBlock(result.adjacentPosition.x, result.adjacentPosition.y, result.adjacentPosition.z)
                    cubicWorld != null -> cubicWorld.getBlock(result.adjacentPosition.x, result.adjacentPosition.y, result.adjacentPosition.z)
                    else -> -1
                }
                
                println("DEBUG: Block placement verification - Expected: $selectedBlockType, Actual: $verifyBlock")
                println("✓ Block placed at (${result.adjacentPosition.x}, ${result.adjacentPosition.y}, ${result.adjacentPosition.z})")  
                return true
            } else {
                println("DEBUG: Cannot place block - position is occupied by block ID $currentBlock")
            }
        }
        
        return false
    }
    
    /**
     * Perform raycast based on available world type
     */
    private fun performRaycast(camera: Camera): BlockInteraction.RaycastResult {
        val cameraPos = camera.position
        val cameraRotation = camera.rotation
        
        println("DEBUG: Camera position: (${cameraPos.x}, ${cameraPos.y}, ${cameraPos.z})")
        println("DEBUG: Camera rotation: pitch=${cameraRotation.x}, yaw=${cameraRotation.y}")
        
        // Get direction vector from camera rotation
        val direction = blockInteraction.getDirectionFromRotation(
            cameraRotation.x, // pitch
            cameraRotation.y  // yaw
        )
        
        println("DEBUG: Raycast direction: (${direction.x}, ${direction.y}, ${direction.z})")
        
        return when {
            world != null -> blockInteraction.raycastBlock(world, cameraPos, direction)
            cubicWorld != null -> blockInteraction.raycastCubicBlock(cubicWorld, cameraPos, direction)
            else -> BlockInteraction.RaycastResult(hit = false)
        }
    }
    
    /**
     * Set the selected block type for placement
     */
    fun setSelectedBlockType(blockType: BlockType) {
        selectedBlockType = blockType.id
    }
    
    /**
     * Get information about the currently targeted block
     */
    fun getTargetedBlockInfo(camera: Camera): String? {
        val result = performRaycast(camera)
        
        if (result.hit && result.blockPosition != null) {
            val blockId = when {
                world != null -> world.getBlock(
                    result.blockPosition.x,
                    result.blockPosition.y,
                    result.blockPosition.z
                )
                cubicWorld != null -> cubicWorld.getBlock(
                    result.blockPosition.x,
                    result.blockPosition.y,
                    result.blockPosition.z
                )
                else -> 0
            }
            
            val blockType = BlockType.values().find { it.id == blockId }?.name ?: "UNKNOWN"
            
            return "Target: $blockType at (${result.blockPosition.x}, ${result.blockPosition.y}, ${result.blockPosition.z}) on ${result.blockFace?.name} face"
        }
        
        return null
    }
}