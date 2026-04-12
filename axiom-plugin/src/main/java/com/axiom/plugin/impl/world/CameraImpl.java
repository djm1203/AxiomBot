package com.axiom.plugin.impl.world;

import com.axiom.api.world.Camera;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;

@Slf4j
@Singleton
public class CameraImpl implements Camera
{
    private static final int MAX_YAW   = 2048;
    private static final int YAW_STEP  = 64;
    private static final int MAX_STEPS = 32;

    private final Client client;
    private Robot robot;

    @Inject
    public CameraImpl(Client client)
    {
        this.client = client;
        try
        {
            this.robot = new Robot();
            this.robot.setAutoDelay(0);
        }
        catch (AWTException e)
        {
            log.error("CameraImpl: failed to create Robot — camera rotation disabled: {}", e.getMessage());
        }
    }

    /**
     * Zooms to maximum zoom-out and pitches the camera to top-down.
     * Runs on a daemon thread — never blocks the EDT or game thread.
     *
     * Zoom: scrolls the mouse wheel down 25 times over the canvas centre.
     *   Positive mouseWheel() = scroll down = zoom out in OSRS.
     * Pitch: holds the UP arrow for 2 s (enough to reach max overhead from any angle).
     */
    @Override
    public void setupForScripting()
    {
        new Thread(() ->
        {
            try
            {
                java.awt.Canvas canvas = client.getCanvas();
                if (canvas == null) { log.warn("setupForScripting: canvas null"); return; }

                java.awt.Point origin  = canvas.getLocationOnScreen();
                int cx = origin.x + canvas.getWidth()  / 2;
                int cy = origin.y + canvas.getHeight() / 2;

                java.awt.Robot r = new java.awt.Robot();
                r.setAutoDelay(0);

                // Zoom out to maximum
                r.mouseMove(cx, cy);
                for (int i = 0; i < 25; i++)
                {
                    r.mouseWheel(3);   // positive = scroll down = zoom out
                    r.delay(30);
                }

                // Pitch to top-down: hold UP arrow ~2 s
                r.keyPress(java.awt.event.KeyEvent.VK_UP);
                r.delay(2000);
                r.keyRelease(java.awt.event.KeyEvent.VK_UP);

                log.info("setupForScripting: zoom out + top-down pitch done");
            }
            catch (Exception e)
            {
                log.warn("setupForScripting failed: {}", e.getMessage());
            }
        }, "axiom-cam-setup").start();
    }

    @Override
    public void rotateTo(int worldX, int worldY)
    {
        if (robot == null) return;

        int playerX = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation().getX() : worldX;
        int playerY = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation().getY() : worldY;

        // Calculate the desired yaw angle toward the target
        int dx = worldX - playerX;
        int dy = worldY - playerY;
        // OSRS yaw: 0 = south, increases clockwise. North = 1024, East = 512, West = 1536.
        int targetYaw = (int) ((Math.toDegrees(Math.atan2(-dx, -dy)) + 360) % 360 / 360.0 * MAX_YAW);

        int currentYaw = getYaw();
        int diff = ((targetYaw - currentYaw + MAX_YAW / 2) % MAX_YAW) - MAX_YAW / 2;

        int key = diff > 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        int steps = Math.min(Math.abs(diff) / YAW_STEP, MAX_STEPS);

        robot.keyPress(key);
        try { Thread.sleep(steps * 20L); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        robot.keyRelease(key);
    }

    @Override
    public int getYaw()
    {
        return client.getCameraYaw();
    }

    @Override
    public int getPitch()
    {
        return client.getCameraPitch();
    }
}
