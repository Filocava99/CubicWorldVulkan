package it.filippocavallari.cubicworld.renderer

import it.filippocavallari.cubicworld.utils.VulkanUtils
import it.filippocavallari.cubicworld.window.Window
import it.filippocavallari.cubicworld.world.chunk.Chunk
import it.filippocavallari.cubicworld.world.generators.TerrainGenerator
import it.filippocavallari.models.ModelManager
import it.filippocavallari.textures.TextureStitcher
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer

/**
 * VulkanRenderer handles all Vulkan rendering operations.
 * This includes initialization, resource management, and frame rendering.
 */
class VulkanRenderer(private val window: Window) {
    
    // Vulkan objects
    private var instance: VkInstance? = null
    private var surface: Long = VK_NULL_HANDLE
    private var physicalDevice: VkPhysicalDevice? = null
    private var device: VkDevice? = null
    private var graphicsQueue: VkQueue? = null
    private var presentQueue: VkQueue? = null
    private var swapchain: Long = VK_NULL_HANDLE
    private var swapchainImages: LongBuffer? = null
    private var swapchainImageViews: Array<Long>? = null
    private var swapchainImageFormat: Int = 0
    private var swapchainExtent: VkExtent2D? = null
    private var renderPass: Long = VK_NULL_HANDLE
    private var pipelineLayout: Long = VK_NULL_HANDLE
    private var graphicsPipeline: Long = VK_NULL_HANDLE
    private var framebuffers: Array<Long>? = null
    private var commandPool: Long = VK_NULL_HANDLE
    private var commandBuffers: Array<VkCommandBuffer>? = null
    
    // Synchronization objects
    private var imageAvailableSemaphores: LongBuffer? = null
    private var renderFinishedSemaphores: LongBuffer? = null
    private var inFlightFences: LongBuffer? = null
    private var currentFrame = 0
    
    // Queue family indices
    private var graphicsFamily = -1
    private var presentFamily = -1
    
    // Constants
    private val MAX_FRAMES_IN_FLIGHT = 2
    private val validationLayers = arrayOf("VK_LAYER_KHRONOS_validation")
    private val deviceExtensions = arrayOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
    
    // Depth buffer resources
    private var depthImage: Long = VK_NULL_HANDLE
    private var depthImageMemory: Long = VK_NULL_HANDLE
    private var depthImageView: Long = VK_NULL_HANDLE
    
    // Chunk rendering
    private var chunkRenderer: ChunkRenderer? = null
    private var modelManager: ModelManager? = null
    private var textureStitcher: TextureStitcher? = null
    
    // Camera properties
    private val viewMatrix = Matrix4f()
    private val projMatrix = Matrix4f()
    private val cameraPosition = Vector3f(0f, 70f, 0f)
    private val cameraRotation = Vector3f(0f, 0f, 0f)
    
    // Test chunk for rendering
    private var testChunk: Chunk? = null
    private var terrainGenerator: TerrainGenerator? = null
    
    // Enable validation layers in debug mode
    private val enableValidationLayers = true
    
    fun init() {
        createInstance()
        createSurface()
        pickPhysicalDevice()
        createLogicalDevice()
        createSwapChain()
        createImageViews()
        createDepthResources()
        createRenderPass()
        createGraphicsPipeline()
        createFramebuffers()
        createCommandPool()
        createCommandBuffers()
        createSyncObjects()
        
        // Initialize texture stitcher and load textures
        textureStitcher = it.filippocavallari.textures.TextureAtlasLoader.loadAndStitchTextures(
            "src/main/resources",
            "src/main/resources/atlas",
            128 // Texture size
        )
        
        // Initialize model manager
        modelManager = ModelManager("src/main/resources/models", textureStitcher!!)
        
        // Initialize chunk renderer
        chunkRenderer = ChunkRenderer(
            device!!,
            physicalDevice!!,
            renderPass,
            commandPool,
            graphicsQueue!!,
            swapchainExtent!!,
            modelManager!!,
            textureStitcher!!
        )
        chunkRenderer!!.init()
        
        // Set up camera
        setupCamera()
        
        // Create and prepare test chunk
        createTestChunk()
    }
    
    /**
     * Set up the camera matrices
     */
    private fun setupCamera() {
        // Create projection matrix
        val aspectRatio = swapchainExtent!!.width().toFloat() / swapchainExtent!!.height().toFloat()
        projMatrix.identity().perspective(Math.toRadians(70.0).toFloat(), aspectRatio, 0.1f, 1000.0f)
        
        // Flip Y for Vulkan coordinate system
        projMatrix.m11(projMatrix.m11() * -1)
        
        // Set initial view matrix
        updateViewMatrix()
        
        // Set view and projection in chunk renderer
        chunkRenderer?.setViewProjection(viewMatrix, projMatrix)
        chunkRenderer?.setCameraPosition(cameraPosition)
    }
    
    /**
     * Update the view matrix based on camera position and rotation
     */
    private fun updateViewMatrix() {
        viewMatrix.identity()
            .rotateX(cameraRotation.x)
            .rotateY(cameraRotation.y)
            .rotateZ(cameraRotation.z)
            .translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z)
    }
    
    /**
     * Create a test chunk for rendering
     */
    private fun createTestChunk() {
        // Create terrain generator
        terrainGenerator = TerrainGenerator()
        
        // Create a chunk at origin
        testChunk = Chunk(0, 0)
        
        // Generate terrain
        terrainGenerator!!.generateChunk(testChunk!!)
        
        // Add chunk to renderer
        chunkRenderer?.addChunk(testChunk!!)
    }
    
    /**
     * Create depth buffer resources
     */
    private fun createDepthResources() {
        // Find depth format
        val depthFormat = findDepthFormat()
        
        // Create depth image
        val depthImageResult = createImage(
            swapchainExtent!!.width(),
            swapchainExtent!!.height(),
            depthFormat,
            VK_IMAGE_TILING_OPTIMAL,
            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        )
        
        depthImage = depthImageResult.first
        depthImageMemory = depthImageResult.second
        
        // Create depth image view
        depthImageView = createImageView(depthImage, depthFormat)
        
        // Transition layout to depth optimal
        transitionImageLayout(
            depthImage,
            depthFormat,
            VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        )
    }
    
    /**
     * Find a supported depth format
     */
    private fun findDepthFormat(): Int {
        return findSupportedFormat(
            arrayOf(VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT),
            VK_IMAGE_TILING_OPTIMAL,
            VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT
        )
    }
    
    /**
     * Find a supported format from candidates
     */
    private fun findSupportedFormat(candidates: Array<Int>, tiling: Int, features: Int): Int {
        for (format in candidates) {
            val props = VkFormatProperties.calloc(MemoryStack.stackGet())
            vkGetPhysicalDeviceFormatProperties(physicalDevice!!, format, props)
            
            if (tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() and features) == features) {
                props.free()
                return format
            } else if (tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() and features) == features) {
                props.free()
                return format
            }
            
            props.free()
        }
        
        throw RuntimeException("Failed to find supported format")
    }
    
    /**
     * Create a Vulkan image with memory
     */
    private fun createImage(
        width: Int,
        height: Int,
        format: Int,
        tiling: Int,
        usage: Int,
        properties: Int
    ): Pair<Long, Long> {
        MemoryStack.stackPush().use { stack ->
            // Create image
            val imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .extent { extent ->
                    extent
                        .width(width)
                        .height(height)
                        .depth(1)
                }
                .mipLevels(1)
                .arrayLayers(1)
                .format(format)
                .tiling(tiling)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(usage)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            
            val pImage = stack.mallocLong(1)
            if (vkCreateImage(device!!, imageInfo, null, pImage) != VK_SUCCESS) {
                throw RuntimeException("Failed to create image")
            }
            val image = pImage.get(0)
            
            // Get memory requirements
            val memRequirements = VkMemoryRequirements.calloc(stack)
            vkGetImageMemoryRequirements(device!!, image, memRequirements)
            
            // Allocate memory
            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(VulkanUtils.findMemoryType(
                    physicalDevice!!,
                    memRequirements.memoryTypeBits(),
                    properties
                ))
            
            val pImageMemory = stack.mallocLong(1)
            if (vkAllocateMemory(device!!, allocInfo, null, pImageMemory) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate image memory")
            }
            val imageMemory = pImageMemory.get(0)
            
            // Bind memory to image
            vkBindImageMemory(device!!, image, imageMemory, 0)
            
            return Pair(image, imageMemory)
        }
    }
    
    /**
     * Transition image layout
     */
    private fun transitionImageLayout(image: Long, format: Int, oldLayout: Int, newLayout: Int) {
        MemoryStack.stackPush().use { stack ->
            val commandBuffer = beginSingleTimeCommands()
            
            val barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange { range ->
                    if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                        range.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                        
                        // Add stencil aspect if format has stencil component
                        if (hasStencilComponent(format)) {
                            range.aspectMask(range.aspectMask() or VK_IMAGE_ASPECT_STENCIL_BIT)
                        }
                    } else {
                        range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    }
                    
                    range.baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            
            var sourceStage: Int
            var destinationStage: Int
            
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.srcAccessMask(0)
                barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
            } else {
                throw IllegalArgumentException("Unsupported layout transition")
            }
            
            vkCmdPipelineBarrier(
                commandBuffer,
                sourceStage, destinationStage,
                0,
                null,
                null,
                barrier
            )
            
            endSingleTimeCommands(commandBuffer)
        }
    }
    
    /**
     * Check if format has stencil component
     */
    private fun hasStencilComponent(format: Int): Boolean {
        return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT
    }
    
    /**
     * Begin a single-use command buffer
     */
    private fun beginSingleTimeCommands(): VkCommandBuffer {
        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandPool(commandPool)
                .commandBufferCount(1)
            
            val pCommandBuffer = stack.mallocPointer(1)
            vkAllocateCommandBuffers(device!!, allocInfo, pCommandBuffer)
            val commandBuffer = VkCommandBuffer(pCommandBuffer.get(0), device!!)
            
            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
            
            vkBeginCommandBuffer(commandBuffer, beginInfo)
            
            return commandBuffer
        }
    }
    
    /**
     * End and submit a single-use command buffer
     */
    private fun endSingleTimeCommands(commandBuffer: VkCommandBuffer) {
        MemoryStack.stackPush().use { stack ->
            vkEndCommandBuffer(commandBuffer)
            
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(commandBuffer.address()))
            
            vkQueueSubmit(graphicsQueue!!, submitInfo, VK_NULL_HANDLE)
            vkQueueWaitIdle(graphicsQueue!!)
            
            vkFreeCommandBuffers(device!!, commandPool, stack.pointers(commandBuffer.address()))
        }
    }
    
    private fun createInstance() {
        if (enableValidationLayers && !checkValidationLayerSupport()) {
            throw RuntimeException("Validation layers requested, but not available!")
        }
        
        MemoryStack.stackPush().use { stack ->
            // Application info
            val appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("CubicWorld"))
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("CubicWorld Engine"))
                .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK_API_VERSION_1_0)
            
            // Instance creation info
            val createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
            
            // Add validation layers if enabled
            if (enableValidationLayers) {
                createInfo.ppEnabledLayerNames(VulkanUtils.asPointerBuffer(stack, validationLayers))
            }
            
            // Add extensions required by GLFW
            val glfwExtensions = getRequiredExtensions(stack)
            createInfo.ppEnabledExtensionNames(glfwExtensions)
            
            // Create instance
            val pInstance = stack.mallocPointer(1)
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
                throw RuntimeException("Failed to create Vulkan instance")
            }
            
            instance = VkInstance(pInstance.get(0), createInfo)
        }
    }
    
    private fun createSurface() {
        MemoryStack.stackPush().use { stack ->
            val pSurface = stack.longs(VK_NULL_HANDLE)
            if (GLFWVulkan.glfwCreateWindowSurface(instance!!, window.getWindowHandle(), null, pSurface) != VK_SUCCESS) {
                throw RuntimeException("Failed to create window surface")
            }
            surface = pSurface.get(0)
        }
    }
    
    private fun pickPhysicalDevice() {
        MemoryStack.stackPush().use { stack ->
            // Get available devices
            val deviceCount = stack.ints(0)
            vkEnumeratePhysicalDevices(instance!!, deviceCount, null)
            if (deviceCount.get(0) == 0) {
                throw RuntimeException("Failed to find GPUs with Vulkan support")
            }
            
            val ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0))
            vkEnumeratePhysicalDevices(instance!!, deviceCount, ppPhysicalDevices)
            
            // Find suitable device
            for (i in 0 until deviceCount.get(0)) {
                val device = VkPhysicalDevice(ppPhysicalDevices.get(i), instance!!)
                if (isDeviceSuitable(device, stack)) {
                    physicalDevice = device
                    break
                }
            }
            
            if (physicalDevice == null) {
                throw RuntimeException("Failed to find a suitable GPU")
            }
        }
    }
    
    private fun createLogicalDevice() {
        MemoryStack.stackPush().use { stack ->
            // Create queue create infos
            val queueCreateInfos = VkDeviceQueueCreateInfo.calloc(if (graphicsFamily != presentFamily) 2 else 1, stack)
            
            // Set priorities for queues
            val queuePriority = stack.floats(1.0f)
            
            // Graphics queue info
            queueCreateInfos.get(0)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsFamily)
                .pQueuePriorities(queuePriority)
            
            // Present queue info (if different from graphics)
            if (graphicsFamily != presentFamily) {
                queueCreateInfos.get(1)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(presentFamily)
                    .pQueuePriorities(queuePriority)
            }
            
            // Device features to enable
            val deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)
            
            // Device creation info
            val createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfos)
                .pEnabledFeatures(deviceFeatures)
                .ppEnabledExtensionNames(VulkanUtils.asPointerBuffer(stack, deviceExtensions))
            
            // Add validation layers if enabled
            if (enableValidationLayers) {
                createInfo.ppEnabledLayerNames(VulkanUtils.asPointerBuffer(stack, validationLayers))
            }
            
            // Create device
            val pDevice = stack.pointers(VK_NULL_HANDLE)
            if (vkCreateDevice(physicalDevice!!, createInfo, null, pDevice) != VK_SUCCESS) {
                throw RuntimeException("Failed to create logical device")
            }
            device = VkDevice(pDevice.get(0), physicalDevice!!, createInfo)
            
            // Get queue handles
            val pQueue = stack.pointers(VK_NULL_HANDLE)
            vkGetDeviceQueue(device!!, graphicsFamily, 0, pQueue)
            graphicsQueue = VkQueue(pQueue.get(0), device!!)
            
            vkGetDeviceQueue(device!!, presentFamily, 0, pQueue)
            presentQueue = VkQueue(pQueue.get(0), device!!)
        }
    }
    
    private fun createSwapChain() {
        MemoryStack.stackPush().use { stack ->
            // Query swap chain support details
            val swapChainSupport = querySwapChainSupport(physicalDevice!!, stack)
            
            // Choose the best swap chain format, present mode, and extent
            val surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.formats)
            val presentMode = chooseSwapPresentMode(swapChainSupport.presentModes)
            val extent = chooseSwapExtent(swapChainSupport.capabilities, stack)
            
            // Determine the number of images in the swap chain
            var imageCount = swapChainSupport.capabilities.minImageCount() + 1
            if (swapChainSupport.capabilities.maxImageCount() > 0 && imageCount > swapChainSupport.capabilities.maxImageCount()) {
                imageCount = swapChainSupport.capabilities.maxImageCount()
            }
            
            // Create the swap chain create info
            val createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(surfaceFormat.format())
                .imageColorSpace(surfaceFormat.colorSpace())
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
            
            // Set up queue sharing mode
            val queueFamilyIndices = stack.ints(graphicsFamily, presentFamily)
            if (graphicsFamily != presentFamily) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                    .queueFamilyIndexCount(2)
                    .pQueueFamilyIndices(queueFamilyIndices)
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            }
            
            // Complete the creation info
            createInfo.preTransform(swapChainSupport.capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE)
            
            // Create the swap chain
            val pSwapChain = stack.longs(VK_NULL_HANDLE)
            if (vkCreateSwapchainKHR(device!!, createInfo, null, pSwapChain) != VK_SUCCESS) {
                throw RuntimeException("Failed to create swap chain")
            }
            swapchain = pSwapChain.get(0)
            
            // Get the swap chain images
            val imagesCount = stack.ints(0)
            vkGetSwapchainImagesKHR(device!!, swapchain, imagesCount, null)
            
            swapchainImages = stack.mallocLong(imagesCount.get(0))
            vkGetSwapchainImagesKHR(device!!, swapchain, imagesCount, swapchainImages)
            
            // Store format and extent
            swapchainImageFormat = surfaceFormat.format()
            swapchainExtent = VkExtent2D.create().set(extent)
        }
    }
    
    private fun createImageViews() {
        swapchainImageViews = Array(swapchainImages!!.capacity()) { VK_NULL_HANDLE }
        
        for (i in 0 until swapchainImages!!.capacity()) {
            swapchainImageViews!![i] = createImageView(swapchainImages!!.get(i), swapchainImageFormat)
        }
    }
    
    private fun createImageView(image: Long, format: Int): Long {
        return createImageView(image, format, VK_IMAGE_ASPECT_COLOR_BIT)
    }
    
    private fun createImageView(image: Long, format: Int, aspectMask: Int): Long {
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(format)
                .components { comp ->
                    comp.r(VK_COMPONENT_SWIZZLE_IDENTITY)
                        .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                        .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                        .a(VK_COMPONENT_SWIZZLE_IDENTITY)
                }
                .subresourceRange { range ->
                    range.aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            
            val pImageView = stack.mallocLong(1)
            if (vkCreateImageView(device!!, createInfo, null, pImageView) != VK_SUCCESS) {
                throw RuntimeException("Failed to create image view")
            }
            
            return pImageView.get(0)
        }
    }
    
    private fun createRenderPass() {
        MemoryStack.stackPush().use { stack ->
            // Create attachments
            val attachments = VkAttachmentDescription.calloc(2, stack)
            
            // Color attachment
            attachments.get(0)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            
            // Depth attachment
            attachments.get(1)
                .format(findDepthFormat())
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            
            // Create attachment references
            val colorReference = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            
            val depthReference = VkAttachmentReference.calloc(stack)
                .attachment(1)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            
            // Create subpass
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorReference)
                .pDepthStencilAttachment(depthReference)
            
            // Create subpass dependencies
            val dependencies = VkSubpassDependency.calloc(2, stack)
            
            // First dependency - transition to color attachment
            dependencies.get(0)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)
            
            // Second dependency - transition from color attachment
            dependencies.get(1)
                .srcSubpass(0)
                .dstSubpass(VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)
            
            // Create render pass
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependencies)
            
            val pRenderPass = stack.mallocLong(1)
            if (vkCreateRenderPass(device!!, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw RuntimeException("Failed to create render pass")
            }
            renderPass = pRenderPass.get(0)
        }
    }
    
    private fun createGraphicsPipeline() {
        // To be implemented - would load shaders and create the full graphics pipeline
        // For now, we'll just create a minimal pipeline layout
        
        MemoryStack.stackPush().use { stack ->
            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(null)
                .pPushConstantRanges(null)
            
            val pPipelineLayout = stack.mallocLong(1)
            if (vkCreatePipelineLayout(device!!, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw RuntimeException("Failed to create pipeline layout")
            }
            pipelineLayout = pPipelineLayout.get(0)
        }
    }
    
    private fun createFramebuffers() {
        framebuffers = Array(swapchainImageViews!!.size) { VK_NULL_HANDLE }
        
        MemoryStack.stackPush().use { stack ->
            val attachments = stack.mallocLong(2) // Color + Depth
            val framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .renderPass(renderPass)
                .width(swapchainExtent!!.width())
                .height(swapchainExtent!!.height())
                .layers(1)
            
            val pFramebuffer = stack.mallocLong(1)
            
            for (i in swapchainImageViews!!.indices) {
                // Set attachments - color and depth
                attachments.put(0, swapchainImageViews!![i])
                attachments.put(1, depthImageView)
                
                framebufferInfo.pAttachments(attachments)
                
                if (vkCreateFramebuffer(device!!, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw RuntimeException("Failed to create framebuffer")
                }
                
                framebuffers!![i] = pFramebuffer.get(0)
            }
        }
    }
    
    private fun createCommandPool() {
        MemoryStack.stackPush().use { stack ->
            val poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .queueFamilyIndex(graphicsFamily)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
            
            val pCommandPool = stack.mallocLong(1)
            if (vkCreateCommandPool(device!!, poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw RuntimeException("Failed to create command pool")
            }
            commandPool = pCommandPool.get(0)
        }
    }
    
    private fun createCommandBuffers() {
        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(MAX_FRAMES_IN_FLIGHT)
            
            val pCommandBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT)
            if (vkAllocateCommandBuffers(device!!, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw RuntimeException("Failed to allocate command buffers")
            }
            
            commandBuffers = Array(MAX_FRAMES_IN_FLIGHT) { i ->
                VkCommandBuffer(pCommandBuffers.get(i), device!!)
            }
        }
    }
    
    private fun createSyncObjects() {
        MemoryStack.stackPush().use { stack ->
            imageAvailableSemaphores = MemoryUtil.memAllocLong(MAX_FRAMES_IN_FLIGHT)
            renderFinishedSemaphores = MemoryUtil.memAllocLong(MAX_FRAMES_IN_FLIGHT)
            inFlightFences = MemoryUtil.memAllocLong(MAX_FRAMES_IN_FLIGHT)
            
            val semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            
            val fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT)
            
            val pSemaphore1 = stack.mallocLong(1)
            val pSemaphore2 = stack.mallocLong(1)
            val pFence = stack.mallocLong(1)
            
            for (i in 0 until MAX_FRAMES_IN_FLIGHT) {
                if (vkCreateSemaphore(device!!, semaphoreInfo, null, pSemaphore1) != VK_SUCCESS ||
                    vkCreateSemaphore(device!!, semaphoreInfo, null, pSemaphore2) != VK_SUCCESS ||
                    vkCreateFence(device!!, fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw RuntimeException("Failed to create synchronization objects")
                }
                
                imageAvailableSemaphores!!.put(i, pSemaphore1.get(0))
                renderFinishedSemaphores!!.put(i, pSemaphore2.get(0))
                inFlightFences!!.put(i, pFence.get(0))
            }
        }
    }
    
    fun render() {
        // Wait for previous frame to finish
        vkWaitForFences(device!!, stackPut(inFlightFences!!, currentFrame), true, Long.MAX_VALUE)
        
        // Acquire next image
        val imageIndex = acquireNextImage()
        if (imageIndex < 0) {
            return // Swapchain out of date or suboptimal
        }
        
        // Reset fence for current frame
        vkResetFences(device!!, stackPut(inFlightFences!!, currentFrame))
        
        // Record command buffer
        recordCommandBuffer(imageIndex)
        
        // Submit command buffer
        submitCommandBuffer()
        
        // Present the image
        presentImage(imageIndex)
        
        // Move to next frame
        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT
    }
    
    private fun acquireNextImage(): Int {
        MemoryStack.stackPush().use { stack ->
            val pImageIndex = stack.ints(0)
            val result = vkAcquireNextImageKHR(
                device!!,
                swapchain,
                Long.MAX_VALUE,
                imageAvailableSemaphores!!.get(currentFrame),
                VK_NULL_HANDLE,
                pImageIndex
            )
            
            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapChain()
                return -1
            } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw RuntimeException("Failed to acquire next image")
            }
            
            return pImageIndex.get(0)
        }
    }
    
    private fun recordCommandBuffer(imageIndex: Int) {
        MemoryStack.stackPush().use { stack ->
            val cmdBuf = commandBuffers!![currentFrame]
            
            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            
            // Begin recording
            if (vkBeginCommandBuffer(cmdBuf, beginInfo) != VK_SUCCESS) {
                throw RuntimeException("Failed to begin recording command buffer")
            }
            
            // Start render pass
            val renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass)
                .framebuffer(framebuffers!![imageIndex])
                .renderArea(VkRect2D.calloc(stack)
                    .offset(VkOffset2D.calloc(stack).set(0, 0))
                    .extent(swapchainExtent!!))
            
            // Set clear values for color and depth
            val clearValues = VkClearValue.calloc(2, stack)
            
            // Clear color
            clearValues.get(0).color()
                .float32(0, 0.4f) // R - Sky blue
                .float32(1, 0.6f) // G
                .float32(2, 0.9f) // B
                .float32(3, 1.0f) // A
            
            // Clear depth
            clearValues.get(1).depthStencil()
                .depth(1.0f)
                .stencil(0)
            
            renderPassInfo.pClearValues(clearValues)
            
            // Begin render pass
            vkCmdBeginRenderPass(cmdBuf, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)
            
            // Use the chunk renderer to render all chunks
            chunkRenderer?.render(cmdBuf)
            
            // End render pass
            vkCmdEndRenderPass(cmdBuf)
            
            // End recording
            if (vkEndCommandBuffer(cmdBuf) != VK_SUCCESS) {
                throw RuntimeException("Failed to record command buffer")
            }
        }
    }
    
    private fun submitCommandBuffer() {
        MemoryStack.stackPush().use { stack ->
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            
            // Wait semaphores
            submitInfo.waitSemaphoreCount(1)
                .pWaitSemaphores(stack.longs(imageAvailableSemaphores!!.get(currentFrame)))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
            
            // Command buffers to execute
            submitInfo.pCommandBuffers(stack.pointers(commandBuffers!![currentFrame].address()))
            
            // Signal semaphores
            val signalSemaphores = stack.longs(renderFinishedSemaphores!!.get(currentFrame))
            submitInfo.pSignalSemaphores(signalSemaphores)
            
            // Submit to queue
            if (vkQueueSubmit(graphicsQueue!!, submitInfo, inFlightFences!!.get(currentFrame)) != VK_SUCCESS) {
                throw RuntimeException("Failed to submit draw command buffer")
            }
        }
    }
    
    private fun presentImage(imageIndex: Int) {
        MemoryStack.stackPush().use { stack ->
            val presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            
            // Wait semaphores
            val waitSemaphores = stack.longs(renderFinishedSemaphores!!.get(currentFrame))
            presentInfo.pWaitSemaphores(waitSemaphores)
            
            // Swapchains
            presentInfo.swapchainCount(1)
                .pSwapchains(stack.longs(swapchain))
                .pImageIndices(stack.ints(imageIndex))
            
            // Present
            val result = vkQueuePresentKHR(presentQueue!!, presentInfo)
            
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                recreateSwapChain()
            } else if (result != VK_SUCCESS) {
                throw RuntimeException("Failed to present swap chain image")
            }
        }
    }
    
    private fun recreateSwapChain() {
        // Handle minimization
        MemoryStack.stackPush().use { stack ->
            val width = stack.ints(0)
            val height = stack.ints(0)
            while (width.get(0) == 0 || height.get(0) == 0) {
                GLFW.glfwGetFramebufferSize(window.getWindowHandle(), width, height)
                GLFW.glfwWaitEvents()
            }
        }
        
        // Wait for device to finish all operations
        vkDeviceWaitIdle(device!!)
        
        // Clean up old swap chain resources
        cleanupSwapChain()
        
        // Recreate swap chain and dependent resources
        createSwapChain()
        createImageViews()
        createRenderPass()
        createGraphicsPipeline()
        createFramebuffers()
        createCommandBuffers()
    }
    
    private fun cleanupSwapChain() {
        // Cleanup depth resources
        vkDestroyImageView(device!!, depthImageView, null)
        vkDestroyImage(device!!, depthImage, null)
        vkFreeMemory(device!!, depthImageMemory, null)
        
        // Cleanup framebuffers
        framebuffers?.forEach { vkDestroyFramebuffer(device!!, it, null) }
        
        // Cleanup pipeline
        if (graphicsPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device!!, graphicsPipeline, null)
            graphicsPipeline = VK_NULL_HANDLE
        }
        
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device!!, pipelineLayout, null)
            pipelineLayout = VK_NULL_HANDLE
        }
        
        // Cleanup render pass
        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device!!, renderPass, null)
            renderPass = VK_NULL_HANDLE
        }
        
        // Cleanup swapchain image views
        swapchainImageViews?.forEach { vkDestroyImageView(device!!, it, null) }
        
        // Cleanup swapchain
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device!!, swapchain, null)
            swapchain = VK_NULL_HANDLE
        }
    }
    
    fun cleanup() {
        // Wait for device to finish operations
        vkDeviceWaitIdle(device!!)
        
        // Cleanup chunk renderer
        chunkRenderer?.cleanup()
        
        // Cleanup swap chain resources
        cleanupSwapChain()
        
        // Cleanup depth resources
        vkDestroyImageView(device!!, depthImageView, null)
        vkDestroyImage(device!!, depthImage, null)
        vkFreeMemory(device!!, depthImageMemory, null)
        
        // Cleanup synchronization objects
        for (i in 0 until MAX_FRAMES_IN_FLIGHT) {
            vkDestroySemaphore(device!!, imageAvailableSemaphores!!.get(i), null)
            vkDestroySemaphore(device!!, renderFinishedSemaphores!!.get(i), null)
            vkDestroyFence(device!!, inFlightFences!!.get(i), null)
        }
        
        // Free memory
        MemoryUtil.memFree(imageAvailableSemaphores)
        MemoryUtil.memFree(renderFinishedSemaphores)
        MemoryUtil.memFree(inFlightFences)
        
        // Destroy command pool
        vkDestroyCommandPool(device!!, commandPool, null)
        
        // Destroy device
        vkDestroyDevice(device!!, null)
        
        // Destroy surface
        vkDestroySurfaceKHR(instance!!, surface, null)
        
        // Destroy instance
        vkDestroyInstance(instance!!, null)
    }
    
    // Helper functions
    
    private fun checkValidationLayerSupport(): Boolean {
        MemoryStack.stackPush().use { stack ->
            val layerCount = stack.ints(0)
            vkEnumerateInstanceLayerProperties(layerCount, null)
            
            val availableLayers = VkLayerProperties.calloc(layerCount.get(0), stack)
            vkEnumerateInstanceLayerProperties(layerCount, availableLayers)
            
            for (layerName in validationLayers) {
                var layerFound = false
                
                for (i in 0 until availableLayers.capacity()) {
                    val layerProperties = availableLayers.get(i)
                    if (layerName == layerProperties.layerNameString()) {
                        layerFound = true
                        break
                    }
                }
                
                if (!layerFound) {
                    return false
                }
            }
            
            return true
        }
    }
    
    private fun getRequiredExtensions(stack: MemoryStack): PointerBuffer {
        val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
            ?: throw RuntimeException("Failed to find GLFW required extensions")
        
        if (enableValidationLayers) {
            val extensions = stack.mallocPointer(glfwExtensions.remaining() + 1)
            extensions.put(glfwExtensions)
            extensions.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME))
            extensions.flip()
            return extensions
        }
        
        return glfwExtensions
    }
    
    private fun isDeviceSuitable(device: VkPhysicalDevice, stack: MemoryStack): Boolean {
        val indices = findQueueFamilies(device, stack)
        val extensionsSupported = checkDeviceExtensionSupport(device, stack)
        
        var swapChainAdequate = false
        if (extensionsSupported) {
            val swapChainSupport = querySwapChainSupport(device, stack)
            swapChainAdequate = swapChainSupport.formats.hasRemaining() && swapChainSupport.presentModes.hasRemaining()
        }
        
        if (indices.isComplete() && extensionsSupported && swapChainAdequate) {
            graphicsFamily = indices.graphicsFamily
            presentFamily = indices.presentFamily
            return true
        }
        
        return false
    }
    
    private fun findQueueFamilies(device: VkPhysicalDevice, stack: MemoryStack): QueueFamilyIndices {
        val indices = QueueFamilyIndices()
        
        val queueFamilyCount = stack.ints(0)
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null)
        
        val queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), stack)
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies)
        
        for (i in 0 until queueFamilies.capacity()) {
            if ((queueFamilies.get(i).queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0) {
                indices.graphicsFamily = i
            }
            
            val presentSupport = stack.ints(0)
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport)
            
            if (presentSupport.get(0) == VK_TRUE) {
                indices.presentFamily = i
            }
            
            if (indices.isComplete()) {
                break
            }
        }
        
        return indices
    }
    
    private fun checkDeviceExtensionSupport(device: VkPhysicalDevice, stack: MemoryStack): Boolean {
        val extensionCount = stack.ints(0)
        vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, extensionCount, null)
        
        val availableExtensions = VkExtensionProperties.calloc(extensionCount.get(0), stack)
        vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, extensionCount, availableExtensions)
        
        for (requiredExtension in deviceExtensions) {
            var found = false
            
            for (i in 0 until availableExtensions.capacity()) {
                val extension = availableExtensions.get(i)
                if (requiredExtension == extension.extensionNameString()) {
                    found = true
                    break
                }
            }
            
            if (!found) {
                return false
            }
        }
        
        return true
    }
    
    private fun querySwapChainSupport(device: VkPhysicalDevice, stack: MemoryStack): SwapChainSupportDetails {
        val details = SwapChainSupportDetails()
        
        // Capabilities
        details.capabilities = VkSurfaceCapabilitiesKHR.calloc(stack)
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities)
        
        // Formats
        val formatCount = stack.ints(0)
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, null)
        
        if (formatCount.get(0) != 0) {
            details.formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack)
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, details.formats)
        }
        
        // Present modes
        val presentModeCount = stack.ints(0)
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, null)
        
        if (presentModeCount.get(0) != 0) {
            details.presentModes = stack.mallocInt(presentModeCount.get(0))
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, details.presentModes)
        }
        
        return details
    }
    
    private fun chooseSwapSurfaceFormat(availableFormats: VkSurfaceFormatKHR.Buffer): VkSurfaceFormatKHR {
        for (i in 0 until availableFormats.capacity()) {
            val availableFormat = availableFormats.get(i)
            if (availableFormat.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                availableFormat.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return availableFormat
            }
        }
        
        return availableFormats.get(0)
    }
    
    private fun chooseSwapPresentMode(availablePresentModes: IntBuffer): Int {
        for (i in 0 until availablePresentModes.capacity()) {
            if (availablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR
            }
        }
        
        return VK_PRESENT_MODE_FIFO_KHR
    }
    
    private fun chooseSwapExtent(capabilities: VkSurfaceCapabilitiesKHR, stack: MemoryStack): VkExtent2D {
        if (capabilities.currentExtent().width() != Int.MAX_VALUE) {
            return capabilities.currentExtent()
        }
        
        val actualExtent = VkExtent2D.calloc(stack)
            .width(window.getWidth())
            .height(window.getHeight())
        
        val minExtent = capabilities.minImageExtent()
        val maxExtent = capabilities.maxImageExtent()
        
        actualExtent.width(Math.max(minExtent.width(), Math.min(maxExtent.width(), actualExtent.width())))
        actualExtent.height(Math.max(minExtent.height(), Math.min(maxExtent.height(), actualExtent.height())))
        
        return actualExtent
    }
    
    private fun stackPut(buffer: LongBuffer, index: Int): LongBuffer {
        MemoryStack.stackPush().use { stack ->
            val fence = stack.mallocLong(1)
            fence.put(0, buffer.get(index))
            return fence
        }
    }
    
    // Inner classes
    
    private class QueueFamilyIndices {
        var graphicsFamily = -1
        var presentFamily = -1
        
        fun isComplete(): Boolean {
            return graphicsFamily >= 0 && presentFamily >= 0
        }
    }
    
    private class SwapChainSupportDetails {
        lateinit var capabilities: VkSurfaceCapabilitiesKHR
        lateinit var formats: VkSurfaceFormatKHR.Buffer
        lateinit var presentModes: IntBuffer
    }
}