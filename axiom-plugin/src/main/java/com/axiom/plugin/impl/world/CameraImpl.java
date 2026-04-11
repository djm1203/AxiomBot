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
