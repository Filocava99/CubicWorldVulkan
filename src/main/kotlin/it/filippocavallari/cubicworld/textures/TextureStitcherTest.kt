package it.filippocavallari.cubicworld.textures

import java.io.File

/**
 * Test utility for the TextureStitcher to verify all textures are loaded properly.
 */
object TextureStitcherTest {
    @JvmStatic
    fun main(args: Array<String>) {
        // Path to textures folder
        val texturesPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures"
        val outputPath = "C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\atlas"
        
        // First, count the total base textures manually for verification
        val baseTextures = countBaseTextures(texturesPath)
        println("Manually counted ${baseTextures.size} base textures in the directory")
        
        // Create texture stitcher and build the atlas
        val stitcher = TextureStitcher(texturesPath)
        stitcher.build(64) // 64px texture size for better quality
        
        // Save the atlases to the output directory
        File(outputPath).mkdirs() // Ensure the output directory exists
        stitcher.saveAtlases(outputPath)
        
        // Verify all textures were loaded
        val totalStitched = stitcher.totalTextures
        println("\nVerification Results:")
        println("- Manual count: ${baseTextures.size} base textures")
        println("- Stitcher loaded: $totalStitched textures")
        
        if (totalStitched < baseTextures.size) {
            println("WARNING: Not all textures were loaded!")
            // Let's find which textures were missed
            println("\nMissing Textures:")
            val missed = baseTextures.take(10) // Show first 10 missing, if any
            missed.forEach { println("- $it") }
            if (missed.size > 10) {
                println("... and ${missed.size - 10} more")
            }
        } else {
            println("SUCCESS: All textures were loaded successfully!")
        }
        
        println("\nAtlas dimensions: ${stitcher.atlasWidth}x${stitcher.atlasHeight}")
        println("Saved atlas files to: $outputPath")
    }
    
    /**
     * Count the number of base textures in the directory (excluding normal and specular maps)
     */
    private fun countBaseTextures(texturesPath: String): List<String> {
        val dir = File(texturesPath)
        return dir.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val name = file.name.lowercase()
                (name.endsWith(".png") || name.endsWith(".jpg")) && 
                !name.endsWith("_n.png") && !name.endsWith("_n.jpg") && 
                !name.endsWith("_s.png") && !name.endsWith("_s.jpg")
            }
            .map { it.name }
            .sorted()
            .toList()
    }
}