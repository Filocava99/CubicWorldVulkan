#version 450
#extension GL_ARB_separate_shader_objects : enable

// Input from vertex shader
layout(location = 0) in vec3 fragPosition;
layout(location = 1) in vec3 fragNormal;
layout(location = 2) in vec2 fragTexCoord;

// Output
layout(location = 0) out vec4 outColor;

// Texture sampler
layout(binding = 1) uniform sampler2D texSampler;

// Light properties
layout(binding = 2) uniform LightProperties {
    vec3 lightPosition;
    vec3 lightColor;
    vec3 viewPosition;
    float ambientStrength;
    float specularStrength;
    float shininess;
} light;

void main() {
    // Ambient
    vec3 ambient = light.ambientStrength * light.lightColor;
    
    // Diffuse
    vec3 normal = normalize(fragNormal);
    vec3 lightDir = normalize(light.lightPosition - fragPosition);
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * light.lightColor;
    
    // Specular
    vec3 viewDir = normalize(light.viewPosition - fragPosition);
    vec3 reflectDir = reflect(-lightDir, normal);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), light.shininess);
    vec3 specular = light.specularStrength * spec * light.lightColor;
    
    // Combined lighting
    vec3 lighting = ambient + diffuse + specular;
    
    // Sample texture
    vec4 texColor = texture(texSampler, fragTexCoord);
    
    // Final color
    outColor = vec4(lighting * texColor.rgb, texColor.a);
}
