package it.filippocavallari.cubicworld.world.generators

import it.filippocavallari.cubicworld.world.chunk.Chunk

/**
 * Interface for world generators that create chunks.
 */
interface WorldGenerator {
    /**
     * Generate a chunk at the specified position.
     *
     * @param chunk The chunk to fill with blocks
     */
    fun generateChunk(chunk: Chunk)
}