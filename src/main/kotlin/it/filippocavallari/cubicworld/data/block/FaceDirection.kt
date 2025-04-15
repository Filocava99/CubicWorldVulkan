package it.filippocavallari.cubicworld.data.block

/**
 * Enum representing the six possible face directions of a block.
 */
enum class FaceDirection(
    val offsetX: Int,
    val offsetY: Int,
    val offsetZ: Int
) {
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0);
    
    /**
     * Get the opposite face direction.
     */
    fun getOpposite(): FaceDirection {
        return when (this) {
            UP -> DOWN
            DOWN -> UP
            NORTH -> SOUTH
            SOUTH -> NORTH
            EAST -> WEST
            WEST -> EAST
        }
    }
}