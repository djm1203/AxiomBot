package com.axiom.api.world;

/**
 * Camera control API.
 * Implementation lives in axiom-plugin.
 */
public interface Camera
{
    /**
     * Rotates the camera toward the given world coordinates.
     * Uses arrow key presses via java.awt.Robot so the client thread is not blocked.
     */
    void rotateTo(int worldX, int worldY);

    /** Returns the current camera yaw (0–2047). */
    int getYaw();

    /** Returns the current camera pitch (0–2047). */
    int getPitch();
}
