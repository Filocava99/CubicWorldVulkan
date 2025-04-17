package it.filippocavallari.cubicworld.integration

import org.vulkanb.eng.graph.TextureCache
import org.lwjgl.vulkan.VkDevice
import java.nio.LongBuffer
import java.nio.ByteBuffer
import java.util.HashMap

/**
 * Extension to the Vulkan engine's TextureCache to support texture atlases.
 * This class enables using our texture atlas system with the Vulkan engine.
 */
class VulkanTextureCache {
    // Map of registered atlas descriptors
    private val atlasDescriptors = HashMap<String, Long>()
    
    // Reference to the Vulkan engine's texture cache
    private lateinit var textureCache: TextureCache
    
    /**
     * Initialize with the Vulkan engine's texture cache
     * 
     * @param textureCache The engine's texture cache instance
     */
    fun initialize(textureCache: TextureCache) {
        this.textureCache = textureCache
    }
    
    /**
     * Register a texture atlas descriptor set
     * 
     * @param atlasName The name of the atlas (e.g., "diffuse", "normal", "specular")
     * @param descriptorSet The Vulkan descriptor set handle
     */
    fun registerAtlasDescriptor(atlasName: String, descriptorSet: Long) {
        atlasDescriptors[atlasName] = descriptorSet
    }
    
    /**
     * Register multiple atlas descriptors
     * 
     * @param diffuseDescriptor Descriptor for diffuse atlas
     * @param normalDescriptor Descriptor for normal atlas
     * @param specularDescriptor Descriptor for specular atlas
     */
    fun registerAtlasDescriptors(
        diffuseDescriptor: Long,
        normalDescriptor: Long,
        specularDescriptor: Long
    ) {
        atlasDescriptors["diffuse"] = diffuseDescriptor
        atlasDescriptors["normal"] = normalDescriptor
        atlasDescriptors["specular"] = specularDescriptor
    }
    
    /**
     * Get the descriptor set for an atlas
     * 
     * @param atlasName The name of the atlas
     * @return The descriptor set handle, or 0 if not found
     */
    fun getAtlasDescriptor(atlasName: String): Long {
        return atlasDescriptors[atlasName] ?: 0L
    }
    
    /**
     * Get the diffuse atlas descriptor
     * 
     * @return The descriptor set handle, or 0 if not registered
     */
    fun getDiffuseAtlasDescriptor(): Long {
        return getAtlasDescriptor("diffuse")
    }
    
    /**
     * Get the normal atlas descriptor
     * 
     * @return The descriptor set handle, or 0 if not registered
     */
    fun getNormalAtlasDescriptor(): Long {
        return getAtlasDescriptor("normal")
    }
    
    /**
     * Get the specular atlas descriptor
     * 
     * @return The descriptor set handle, or 0 if not registered
     */
    fun getSpecularAtlasDescriptor(): Long {
        return getAtlasDescriptor("specular")
    }
    
    /**
     * Check if a texture atlas has been registered
     * 
     * @param atlasName The name of the atlas
     * @return true if the atlas is registered
     */
    fun hasAtlas(atlasName: String): Boolean {
        return atlasDescriptors.containsKey(atlasName)
    }
    
    /**
     * Cleanup resources
     * 
     * @param device The Vulkan device
     */
    fun cleanup(device: VkDevice) {
        // The actual descriptor sets are managed by the engine's texture cache
        // or the TextureAtlasLoader, so we just clear our references
        atlasDescriptors.clear()
    }
}