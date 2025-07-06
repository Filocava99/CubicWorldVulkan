package it.filippocavallari.cubicworld.data.block

/**
 * Enum of all block types in the game.
 * Each block type has associated properties.
 * Textures are resolved dynamically via TextureManager.
 */
enum class BlockType(
    val id: Int,
    val isTransparent: Boolean
) {
    // Air is special case (id 0)
    AIR(0, true),
    
    // Terrain blocks
    STONE(1, false),
    GRASS(2, false),
    DIRT(3, false),
    COBBLESTONE(4, false),
    BEDROCK(5, false),
    SAND(6, false),
    GRAVEL(7, false),
    
    // Wood types
    LOG_OAK(8, false),
    LEAVES_OAK(9, true),
    
    // Ore blocks
    COAL_ORE(10, false),
    IRON_ORE(11, false),
    GOLD_ORE(12, false),
    DIAMOND_ORE(13, false),
    REDSTONE_ORE(14, false),
    LAPIS_ORE(15, false),
    
    // Liquids
    WATER(16, true);
    
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