package com.axiom.plugin.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.awt.Canvas;
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
 * Used by BankImpl (deposit/close buttons) and InventoryImpl (item-on-item).
 */
@Slf4j
public final class RobotClick
{
    private RobotClick() {}

    /**
     * Moves the system mouse cursor to the center of the widget's canvas bounds
     * and fires a left-click.
     *
     * @param widget  the target widget (must have non-null bounds)
     * @param client  RuneLite client (used to resolve canvas screen position)
     */
    public static void click(Widget widget, Client client)
    {
        try
        {
            Rectangle bounds = widget.getBounds();
            Canvas    canvas = client.getCanvas();
            if (canvas == null) { log.warn("RobotClick: canvas null"); return; }

            Point origin = canvas.getLocationOnScreen();
            int x = origin.x + bounds.x + bounds.width  / 2;
            int y = origin.y + bounds.y + bounds.height / 2;

            Robot robot = new Robot();
            robot.mouseMove(x, y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            log.info("RobotClick: clicked ({}, {})", x, y);
        }
        catch (Exception e)
        {
            log.warn("RobotClick: failed — {}", e.getMessage());
        }
    }
}
