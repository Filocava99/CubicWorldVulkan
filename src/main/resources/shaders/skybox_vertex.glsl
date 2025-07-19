#version 450

layout(location = 0) in vec3 entityPos;

layout(location = 0) out vec3 outTexCoords;

out gl_PerVertex
{
    vec4 gl_Position;
};

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 projectionMatrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 viewMatrix;
} viewUniform;

void main()
{
    // Remove translation from view matrix to keep skybox centered on camera
    mat4 skyboxViewMatrix = mat4(mat3(viewUniform.viewMatrix));
    
    // Use vertex position as texture coordinates for cubemap sampling
    outTexCoords = entityPos;
    
    // Transform to clip space
    vec4 pos = projUniform.projectionMatrix * skyboxViewMatrix * vec4(entityPos, 1.0);
    
    // Ensure skybox is always at maximum depth
    gl_Position = pos.xyww;
}