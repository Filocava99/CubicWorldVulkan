# CubicWorld Biome Generator System

This package contains the implementation of an advanced biome-based world generator inspired by Minecraft and popular biome mods like "Biomes O' Plenty" and "Oh The Biomes You'll Go".

## Architecture

The biome generation system follows a modular architecture:

1. **BiodiverseWorldGenerator**: The main world generator that coordinates biome distribution and terrain generation.

2. **BiomeGenerator Interface**: The core interface that all biome generators implement, providing a standard API for generating biome-specific terrain.

3. **AbstractBiomeGenerator**: A base implementation with common functionality that most biomes extend.

4. **BiomeRegistry**: A centralized registry for all biome types, allowing easy retrieval and climate-based biome selection.

5. **NoiseFactory**: A utility class providing various noise functions used for terrain generation.

## Generation Process

The world generation process follows these steps:

1. **Continent Generation**: Creates large-scale terrain features using layered noise.

2. **Biome Distribution**: Uses Worley (cellular) noise to distribute biomes naturally, with smooth transitions between them.

3. **Terrain Shaping**: Each biome modifies the base terrain according to its characteristics.

4. **Surface Generation**: Adds appropriate blocks for the surface layer (grass, sand, etc.).

5. **Cave Generation**: Carves out underground cave systems using 3D noise.

6. **Feature Placement**: Adds biome-specific features like trees, plants, and structures.

7. **Ore Generation**: Places mineral deposits throughout the underground.

## Biome Types

The system comes with several biome types:

- **Forest**: Dense oak and birch trees with gentle hills.
- **Desert**: Sandy dunes with occasional cacti.
- **Mountains**: Tall peaks with stone outcrops and sparse vegetation.
- **Magical Forest**: A mod-inspired biome with fantastical features.

## Usage

To use the biome generator system, simply create an instance of `BiodiverseWorldGenerator` and pass it to the `World` constructor:

```kotlin
// Initialize the world generator registry
WorldGeneratorRegistry.initialize()

// Create a biodiverse world generator with random seed
val worldGenerator = WorldGeneratorRegistry.create("biodiverse", seed)

// Create the world with this generator
val world = World(worldGenerator)
```

## Adding New Biomes

To add a new biome:

1. Create a new class extending `AbstractBiomeGenerator`.
2. Implement the required methods for terrain shaping and decoration.
3. Register your biome in the `BiomeRegistry` during initialization.

Example of a new biome registration:

```kotlin
// Create a new biome instance
val newBiome = MyCustomBiome()

// Register it with the system
BiomeRegistry.register(newBiome)
```

## Performance Considerations

- The system uses caching for biome distribution to avoid recalculating values.
- Chunk generation is optimized to generate contiguous biome regions together.
- The biome blend system ensures smooth transitions without performance impact.

## Future Enhancements

- Support for structures spanning multiple chunks
- Enhanced cave generation with different types of cave systems
- Support for custom ore distribution per biome
- Dynamic biome transitions based on altitude