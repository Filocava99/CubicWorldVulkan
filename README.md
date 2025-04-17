# CubicWorld - Voxel Game with Vulkan

CubicWorld is a voxel game inspired by Minecraft, built with LWJGL and Vulkan. This project integrates Kotlin-based voxel game mechanics with a Java-based Vulkan rendering engine.

## Architecture

The game is structured with the following components:

### Core Engine (Java)
- Vulkan rendering pipeline
- Scene management
- Window and input handling
- Entity system

### Game Components (Kotlin)
- Voxel world management
- Chunk generation and mesh building
- Texture atlas stitching
- JSON-based model loading

### Integration Layer
- Bridges between Java Vulkan engine and Kotlin game components
- Texture and model conversion
- Vulkan-optimized renderers for voxel content

## Getting Started

1. Make sure you have the Vulkan SDK installed on your system
2. Open the project in IntelliJ IDEA
3. Run the `MainKt` configuration to start the game

## Key Components

### Texture Atlas System

The game uses a texture atlas system that combines multiple textures into one larger texture to improve rendering performance:

- `TextureStitcher`: Combines individual textures into atlases
- `TextureAtlasLoader`: Handles loading atlases to the GPU
- `TextureRegion`: Represents a portion of the atlas for a specific texture

### Voxel World

The world is divided into chunks:

- `World`: Manages loading and unloading of chunks
- `Chunk`: A 16x256x16 section of the world
- `ChunkMeshBuilder`: Converts chunk data into renderable meshes
- `TerrainGenerator`: Creates procedural terrain

### Model System

Models are loaded from JSON files similar to Minecraft's format:

- `ModelManager`: Loads and manages models
- `VulkanModelConverter`: Converts models to Vulkan-compatible format
- `Model`, `Element`, `Face`: Define the structure of models

### Vulkan Integration

- `VulkanIntegration`: Main bridge between Kotlin and Java components
- `VulkanBridge`: Provides Java-accessible methods for integration
- `VulkanRenderer`: Custom renderer for voxel components
- `VulkanChunkMeshBuilder`: Creates optimized meshes for Vulkan rendering

## Controls

- WASD: Move the camera
- Space: Move up
- Left Shift: Move down
- Right mouse button: Look around

## Notes

- This is a development version and may contain bugs
- Performance optimizations are still in progress
- The Vulkan renderer is used instead of OpenGL for better performance
