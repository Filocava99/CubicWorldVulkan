package it.filippocavallari.textures

import java.io.IOException

/**
 * Example usage of the TextureStitcher
 */
object TextureAtlasExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            // Create a texture stitcher pointing to your textures folder
            val stitcher = TextureStitcher("C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\textures")
            
            // Build the texture atlases (specify the size of each texture in pixels)
            stitcher.build(128)
            
            // Save the atlas images (optional)
            stitcher.saveAtlases("C:\\Users\\filip\\Desktop\\CubicWorld\\CubicWorld\\src\\main\\resources\\atlas")
            
            // Example of getting a texture region
            val textureIndex = 0
            val region = stitcher.getTextureRegion(textureIndex)
            println("Texture region for index $textureIndex: $region")
            
            // When setting up your renderer, you would load the atlas textures to the GPU
            // val textureIds = TextureAtlasLoader.loadAtlasesToGPU(stitcher)
            
            // Then in your rendering code, you would:
            // 1. Bind the textures
            // 2. Set the texture coordinates based on the texture region for each mesh
            
            println("Texture atlas created successfully with ${stitcher.totalTextures} textures.")
            
        } catch (e: IOException) {
            System.err.println("Error creating texture atlas: ${e.message}")
            e.printStackTrace()
        }
    }
}