#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
// entityTangent at location 2 removed
// entityBitangent at location 3 removed
layout(location = 4) in vec2 entityTextCoords; // Location remains 4

// Instanced attributes
layout (location = 5) in mat4 entityModelMatrix;
layout (location = 9) in uint entityMatIdx;

layout(location = 0) out vec3 outNormal;
// outTangent at location 1 removed
// outBitangent at location 2 removed
layout(location = 1) out vec2 outTextCoords; // Was location 3, now 1
layout(location = 2) flat out uint outMatIdx; // Was location 4, now 2

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
    mat4 modelViewMatrix = viewUniform.viewMatrix * entityModelMatrix;
    outNormal     = normalize(modelViewMatrix * vec4(entityNormal, 0)).xyz;
    outTextCoords = entityTextCoords;
    outMatIdx     = entityMatIdx;
    gl_Position   = projUniform.projectionMatrix * modelViewMatrix * vec4(entityPos * 127.0, 1);
}
