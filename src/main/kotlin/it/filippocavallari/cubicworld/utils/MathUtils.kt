package it.filippocavallari.cubicworld.utils

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Math utilities for 3D graphics.
 * Contains common mathematical operations and constants.
 */
object MathUtils {
    
    /**
     * PI constant.
     */
    const val PI = Math.PI.toFloat()
    
    /**
     * PI * 2 constant.
     */
    const val TWO_PI = PI * 2.0f
    
    /**
     * PI / 2 constant.
     */
    const val HALF_PI = PI / 2.0f
    
    /**
     * Converts degrees to radians.
     * 
     * @param degrees The angle in degrees
     * @return The angle in radians
     */
    fun toRadians(degrees: Float): Float {
        return degrees * PI / 180.0f
    }
    
    /**
     * Converts radians to degrees.
     * 
     * @param radians The angle in radians
     * @return The angle in degrees
     */
    fun toDegrees(radians: Float): Float {
        return radians * 180.0f / PI
    }
    
    /**
     * Linear interpolation between two values.
     * 
     * @param a First value
     * @param b Second value
     * @param t Interpolation factor (0-1)
     * @return Interpolated value
     */
    fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }
    
    /**
     * Linear interpolation between two vectors.
     * 
     * @param a First vector
     * @param b Second vector
     * @param t Interpolation factor (0-1)
     * @return Interpolated vector
     */
    fun lerp(a: Vector3f, b: Vector3f, t: Float): Vector3f {
        return Vector3f(
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t),
            lerp(a.z, b.z, t)
        )
    }
    
    /**
     * Spherical linear interpolation between two quaternions.
     * 
     * @param a First quaternion
     * @param b Second quaternion
     * @param t Interpolation factor (0-1)
     * @return Interpolated quaternion
     */
    fun slerp(a: Quaternionf, b: Quaternionf, t: Float): Quaternionf {
        // Compute the cosine of the angle between the two vectors
        var dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
        
        // If the dot product is negative, negate one of the quaternions
        // to take the shortest path
        val result = Quaternionf()
        if (dot < 0.0f) {
            dot = -dot
            result.set(-b.x, -b.y, -b.z, -b.w)
        } else {
            result.set(b.x, b.y, b.z, b.w)
        }
        
        // If the inputs are too close for comfort, linearly interpolate
        if (dot > 0.9995f) {
            result.x = lerp(a.x, result.x, t)
            result.y = lerp(a.y, result.y, t)
            result.z = lerp(a.z, result.z, t)
            result.w = lerp(a.w, result.w, t)
            return result.normalize()
        }
        
        // Calculate final values
        val theta0 = kotlin.math.acos(dot)
        val theta = theta0 * t
        val sinTheta = sin(theta)
        val sinTheta0 = sin(theta0)
        
        val s0 = cos(theta) - dot * sinTheta / sinTheta0
        val s1 = sinTheta / sinTheta0
        
        result.x = s0 * a.x + s1 * result.x
        result.y = s0 * a.y + s1 * result.y
        result.z = s0 * a.z + s1 * result.z
        result.w = s0 * a.w + s1 * result.w
        
        return result
    }
    
    /**
     * Clamps a value between a minimum and maximum.
     * 
     * @param value The value to clamp
     * @param min The minimum value
     * @param max The maximum value
     * @return The clamped value
     */
    fun clamp(value: Float, min: Float, max: Float): Float {
        return kotlin.math.max(min, kotlin.math.min(value, max))
    }
    
    /**
     * Normalizes a value from one range to another.
     * 
     * @param value The value to normalize
     * @param min The minimum of the input range
     * @param max The maximum of the input range
     * @param newMin The minimum of the output range
     * @param newMax The maximum of the output range
     * @return The normalized value
     */
    fun normalize(value: Float, min: Float, max: Float, newMin: Float, newMax: Float): Float {
        return (value - min) / (max - min) * (newMax - newMin) + newMin
    }
    
    /**
     * Checks if two floating point values are approximately equal.
     * 
     * @param a First value
     * @param b Second value
     * @param epsilon Tolerance
     * @return True if the values are approximately equal
     */
    fun approxEqual(a: Float, b: Float, epsilon: Float = 0.0001f): Boolean {
        return abs(a - b) < epsilon
    }
    
    /**
     * Checks if a quaternion is approximately equal to another.
     * 
     * @param a First quaternion
     * @param b Second quaternion
     * @param epsilon Tolerance
     * @return True if the quaternions are approximately equal
     */
    fun approxEqual(a: Quaternionf, b: Quaternionf, epsilon: Float = 0.0001f): Boolean {
        return (approxEqual(a.x, b.x, epsilon) &&
                approxEqual(a.y, b.y, epsilon) &&
                approxEqual(a.z, b.z, epsilon) &&
                approxEqual(a.w, b.w, epsilon))
    }
    
    /**
     * Checks if a vector is approximately equal to another.
     * 
     * @param a First vector
     * @param b Second vector
     * @param epsilon Tolerance
     * @return True if the vectors are approximately equal
     */
    fun approxEqual(a: Vector3f, b: Vector3f, epsilon: Float = 0.0001f): Boolean {
        return (approxEqual(a.x, b.x, epsilon) &&
                approxEqual(a.y, b.y, epsilon) &&
                approxEqual(a.z, b.z, epsilon))
    }
    
    /**
     * Creates a view matrix from position, target, and up vectors.
     * 
     * @param position Camera position
     * @param target Look-at target
     * @param up Up vector
     * @return View matrix
     */
    fun createViewMatrix(position: Vector3f, target: Vector3f, up: Vector3f): Matrix4f {
        return Matrix4f().lookAt(position, target, up)
    }
    
    /**
     * Creates a perspective projection matrix.
     * 
     * @param fovDegrees Field of view in degrees
     * @param aspectRatio Aspect ratio (width / height)
     * @param near Near plane distance
     * @param far Far plane distance
     * @return Perspective projection matrix
     */
    fun createPerspectiveMatrix(fovDegrees: Float, aspectRatio: Float, near: Float, far: Float): Matrix4f {
        return Matrix4f().perspective(toRadians(fovDegrees), aspectRatio, near, far)
    }
    
    /**
     * Creates an orthographic projection matrix.
     * 
     * @param left Left plane coordinate
     * @param right Right plane coordinate
     * @param bottom Bottom plane coordinate
     * @param top Top plane coordinate
     * @param near Near plane distance
     * @param far Far plane distance
     * @return Orthographic projection matrix
     */
    fun createOrthographicMatrix(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): Matrix4f {
        return Matrix4f().ortho(left, right, bottom, top, near, far)
    }
    
    /**
     * Calculates a random value between min and max.
     * 
     * @param min Minimum value
     * @param max Maximum value
     * @return Random value between min and max
     */
    fun random(min: Float, max: Float): Float {
        return min + (max - min) * Math.random().toFloat()
    }
    
    /**
     * Converts a quaternion to Euler angles (in degrees).
     * 
     * @param q Input quaternion
     * @return Vector3f containing Euler angles (pitch, yaw, roll) in degrees
     */
    fun quaternionToEuler(q: Quaternionf): Vector3f {
        // Convert to Euler angles
        val angles = Vector3f()
        
        // Roll (x-axis rotation)
        val sinRollCosPitch = 2 * (q.w * q.x + q.y * q.z)
        val cosRollCosPitch = 1 - 2 * (q.x * q.x + q.y * q.y)
        angles.z = toDegrees(kotlin.math.atan2(sinRollCosPitch, cosRollCosPitch))
        
        // Pitch (y-axis rotation)
        val sinPitch = 2 * (q.w * q.y - q.z * q.x)
        if (abs(sinPitch) >= 1) {
            angles.x = toDegrees(if (sinPitch >= 0) HALF_PI else -HALF_PI)
        } else {
            angles.x = toDegrees(kotlin.math.asin(sinPitch))
        }
        
        // Yaw (z-axis rotation)
        val sinYawCosPitch = 2 * (q.w * q.z + q.x * q.y)
        val cosYawCosPitch = 1 - 2 * (q.y * q.y + q.z * q.z)
        angles.y = toDegrees(kotlin.math.atan2(sinYawCosPitch, cosYawCosPitch))
        
        return angles
    }
    
    /**
     * Converts Euler angles (in degrees) to a quaternion.
     * 
     * @param pitch X-axis rotation in degrees
     * @param yaw Y-axis rotation in degrees
     * @param roll Z-axis rotation in degrees
     * @return Quaternion representing the rotation
     */
    fun eulerToQuaternion(pitch: Float, yaw: Float, roll: Float): Quaternionf {
        // Convert to radians
        val pitchRad = toRadians(pitch) * 0.5f
        val yawRad = toRadians(yaw) * 0.5f
        val rollRad = toRadians(roll) * 0.5f
        
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val cosRoll = cos(rollRad)
        val sinRoll = sin(rollRad)
        
        val q = Quaternionf()
        q.x = sinRoll * cosPitch * cosYaw - cosRoll * sinPitch * sinYaw
        q.y = cosRoll * sinPitch * cosYaw + sinRoll * cosPitch * sinYaw
        q.z = cosRoll * cosPitch * sinYaw - sinRoll * sinPitch * cosYaw
        q.w = cosRoll * cosPitch * cosYaw + sinRoll * sinPitch * sinYaw
        
        return q.normalize()
    }
    
    /**
     * Converts a direction vector to Euler angles (in degrees).
     * 
     * @param direction Direction vector (should be normalized)
     * @return Vector3f containing Euler angles (pitch, yaw, roll) in degrees
     */
    fun directionToEuler(direction: Vector3f): Vector3f {
        val dir = Vector3f(direction).normalize()
        
        // Calculate yaw (y-axis rotation)
        val yaw = toDegrees(kotlin.math.atan2(dir.x, dir.z))
        
        // Calculate pitch (x-axis rotation)
        val pitch = toDegrees(kotlin.math.asin(-dir.y))
        
        // Roll is usually determined by additional constraints,
        // but for a direction vector, we'll set it to 0
        val roll = 0.0f
        
        return Vector3f(pitch, yaw, roll)
    }
}