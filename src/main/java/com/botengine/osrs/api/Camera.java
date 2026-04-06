package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;

/**
 * Camera controls: rotate yaw to face a world point and adjust pitch.
 *
 * Used to bring off-screen NPCs or objects into view before interacting.
 * Scripts should call rotateTo() when mouse.click() returns false (off-screen),
 * then wait a tick or two for the rotation to complete before retrying the click.
 *
 * RuneLite camera yaw convention (0–2047):
 *   0    = north
 *   512  = east
 *   1024 = south
 *   1536 = west
 *
 * Pitch convention (0–512):
 *   128 = low angle (looking at the horizon)
 *   383 = overhead / maximum zoom-out angle
 *
 * setCameraYawTarget / setCameraPitchTarget perform smooth interpolated rotation —
 * the camera glides toward the target rather than snapping instantly.
 */
@Slf4j
public class Camera
{
    /** Good overhead pitch for AFK scripts — clear view of surrounding tiles. */
    public static final int PITCH_OVERHEAD = 383;

    private final Client client;

    @Inject
    public Camera(Client client)
    {
        this.client = client;
    }

    // ── Rotation ─────────────────────────────────────────────────────────────

    /**
     * Smoothly rotates the camera yaw to face the given world point.
     *
     * Computes the angle from the player's current position to the target,
     * converts to OSRS yaw units (0–2047), and sets the camera yaw target.
     * The camera will animate toward the new yaw over the next few ticks.
     *
     * @param target the world tile to face
     */
    public void rotateTo(WorldPoint target)
    {
        if (client.getLocalPlayer() == null) return;
        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        int dx = target.getX() - player.getX(); // positive = east
        int dy = target.getY() - player.getY(); // positive = north

        // atan2(dx, dy): angle from north axis, positive clockwise
        //   north (0,+1) → 0 rad → yaw 0
        //   east  (+1,0) → π/2   → yaw 512
        //   south (0,-1) → π     → yaw 1024
        //   west  (-1,0) → -π/2  → yaw 1536 (after normalise)
        int yaw = (int) (Math.atan2(dx, dy) / (Math.PI * 2) * 2048);
        yaw = ((yaw % 2048) + 2048) % 2048;

        log.debug("Camera.rotateTo: player={} target={} dx={} dy={} yaw={}",
            player, target, dx, dy, yaw);

        client.setCameraYawTarget(yaw);
    }

    /**
     * Sets the camera pitch to an overhead/top-down angle.
     * Useful when starting a script to ensure a wide field of view.
     */
    public void pitchToOverhead()
    {
        log.debug("Camera.pitchToOverhead: pitch={}", PITCH_OVERHEAD);
        client.setCameraPitchTarget(PITCH_OVERHEAD);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Returns the current camera yaw (0–2047). */
    public int getYaw()
    {
        return client.getCameraYaw();
    }

    /** Returns the current camera pitch (0–512). */
    public int getPitch()
    {
        return client.getCameraPitch();
    }

    /**
     * Returns true if the camera is roughly facing the given world point
     * (within 128 yaw units — approximately ±22.5 degrees).
     */
    public boolean isFacing(WorldPoint target)
    {
        if (client.getLocalPlayer() == null) return false;
        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        int dx = target.getX() - player.getX();
        int dy = target.getY() - player.getY();
        int desiredYaw = (int) (Math.atan2(dx, dy) / (Math.PI * 2) * 2048);
        desiredYaw = ((desiredYaw % 2048) + 2048) % 2048;

        int diff = Math.abs(client.getCameraYaw() - desiredYaw);
        if (diff > 1024) diff = 2048 - diff; // wrap-around distance
        return diff < 128;
    }
}
