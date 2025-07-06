package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK11.*;

/**
 * Optimized vertex buffer structure for voxel rendering.
 * Uses compact data types to reduce memory usage by ~80%:
 * - Position: 3 shorts (6 bytes) instead of 3 floats (12 bytes)
 * - Normal Index: 1 byte instead of 3 floats (12 bytes)
 * - Texture Coords: 2 shorts (4 bytes) instead of 2 floats (8 bytes)
 * - Tangent/BiTangent: Computed in shader instead of stored (0 bytes vs 24 bytes)
 * 
 * Total: 11 bytes per vertex (was 56 bytes) = 80% memory reduction
 */
public class OptimizedVertexBufferStructure extends VertexInputStateInfo {

    private static final int POSITION_COMPONENTS = 3;      // 3 shorts (6 bytes)
    private static final int NORMAL_INDEX_COMPONENTS = 1;  // 1 byte
    private static final int TEXT_COORD_COMPONENTS = 2;    // 2 shorts (4 bytes)
    private static final int NUMBER_OF_ATTRIBUTES = 3;
    
    // Calculate size: 3 shorts + 1 byte + 2 shorts = 6 + 1 + 4 = 11 bytes
    public static final int SIZE_IN_BYTES = POSITION_COMPONENTS * GraphConstants.SHORT_LENGTH + 
                                           NORMAL_INDEX_COMPONENTS + 
                                           TEXT_COORD_COMPONENTS * GraphConstants.SHORT_LENGTH;

    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public OptimizedVertexBufferStructure() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        
        // Position (3 signed shorts: x, y, z)
        // Range: -32,768 to 32,767 (sufficient for most voxel world coordinates)
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R16G16B16_SINT)
                .offset(0);

        // Normal Index (1 unsigned byte: 0-5 for cube face directions)
        // 0=+X, 1=-X, 2=+Y, 3=-Y, 4=+Z, 5=-Z
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R8_UINT)
                .offset(POSITION_COMPONENTS * GraphConstants.SHORT_LENGTH);

        // Texture coordinates (2 unsigned shorts: normalized 0-65535)
        // Shader will divide by 65535.0 to get 0-1 range
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R16G16_UNORM)
                .offset(POSITION_COMPONENTS * GraphConstants.SHORT_LENGTH + NORMAL_INDEX_COMPONENTS);

        viBindings.get(0)
                .binding(0)
                .stride(SIZE_IN_BYTES)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(viBindings)
                .pVertexAttributeDescriptions(viAttrs);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        viBindings.free();
        viAttrs.free();
    }
}