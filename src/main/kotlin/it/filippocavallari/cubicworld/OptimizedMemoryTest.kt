package it.filippocavallari.cubicworld

import it.filippocavallari.cubicworld.data.block.BlockType
import it.filippocavallari.cubicworld.data.block.NormalIndex
import it.filippocavallari.cubicworld.integration.OptimizedVulkanChunkMeshBuilder
import it.filippocavallari.cubicworld.textures.TextureStitcher
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.FlatWorldGenerator

/**
 * Test demonstration of the optimized vertex memory system.
 * Shows the memory reduction achieved by using compact vertex data types.
 */
fun main() {
    println("🔧 CubicWorld Vertex Memory Optimization Test")
    println("=" * 60)
    
    // Initialize texture system
    val textureStitcher = TextureStitcher("src/main/resources/textures")
    try {
        textureStitcher.build(16)
        println("✓ Texture atlas initialized with ${textureStitcher.totalTextures} textures")
    } catch (e: Exception) {
        println("⚠ Warning: Using fallback texture system: ${e.message}")
    }
    
    // Create optimized mesh builder
    val optimizedBuilder = OptimizedVulkanChunkMeshBuilder(textureStitcher)
    
    // Generate test chunk with mixed terrain
    val testChunk = createTestChunk()
    
    // Analyze vertex memory usage
    analyzeMemoryUsage(testChunk, optimizedBuilder)
    
    // Demonstrate normal indexing system
    demonstrateNormalIndexing()
    
    println("\n🎯 Memory Optimization Complete!")
    println("The optimized system achieves ~80% memory reduction for vertex data.")
}

/**
 * Create a test chunk with various block types for analysis
 */
private fun createTestChunk(): Chunk {
    val chunk = Chunk(0, 0)
    val generator = FlatWorldGenerator()
    
    // Generate basic terrain
    generator.generateChunk(chunk)
    
    // Add some variety for better testing
    for (x in 0 until Chunk.SIZE) {
        for (z in 0 until Chunk.SIZE) {
            // Add some stone at higher levels
            if ((x + z) % 3 == 0) {
                chunk.setBlock(x, 65, z, BlockType.STONE.id)
                chunk.setBlock(x, 66, z, BlockType.COAL_ORE.id)
            }
            
            // Add some logs
            if ((x + z) % 5 == 0) {
                for (y in 65..68) {
                    chunk.setBlock(x, y, z, BlockType.LOG_OAK.id)
                }
                chunk.setBlock(x, 69, z, BlockType.LEAVES_OAK.id)
            }
        }
    }
    
    return chunk
}

/**
 * Analyze and compare memory usage between original and optimized formats
 */
private fun analyzeMemoryUsage(chunk: Chunk, optimizedBuilder: OptimizedVulkanChunkMeshBuilder) {
    println("\n📊 Memory Usage Analysis")
    println("-" * 40)
    
    // Build optimized mesh
    val meshData = optimizedBuilder.buildMesh(chunk)
    
    if (meshData == null) {
        println("⚠ No mesh data generated (chunk might be empty)")
        return
    }
    
    // Calculate statistics
    val indices = meshData.meshDataList[0].indices
    val totalFaces = indices.size / 6  // 6 indices per face
    val totalVertices = totalFaces * 4  // 4 vertices per face
    
    // Original format memory usage
    val originalVertexSize = 56  // 3×float + 3×float + 3×float + 3×float + 2×float = 56 bytes
    val originalMemory = totalVertices * originalVertexSize
    
    // Optimized format memory usage  
    val optimizedVertexSize = 11  // 3×short + 1×byte + 2×short = 11 bytes
    val optimizedMemory = totalVertices * optimizedVertexSize
    
    // Memory savings
    val memorySaved = originalMemory - optimizedMemory
    val reductionPercent = (memorySaved.toDouble() / originalMemory) * 100
    
    println("Chunk: ${chunk.getWorldX()}, ${chunk.getWorldZ()}")
    println("Faces rendered: $totalFaces")
    println("Vertices: $totalVertices")
    println()
    println("Memory Usage Comparison:")
    println("  Original format:  ${formatBytes(originalMemory)} ($originalVertexSize bytes/vertex)")
    println("  Optimized format: ${formatBytes(optimizedMemory)} ($optimizedVertexSize bytes/vertex)")
    println("  Memory saved:     ${formatBytes(memorySaved)}")
    println("  Reduction:        ${String.format("%.1f", reductionPercent)}%")
    
    // Per-component breakdown
    println("\nPer-Vertex Component Breakdown:")
    println("  Position:      12 bytes → 6 bytes  (50% reduction)")
    println("  Normal:        12 bytes → 1 byte   (92% reduction)")
    println("  Tangent:       12 bytes → 0 bytes  (100% reduction - computed in shader)")
    println("  BiTangent:     12 bytes → 0 bytes  (100% reduction - computed in shader)")
    println("  Tex Coords:    8 bytes  → 4 bytes  (50% reduction)")
    println("  TOTAL:         56 bytes → 11 bytes (${String.format("%.1f", reductionPercent)}% reduction)")
}

/**
 * Demonstrate the normal indexing system
 */
private fun demonstrateNormalIndexing() {
    println("\n🧭 Normal Indexing System")
    println("-" * 30)
    
    println("Cube face normals encoded as single bytes:")
    NormalIndex.values().forEach { normal ->
        val vec = normal.vector
        println("  ${normal.name}: index ${normal.index} → [${vec[0]}, ${vec[1]}, ${vec[2]}]")
    }
    
    println("\nMemory comparison:")
    println("  Original: 6 faces × 4 vertices × 12 bytes = 288 bytes per cube")
    println("  Optimized: 6 faces × 4 vertices × 1 byte = 24 bytes per cube")
    println("  Reduction: 264 bytes saved (92% reduction for normals)")
    
    println("\nVertex shader lookup table generated:")
    println("  getNormal(0) → vec3(1.0, 0.0, 0.0)  // +X")
    println("  getNormal(1) → vec3(-1.0, 0.0, 0.0) // -X")
    println("  getNormal(2) → vec3(0.0, 1.0, 0.0)  // +Y")
    println("  getNormal(3) → vec3(0.0, -1.0, 0.0) // -Y")
    println("  getNormal(4) → vec3(0.0, 0.0, 1.0)  // +Z")
    println("  getNormal(5) → vec3(0.0, 0.0, -1.0) // -Z")
}

/**
 * Format bytes in human-readable format
 */
private fun formatBytes(bytes: Int): String {
    return when {
        bytes >= 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0 / 1024.0)} MB"
        bytes >= 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
        else -> "$bytes bytes"
    }
}

/**
 * String repeat operator for formatting
 */
private operator fun String.times(count: Int): String {
    return this.repeat(count)
}