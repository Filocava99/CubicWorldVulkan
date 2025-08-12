#version 450

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

layout(location = 0) in vec3 inNormal;
layout(location = 1) in vec3 inTangent;
layout(location = 2) in vec3 inBitangent;
layout(location = 3) in vec2 inTextCoords;
layout(location = 4) flat in uint inMatIdx;
layout(location = 5) in vec3 inWorldPos;
layout(location = 6) in vec3 inViewVec;

layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outPBR;

struct Material {
    vec4 diffuseColor;
    int textureIdx;
    int normalMapIdx;
    int metalRoughMapIdx;
    float roughnessFactor;
    float metallicFactor;
};

layout (std430, set=2, binding=0) readonly buffer srcBuf {
    Material data[];
} materialsBuf;
layout(set = 3, binding = 0) uniform sampler2D textSampler[MAX_TEXTURES];

layout(push_constant) uniform PushConstants {
    float time;
    int isWater;
} pc;

// 2D Noise function (simple version)
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

vec4 calcAlbedo(Material material) {
    vec4 albedo = material.diffuseColor;
    if (material.textureIdx >= 0) {
        albedo = texture(textSampler[material.textureIdx], inTextCoords);
    }
    return albedo;
}

vec3 calcNormal(Material material, vec3 normal, vec2 textCoords, mat3 TBN) {
    vec3 newNormal = normal;
    if (material.normalMapIdx >= 0) {
        newNormal = texture(textSampler[material.normalMapIdx], textCoords).rgb;
        newNormal = normalize(newNormal * 2.0 - 1.0);
        newNormal = normalize(TBN * newNormal);
    }
    return newNormal;
}

vec2 calcRoughnessMetallicFactor(Material material, vec2 textCoords) {
    float roughnessFactor = 0.0f;
    float metallicFactor = 0.0f;
    if (material.metalRoughMapIdx >= 0) {
        vec4 metRoughValue = texture(textSampler[material.metalRoughMapIdx], textCoords);
        roughnessFactor = metRoughValue.g;
        metallicFactor = metRoughValue.b;
    } else {
        roughnessFactor = material.roughnessFactor;
        metallicFactor = material.metallicFactor;
    }

    return vec2(roughnessFactor, metallicFactor);
}

void main()
{
    if (pc.isWater == 1) {
        vec2 waveSpeed = vec2(0.1, 0.05);
        vec2 waveFrequency = vec2(0.5, 1.0);
        float waveAmplitude = 0.1;

        float noiseVal1 = noise(inWorldPos.xz * waveFrequency.x + pc.time * waveSpeed.x) * waveAmplitude;
        float noiseVal2 = noise(inWorldPos.xz * waveFrequency.y - pc.time * waveSpeed.y) * waveAmplitude;

        vec3 distortedNormal = normalize(inNormal + vec3(noiseVal1, 0.0, noiseVal2));

        vec3 viewDir = normalize(inViewVec);
        float fresnel = pow(1.0 - max(0.0, dot(viewDir, distortedNormal)), 3.0);

        vec4 refractionColor = vec4(0.2, 0.5, 0.7, 0.8);
        vec4 reflectionColor = vec4(0.8, 0.9, 1.0, 1.0);

        outAlbedo = mix(refractionColor, reflectionColor, fresnel);
        outNormal = vec4(0.5 * distortedNormal + 0.5, 1.0);
        outPBR = vec4(1.0, 0.1, 0.0, 1.0); // ao, roughness, metallic
    } else {
        Material material = materialsBuf.data[inMatIdx];
        outAlbedo = calcAlbedo(material);

        // Hack to avoid transparent PBR artifacts
        if (outAlbedo.a < 0.5) {
            discard;
        }

        mat3 TBN = mat3(inTangent, inBitangent, inNormal);
        vec3 newNormal = calcNormal(material, inNormal, inTextCoords, TBN);
        // Transform normals from [-1, 1] to [0, 1]
        outNormal = vec4(0.5 * newNormal + 0.5, 1.0);

        float ao = 0.5f;
        vec2 roughmetfactor = calcRoughnessMetallicFactor(material, inTextCoords);

        outPBR = vec4(ao, roughmetfactor.x, roughmetfactor.y, 1.0f);
    }
}