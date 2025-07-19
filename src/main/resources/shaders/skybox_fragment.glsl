#version 450

layout(location = 0) in vec3 inTexCoords;

layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outPBR;

layout(set = 2, binding = 0) uniform sampler2D skyboxSampler;

// Skybox texture layout:
// +----+----+----+
// | -Y | +Y | -X |  Row 0 (bottom, top, left)
// +----+----+----+
// | +Z | -Z | +X |  Row 1 (front, back, right)  
// +----+----+----+

vec2 mapCubeToTexture(vec3 coords) {
    vec3 absCoords = abs(coords);
    vec2 uv;
    int face;
    
    // Determine which face of the cube we're sampling
    if (absCoords.x >= absCoords.y && absCoords.x >= absCoords.z) {
        // X face (left or right)
        if (coords.x > 0.0) {
            // +X face (right) - position (2, 1)
            face = 5;
            uv = vec2(-coords.z, coords.y);
        } else {
            // -X face (left) - position (0, 2)  
            face = 2;
            uv = vec2(coords.z, coords.y);
        }
    } else if (absCoords.y >= absCoords.z) {
        // Y face (top or bottom)
        if (coords.y > 0.0) {
            // +Y face (top) - position (1, 0)
            face = 1;
            uv = vec2(coords.x, -coords.z);
        } else {
            // -Y face (bottom) - position (0, 0)
            face = 0;
            uv = vec2(coords.x, coords.z);
        }
    } else {
        // Z face (front or back)
        if (coords.z > 0.0) {
            // +Z face (front) - position (0, 1)
            face = 3;
            uv = vec2(coords.x, coords.y);
        } else {
            // -Z face (back) - position (1, 1)
            face = 4;
            uv = vec2(-coords.x, coords.y);
        }
    }
    
    // Normalize UV coordinates to [0, 1] range
    uv = (uv + 1.0) * 0.5;
    
    // Map to correct position in the texture atlas
    // Each face is 1/3 width and 1/2 height
    float faceWidth = 1.0 / 3.0;
    float faceHeight = 1.0 / 2.0;
    
    vec2 finalUV;
    
    if (face == 0) {
        // -Y (bottom) - position (0, 0)
        finalUV = vec2(uv.x * faceWidth, uv.y * faceHeight);
    } else if (face == 1) {
        // +Y (top) - position (1, 0)
        finalUV = vec2((1.0 + uv.x) * faceWidth, uv.y * faceHeight);
    } else if (face == 2) {
        // -X (left) - position (2, 0)
        finalUV = vec2((2.0 + uv.x) * faceWidth, uv.y * faceHeight);
    } else if (face == 3) {
        // +Z (front) - position (0, 1)
        finalUV = vec2(uv.x * faceWidth, (1.0 + uv.y) * faceHeight);
    } else if (face == 4) {
        // -Z (back) - position (1, 1)
        finalUV = vec2((1.0 + uv.x) * faceWidth, (1.0 + uv.y) * faceHeight);
    } else {
        // +X (right) - position (2, 1)
        finalUV = vec2((2.0 + uv.x) * faceWidth, (1.0 + uv.y) * faceHeight);
    }
    
    return finalUV;
}

void main() {
    vec2 texCoords = mapCubeToTexture(normalize(inTexCoords));
    vec4 skyboxColor = texture(skyboxSampler, texCoords);
    
    // Write to G-buffer
    outAlbedo = skyboxColor;
    outNormal = vec4(0.5, 0.5, 1.0, 1.0); // Default normal pointing up in G-buffer format
    outPBR = vec4(1.0, 0.0, 0.0, 1.0); // No roughness, no metallic, full AO
}