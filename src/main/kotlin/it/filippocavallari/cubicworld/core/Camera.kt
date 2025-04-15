package it.filippocavallari.cubicworld.core

import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Represents a camera in the 3D world.
 * Provides view and projection matrices for rendering.
 */
class Camera : Component() {
    
    // Camera properties
    private val front = Vector3f(0f, 0f, -1f)
    private val up = Vector3f(0f, 1f, 0f)
    private val right = Vector3f(1f, 0f, 0f)
    
    // View and projection matrices
    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()
    
    // Camera parameters
    private var fov = 45f
    private var aspectRatio = 16f / 9f
    private var nearPlane = 0.1f
    private var farPlane = 1000f
    
    // Matrices are marked as dirty when camera properties change
    private var viewMatrixDirty = true
    private var projectionMatrixDirty = true
    
    /**
     * Gets the view matrix.
     * Recalculates if necessary.
     * 
     * @return The view matrix
     */
    fun getViewMatrix(): Matrix4f {
        if (viewMatrixDirty) {
            updateViewMatrix()
        }
        return viewMatrix
    }
    
    /**
     * Gets the projection matrix.
     * Recalculates if necessary.
     * 
     * @return The projection matrix
     */
    fun getProjectionMatrix(): Matrix4f {
        if (projectionMatrixDirty) {
            updateProjectionMatrix()
        }
        return projectionMatrix
    }
    
    /**
     * Sets the camera's perspective parameters.
     * 
     * @param fov Field of view in degrees
     * @param aspectRatio Aspect ratio (width / height)
     * @param nearPlane Near clipping plane
     * @param farPlane Far clipping plane
     */
    fun setPerspective(fov: Float, aspectRatio: Float, nearPlane: Float, farPlane: Float) {
        this.fov = fov
        this.aspectRatio = aspectRatio
        this.nearPlane = nearPlane
        this.farPlane = farPlane
        projectionMatrixDirty = true
    }
    
    /**
     * Sets the camera's aspect ratio.
     * Useful when the window is resized.
     * 
     * @param aspectRatio The new aspect ratio
     */
    fun setAspectRatio(aspectRatio: Float) {
        this.aspectRatio = aspectRatio
        projectionMatrixDirty = true
    }
    
    /**
     * Looks at a specific point in the world.
     * 
     * @param target The point to look at
     */
    fun lookAt(target: Vector3f) {
        front.set(target).sub(gameObject.position).normalize()
        right.set(front).cross(Vector3f(0f, 1f, 0f)).normalize()
        up.set(right).cross(front).normalize()
        viewMatrixDirty = true
    }
    
    /**
     * Sets the camera's orientation using Euler angles.
     * 
     * @param yaw Rotation around the Y axis
     * @param pitch Rotation around the X axis
     */
    fun setOrientation(yaw: Float, pitch: Float) {
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        
        front.x = Math.cos(yawRad.toDouble()).toFloat() * Math.cos(pitchRad.toDouble()).toFloat()
        front.y = Math.sin(pitchRad.toDouble()).toFloat()
        front.z = Math.sin(yawRad.toDouble()).toFloat() * Math.cos(pitchRad.toDouble()).toFloat()
        
        front.normalize()
        right.set(front).cross(Vector3f(0f, 1f, 0f)).normalize()
        up.set(right).cross(front).normalize()
        
        viewMatrixDirty = true
    }
    
    /**
     * Called when the camera's transform changes.
     */
    override fun update(deltaTime: Float) {
        viewMatrixDirty = true
    }
    
    /**
     * Updates the view matrix based on the camera's position and orientation.
     */
    private fun updateViewMatrix() {
        val position = gameObject.position
        viewMatrix.setLookAt(
            position.x, position.y, position.z,
            position.x + front.x, position.y + front.y, position.z + front.z,
            up.x, up.y, up.z
        )
        viewMatrixDirty = false
    }
    
    /**
     * Updates the projection matrix based on the camera's perspective parameters.
     */
    private fun updateProjectionMatrix() {
        projectionMatrix.setPerspective(
            Math.toRadians(fov.toDouble()).toFloat(),
            aspectRatio,
            nearPlane,
            farPlane
        )
        projectionMatrixDirty = false
    }
    
    /**
     * Gets the camera's front direction vector.
     * 
     * @return The front vector
     */
    fun getFront(): Vector3f {
        return Vector3f(front)
    }
    
    /**
     * Gets the camera's up vector.
     * 
     * @return The up vector
     */
    fun getUp(): Vector3f {
        return Vector3f(up)
    }
    
    /**
     * Gets the camera's right vector.
     * 
     * @return The right vector
     */
    fun getRight(): Vector3f {
        return Vector3f(right)
    }
}