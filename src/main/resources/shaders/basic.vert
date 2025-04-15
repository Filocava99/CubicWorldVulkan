#version 450
#extension GL_ARB_separate_shader_objects : enable

// Input vertex data
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;

// Output to fragment shader
layout(location = 0) out vec3 fragPosition;
layout(location = 1) out vec3 fragNormal;
layout(location = 2) out vec2 fragTexCoord;

// Uniform buffer
layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

void main() {
    // Transform vertex position
    vec4 worldPosition = ubo.model * vec4(inPosition, 1.0);
    gl_Position = ubo.proj * ubo.view * worldPosition;
    
    // Pass data to fragment shader
    fragPosition = worldPosition.xyz;
    fragNormal = mat3(transpose(inverse(ubo.model))) * inNormal;
    fragTexCoord = inTexCoord;
}
