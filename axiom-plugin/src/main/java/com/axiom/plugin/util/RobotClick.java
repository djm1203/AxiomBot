package com.axiom.plugin.util;

import com.axiom.api.util.Antiban;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.awt.Canvas;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 * Shared Robot-based widget click utility.
 *
 * menuAction() CC_OP calls are silently ignored for certain widget types
 * (inventory slots, bank deposit buttons) in this RuneLite build. A real
 * java.awt.Robot mouse event bypasses that and reaches the game directly.
 *
 * Uses Antiban.moveMouse() to travel a Bezier curve path to the target
 * instead of teleporting directly — every click looks natural.
 */
@Slf4j
public final class RobotClick
{
    private RobotClick() {}

    /**
     * Moves the cursor along a Bezier curve to the center of the widget's
     * canvas bounds (with jitter), then fires a left-click.
     *
     * @param widget  the target widget (must have non-null bounds)
     * @param client  RuneLite client (used to resolve canvas screen position)
     * @param antiban antiban instance (drives the Bezier path)
     */
    public static void click(Widget widget, Client client, Antiban antiban)
    {
        try
        {
            Rectangle bounds = widget.getBounds();
            Canvas    canvas = client.getCanvas();
            if (canvas == null) { log.warn("RobotClick: canvas null"); return; }

            Point origin  = canvas.getLocationOnScreen();
            int   targetX = origin.x + bounds.x + bounds.width  / 2;
            int   targetY = origin.y + bounds.y + bounds.height / 2;

            // Bezier path from current cursor position to target (with jitter)
            Point current = MouseInfo.getPointerInfo().getLocation();
            antiban.moveMouse(current.x, current.y, targetX, targetY);

            Robot robot = new Robot();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            log.info("RobotClick: clicked ({}, {})", targetX, targetY);
        }
        catch (Exception e)
        {
            log.warn("RobotClick: failed — {}", e.getMessage());
        }
    }
}
