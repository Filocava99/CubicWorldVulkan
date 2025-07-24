package org.vulkanb.eng.scene;

import org.joml.*;

/**
 * Frustum culling implementation for efficient rendering.
 * Extracts frustum planes from view-projection matrix and tests entities for visibility.
 */
public class FrustumCuller {
    
    private static final int NUM_PLANES = 6;
    private static final int PLANE_LEFT = 0;
    private static final int PLANE_RIGHT = 1;
    private static final int PLANE_TOP = 2;
    private static final int PLANE_BOTTOM = 3;
    private static final int PLANE_NEAR = 4;
    private static final int PLANE_FAR = 5;
    
    private final Vector4f[] frustumPlanes = new Vector4f[NUM_PLANES];
    private final Matrix4f viewProjectionMatrix = new Matrix4f();
    
    public FrustumCuller() {
        for (int i = 0; i < NUM_PLANES; i++) {
            frustumPlanes[i] = new Vector4f();
        }
    }
    
    /**
     * Update frustum planes from camera and projection matrices
     */
    public void updateFrustum(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        // Calculate view-projection matrix
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix);
        
        // Extract frustum planes from the view-projection matrix
        // Left plane
        frustumPlanes[PLANE_LEFT].set(
            viewProjectionMatrix.m30() + viewProjectionMatrix.m00(),
            viewProjectionMatrix.m31() + viewProjectionMatrix.m01(),
            viewProjectionMatrix.m32() + viewProjectionMatrix.m02(),
            viewProjectionMatrix.m33() + viewProjectionMatrix.m03()
        );
        
        // Right plane
        frustumPlanes[PLANE_RIGHT].set(
            viewProjectionMatrix.m30() - viewProjectionMatrix.m00(),
            viewProjectionMatrix.m31() - viewProjectionMatrix.m01(),
            viewProjectionMatrix.m32() - viewProjectionMatrix.m02(),
            viewProjectionMatrix.m33() - viewProjectionMatrix.m03()
        );
        
        // Top plane
        frustumPlanes[PLANE_TOP].set(
            viewProjectionMatrix.m30() - viewProjectionMatrix.m10(),
            viewProjectionMatrix.m31() - viewProjectionMatrix.m11(),
            viewProjectionMatrix.m32() - viewProjectionMatrix.m12(),
            viewProjectionMatrix.m33() - viewProjectionMatrix.m13()
        );
        
        // Bottom plane
        frustumPlanes[PLANE_BOTTOM].set(
            viewProjectionMatrix.m30() + viewProjectionMatrix.m10(),
            viewProjectionMatrix.m31() + viewProjectionMatrix.m11(),
            viewProjectionMatrix.m32() + viewProjectionMatrix.m12(),
            viewProjectionMatrix.m33() + viewProjectionMatrix.m13()
        );
        
        // Near plane
        frustumPlanes[PLANE_NEAR].set(
            viewProjectionMatrix.m30() + viewProjectionMatrix.m20(),
            viewProjectionMatrix.m31() + viewProjectionMatrix.m21(),
            viewProjectionMatrix.m32() + viewProjectionMatrix.m22(),
            viewProjectionMatrix.m33() + viewProjectionMatrix.m23()
        );
        
        // Far plane
        frustumPlanes[PLANE_FAR].set(
            viewProjectionMatrix.m30() - viewProjectionMatrix.m20(),
            viewProjectionMatrix.m31() - viewProjectionMatrix.m21(),
            viewProjectionMatrix.m32() - viewProjectionMatrix.m22(),
            viewProjectionMatrix.m33() - viewProjectionMatrix.m23()
        );
        
        // Normalize all planes
        for (Vector4f plane : frustumPlanes) {
            float length = (float) java.lang.Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
            if (length > 0.0f) {
                plane.div(length);
            }
        }
    }
    
    /**
     * Test if a point is inside the frustum
     */
    public boolean isPointInFrustum(float x, float y, float z) {
        for (Vector4f plane : frustumPlanes) {
            if (plane.x * x + plane.y * y + plane.z * z + plane.w < 0.0f) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test if a sphere is visible in the frustum
     */
    public boolean isSphereInFrustum(float centerX, float centerY, float centerZ, float radius) {
        for (Vector4f plane : frustumPlanes) {
            float distance = plane.x * centerX + plane.y * centerY + plane.z * centerZ + plane.w;
            if (distance < -radius) {
                return false; // Sphere is completely outside this plane
            }
        }
        return true; // Sphere intersects or is inside the frustum
    }
    
    /**
     * Test if an axis-aligned bounding box (AABB) is visible in the frustum
     */
    public boolean isAABBInFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (Vector4f plane : frustumPlanes) {
            // Find the positive vertex (farthest in the direction of the plane normal)
            float positiveVertexX = plane.x >= 0 ? maxX : minX;
            float positiveVertexY = plane.y >= 0 ? maxY : minY;
            float positiveVertexZ = plane.z >= 0 ? maxZ : minZ;
            
            // Test the positive vertex against the plane
            float distance = plane.x * positiveVertexX + plane.y * positiveVertexY + plane.z * positiveVertexZ + plane.w;
            if (distance < 0.0f) {
                return false; // AABB is completely outside this plane
            }
        }
        return true; // AABB intersects or is inside the frustum
    }
    
    /**
     * Test if a chunk (16x16x16 cube) is visible in the frustum
     */
    public boolean isChunkInFrustum(float chunkX, float chunkY, float chunkZ, float chunkSize) {
        float minX = chunkX * chunkSize;
        float minY = chunkY * chunkSize;
        float minZ = chunkZ * chunkSize;
        float maxX = minX + chunkSize;
        float maxY = minY + chunkSize;
        float maxZ = minZ + chunkSize;
        
        return isAABBInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Enhanced chunk visibility test with world coordinates
     */
    public boolean isChunkInFrustum(float worldMinX, float worldMinY, float worldMinZ, 
                                  float worldMaxX, float worldMaxY, float worldMaxZ) {
        return isAABBInFrustum(worldMinX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ);
    }
    
    /**
     * Get the distance from a point to a frustum plane (for sorting)
     */
    public float getDistanceToNearPlane(float x, float y, float z) {
        Vector4f nearPlane = frustumPlanes[PLANE_NEAR];
        return nearPlane.x * x + nearPlane.y * y + nearPlane.z * z + nearPlane.w;
    }
}