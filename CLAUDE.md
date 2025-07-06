# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

### Building the Project
```bash
# Build the project
./gradlew build

# Clean and build
./gradlew clean build
```

### Running the Application
```bash
# Run the main application (after building)
./run.bat

# Or run directly with Gradle
./gradlew run
```

### Testing
```bash
# Run tests
./gradlew test

# Run specific test classes (these are located in main source tree)
./gradlew run --main-class="it.filippocavallari.cubicworld.MainTestStitcherKt"
./gradlew run --main-class="it.filippocavallari.cubicworld.MinimalTestKt"
./gradlew run --main-class="it.filippocavallari.cubicworld.TerrainGenerationTestKt"
```

## High-Level Architecture

### Core Components

**CubicWorld** is a voxel-based game engine with a hybrid Kotlin/Java architecture:

1. **Java Vulkan Engine Foundation** (`src/main/java/org/vulkanb/`)
   - Low-level Vulkan rendering pipeline
   - Scene management and entity system
   - Camera, lighting, and shadow systems
   - Sound management via OpenAL
   - Window and input handling via GLFW

2. **Kotlin Game Logic** (`src/main/kotlin/it/filippocavallari/cubicworld/`)
   - Voxel world management and chunk system
   - Procedural terrain generation with multiple biomes
   - Texture atlas system for efficient GPU usage
   - JSON-based block model system
   - Integration bridge to Vulkan renderer

3. **Integration Layer** (`src/main/kotlin/.../integration/`)
   - `VulkanIntegration`: Main bridge between Kotlin and Java systems
   - `VulkanChunkMeshBuilder`: Converts voxel data to Vulkan-compatible meshes
   - `DirectionalVulkanChunkMeshBuilder`: Advanced culling-optimized mesh builder
   - `VulkanRenderer`: Custom renderer for voxel content

### Key Systems

**World Generation**
- `WorldGenerator`: Interface for procedural world generation
- `BiodiverseWorldGenerator`: Multi-biome world generation
- Biome generators: Forest, Desert, Mountain, Plains, Taiga, Swamp, Savanna, MagicalForest
- `TerrainGenerator`: Core terrain generation with noise functions

**Chunk Management**
- 4x4 dynamic chunk loading system (16 chunks around player)
- Circular loading pattern prioritizing closest chunks
- Automatic unloading of distant chunks
- Memory-efficient descriptor set management

**Texture System**
- `TextureStitcher`: Combines individual block textures into atlases
- `TextureAtlasLoader`: GPU texture loading
- `TextureRegion`: UV coordinate mapping for atlas sections
- Support for diffuse, normal, and specular maps

**Rendering Optimizations**
- Directional face culling (splits chunks into 6 directional meshes)
- Vulkan descriptor pool management
- Greedy meshing for reduced vertex count
- Frustum culling and occlusion detection

## Development Workflow

### Adding New Block Types
1. Add texture files to `src/main/resources/textures/`
2. Define block in `BlockType.kt`
3. Create JSON model in `src/main/resources/models/` (if needed)
4. Update texture atlas generation in `TextureStitcher`

### Adding New Biomes
1. Extend `AbstractBiomeGenerator` in `src/main/kotlin/.../generators/biome/`
2. Register biome in `BiomeRegistry`
3. Add biome to `WorldGeneratorRegistry` initialization

### Testing Changes
- Use standalone test classes (e.g., `TextureStitcherTest.kt`) to verify specific systems
- Run `MinimalTest.kt` for basic rendering verification
- Use `MainTextureDebugger.kt` to debug texture issues

## Important Configuration

### Memory Settings
- Application requires increased stack size: `-Xss8m -Xmx2g`
- Vulkan descriptor pool limited to 100 sets to prevent crashes

### Chunk Loading Settings
```kotlin
const val RENDER_DISTANCE = 2        // 4x4 grid around player
const val LOAD_THRESHOLD_BLOCKS = 8  // Preload when near chunk edge
const val MAX_CHUNKS_PER_FRAME = 1   // Smooth loading rate
const val UNLOAD_DISTANCE = 3        // Auto-unload distance
```

### Required Dependencies
- Vulkan SDK must be installed on system
- LWJGL 3.4.0 with Vulkan bindings
- JOML for math operations
- Gson for JSON model parsing
- ImGui for debug UI

## Entry Points

- **Main Application**: `Main.kt` (Kotlin) - Primary CubicWorld entry point
- **Legacy Engine**: `Main.java` (Java) - Underlying Vulkan engine foundation
- **Testing**: Various `*Test.kt` files with individual main functions

## Project Structure Notes

- Tests are integrated into main source tree rather than separate test directories
- Shaders are precompiled to `.spv` format for Vulkan
- Texture atlas generation happens at runtime
- Models use Minecraft-compatible JSON format
- Audio files include proper licensing information