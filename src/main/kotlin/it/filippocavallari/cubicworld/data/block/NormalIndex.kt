package it.filippocavallari.cubicworld.data.block

/**
 * Compact representation of cube face normals using byte indices.
 * This reduces normal storage from 12 bytes (3 floats) to 1 byte per vertex.
 * 
 * The actual normal vectors are computed in the vertex shader using a lookup table.
 */
enum class NormalIndex(val index: Byte, val vector: FloatArray) {
    POSITIVE_X(0, floatArrayOf(1.0f, 0.0f, 0.0f)),   // East
    NEGATIVE_X(1, floatArrayOf(-1.0f, 0.0f, 0.0f)),  // West
    POSITIVE_Y(2, floatArrayOf(0.0f, 1.0f, 0.0f)),   // Up
    NEGATIVE_Y(3, floatArrayOf(0.0f, -1.0f, 0.0f)),  // Down
    POSITIVE_Z(4, floatArrayOf(0.0f, 0.0f, 1.0f)),   // South
    NEGATIVE_Z(5, floatArrayOf(0.0f, 0.0f, -1.0f));  // North

    companion object {
        /**
         * Get the normal index for a given face direction
         */
        fun fromFaceDirection(face: FaceDirection): NormalIndex {
            return when (face) {
                FaceDirection.UP -> POSITIVE_Y
                FaceDirection.DOWN -> NEGATIVE_Y
                FaceDirection.NORTH -> NEGATIVE_Z
                FaceDirection.SOUTH -> POSITIVE_Z
                FaceDirection.EAST -> POSITIVE_X
                FaceDirection.WEST -> NEGATIVE_X
            }
        }

        /**
         * Get the normal vector for a given index (for debugging/fallback)
         */
        fun getVector(index: Byte): FloatArray {
            return when (index.toInt()) {
                0 -> POSITIVE_X.vector
                1 -> NEGATIVE_X.vector
                2 -> POSITIVE_Y.vector
                3 -> NEGATIVE_Y.vector
                4 -> POSITIVE_Z.vector
                5 -> NEGATIVE_Z.vector
                else -> POSITIVE_Y.vector // Default fallback
            }
        }

        /**
         * Generate the GLSL lookup table code for the vertex shader
         */
        fun generateGLSLLookupTable(): String {
            return """
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
            
            // Compute tangent and bitangent from normal
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
            """.trimIndent()
        }
    }
}