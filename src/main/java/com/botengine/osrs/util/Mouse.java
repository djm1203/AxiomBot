package com.botengine.osrs.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 * Mouse movement and click utility using java.awt.Robot.
 *
 * OSRS requires the cursor to be positioned over the entity before a click
 * registers. This utility moves the cursor to the NPC/object's screen position
 * and fires a real left-click, which the OSRS client processes identically to
 * a physical mouse click.
 *
 * Uses Perspective.localToCanvas() to convert the entity's local (scene) coordinates
 * to canvas-space pixel coordinates, then adds the canvas component's screen offset
 * to get absolute screen coordinates for Robot.
 *
 * Not needed for:
 *   - Widget/UI clicks (CC_OP via menuAction) — validated by widget ID only
 *   - Walking (WALK via menuAction) — validated by scene tile coordinates
 */
@Slf4j
@Singleton
public class Mouse
{
    private final Client client;
    private Robot robot;

    @Inject
    public Mouse(Client client)
    {
        this.client = client;
        try
        {
            this.robot = new Robot();
        }
        catch (AWTException e)
        {
            log.error("Mouse: failed to create java.awt.Robot — NPC click movement disabled: {}", e.getMessage());
        }
    }

    /**
     * Moves the mouse cursor to the actor's model center and fires a left-click.
     *
     * Uses Perspective.localToCanvas() with half the model height as a vertical
     * offset, which targets the visual center of the NPC rather than the floor tile.
     *
     * @param actor the NPC or player to click on
     * @return true if the click was fired, false if the actor was off-screen or Robot unavailable
     */
    public boolean click(Actor actor)
    {
        if (robot == null) return false;

        LocalPoint local = actor.getLocalLocation();
        if (local == null)
        {
            log.debug("Mouse.click: {} has no local location", actor.getName());
            return false;
        }

        // localToCanvas converts scene-space coords to canvas-space pixel coords.
        // zOffset = half model height → targets the visual center of the NPC.
        Point canvasPoint = Perspective.localToCanvas(
            client, local, client.getPlane(), actor.getModelHeight() / 2);

        if (canvasPoint == null)
        {
            log.debug("Mouse.click: {} is off-screen (localToCanvas returned null)", actor.getName());
            return false;
        }

        // Validate the point is within the visible canvas. localToCanvas can return
        // coordinates outside the viewport for NPCs that are in the scene graph but
        // not currently on-screen (behind a wall, partially off camera, etc.).
        int canvasW = client.getCanvas().getWidth();
        int canvasH = client.getCanvas().getHeight();
        int cx = canvasPoint.getX();
        int cy = canvasPoint.getY();

        if (cx < 0 || cx >= canvasW || cy < 0 || cy >= canvasH)
        {
            log.debug("Mouse.click: {} canvas ({},{}) is outside viewport ({}x{}) — skipping",
                actor.getName(), cx, cy, canvasW, canvasH);
            return false;
        }

        java.awt.Point canvasOrigin = canvasLocation();
        if (canvasOrigin == null) return false;

        int screenX = canvasOrigin.x + cx;
        int screenY = canvasOrigin.y + cy;

        log.debug("Mouse.click: {} → canvas ({},{}) → screen ({},{})",
            actor.getName(), cx, cy, screenX, screenY);

        robot.mouseMove(screenX, screenY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        return true;
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private java.awt.Point canvasLocation()
    {
        try
        {
            return client.getCanvas().getLocationOnScreen();
        }
        catch (Exception e)
        {
            log.warn("Mouse: could not get canvas screen location: {}", e.getMessage());
            return null;
        }
    }
}
