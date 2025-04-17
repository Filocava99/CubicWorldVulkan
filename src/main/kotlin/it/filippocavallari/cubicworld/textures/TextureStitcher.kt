package it.filippocavallari.cubicworld.textures

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * A texture stitcher that combines multiple images into texture atlases.
 * It creates three atlas textures: diffuse, normal, and specular.
 */
class TextureStitcher(folderPath: String) {
    private val sourceFolderPath: Path = Paths.get(folderPath)
    private lateinit var diffuseAtlas: BufferedImage
    private lateinit var normalAtlas: BufferedImage
    private lateinit var specularAtlas: BufferedImage
    private val textureRegions: MutableMap<Int, TextureRegion> = HashMap()
    private val fileNamePattern: Pattern
    
    // Texture sizes and metadata
    var atlasWidth: Int = 0
    var atlasHeight: Int = 0
    private var textureSize: Int = 0
    private var texturesPerRow: Int = 0
    
    // Total number of textures
    var totalTextures: Int = 0
        private set
    
    init {
        // Pattern to match base textures (not ending with _n or _s)
        this.fileNamePattern = Pattern.compile("^(.+?)(?<!_n)(?<!_s)\\.(png|jpg)$")
    }
    
    /**
     * Build the texture atlases from the images in the source folder.
     * 
     * @param textureSize The size of each texture (assuming square textures)
     * @throws IOException If there's an error reading or writing images
     */
    @Throws(IOException::class)
    fun build(textureSize: Int) {
        this.textureSize = textureSize
        
        // 1. Find all base texture files
        val baseTextureFiles = findBaseTextureFiles()
        this.totalTextures = baseTextureFiles.size
        
        if (totalTextures == 0) {
            // No textures found, create a default atlas with a single texture
            println("Warning: No texture files found in $sourceFolderPath, creating a default atlas")
            this.totalTextures = 1
            this.textureSize = textureSize
            calculateAtlasDimensions()
            createEmptyAtlases()
            createDefaultAtlas()
            return
        }
        
        // 2. Calculate atlas dimensions
        calculateAtlasDimensions()
        
        // 3. Create empty atlas images
        createEmptyAtlases()
        
        // 4. Populate the atlases with textures
        populateAtlases(baseTextureFiles)
        
        println("Built texture atlases with $totalTextures textures")
        println("Atlas dimensions: ${atlasWidth}x$atlasHeight")
    }
    
    /**
     * Find all base texture files in the source folder.
     */
    @Throws(IOException::class)
    private fun findBaseTextureFiles(): List<File> {
        return Files.walk(sourceFolderPath, 1)
            .filter(Files::isRegularFile)
            .map { it.toFile() }
            .filter { file ->
                val matcher = fileNamePattern.matcher(file.name)
                matcher.matches()
            }
            .sorted(Comparator.comparing { it.name })
            .collect(Collectors.toList())
    }
    
    /**
     * Calculate the dimensions for the atlas textures.
     */
    private fun calculateAtlasDimensions() {
        // Calculate how many textures we can fit per row
        // We want a roughly square atlas, so we'll take the square root of the total
        texturesPerRow = Math.ceil(Math.sqrt(totalTextures.toDouble())).toInt()
        
        // Calculate atlas dimensions
        atlasWidth = texturesPerRow * textureSize
        atlasHeight = Math.ceil(totalTextures.toDouble() / texturesPerRow).toInt() * textureSize
        
        // Ensure dimensions are power of 2 for optimal GPU performance
        atlasWidth = nextPowerOfTwo(atlasWidth)
        atlasHeight = nextPowerOfTwo(atlasHeight)
    }
    
    /**
     * Get the next power of two greater than or equal to the given number.
     */
    private fun nextPowerOfTwo(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }
    
    /**
     * Create empty atlas images.
     */
    private fun createEmptyAtlases() {
        diffuseAtlas = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        normalAtlas = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        specularAtlas = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
    }
    
    /**
     * Create a default atlas with a simple checkerboard pattern.
     */
    private fun createDefaultAtlas() {
        val diffuseGraphics = diffuseAtlas.createGraphics()
        val normalGraphics = normalAtlas.createGraphics()
        val specularGraphics = specularAtlas.createGraphics()
        
        // Create a checkerboard pattern for the diffuse atlas
        val cellSize = textureSize / 8
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val color = if ((row + col) % 2 == 0) 0xFFFF00FF.toInt() else 0xFF00FFFF.toInt()
                for (y in row * cellSize until (row + 1) * cellSize) {
                    for (x in col * cellSize until (col + 1) * cellSize) {
                        diffuseAtlas.setRGB(x, y, color)
                    }
                }
            }
        }
        
        // Fill normal map with a flat normal (pointing up)
        val normalColor = 0xFF8080FF.toInt() // RGB(128, 128, 255) - blue is up
        for (y in 0 until textureSize) {
            for (x in 0 until textureSize) {
                normalAtlas.setRGB(x, y, normalColor)
            }
        }
        
        // Fill specular map with a medium specularity
        val specularColor = 0xFF808080.toInt() // RGB(128, 128, 128) - medium specularity
        for (y in 0 until textureSize) {
            for (x in 0 until textureSize) {
                specularAtlas.setRGB(x, y, specularColor)
            }
        }
        
        // Store texture region for the default texture
        textureRegions[0] = TextureRegion(0f, 0f, 1f, 1f)
        
        diffuseGraphics.dispose()
        normalGraphics.dispose()
        specularGraphics.dispose()
    }
    
    /**
     * Populate the atlases with textures.
     */
    @Throws(IOException::class)
    private fun populateAtlases(baseTextureFiles: List<File>) {
        val diffuseGraphics = diffuseAtlas.createGraphics()
        val normalGraphics = normalAtlas.createGraphics()
        val specularGraphics = specularAtlas.createGraphics()
        
        for (i in baseTextureFiles.indices) {
            val baseFile = baseTextureFiles[i]
            val baseName = baseFile.name
            val baseNameWithoutExtension = baseName.substring(0, baseName.lastIndexOf('.'))
            
            // Compute position in atlas
            val row = i / texturesPerRow
            val col = i % texturesPerRow
            val x = col * textureSize
            val y = row * textureSize
            
            // Store texture region
            val u1 = x.toFloat() / atlasWidth
            val v1 = y.toFloat() / atlasHeight
            val u2 = (x + textureSize).toFloat() / atlasWidth
            val v2 = (y + textureSize).toFloat() / atlasHeight
            textureRegions[i] = TextureRegion(u1, v1, u2, v2)
            
            // Load and draw diffuse texture
            val diffuseImage = loadAndResizeImage(baseFile)
            diffuseGraphics.drawImage(diffuseImage, x, y, textureSize, textureSize, null)
            
            // Load and draw normal map
            val normalFile = File(baseFile.parent, "${baseNameWithoutExtension}_n.png")
            if (normalFile.exists()) {
                val normalImage = loadAndResizeImage(normalFile)
                normalGraphics.drawImage(normalImage, x, y, textureSize, textureSize, null)
            }
            
            // Load and draw specular map
            val specularFile = File(baseFile.parent, "${baseNameWithoutExtension}_s.png")
            if (specularFile.exists()) {
                val specularImage = loadAndResizeImage(specularFile)
                specularGraphics.drawImage(specularImage, x, y, textureSize, textureSize, null)
            }
        }
        
        diffuseGraphics.dispose()
        normalGraphics.dispose()
        specularGraphics.dispose()
    }
    
    /**
     * Load and resize an image to match the texture size.
     */
    @Throws(IOException::class)
    private fun loadAndResizeImage(file: File): BufferedImage {
        val original = ImageIO.read(file)
        
        // If the image is already the right size, return it
        if (original.width == textureSize && original.height == textureSize) {
            return original
        }
        
        // Otherwise, resize it
        val resized = BufferedImage(textureSize, textureSize, BufferedImage.TYPE_INT_ARGB)
        val g = resized.createGraphics()
        g.drawImage(original, 0, 0, textureSize, textureSize, null)
        g.dispose()
        
        return resized
    }
    
    /**
     * Get the texture region for a specific texture index.
     * 
     * @param index The index of the texture
     * @return The texture region containing UV coordinates
     */
    fun getTextureRegion(index: Int): TextureRegion {
        // If the index is negative, it means a texture wasn't found
        // Return a fallback texture to avoid crashes
        if (index < 0) {
            // Don't print a warning here, it's handled in ChunkMeshBuilder
            return textureRegions.getOrDefault(0, TextureRegion(0f, 0f, 1f, 1f))
        }
        
        if (!textureRegions.containsKey(index)) {
            // Don't print a warning here since this is a fallback case
            return textureRegions.getOrDefault(0, TextureRegion(0f, 0f, 1f, 1f))
        }
        
        return textureRegions[index]!!
    }
    
    /**
     * Get the atlas image for diffuse textures.
     */
    fun getDiffuseAtlas(): BufferedImage {
        return diffuseAtlas
    }
    
    /**
     * Get the atlas image for normal maps.
     */
    fun getNormalAtlas(): BufferedImage {
        return normalAtlas
    }
    
    /**
     * Get the atlas image for specular maps.
     */
    fun getSpecularAtlas(): BufferedImage {
        return specularAtlas
    }
    
    /**
     * Save the atlas images to files.
     * 
     * @param outputFolder The folder to save the atlas images to
     * @throws IOException If there's an error writing the images
     */
    @Throws(IOException::class)
    fun saveAtlases(outputFolder: String) {
        val outputPath = Paths.get(outputFolder)
        Files.createDirectories(outputPath)
        
        val diffuseFile = outputPath.resolve("diffuse_atlas.png").toFile()
        val normalFile = outputPath.resolve("normal_atlas.png").toFile()
        val specularFile = outputPath.resolve("specular_atlas.png").toFile()
        
        ImageIO.write(diffuseAtlas, "PNG", diffuseFile)
        ImageIO.write(normalAtlas, "PNG", normalFile)
        ImageIO.write(specularAtlas, "PNG", specularFile)
        
        println("Saved atlas images to ${outputPath.toAbsolutePath()}")
    }
}