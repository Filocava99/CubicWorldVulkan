# Model Format Documentation

Our model format is inspired by Minecraft's JSON model system but expanded to support additional features like normal mapping, specular mapping, and emissive lighting.

## Basic Structure

```json
{
  "parent": "block/block",
  "ambientocclusion": true,
  "textures": {
    "particle": "block/stone",
    "all": "block/stone"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down": { "texture": "#all", "cullface": "down" },
        "up": { "texture": "#all", "cullface": "up" },
        "north": { "texture": "#all", "cullface": "north" },
        "south": { "texture": "#all", "cullface": "south" },
        "west": { "texture": "#all", "cullface": "west" },
        "east": { "texture": "#all", "cullface": "east" }
      }
    }
  ],
  "display": {
    "gui": {
      "rotation": [30, 225, 0],
      "translation": [0, 0, 0],
      "scale": [0.625, 0.625, 0.625]
    }
  }
}
```

## Top-Level Properties

- `parent`: References another model that this model extends
- `ambientocclusion`: Whether to apply ambient occlusion shading (true/false)
- `textures`: Map of texture variables to texture file paths
- `elements`: Array of cuboids that make up the model
- `display`: Display transformations for different contexts (GUI, hand, etc.)

## Texture References

Texture paths are relative to your textures folder:
- `block/stone` refers to a texture in the block subfolder
- `#all` refers to a texture variable defined in the textures map
- Normal maps will automatically be loaded from the texture path with "_n" appended
- Specular maps will automatically be loaded from the texture path with "_s" appended

## Elements (Cuboids)

Each element is a cuboid with the following properties:
- `from`: Starting coordinates [x, y, z]
- `to`: Ending coordinates [x, y, z]
- `rotation`: Optional rotation with properties:
  - `origin`: Rotation origin point [x, y, z]
  - `axis`: Rotation axis ("x", "y", or "z")
  - `angle`: Rotation angle in degrees
  - `rescale`: Whether to rescale faces
- `shade`: Whether the faces cast shadows (true/false)
- `faces`: Face definitions (up, down, north, south, east, west)

## Face Properties

Each face can have the following properties:
- `texture`: Texture reference (must start with #)
- `uv`: UV coordinates [u1, v1, u2, v2] (defaults to full texture)
- `rotation`: Texture rotation in 90-degree increments (0, 90, 180, 270)
- `cullface`: Direction for culling ("up", "down", etc.)
- `tintindex`: Index for tinting (-1 for no tint)
- `doubleSided`: Whether the face renders from both sides (true/false)
- `emissive`: Emission intensity (0.0-1.0, 0 for non-emissive)

## Display Positions

The `display` section can have the following contexts:
- `gui`: In the GUI (inventory, etc.)
- `ground`: Dropped on the ground
- `head`: Worn on the head
- `fixed`: Fixed position (item frames)
- `thirdperson_righthand`: Third-person right hand
- `thirdperson_lefthand`: Third-person left hand
- `firstperson_righthand`: First-person right hand
- `firstperson_lefthand`: First-person left hand

Each context has the following transformations:
- `rotation`: Rotation in degrees [x, y, z]
- `translation`: Translation [x, y, z]
- `scale`: Scale [x, y, z] (1.0 is normal size)

## Example Models

### Basic Block
```json
{
  "parent": "block/block",
  "textures": {
    "particle": "block/stone",
    "all": "block/stone"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down": { "texture": "#all", "cullface": "down" },
        "up": { "texture": "#all", "cullface": "up" },
        "north": { "texture": "#all", "cullface": "north" },
        "south": { "texture": "#all", "cullface": "south" },
        "west": { "texture": "#all", "cullface": "west" },
        "east": { "texture": "#all", "cullface": "east" }
      }
    }
  ]
}
```

### Transparent Block (Glass)
```json
{
  "parent": "block/block",
  "ambientocclusion": false,
  "textures": {
    "particle": "block/glass",
    "all": "block/glass"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down": { "texture": "#all", "cullface": "down", "doubleSided": true },
        "up": { "texture": "#all", "cullface": "up", "doubleSided": true },
        "north": { "texture": "#all", "cullface": "north", "doubleSided": true },
        "south": { "texture": "#all", "cullface": "south", "doubleSided": true },
        "west": { "texture": "#all", "cullface": "west", "doubleSided": true },
        "east": { "texture": "#all", "cullface": "east", "doubleSided": true }
      }
    }
  ]
}
```

### Emissive Block (Glowing)
```json
{
  "parent": "block/block",
  "textures": {
    "particle": "block/redstone_lamp_on",
    "all": "block/redstone_lamp_on"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down": { "texture": "#all", "cullface": "down", "emissive": 1.0 },
        "up": { "texture": "#all", "cullface": "up", "emissive": 1.0 },
        "north": { "texture": "#all", "cullface": "north", "emissive": 1.0 },
        "south": { "texture": "#all", "cullface": "south", "emissive": 1.0 },
        "west": { "texture": "#all", "cullface": "west", "emissive": 1.0 },
        "east": { "texture": "#all", "cullface": "east", "emissive": 1.0 }
      }
    }
  ]
}
```

### Tinted Block (Grass)
```json
{
  "parent": "block/block",
  "textures": {
    "particle": "block/grass_block_side",
    "bottom": "block/dirt",
    "top": "block/grass_block_top",
    "side": "block/grass_block_side",
    "overlay": "block/grass_block_side_overlay"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down": { "texture": "#bottom", "cullface": "down" },
        "up": { "texture": "#top", "cullface": "up", "tintindex": 0 },
        "north": { "texture": "#side", "cullface": "north" },
        "south": { "texture": "#side", "cullface": "south" },
        "west": { "texture": "#side", "cullface": "west" },
        "east": { "texture": "#side", "cullface": "east" }
      }
    },
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "north": { "texture": "#overlay", "cullface": "north", "tintindex": 0 },
        "south": { "texture": "#overlay", "cullface": "south", "tintindex": 0 },
        "west": { "texture": "#overlay", "cullface": "west", "tintindex": 0 },
        "east": { "texture": "#overlay", "cullface": "east", "tintindex": 0 }
      }
    }
  ]
}
```
