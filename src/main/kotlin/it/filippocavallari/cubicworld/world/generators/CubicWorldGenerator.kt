package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.world.chunk.CubicChunk

/**
 * Interface for world generators that work natively with CubicChunks.
 * This replaces the traditional WorldGenerator interface for cubic chunk systems.
 */
interface CubicWorldGenerator {
    /**
     * Generate content for a cubic chunk (16x16x16)
     */
    fun generateCubicChunk(chunk: CubicChunk)
    
    /**
     * Seed used for world generation
     */
    val seed: Long
}