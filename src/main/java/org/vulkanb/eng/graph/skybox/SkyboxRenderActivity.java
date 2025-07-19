package org.vulkanb.eng.graph.skybox;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;

public class SkyboxRenderActivity {

    private static final String SKYBOX_FRAGMENT_SHADER_FILE_GLSL = "src/main/resources/shaders/skybox_fragment.glsl";
    private static final String SKYBOX_FRAGMENT_SHADER_FILE_SPV = SKYBOX_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String SKYBOX_VERTEX_SHADER_FILE_GLSL = "src/main/resources/shaders/skybox_vertex.glsl";
    private static final String SKYBOX_VERTEX_SHADER_FILE_SPV = SKYBOX_VERTEX_SHADER_FILE_GLSL + ".spv";

    // Skybox cube vertices (unit cube centered at origin)
    private static final float[] SKYBOX_VERTICES = {
        // Front face
        -1.0f, -1.0f,  1.0f,
         1.0f, -1.0f,  1.0f,
         1.0f,  1.0f,  1.0f,
        -1.0f,  1.0f,  1.0f,
        // Back face
        -1.0f, -1.0f, -1.0f,
        -1.0f,  1.0f, -1.0f,
         1.0f,  1.0f, -1.0f,
         1.0f, -1.0f, -1.0f,
        // Top face
        -1.0f,  1.0f, -1.0f,
        -1.0f,  1.0f,  1.0f,
         1.0f,  1.0f,  1.0f,
         1.0f,  1.0f, -1.0f,
        // Bottom face
        -1.0f, -1.0f, -1.0f,
         1.0f, -1.0f, -1.0f,
         1.0f, -1.0f,  1.0f,
        -1.0f, -1.0f,  1.0f,
        // Right face
         1.0f, -1.0f, -1.0f,
         1.0f,  1.0f, -1.0f,
         1.0f,  1.0f,  1.0f,
         1.0f, -1.0f,  1.0f,
        // Left face
        -1.0f, -1.0f, -1.0f,
        -1.0f, -1.0f,  1.0f,
        -1.0f,  1.0f,  1.0f,
        -1.0f,  1.0f, -1.0f,
    };

    // Skybox cube indices
    private static final int[] SKYBOX_INDICES = {
        0,  1,  2,      0,  2,  3,    // front
        4,  5,  6,      4,  6,  7,    // back
        8,  9,  10,     8,  10, 11,   // top
        12, 13, 14,     12, 14, 15,   // bottom
        16, 17, 18,     16, 18, 19,   // right
        20, 21, 22,     20, 22, 23    // left
    };

    private final Device device;
    private final Scene scene;
    private final long renderPass;
    private final Queue.GraphicsQueue graphicsQueue;

    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] skyboxDescriptorSetLayouts;
    private Pipeline pipeline;
    private DescriptorSet.UniformDescriptorSet projMatrixDescriptorSet;
    private VulkanBuffer projMatrixUniform;
    private ShaderProgram shaderProgram;
    private VulkanBuffer skyboxIndexBuffer;
    private VulkanBuffer skyboxVertexBuffer;
    private Texture skyboxTexture;
    private TextureDescriptorSet skyboxTextureDescriptorSet;
    private DescriptorSetLayout.SamplerDescriptorSetLayout skyboxTextureDescriptorSetLayout;
    private TextureSampler skyboxTextureSampler;
    private SwapChain swapChain;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;
    private VulkanBuffer[] viewMatricesBuffer;
    private DescriptorSet.UniformDescriptorSet[] viewMatricesDescriptorSets;

    public SkyboxRenderActivity(SwapChain swapChain, PipelineCache pipelineCache, Scene scene, long renderPass, Queue.GraphicsQueue graphicsQueue) {
        this.swapChain = swapChain;
        this.scene = scene;
        this.renderPass = renderPass;
        this.graphicsQueue = graphicsQueue;
        device = swapChain.getDevice();

        int numImages = swapChain.getNumImages();
        createShaders();
        createSkyboxBuffers();
        createDescriptorPool();
        createDescriptorSets(numImages);
        createPipeline(pipelineCache);
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
    }

    public void cleanup() {
        pipeline.cleanup();
        skyboxIndexBuffer.cleanup();
        skyboxVertexBuffer.cleanup();
        skyboxTexture.cleanup();
        skyboxTextureSampler.cleanup();
        skyboxTextureDescriptorSetLayout.cleanup();
        for (VulkanBuffer buffer : viewMatricesBuffer) {
            buffer.cleanup();
        }
        projMatrixUniform.cleanup();
        uniformDescriptorSetLayout.cleanup();
        descriptorPool.cleanup();
        shaderProgram.cleanup();
    }

    private void createDescriptorPool() {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(swapChain.getNumImages() + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages) {
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT);
        skyboxTextureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(device, 1, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        skyboxDescriptorSetLayouts = new DescriptorSetLayout[]{
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                skyboxTextureDescriptorSetLayout,
        };

        // Create texture and sampler
        skyboxTexture = new Texture(device, "src/main/resources/skybox/day.png", VK_FORMAT_R8G8B8A8_SRGB);
        
        // Transition the texture to the correct layout for shader use
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CommandPool commandPool = new CommandPool(device, graphicsQueue.getQueueFamilyIndex());
            CommandBuffer cmd = new CommandBuffer(commandPool, true, false);
            cmd.beginRecording();
            skyboxTexture.recordTextureTransition(cmd);
            cmd.endRecording();
            
            Fence fence = new Fence(device, true);
            fence.reset();
            graphicsQueue.submit(stack.pointers(cmd.getVkCommandBuffer()), null, null, null, fence);
            fence.fenceWait();
            fence.cleanup();
            cmd.cleanup();
            commandPool.cleanup();
        }
        
        skyboxTextureSampler = new TextureSampler(device, 1, false);
        skyboxTextureDescriptorSet = new TextureDescriptorSet(descriptorPool, skyboxTextureDescriptorSetLayout, 
                List.of(skyboxTexture), skyboxTextureSampler, 0);

        projMatrixUniform = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0);

        viewMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        viewMatricesBuffer = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            viewMatricesBuffer[i] = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            viewMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    viewMatricesBuffer[i], 0);
        }
    }

    private void createPipeline(PipelineCache pipelineCache) {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                renderPass, shaderProgram, 3,
                true, false, 0, // Enable depth testing, no depth writing for skybox
                new SkyboxVertexBufferStructure(), skyboxDescriptorSetLayouts);
        pipeline = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanup();
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(SKYBOX_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(SKYBOX_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SKYBOX_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, SKYBOX_FRAGMENT_SHADER_FILE_SPV),
                });
    }

    private void createSkyboxBuffers() {
        // Create vertex buffer
        skyboxVertexBuffer = new VulkanBuffer(device, SKYBOX_VERTICES.length * Float.BYTES,
                VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        long mappedMemory = skyboxVertexBuffer.map();
        FloatBuffer vertexBuffer = MemoryUtil.memFloatBuffer(mappedMemory, SKYBOX_VERTICES.length);
        vertexBuffer.put(SKYBOX_VERTICES);
        skyboxVertexBuffer.unMap();

        // Create index buffer
        skyboxIndexBuffer = new VulkanBuffer(device, SKYBOX_INDICES.length * Integer.BYTES,
                VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        mappedMemory = skyboxIndexBuffer.map();
        IntBuffer indexBuffer = MemoryUtil.memIntBuffer(mappedMemory, SKYBOX_INDICES.length);
        indexBuffer.put(SKYBOX_INDICES);
        skyboxIndexBuffer.unMap();
    }

    public void recordCommandBuffer(CommandBuffer commandBuffer, int idx) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            LongBuffer descriptorSets = stack.mallocLong(3)
                    .put(0, projMatrixDescriptorSet.getVkDescriptorSet())
                    .put(1, viewMatricesDescriptorSets[idx].getVkDescriptorSet())
                    .put(2, skyboxTextureDescriptorSet.getVkDescriptorSet());

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            LongBuffer vertexBuffer = stack.mallocLong(1);
            LongBuffer offsets = stack.mallocLong(1).put(0, 0L);
            vertexBuffer.put(0, skyboxVertexBuffer.getBuffer());

            vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, skyboxIndexBuffer.getBuffer(), 0, VK_INDEX_TYPE_UINT32);

            vkCmdDrawIndexed(cmdHandle, SKYBOX_INDICES.length, 1, 0, 0, 0);
        }
    }

    public void render() {
        int idx = swapChain.getCurrentFrame();
        VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.getCamera().getViewMatrix());
    }

    public void resize(SwapChain swapChain) {
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
        this.swapChain = swapChain;
    }
}