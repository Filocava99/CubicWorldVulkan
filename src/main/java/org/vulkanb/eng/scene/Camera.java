package org.vulkanb.eng.scene;

import org.joml.*;

public class Camera {

    private boolean hasMoved;
    private Vector3f position;
    private Vector2f rotation;
    private Matrix4f viewMatrix;

    public Camera() {
        position = new Vector3f();
        viewMatrix = new Matrix4f();
        rotation = new Vector2f();
    }

    public void addRotation(float x, float y) {
        rotation.add(x, y);
        recalculate();
    }

    public Vector3f getPosition() {
        return position;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    public boolean isHasMoved() {
        return hasMoved;
    }

    public void moveBackwards(float inc) {
        // Move backward based on horizontal rotation only (ignore pitch)
        float x = (float) java.lang.Math.sin(rotation.y) * inc;
        float z = (float) java.lang.Math.cos(rotation.y) * inc;
        position.x -= x;
        position.z += z;
        recalculate();
    }

    public void moveDown(float inc) {
        // Move along negative Y axis (world coordinates)
        position.y -= inc;
        recalculate();
    }

    public void moveForward(float inc) {
        // Move forward based on horizontal rotation only (ignore pitch)
        float x = (float) java.lang.Math.sin(rotation.y) * inc;
        float z = (float) java.lang.Math.cos(rotation.y) * inc;
        position.x += x;
        position.z -= z;
        recalculate();
    }

    public void moveLeft(float inc) {
        // Strafe left based on horizontal rotation only (ignore pitch)
        float x = (float) java.lang.Math.sin(rotation.y - java.lang.Math.PI/2) * inc;
        float z = (float) java.lang.Math.cos(rotation.y - java.lang.Math.PI/2) * inc;
        position.x += x;
        position.z -= z;
        recalculate();
    }

    public void moveRight(float inc) {
        // Strafe right based on horizontal rotation only (ignore pitch)
        float x = (float) java.lang.Math.sin(rotation.y + java.lang.Math.PI/2) * inc;
        float z = (float) java.lang.Math.cos(rotation.y + java.lang.Math.PI/2) * inc;
        position.x += x;
        position.z -= z;
        recalculate();
    }

    public void moveUp(float inc) {
        // Move along positive Y axis (world coordinates)
        position.y += inc;
        recalculate();
    }

    public void recalculate() {
        hasMoved = true;
        viewMatrix.identity()
                .rotateX(rotation.x)
                .rotateY(rotation.y)
                .translate(-position.x, -position.y, -position.z);
    }

    public void setHasMoved(boolean hasMoved) {
        this.hasMoved = hasMoved;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        recalculate();
    }

    public void setRotation(float x, float y) {
        rotation.set(x, y);
        recalculate();
    }

    public Vector2f getRotation() {
        return new Vector2f(rotation);
    }
}
