#version 450

// Optimized vertex inputs using compact data types
layout(location = 0) in ivec3 entityPosShort;        // 3 signed shorts (6 bytes)
layout(location = 1) in uint entityNormalIndex;      // 1 unsigned byte (1 byte)
layout(location = 2) in vec2 entityTextCoords;       // 2 normalized shorts (4 bytes) - auto-converted by Vulkan

// Instanced attributes (unchanged)
layout (location = 5) in mat4 entityModelMatrix;
layout (location = 9) in uint entityMatIdx;

// Outputs (unchanged)
layout(location = 0) out vec3 outNormal;
layout(location = 1) out vec3 outTangent;
layout(location = 2) out vec3 outBitangent;
layout(location = 3) out vec2 outTextCoords;
layout(location = 4) flat out uint outMatIdx;

out gl_PerVertex
{
    vec4 gl_Position;
};

// Uniforms (unchanged)
layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 projectionMatrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 viewMatrix;
} viewUniform;

// Normal lookup table for cube faces
vec3 getNormal(uint normalIndex) {
    switch(normalIndex) {
        case 0u: return vec3(1.0, 0.0, 0.0);   // +X (East)
        case 1u: return vec3(-1.0, 0.0, 0.0);  // -X (West)
        case 2u: return vec3(0.0, 1.0, 0.0);   // +Y (Up)
        case 3u: return vec3(0.0, -1.0, 0.0);  // -Y (Down)
        case 4u: return vec3(0.0, 0.0, 1.0);   // +Z (South)
        case 5u: return vec3(0.0, 0.0, -1.0);  // -Z (North)
        default: return vec3(0.0, 1.0, 0.0);   // Default to up
    }
}

// Compute tangent and bitangent from normal for cube faces
void computeTangentSpace(vec3 normal, out vec3 tangent, out vec3 bitangent) {
    // For cube faces, we can use simple cross products
    if (abs(normal.y) > 0.9) {
        // Top/bottom faces: use X as tangent direction
        tangent = vec3(1.0, 0.0, 0.0);
    } else {
        // Side faces: use Y as tangent direction  
        tangent = vec3(0.0, 1.0, 0.0);
    }
    
    // Ensure tangent is perpendicular to normal
    tangent = normalize(tangent - dot(tangent, normal) * normal);
    
    // Compute bitangent
    bitangent = cross(normal, tangent);
}

void main()
{
    // Convert short positions to float world coordinates
    vec3 entityPos = vec3(entityPosShort);
    
    // Lookup normal from index
    vec3 entityNormal = getNormal(entityNormalIndex);
    
    // Compute tangent space from normal
    vec3 entityTangent, entityBitangent;
    computeTangentSpace(entityNormal, entityTangent, entityBitangent);
    
    // Transform to view space (same as original shader)
    mat4 modelViewMatrix = viewUniform.viewMatrix * entityModelMatrix;
    outNormal     = normalize(modelViewMatrix * vec4(entityNormal, 0)).xyz;
    outTangent    = normalize(modelViewMatrix * vec4(entityTangent, 0)).xyz;
    outBitangent  = normalize(modelViewMatrix * vec4(entityBitangent, 0)).xyz;
    outTextCoords = entityTextCoords;  // Already normalized by Vulkan
    outMatIdx     = entityMatIdx;
    
    // Project to clip space
    gl_Position   = projUniform.projectionMatrix * modelViewMatrix * vec4(entityPos, 1);
}