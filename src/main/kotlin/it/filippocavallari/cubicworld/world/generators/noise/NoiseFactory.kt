package it.filippocavallari.cubicworld.world.generators.noise

import org.joml.SimplexNoise
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A factory for generating various types of noise functions needed for terrain generation.
 * Provides simplex, perlin, worley (cellular), and combined noise functions.
 */
class NoiseFactory {
    companion object {
        /**
         * Generate simplex noise at the given coordinates
         */
        fun simplexNoise(x: Float, y: Float, scale: Float): Float {
            return SimplexNoise.noise(x * scale, y * scale)
        }
        
        /**
         * Generate 3D simplex noise
         */
        fun simplexNoise3D(x: Float, y: Float, z: Float, scale: Float): Float {
            return SimplexNoise.noise(x * scale, y * scale, z * scale)
        }
        
        /**
         * Generate octaved (fractal) simplex noise (2D)
         */
        fun octavedSimplexNoise(
            x: Float, 
            y: Float, 
            octaves: Int = 4,
            baseScale: Float = 0.01f,
            persistence: Float = 0.5f,
            lacunarity: Float = 2.0f
        ): Float {
            var result = 0.0f
            var amplitude = 1.0f
            var frequency = baseScale
            var max = 0.0f
            
            for (i in 0 until octaves) {
                result += amplitude * SimplexNoise.noise(x * frequency, y * frequency)
                max += amplitude
                amplitude *= persistence
                frequency *= lacunarity
            }
            
            // Normalize to -1 to 1
            return result / max
        }
        
        /**
         * Generate octaved (fractal) simplex noise (3D)
         */
        fun octavedSimplexNoise3D(
            x: Float, 
            y: Float, 
            z: Float,
            octaves: Int = 4,
            baseScale: Float = 0.01f,
            persistence: Float = 0.5f,
            lacunarity: Float = 2.0f
        ): Float {
            var result = 0.0f
            var amplitude = 1.0f
            var frequency = baseScale
            var max = 0.0f
            
            for (i in 0 until octaves) {
                result += amplitude * SimplexNoise.noise(
                    x * frequency, 
                    y * frequency, 
                    z * frequency
                )
                max += amplitude
                amplitude *= persistence
                frequency *= lacunarity
            }
            
            // Normalize to -1 to 1
            return result / max
        }
        
        /**
         * Generate 2D Worley noise (also known as Cellular noise)
         * This function generates Voronoi/cellular noise which is excellent for biome borders
         * Used by Minecraft for modern biome generation
         */
        fun worleyNoise(
            x: Float, 
            y: Float, 
            scale: Float = 0.01f,
            jitter: Float = 1.0f
        ): Float {
            // Scale the coordinates to get the cell coordinates
            val cellX = (x * scale).toInt()
            val cellY = (y * scale).toInt()
            
            var minDist = Float.MAX_VALUE
            
            // Check current cell and neighboring cells
            for (i in -1..1) {
                for (j in -1..1) {
                    val neighborCellX = cellX + i
                    val neighborCellY = cellY + j
                    
                    // Generate a deterministic random point within this cell
                    // Using simple hash function for reproducibility
                    val hash = hashPoint(neighborCellX, neighborCellY)
                    val pointX = neighborCellX + jitter * (hash and 0xFF) / 255.0f
                    val pointY = neighborCellY + jitter * ((hash shr 8) and 0xFF) / 255.0f
                    
                    // Calculate distance to this point
                    val dx = x * scale - pointX
                    val dy = y * scale - pointY
                    val dist = sqrt(dx * dx + dy * dy)
                    
                    // Keep track of minimum distance
                    if (dist < minDist) {
                        minDist = dist
                    }
                }
            }
            
            // Return the minimum distance mapped to 0.0-1.0 range
            // Clamping to avoid extreme values
            return minDist.coerceIn(0.0f, 1.0f)
        }
        
        /**
         * Generate ridged noise, good for mountain ridges and terrain features
         */
        fun ridgedNoise(
            x: Float,
            y: Float,
            scale: Float = 0.01f,
            octaves: Int = 4,
            persistence: Float = 0.5f,
            lacunarity: Float = 2.0f
        ): Float {
            var result = 0.0f
            var amplitude = 1.0f
            var frequency = scale
            var max = 0.0f
            
            for (i in 0 until octaves) {
                // Create ridged effect by subtracting from 1.0
                val noise = SimplexNoise.noise(x * frequency, y * frequency)
                val ridged = 1.0f - abs(noise)
                
                result += amplitude * ridged
                max += amplitude
                amplitude *= persistence
                frequency *= lacunarity
            }
            
            // Normalize to 0-1
            return result / max
        }
        
        /**
         * Generate biome blend data (for blending between biomes)
         * Returns a pair of (biomeIndex, blendFactor) where blendFactor is how much to blend
         * with the next biome over.
         */
        fun biomeBlendData(x: Float, z: Float, scale: Float = 0.001f): Pair<Int, Float> {
            // Get base noise value
            val noise = octavedSimplexNoise(x, z, 2, scale) 
            
            // Scale to 0.0-1.0
            val scaledNoise = (noise + 1.0f) * 0.5f
            
            // Scale to number of biomes (assuming we have 16 biomes)
            val biomeValue = scaledNoise * 16.0f
            
            // Get the integer part (biome index)
            val biomeIndex = biomeValue.toInt() % 16
            
            // Get the fractional part (blend factor)
            val blendFactor = biomeValue - biomeIndex
            
            return Pair(biomeIndex, blendFactor)
        }
        
        /**
         * Generate continent noise (large-scale terrain features)
         * This creates the base terrain shape across large distances
         */
        fun continentNoise(x: Float, z: Float, scale: Float = 0.0001f): Float {
            // Multi-layer approach for more natural terrain
            // Base continent layer
            val baseNoise = octavedSimplexNoise(x, z, 3, scale, 0.5f, 2.0f)
            
            // Add some larger-scale variation for major landmasses
            val largeScale = scale * 0.3f
            val largeNoise = SimplexNoise.noise(x * largeScale, z * largeScale) * 0.3f
            
            // Combine for final terrain shape
            return (baseNoise * 0.7f + largeNoise).coerceIn(-1.0f, 1.0f)
        }
        
        /**
         * Generate Minecraft-like 3D cave noise
         */
        fun caveNoise(x: Float, y: Float, z: Float, scale: Float = 0.03f): Float {
            // Combine multiple frequencies of 3D noise
            val noise1 = simplexNoise3D(x, y, z, scale)
            val noise2 = simplexNoise3D(x, y, z, scale * 2.0f) * 0.5f
            
            return (noise1 + noise2) * 0.75f
        }
        
        /**
         * Generate domain warping noise (for more natural-looking features)
         * Uses noise to distort the coordinates for another noise function
         */
        fun domainWarpNoise(x: Float, y: Float, warpStrength: Float = 10.0f, scale: Float = 0.01f): Float {
            // Generate offset noise
            val warpX = SimplexNoise.noise(x * scale * 0.5f, y * scale * 0.5f) * warpStrength
            val warpY = SimplexNoise.noise(x * scale * 0.5f + 100f, y * scale * 0.5f + 100f) * warpStrength
            
            // Apply the warp to the coordinates
            val warpedX = x + warpX
            val warpedY = y + warpY
            
            // Generate noise using the warped coordinates
            return SimplexNoise.noise(warpedX * scale, warpedY * scale)
        }
        
        /**
         * Utility method to hash two integers into one for deterministic "random" generation
         */
        private fun hashPoint(x: Int, y: Int): Int {
            var hash = x * 73856093
            hash = hash xor (y * 19349663)
            return hash
        }
    }
}