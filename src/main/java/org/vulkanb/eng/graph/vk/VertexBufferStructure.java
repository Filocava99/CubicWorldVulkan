package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK11.*;

public class VertexBufferStructure extends VertexInputStateInfo {

    public static final int TEXT_COORD_COMPONENTS = 2;
    private static final int NORMAL_COMPONENTS = 3;
    private static final int NUMBER_OF_ATTRIBUTES = 3; // Position, Normal, TextureCoords
    private static final int POSITION_COMPONENTS = 3;
    // Position (3 bytes) + Normal (3 floats) + Texture Coordinates (2 floats)
    public static final int SIZE_IN_BYTES = (POSITION_COMPONENTS * 1) + (NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH) + (TEXT_COORD_COMPONENTS * GraphConstants.FLOAT_LENGTH);

    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public VertexBufferStructure() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R8G8B8_SNORM)
                .offset(0);

        // Normal
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(POSITION_COMPONENTS * 1); // Offset after 3 bytes

        // Texture coordinates
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i) // This will be location 2
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(POSITION_COMPONENTS * 1 + NORMAL_COMPONENTS * GraphConstants.FLOAT_LENGTH);

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
