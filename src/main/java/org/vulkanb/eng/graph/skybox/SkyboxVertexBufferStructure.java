package org.vulkanb.eng.graph.skybox;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.GraphConstants;
import org.vulkanb.eng.graph.vk.VertexInputStateInfo;

import static org.lwjgl.vulkan.VK11.*;

public class SkyboxVertexBufferStructure extends VertexInputStateInfo {

    private static final int NUMBER_OF_ATTRIBUTES = 1;
    private static final int POSITION_COMPONENTS = 3;
    public static final int SIZE_IN_BYTES = POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH;

    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public SkyboxVertexBufferStructure() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        // Position only for skybox
        viAttrs.get(0)
                .binding(0)
                .location(0)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);

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