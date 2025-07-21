#version 450

layout(location = 0) in vec3 inTexCoords;

layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outPBR;

layout(set = 2, binding = 0) uniform sampler2D skyboxSampler;

// Skybox texture layout:
// +----+----+----+
// | -Y | +Y | -X |  Row 0 (bottom, top, west)
// +----+----+----+
// | +Z | -Z | +X |  Row 1 (north, east, south)  
// +----+----+----+

vec2 mapCubeToTexture(vec3 coords) {
    vec3 absCoords = abs(coords);
    vec2 uv;
    vec2 finalUV;
    
    // Each face is 1/3 width and 1/2 height
    float faceWidth = 1.0 / 3.0;
    float faceHeight = 1.0 / 2.0;
    
    // Determine which face has the largest absolute coordinate
    if (absCoords.y >= absCoords.x && absCoords.y >= absCoords.z) {
        // Y face (top or bottom)
        if (coords.y > 0.0) {
            // +Y face (top) - position (1, 0)
            uv = vec2(coords.x / absCoords.y, coords.z / absCoords.y);
            uv = (uv + 1.0) * 0.5;
            uv = vec2(1.0 - uv.y, uv.x);  // rotate +Y face UV 270°
            finalUV = vec2((1.0 + uv.x) * faceWidth, uv.y * faceHeight);
        } else {
            // -Y face (bottom) - position (0, 0)
            uv = vec2(coords.x / absCoords.y, coords.z / absCoords.y);
            uv = (uv + 1.0) * 0.5;
            finalUV = vec2(uv.x * faceWidth, uv.y * faceHeight);
        }
    } else if (absCoords.x >= absCoords.z) {
        // X face (east or west)
        if (coords.x > 0.0) {
            // +X face (east) - position (1, 1)
            uv = vec2(-coords.z / absCoords.x, -coords.y / absCoords.x);
            uv = (uv + 1.0) * 0.5;
            finalUV = vec2((1.0 + uv.x) * faceWidth, (1.0 + uv.y) * faceHeight);
        } else {
            // -X face (west) - position (2, 0)
            uv = vec2(coords.z / absCoords.x, -coords.y / absCoords.x);
            uv = (uv + 1.0) * 0.5;
            finalUV = vec2((2.0 + uv.x) * faceWidth, uv.y * faceHeight);
        }
    } else {
        // Z face (north or south)
        if (coords.z > 0.0) {
            // +Z face (north) - position (0, 1)
            uv = vec2(coords.x / absCoords.z, -coords.y / absCoords.z);
            uv = (uv + 1.0) * 0.5;
            finalUV = vec2(uv.x * faceWidth, (1.0 + uv.y) * faceHeight);
        } else {
            // -Z face (south) - position (2, 1)
            uv = vec2(-coords.x / absCoords.z, -coords.y / absCoords.z);
            uv = (uv + 1.0) * 0.5;
            finalUV = vec2((2.0 + uv.x) * faceWidth, (1.0 + uv.y) * faceHeight);
        }
    }
    
    return finalUV;
}

void main() {
    vec2 texCoords = mapCubeToTexture(normalize(inTexCoords));
    vec4 skyboxColor = texture(skyboxSampler, texCoords);
    skyboxColor = clamp(skyboxColor * 1.5, 0.0, 1.0);  // increase skybox brightness

    // Overlay colored marker squares on side faces
    vec2 faceUV = fract(texCoords * vec2(3.0, 2.0));
    ivec2 cell = ivec2(floor(texCoords * vec2(3.0, 2.0)));
    float markerSize = 0.1;
    bool inMarker = faceUV.x > 0.5 - markerSize * 0.5 && faceUV.x < 0.5 + markerSize * 0.5
                  && faceUV.y > 0.5 - markerSize * 0.5 && faceUV.y < 0.5 + markerSize * 0.5;
    if (inMarker) {
        if (cell.y == 1 && cell.x == 0) {
            // +Z face (north) - red
            skyboxColor = vec4(1.0, 0.0, 0.0, 1.0);
        } else if (cell.y == 1 && cell.x == 1) {
            // -Z face (east) - green
            skyboxColor = vec4(0.0, 1.0, 0.0, 1.0);
        } else if (cell.y == 1 && cell.x == 2) {
            // +X face (south) - blue
            skyboxColor = vec4(0.0, 0.0, 1.0, 1.0);
        } else if (cell.y == 0 && cell.x == 2) {
            // -X face (west) - yellow
            skyboxColor = vec4(1.0, 1.0, 0.0, 1.0);
        }
    }

    // Write to G-buffer
    outAlbedo = skyboxColor;
    outNormal = vec4(0.5, 0.5, 1.0, 1.0); // Default normal pointing up in G-buffer format
    outPBR = vec4(1.0, 0.0, 0.0, 1.0); // No roughness, no metallic, full AO
}