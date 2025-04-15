package it.filippocavallari.cubicworld.data.block

import it.filippocavallari.models.Model

/**
 * Registry of block types with their properties.
 */
enum class BlockType(

    val id: Int,
    val modelId: String? = null,
    val isSolid: Boolean,
    val isTransparent: Boolean
) {
    AIR(0, isSolid = false, isTransparent = true),
    STONE(1, "block/stone", true, false),
    DIRT(2, "block/dirt", true, false),
    GRASS(3, "block/grass_block", true, false),
    SAND(4, "block/sand", true, false),
    COBBLESTONE(5, "block/cobblestone", true, false),
    BEDROCK(6, "block/bedrock", true, false),
    LOG_OAK(7, "block/oak_log", true, false),
    WATER(8, "block/water", false, true),
    GLASS(9, "block/glass", false, false),
    LEAVES_OAK(10, "block/oak_leaves", false, false),
    BRICK(11, "block/bricks", true, false),
    PLANKS_OAK(12, "block/oak_planks", true, false),
    CRAFTING_TABLE(13, "block/crafting_table", true, false),
    GOLD_ORE(14, "block/gold_ore", true, false),
    IRON_ORE(15, "block/iron_ore", true, false),
    COAL_ORE(16, "block/coal_ore", true, false),
    DIAMOND_ORE(17, "block/diamond_ore", true, false),
    REDSTONE_ORE(18, "block/redstone_ore", true, false),
    LAPIS_ORE(19, "block/lapis_ore", true, false),
    LEAVES_BIRCH(20, "block/birch_leaves", false, false),
    LAVA(21, "block/lava", false, false),
    SAND_RED(22, "block/red_sand", true, false),
    GRAVEL(23, "block/gravel", true, false),
    BOOKSHELF(24, "block/bookshelf", true, false),
    OBSIDIAN(25, "block/obsidian", true, false);
    
    companion object {
        /**
         * Map to store dynamically registered block types
         */
        val registeredTypes = mutableMapOf<Int, String>()
        
        /**
         * Get block type by ID
         */
        fun getById(id: Int): BlockType {
            return values().find { it.id == id } ?: AIR // Default to air if not found
        }
        
        /**
         * Register a new block type at runtime
         */
        fun registerType(id: Int, name: String, modelId: String, isTransparent: Boolean) {
            // This is a simplified version that doesn't actually register new enum values
            // at runtime (which isn't possible in Kotlin)
            // Instead, we'll map these to existing block types with the closest properties
            registeredTypes[id] = name
            println("Registering block type: $name (ID: $id, Model: $modelId, Transparent: $isTransparent)")
            // In a real implementation, this would add to a dynamic registry
        }
    }
}