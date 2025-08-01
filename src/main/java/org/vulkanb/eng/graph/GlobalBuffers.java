package org.vulkanb.eng.graph;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;
import java.util.function.Predicate;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.GraphConstants.*;

public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndexedIndirectCommand.SIZEOF;
    // Handle std430 alignment
    private static final int MATERIAL_PADDING = FLOAT_LENGTH * 3;
    private static final int MATERIAL_SIZE = VEC4_SIZE + INT_LENGTH * 3 + FLOAT_LENGTH * 2 + MATERIAL_PADDING;
    private final VulkanBuffer animJointMatricesBuffer;
    private final VulkanBuffer animWeightsBuffer;
    private final VulkanBuffer indicesBuffer;
    private final VulkanBuffer materialsBuffer;
    private final VulkanBuffer verticesBuffer;
    // Track current offsets to properly append new chunks
    private int currentVerticesOffset = 0;
    private int currentIndicesOffset = 0;
    private VulkanBuffer animIndirectBuffer;
    private VulkanBuffer[] animInstanceDataBuffers;
    private VulkanBuffer animVerticesBuffer;
    private VulkanBuffer indirectBuffer;
    private VulkanBuffer[] instanceDataBuffers;
    private int numAnimIndirectCommands;
    private int numIndirectCommands;
    private List<VulkanAnimEntity> vulkanAnimEntityList;

    public GlobalBuffers(Device device) {
        Logger.debug("Creating global buffers");
        EngineProperties engProps = EngineProperties.getInstance();
        verticesBuffer = new VulkanBuffer(device, engProps.getMaxVerticesBuffer(), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        indicesBuffer = new VulkanBuffer(device, engProps.getMaxIndicesBuffer(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        int maxMaterials = engProps.getMaxMaterials();
        materialsBuffer = new VulkanBuffer(device, (long) maxMaterials * MATERIAL_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animJointMatricesBuffer = new VulkanBuffer(device, engProps.getMaxJointMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animWeightsBuffer = new VulkanBuffer(device, engProps.getMaxAnimWeightsBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        numIndirectCommands = 0;
    }

    public void cleanup() {
        Logger.debug("Destroying global buffers");
        verticesBuffer.cleanup();
        indicesBuffer.cleanup();
        if (indirectBuffer != null) {
            indirectBuffer.cleanup();
        }
        if (animVerticesBuffer != null) {
            animVerticesBuffer.cleanup();
        }
        if (animIndirectBuffer != null) {
            animIndirectBuffer.cleanup();
        }
        materialsBuffer.cleanup();
        animJointMatricesBuffer.cleanup();
        animWeightsBuffer.cleanup();
        if (instanceDataBuffers != null) {
            Arrays.asList(instanceDataBuffers).forEach(VulkanBuffer::cleanup);
        }
        if (animInstanceDataBuffers != null) {
            Arrays.asList(animInstanceDataBuffers).forEach(VulkanBuffer::cleanup);
        }
        
        // Reset current offsets
        currentVerticesOffset = 0;
        currentIndicesOffset = 0;
        Logger.debug("Reset vertex and index buffer offsets");
    }

    public VulkanBuffer getAnimIndirectBuffer() {
        return animIndirectBuffer;
    }

    public VulkanBuffer[] getAnimInstanceDataBuffers() {
        return animInstanceDataBuffers;
    }

    public VulkanBuffer getAnimJointMatricesBuffer() {
        return animJointMatricesBuffer;
    }

    public VulkanBuffer getAnimVerticesBuffer() {
        return animVerticesBuffer;
    }

    public VulkanBuffer getAnimWeightsBuffer() {
        return animWeightsBuffer;
    }

    public VulkanBuffer getIndicesBuffer() {
        return indicesBuffer;
    }

    public VulkanBuffer getIndirectBuffer() {
        return indirectBuffer;
    }

    public VulkanBuffer[] getInstanceDataBuffers() {
        return instanceDataBuffers;
    }

    public VulkanBuffer getMaterialsBuffer() {
        return materialsBuffer;
    }

    public int getNumAnimIndirectCommands() {
        return numAnimIndirectCommands;
    }

    public int getNumIndirectCommands() {
        return numIndirectCommands;
    }

    public VulkanBuffer getVerticesBuffer() {
        return verticesBuffer;
    }

    public List<VulkanAnimEntity> getVulkanAnimEntityList() {
        return vulkanAnimEntityList;
    }

    private void loadAnimEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool,
                                  Queue queue, int numSwapChainImages) {
        vulkanAnimEntityList = new ArrayList<>();
        numAnimIndirectCommands = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (animVerticesBuffer != null) {
                animVerticesBuffer.cleanup();
            }
            Device device = commandPool.getDevice();
            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

            int bufferOffset = 0;
            int firstInstance = 0;
            List<VkDrawIndexedIndirectCommand> indexedIndirectCommandList = new ArrayList<>();
            for (VulkanModel vulkanModel : vulkanModelList) {
                List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities == null || entities.isEmpty()) {
                    continue;
                }
                for (Entity entity : entities) {
                    if (!entity.hasAnimation()) {
                        continue;
                    }
                    VulkanAnimEntity vulkanAnimEntity = new VulkanAnimEntity(entity, vulkanModel);
                    vulkanAnimEntityList.add(vulkanAnimEntity);
                    List<VulkanAnimEntity.VulkanAnimMesh> vulkanAnimMeshList = vulkanAnimEntity.getVulkanAnimMeshList();
                    for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                        VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                        indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                        indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / INT_LENGTH);
                        indexedIndirectCommand.instanceCount(1);
                        indexedIndirectCommand.vertexOffset(bufferOffset / VertexBufferStructure.SIZE_IN_BYTES);
                        indexedIndirectCommand.firstInstance(firstInstance);
                        indexedIndirectCommandList.add(indexedIndirectCommand);

                        vulkanAnimMeshList.add(new VulkanAnimEntity.VulkanAnimMesh(bufferOffset, vulkanMesh));
                        bufferOffset += vulkanMesh.verticesSize();
                        firstInstance++;
                    }
                }
            }
            if (bufferOffset == 0) {
                return;
            }
            animVerticesBuffer = new VulkanBuffer(device, bufferOffset, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

            numAnimIndirectCommands = indexedIndirectCommandList.size();
            if (numAnimIndirectCommands > 0) {
                cmd.beginRecording();

                StgByteBuffer indirectStgBuffer = new StgByteBuffer(device, (long) IND_COMMAND_STRIDE * numAnimIndirectCommands);
                if (animIndirectBuffer != null) {
                    animIndirectBuffer.cleanup();
                }
                animIndirectBuffer = new VulkanBuffer(device, indirectStgBuffer.stgVulkanBuffer.getRequestedSize(),
                        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
                VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

                indexedIndirectCommandList.forEach(indCommandBuffer::put);

                if (animInstanceDataBuffers != null) {
                    Arrays.asList(animInstanceDataBuffers).forEach(VulkanBuffer::cleanup);
                }
                
                // Add extra safety margin to buffer size (2x)
                long animInstanceBufferSize = (long) numAnimIndirectCommands * (MAT4X4_SIZE + INT_LENGTH) * 2;
                System.out.println("Creating animInstanceDataBuffers with size: " + animInstanceBufferSize);
                
                animInstanceDataBuffers = new VulkanBuffer[numSwapChainImages];
                for (int i = 0; i < numSwapChainImages; i++) {
                    animInstanceDataBuffers[i] = new VulkanBuffer(device,
                            animInstanceBufferSize,
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
                }

                indirectStgBuffer.recordTransferCommand(cmd, animIndirectBuffer);

                cmd.endRecording();
                cmd.submitAndWait(device, queue);
                cmd.cleanup();
                indirectStgBuffer.cleanup();
            }
        }
    }

    private void loadAnimationData(ModelData modelData, VulkanModel vulkanModel, StgIntBuffer animJointMatricesStgBuffer) {
        List<ModelData.Animation> animationsList = modelData.getAnimationsList();
        if (!modelData.hasAnimations()) {
            return;
        }
        IntBuffer dataBuffer = animJointMatricesStgBuffer.getDataBuffer();
        for (ModelData.Animation animation : animationsList) {
            VulkanModel.VulkanAnimationData vulkanAnimationData = new VulkanModel.VulkanAnimationData();
            vulkanModel.addVulkanAnimationData(vulkanAnimationData);
            List<ModelData.AnimatedFrame> frameList = animation.frames();
            for (ModelData.AnimatedFrame frame : frameList) {
                vulkanAnimationData.addVulkanAnimationFrame(new VulkanModel.VulkanAnimationFrame(dataBuffer.position() * INT_LENGTH));
                Matrix4f[] matrices = frame.jointMatrices();
                for (Matrix4f matrix : matrices) {
                    loadMatIntoIntBuffer(matrix, dataBuffer);
                }
            }
        }
    }

    public void loadEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool,
                             Queue queue, int numSwapChainImages) {
        loadStaticEntities(vulkanModelList, scene, commandPool, queue, numSwapChainImages);
        loadAnimEntities(vulkanModelList, scene, commandPool, queue, numSwapChainImages);
    }

    public void loadInstanceData(Scene scene, List<VulkanModel> vulkanModels, int currentSwapChainIdx) {
        Predicate<VulkanModel> excludeAnimatedEntitiesPredicate = VulkanModel::hasAnimations;
        // Add null check to prevent NullPointerException
        if (instanceDataBuffers != null && currentSwapChainIdx < instanceDataBuffers.length) {
            loadInstanceData(scene, vulkanModels, instanceDataBuffers[currentSwapChainIdx], excludeAnimatedEntitiesPredicate);
        }
        
        Predicate<VulkanModel> excludedStaticEntitiesPredicate = v -> !v.hasAnimations();
        if (animInstanceDataBuffers != null && currentSwapChainIdx < animInstanceDataBuffers.length) {
            loadInstanceData(scene, vulkanModels, animInstanceDataBuffers[currentSwapChainIdx], excludedStaticEntitiesPredicate);
        }
    }

    private void loadInstanceData(Scene scene, List<VulkanModel> vulkanModels, VulkanBuffer instanceBuffer,
                                  Predicate<VulkanModel> excludedEntitiesPredicate) {
        if (instanceBuffer == null) {
            return;
        }
        long mappedMemory = instanceBuffer.map();
        ByteBuffer dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) instanceBuffer.getRequestedSize());
        
        // Debug the buffer capacity
        int bufferCapacity = dataBuffer.capacity();
//        System.out.println("Buffer capacity: " + bufferCapacity);
        
        int pos = 0;
        for (VulkanModel vulkanModel : vulkanModels) {
            List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
            if (entities == null || entities.isEmpty() || excludedEntitiesPredicate.test(vulkanModel)) {
                continue;
            }
            
            int entitiesCount = entities.size();
            int meshCount = vulkanModel.getVulkanMeshList().size();
            int requiredCapacity = pos + (entitiesCount * meshCount * (MAT4X4_SIZE + INT_LENGTH));
            
            // Check if we have enough buffer capacity before processing
            if (requiredCapacity > bufferCapacity) {
                System.out.println("Warning: Not enough buffer capacity. Required: " + requiredCapacity + ", Available: " + bufferCapacity);
                break;
            }
            
            for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                for (Entity entity : entities) {
                    // Check for matrix data capacity
                    if (pos + MAT4X4_SIZE > bufferCapacity) {
                        System.out.println("Warning: Buffer capacity exceeded at pos " + pos);
                        break;
                    }
                    
                    entity.getModelMatrix().get(pos, dataBuffer);
                    pos += MAT4X4_SIZE;
                    
                    // Check for material index capacity
                    if (pos + INT_LENGTH > bufferCapacity) {
                        System.out.println("Warning: Buffer capacity exceeded at pos " + pos);
                        break;
                    }
                    
                    dataBuffer.putInt(pos, vulkanMesh.globalMaterialIdx());
                    pos += INT_LENGTH;
                }
            }
        }
        
        instanceBuffer.unMap();
    }

    private void loadMatIntoIntBuffer(Matrix4f m, IntBuffer buffer) {
        buffer.put(Float.floatToRawIntBits(m.m00()));
        buffer.put(Float.floatToRawIntBits(m.m01()));
        buffer.put(Float.floatToRawIntBits(m.m02()));
        buffer.put(Float.floatToRawIntBits(m.m03()));
        buffer.put(Float.floatToRawIntBits(m.m10()));
        buffer.put(Float.floatToRawIntBits(m.m11()));
        buffer.put(Float.floatToRawIntBits(m.m12()));
        buffer.put(Float.floatToRawIntBits(m.m13()));
        buffer.put(Float.floatToRawIntBits(m.m20()));
        buffer.put(Float.floatToRawIntBits(m.m21()));
        buffer.put(Float.floatToRawIntBits(m.m22()));
        buffer.put(Float.floatToRawIntBits(m.m23()));
        buffer.put(Float.floatToRawIntBits(m.m30()));
        buffer.put(Float.floatToRawIntBits(m.m31()));
        buffer.put(Float.floatToRawIntBits(m.m32()));
        buffer.put(Float.floatToRawIntBits(m.m33()));
    }

    private List<VulkanModel.VulkanMaterial> loadMaterials(Device device, TextureCache textureCache, StgIntBuffer
            materialsStgBuffer, List<ModelData.Material> materialList, List<Texture> textureList) {
        List<VulkanModel.VulkanMaterial> vulkanMaterialList = new ArrayList<>();
        for (ModelData.Material material : materialList) {
            IntBuffer dataBuffer = materialsStgBuffer.getDataBuffer();

            Texture texture = textureCache.createTexture(device, material.texturePath(), VK_FORMAT_R8G8B8A8_SRGB);
            if (texture != null) {
                textureList.add(texture);
            }
            boolean hasTexture = material.texturePath() != null && !material.texturePath().trim().isEmpty();
            int textureIdx = hasTexture ? textureCache.getPosition(material.texturePath()) : -1;

            texture = textureCache.createTexture(device, material.normalMapPath(), VK_FORMAT_R8G8B8A8_UNORM);
            if (texture != null) {
                textureList.add(texture);
            }
            boolean hasNormalMap = material.normalMapPath() != null && !material.normalMapPath().trim().isEmpty();
            int normalMapIdx = hasNormalMap ? textureCache.getPosition(material.normalMapPath()) : -1;

            texture = textureCache.createTexture(device, material.metalRoughMap(), VK_FORMAT_R8G8B8A8_UNORM);
            if (texture != null) {
                textureList.add(texture);
            }
            boolean hasRoughMap = material.metalRoughMap() != null && !material.metalRoughMap().trim().isEmpty();
            int metalRoughMapIdx = hasRoughMap ? textureCache.getPosition(material.metalRoughMap()) : -1;

            vulkanMaterialList.add(new VulkanModel.VulkanMaterial(dataBuffer.position() * INT_LENGTH / MATERIAL_SIZE));
            dataBuffer.put(Float.floatToRawIntBits(material.diffuseColor().x));
            dataBuffer.put(Float.floatToRawIntBits(material.diffuseColor().y));
            dataBuffer.put(Float.floatToRawIntBits(material.diffuseColor().z));
            dataBuffer.put(Float.floatToRawIntBits(material.diffuseColor().w));
            dataBuffer.put(textureIdx);
            dataBuffer.put(normalMapIdx);
            dataBuffer.put(metalRoughMapIdx);
            dataBuffer.put(Float.floatToRawIntBits(material.roughnessFactor()));
            dataBuffer.put(Float.floatToRawIntBits(material.metallicFactor()));
            // Padding due to std430 alignment
            dataBuffer.put(Float.floatToRawIntBits(0.0f));
            dataBuffer.put(Float.floatToRawIntBits(0.0f));
            dataBuffer.put(Float.floatToRawIntBits(0.0f));
        }

        return vulkanMaterialList;
    }

    private void loadMeshes(StgIntBuffer verticesStgBuffer, StgIntBuffer indicesStgBuffer, StgIntBuffer animWeightsStgBuffer,
                            ModelData modelData, VulkanModel vulkanModel, List<VulkanModel.VulkanMaterial> vulkanMaterialList) {
        IntBuffer verticesData = verticesStgBuffer.getDataBuffer();
        IntBuffer indicesData = indicesStgBuffer.getDataBuffer();
        List<ModelData.MeshData> meshDataList = modelData.getMeshDataList();
        int meshCount = 0;
        
        int verticesCapacity = verticesData.capacity();
        int indicesCapacity = indicesData.capacity();
        System.out.println("Vertices buffer capacity: " + verticesCapacity);
        System.out.println("Indices buffer capacity: " + indicesCapacity);
        
        for (ModelData.MeshData meshData : meshDataList) {
            float[] positions = meshData.positions();
            float[] normals = meshData.normals();
            float[] tangents = meshData.tangents();
            float[] biTangents = meshData.biTangents();
            float[] textCoords = meshData.textCoords();
            if (textCoords == null || textCoords.length == 0) {
                textCoords = new float[(positions.length / 3) * 2];
            }
            int[] indices = meshData.indices();

            int numElements = positions.length + normals.length + tangents.length + biTangents.length + textCoords.length;
            int verticesSize = numElements * FLOAT_LENGTH;
            
            // Check if we have enough space in the buffer
            if (verticesData.position() + numElements > verticesCapacity) {
                System.out.println("Warning: Not enough space in vertices buffer. Required: " + (verticesData.position() + numElements) + ", Capacity: " + verticesCapacity);
                break;
            }
            
            if (indicesData.position() + indices.length > indicesCapacity) {
                System.out.println("Warning: Not enough space in indices buffer. Required: " + (indicesData.position() + indices.length) + ", Capacity: " + indicesCapacity);
                break;
            }

            int localMaterialIdx = meshData.materialIdx();
            int globalMaterialIdx = 0;
            if (localMaterialIdx >= 0 && localMaterialIdx < vulkanMaterialList.size()) {
                globalMaterialIdx = vulkanMaterialList.get(localMaterialIdx).globalMaterialIdx();
            }
            vulkanModel.addVulkanMesh(new VulkanModel.VulkanMesh(verticesSize, indices.length,
                    currentVerticesOffset + verticesData.position() * INT_LENGTH, 
                    currentIndicesOffset + indicesData.position() * INT_LENGTH,
                    globalMaterialIdx, animWeightsStgBuffer.getDataBuffer().position() * INT_LENGTH));

            int rows = positions.length / 3;
            for (int row = 0; row < rows; row++) {
                // Check buffer capacity again for safety
                if (verticesData.position() + 14 > verticesCapacity) {
                    System.out.println("Warning: Vertices buffer capacity exceeded during row processing");
                    break;
                }
                
                int startPos = row * 3;
                int startTextCoord = row * 2;
                verticesData.put(Float.floatToRawIntBits(positions[startPos]));
                verticesData.put(Float.floatToRawIntBits(positions[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(positions[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(normals[startPos]));
                verticesData.put(Float.floatToRawIntBits(normals[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(normals[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(tangents[startPos]));
                verticesData.put(Float.floatToRawIntBits(tangents[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(tangents[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(biTangents[startPos]));
                verticesData.put(Float.floatToRawIntBits(biTangents[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(biTangents[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(textCoords[startTextCoord]));
                verticesData.put(Float.floatToRawIntBits(textCoords[startTextCoord + 1]));
            }

            // Check indices buffer capacity
            if (indicesData.position() + indices.length > indicesCapacity) {
                System.out.println("Warning: Indices buffer capacity exceeded");
                break;
            }
            
            // Instead of stream, use a safer approach
            for (int index : indices) {
                if (indicesData.position() < indicesCapacity) {
                    indicesData.put(index);
                } else {
                    System.out.println("Warning: Indices buffer capacity exceeded during indices processing");
                    break;
                }
            }

            loadWeightsBuffer(modelData, animWeightsStgBuffer, meshCount);
            meshCount++;
        }
    }

    public List<VulkanModel> loadModels(List<ModelData> modelDataList, TextureCache textureCache, CommandPool
            commandPool, Queue queue) {
        List<VulkanModel> vulkanModelList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        Device device = commandPool.getDevice();
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

        StgIntBuffer verticesStgBuffer = new StgIntBuffer(device, verticesBuffer.getRequestedSize());
        StgIntBuffer indicesStgBuffer = new StgIntBuffer(device, indicesBuffer.getRequestedSize());
        StgIntBuffer materialsStgBuffer = new StgIntBuffer(device, materialsBuffer.getRequestedSize());
        StgIntBuffer animJointMatricesStgBuffer = new StgIntBuffer(device, animJointMatricesBuffer.getRequestedSize());
        StgIntBuffer animWeightsStgBuffer = new StgIntBuffer(device, animWeightsBuffer.getRequestedSize());
        
        // Record initial positions to track how much data we add
        int verticesStartPos = verticesStgBuffer.getDataBuffer().position();
        int indicesStartPos = indicesStgBuffer.getDataBuffer().position();

        cmd.beginRecording();

        // Load a default material
        List<ModelData.Material> defaultMaterialList = Collections.singletonList(new ModelData.Material());
        loadMaterials(device, textureCache, materialsStgBuffer, defaultMaterialList, textureList);

        for (ModelData modelData : modelDataList) {
            VulkanModel vulkanModel = new VulkanModel(modelData.getModelId());
            vulkanModelList.add(vulkanModel);

            List<VulkanModel.VulkanMaterial> vulkanMaterialList = loadMaterials(device, textureCache, materialsStgBuffer,
                    modelData.getMaterialList(), textureList);
            loadMeshes(verticesStgBuffer, indicesStgBuffer, animWeightsStgBuffer, modelData, vulkanModel, vulkanMaterialList);
            loadAnimationData(modelData, vulkanModel, animJointMatricesStgBuffer);
        }

        // We need to ensure that at least we have one texture
        if (textureList.isEmpty()) {
            EngineProperties engineProperties = EngineProperties.getInstance();
            Texture defaultTexture = textureCache.createTexture(device, engineProperties.getDefaultTexturePath(),
                    VK_FORMAT_R8G8B8A8_SRGB);
            textureList.add(defaultTexture);
        }

        // Calculate amount of new data added
        int verticesSize = (verticesStgBuffer.getDataBuffer().position() - verticesStartPos) * INT_LENGTH;
        int indicesSize = (indicesStgBuffer.getDataBuffer().position() - indicesStartPos) * INT_LENGTH;
        
        // Check if we would exceed buffer capacity
        if (currentVerticesOffset + verticesSize > verticesBuffer.getRequestedSize()) {
            Logger.error("Vertex buffer overflow: current offset {} + new data size {} exceeds buffer size {}",
                currentVerticesOffset, verticesSize, verticesBuffer.getRequestedSize());
            // Reset staging buffer position to discard this data
            verticesStgBuffer.getDataBuffer().position(verticesStartPos);
            indicesStgBuffer.getDataBuffer().position(indicesStartPos);
            
            // If buffer is completely full, reset to avoid further issues (this means we'll start overwriting)
            if (currentVerticesOffset >= verticesBuffer.getRequestedSize() * 0.95) {
                Logger.warn("Vertex buffer is nearly full ({}%), resetting offsets", 
                    (currentVerticesOffset * 100.0 / verticesBuffer.getRequestedSize()));
                currentVerticesOffset = 0;
                currentIndicesOffset = 0;
            }
            
            // Skip this model
            return vulkanModelList;
        }
        
        if (currentIndicesOffset + indicesSize > indicesBuffer.getRequestedSize()) {
            Logger.error("Index buffer overflow: current offset {} + new data size {} exceeds buffer size {}",
                currentIndicesOffset, indicesSize, indicesBuffer.getRequestedSize());
            // Reset staging buffer position to discard this data
            verticesStgBuffer.getDataBuffer().position(verticesStartPos);
            indicesStgBuffer.getDataBuffer().position(indicesStartPos);
            
            // If buffer is completely full, reset to avoid further issues
            if (currentIndicesOffset >= indicesBuffer.getRequestedSize() * 0.95) {
                Logger.warn("Index buffer is nearly full ({}%), resetting offsets", 
                    (currentIndicesOffset * 100.0 / indicesBuffer.getRequestedSize()));
                currentVerticesOffset = 0;
                currentIndicesOffset = 0;
            }
            
            // Skip this model
            return vulkanModelList;
        }
        
        // Log info about the data being loaded
        Logger.debug("Loading model data: Vertices size = {} bytes, Indices size = {} bytes", verticesSize, indicesSize);
        Logger.debug("Current offsets: Vertices = {}, Indices = {}", currentVerticesOffset, currentIndicesOffset);

        // Record transfer commands with proper offsets for new data
        materialsStgBuffer.recordTransferCommand(cmd, materialsBuffer);
        verticesStgBuffer.recordTransferCommand(cmd, verticesBuffer, currentVerticesOffset, verticesSize);
        indicesStgBuffer.recordTransferCommand(cmd, indicesBuffer, currentIndicesOffset, indicesSize);
        animJointMatricesStgBuffer.recordTransferCommand(cmd, animJointMatricesBuffer);
        animWeightsStgBuffer.recordTransferCommand(cmd, animWeightsBuffer);
        textureList.forEach(t -> t.recordTextureTransition(cmd));
        cmd.endRecording();
        
        // Update offsets in a thread-safe way after the command is executed
        synchronized(this) {
            currentVerticesOffset += verticesSize;
            currentIndicesOffset += indicesSize;
        }

        cmd.submitAndWait(device, queue);
        cmd.cleanup();

        verticesStgBuffer.cleanup();
        indicesStgBuffer.cleanup();
        materialsStgBuffer.cleanup();
        animJointMatricesStgBuffer.cleanup();
        animWeightsStgBuffer.cleanup();
        textureList.forEach(Texture::cleanupStgBuffer);

        return vulkanModelList;
    }

    private void loadStaticEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool,
                                    Queue queue, int numSwapChainImages) {
        numIndirectCommands = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = commandPool.getDevice();
            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

            List<VkDrawIndexedIndirectCommand> indexedIndirectCommandList = new ArrayList<>();
            int numInstances = 0;
            int firstInstance = 0;
            for (VulkanModel vulkanModel : vulkanModelList) {
                List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities == null || entities.isEmpty() || vulkanModel.hasAnimations()) {
                    continue;
                }
                for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                    indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                    indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / INT_LENGTH);
                    indexedIndirectCommand.instanceCount(entities.size());
                    indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VertexBufferStructure.SIZE_IN_BYTES);
                    indexedIndirectCommand.firstInstance(firstInstance);
                    indexedIndirectCommandList.add(indexedIndirectCommand);

                    numIndirectCommands++;
                    firstInstance += entities.size();
                    numInstances += entities.size();
                }
            }
            if (numIndirectCommands > 0) {
                cmd.beginRecording();

                StgByteBuffer indirectStgBuffer = new StgByteBuffer(device, (long) IND_COMMAND_STRIDE * numIndirectCommands);
                if (indirectBuffer != null) {
                    indirectBuffer.cleanup();
                }
                indirectBuffer = new VulkanBuffer(device, indirectStgBuffer.stgVulkanBuffer.getRequestedSize(),
                        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
                VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

                indexedIndirectCommandList.forEach(indCommandBuffer::put);

                if (instanceDataBuffers != null) {
                    Arrays.asList(instanceDataBuffers).forEach(VulkanBuffer::cleanup);
                }
                
                // Add extra safety margin to buffer size (2x)
                long instanceBufferSize = (long) numInstances * (MAT4X4_SIZE + INT_LENGTH) * 2;
                System.out.println("Creating instanceDataBuffers with size: " + instanceBufferSize);
                
                instanceDataBuffers = new VulkanBuffer[numSwapChainImages];
                for (int i = 0; i < numSwapChainImages; i++) {
                    instanceDataBuffers[i] = new VulkanBuffer(device, instanceBufferSize,
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
                }

                indirectStgBuffer.recordTransferCommand(cmd, indirectBuffer);

                cmd.endRecording();
                cmd.submitAndWait(device, queue);
                cmd.cleanup();
                indirectStgBuffer.cleanup();
            }
        }
    }

    private void loadWeightsBuffer(ModelData modelData, StgIntBuffer animWeightsBuffer, int meshCount) {
        List<ModelData.AnimMeshData> animMeshDataList = modelData.getAnimMeshDataList();
        if (animMeshDataList == null || animMeshDataList.isEmpty()) {
            return;
        }

        ModelData.AnimMeshData animMeshData = animMeshDataList.get(meshCount);
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();

        IntBuffer dataBuffer = animWeightsBuffer.getDataBuffer();

        int rows = weights.length / 4;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 4;
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos]));
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos + 1]));
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos + 2]));
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos + 3]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos + 1]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos + 2]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos + 3]));
        }
    }

    private static abstract class StgBuffer {
        protected final VulkanBuffer stgVulkanBuffer;

        public StgBuffer(Device device, long size) {
            stgVulkanBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }

        public void cleanup() {
            stgVulkanBuffer.unMap();
            stgVulkanBuffer.cleanup();
        }

        public void recordTransferCommand(CommandBuffer cmd, VulkanBuffer dstBuffer, int dstOffset, int size) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0).dstOffset(dstOffset).size(size);
                vkCmdCopyBuffer(cmd.getVkCommandBuffer(), stgVulkanBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
            }
        }
        
        public void recordTransferCommand(CommandBuffer cmd, VulkanBuffer dstBuffer) {
            // For backward compatibility - copies the entire buffer to offset 0
            recordTransferCommand(cmd, dstBuffer, 0, (int)stgVulkanBuffer.getRequestedSize());
        }
    }

    private static class StgByteBuffer extends StgBuffer {
        private final ByteBuffer dataBuffer;

        public StgByteBuffer(Device device, long size) {
            super(device, size);
            long mappedMemory = stgVulkanBuffer.map();
            dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stgVulkanBuffer.getRequestedSize());
        }

        public ByteBuffer getDataBuffer() {
            return dataBuffer;
        }
    }

    private static class StgIntBuffer extends StgBuffer {
        private final IntBuffer dataBuffer;

        public StgIntBuffer(Device device, long size) {
            super(device, size);
            long mappedMemory = stgVulkanBuffer.map();
            dataBuffer = MemoryUtil.memIntBuffer(mappedMemory, (int) stgVulkanBuffer.getRequestedSize() / INT_LENGTH);
        }

        public IntBuffer getDataBuffer() {
            return dataBuffer;
        }
    }
}
