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
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.Random;

/**
 * Human-like mouse movement and click utility using java.awt.Robot.
 *
 * Every click moves the cursor along a randomized Bezier curve from its
 * current position to the target, with per-click jitter on the landing point.
 * Approximately 2% of clicks deliberately miss and self-correct (misclick simulation).
 *
 * Threading note:
 *   moveAlongPath() calls robot.mouseMove() in a tight loop with no sleep —
 *   the cursor traverses the path in milliseconds so the client thread is not
 *   meaningfully blocked.  The misclick recovery pause is capped at 150ms.
 */
@Slf4j
@Singleton
public class Mouse
{
    private final Client  client;
    private final Antiban antiban;
    private final Random  random = new Random();
    private Robot robot;

    @Inject
    public Mouse(Client client, Antiban antiban)
    {
        this.client  = client;
        this.antiban = antiban;
        try
        {
            this.robot = new Robot();
            this.robot.setAutoDelay(0); // no built-in delay between Robot calls
        }
        catch (AWTException e)
        {
            log.error("Mouse: failed to create java.awt.Robot — NPC click movement disabled: {}", e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Moves the cursor to the actor's visual centre along a Bezier curve and
     * fires a left-click, with optional misclick simulation.
     *
     * @param actor the NPC or player to click
     * @return true if the click was fired; false if off-screen or Robot unavailable
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

        Point canvas = Perspective.localToCanvas(
            client, local, client.getPlane(), actor.getModelHeight() / 2);
        if (canvas == null)
        {
            log.debug("Mouse.click: {} is off-screen", actor.getName());
            return false;
        }

        int cw = client.getCanvas().getWidth();
        int ch = client.getCanvas().getHeight();
        if (canvas.getX() < 0 || canvas.getX() >= cw || canvas.getY() < 0 || canvas.getY() >= ch)
        {
            log.debug("Mouse.click: {} canvas ({},{}) outside viewport", actor.getName(), canvas.getX(), canvas.getY());
            return false;
        }

        java.awt.Point origin = canvasLocation();
        if (origin == null) return false;

        int screenX = origin.x + canvas.getX();
        int screenY = origin.y + canvas.getY();

        performClick(screenX, screenY);
        return true;
    }

    /**
     * Moves the cursor to absolute screen coordinates along a Bezier curve
     * and fires a left-click.  Use for clicking canvas-relative UI elements
     * that are not actors (e.g. inventory items when Robot is required).
     */
    public void clickScreen(int screenX, int screenY)
    {
        if (robot == null) return;
        performClick(screenX, screenY);
    }

    // ── Core click logic ──────────────────────────────────────────────────────

    /**
     * Handles the full click sequence:
     *   1. Apply landing jitter to the target coordinates.
     *   2. Move cursor along a Bezier curve to target.
     *   3. 2% chance: click off-target first, pause briefly, re-approach.
     *   4. Fire the real click.
     */
    private void performClick(int targetX, int targetY)
    {
        // Apply jitter to landing position
        int jR  = antiban.getJitterRadius();
        int jX  = targetX + clamp((int)(random.nextGaussian() * jR), -2 * jR, 2 * jR);
        int jY  = targetY + clamp((int)(random.nextGaussian() * jR), -2 * jR, 2 * jR);

        // Misclick: hit a nearby wrong spot, pause, then re-approach
        if (antiban.shouldMisclick())
        {
            int missX = targetX + (int)(random.nextGaussian() * 18);
            int missY = targetY + (int)(random.nextGaussian() * 18);
            moveAlongPath(missX, missY);
            fireClick();
            log.debug("Mouse: misclick at ({},{}) — recovering", missX, missY);

            // Brief recovery pause (capped at 150ms — minimal client-thread impact)
            try { Thread.sleep(80 + random.nextInt(70)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Re-approach the real target
            moveAlongPath(jX, jY);
        }
        else
        {
            moveAlongPath(jX, jY);
        }

        fireClick();
    }

    /**
     * Moves the cursor from its current screen position to (toX, toY) along a
     * quadratic Bezier curve.  No sleep between steps — the loop executes in
     * microseconds so the client thread is not meaningfully blocked.
     *
     * Step count scales with distance so short hops don't overshoot.
     */
    private void moveAlongPath(int toX, int toY)
    {
        java.awt.Point cur = currentScreenPosition();
        int fromX = cur.x;
        int fromY = cur.y;

        double dist  = Math.hypot(toX - fromX, toY - fromY);
        int    steps = Math.max(8, Math.min(20, (int)(dist / 20)));

        // Random quadratic control point offset from midpoint
        double cpX = (fromX + toX) / 2.0 + random.nextGaussian() * 35;
        double cpY = (fromY + toY) / 2.0 + random.nextGaussian() * 35;

        for (int i = 1; i <= steps; i++)
        {
            double t = (double) i / steps;
            // Quadratic Bezier: P(t) = (1-t)²·P0 + 2(1-t)t·CP + t²·P1
            int x = (int)(Math.pow(1 - t, 2) * fromX + 2 * (1 - t) * t * cpX + t * t * toX);
            int y = (int)(Math.pow(1 - t, 2) * fromY + 2 * (1 - t) * t * cpY + t * t * toY);
            robot.mouseMove(x, y);
        }
    }

    private void fireClick()
    {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private java.awt.Point currentScreenPosition()
    {
        try
        {
            java.awt.PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) return pi.getLocation();
        }
        catch (Exception ignored) {}
        // Fallback: centre of canvas
        java.awt.Point origin = canvasLocation();
        if (origin != null)
            return new java.awt.Point(
                origin.x + client.getCanvas().getWidth()  / 2,
                origin.y + client.getCanvas().getHeight() / 2);
        return new java.awt.Point(0, 0);
    }

    private java.awt.Point canvasLocation()
    {
        try   { return client.getCanvas().getLocationOnScreen(); }
        catch (Exception e) { log.warn("Mouse: cannot get canvas location: {}", e.getMessage()); return null; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
