package it.filippocavallari.cubicworld.data.block

/**
 * Enum of all block types in the game.
 * Each block type has associated properties and texture indices.
 */
enum class BlockType(
    val id: Int,
    val isTransparent: Boolean,
    val sideTextureIndex: Int,
    val topTextureIndex: Int,
    val bottomTextureIndex: Int
) {
    // Air is special case (id 0)
    AIR(0, true, 0, 0, 0),
    
    // Terrain blocks
    STONE(1, false, 1, 1, 1),
    GRASS(2, false, 2, 3, 4), // Side, top, bottom
    DIRT(3, false, 4, 4, 4),
    COBBLESTONE(4, false, 5, 5, 5),
    BEDROCK(5, false, 6, 6, 6),
    SAND(6, false, 7, 7, 7),
    GRAVEL(7, false, 8, 8, 8),
    
    // Wood types
    LOG_OAK(8, false, 9, 10, 10), // Side, top/bottom
    LEAVES_OAK(9, true, 11, 11, 11),
    
    // Ore blocks
    COAL_ORE(10, false, 12, 12, 12),
    IRON_ORE(11, false, 13, 13, 13),
    GOLD_ORE(12, false, 14, 14, 14),
    DIAMOND_ORE(13, false, 15, 15, 15),
    REDSTONE_ORE(14, false, 16, 16, 16),
    LAPIS_ORE(15, false, 17, 17, 17),
    
    // Liquids
    WATER(16, true, 18, 18, 18);
    
    companion object {
        // Lookup map for fast block type access by ID
        private val idToType = values().associateBy { it.id }
        
        /**
         * Get a block type from its ID
         */
        fun fromId(id: Int): BlockType {
            return idToType[id] ?: AIR
        }
    }
}