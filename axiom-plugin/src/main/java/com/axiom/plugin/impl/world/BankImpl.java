package com.axiom.plugin.impl.world;

import com.axiom.api.game.SceneObject;
import com.axiom.api.world.Bank;
import com.axiom.plugin.impl.game.GameObjectsImpl;
import com.axiom.plugin.impl.game.NpcsImpl;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class BankImpl implements Bank
{
    private static final int BANK_GROUP_ID  = 12;
    private static final int CLOSE_CHILD_ID = 2;
    private static final int ITEMS_CHILD_ID = 13;

    private final Client         client;
    private final GameObjectsImpl gameObjects;
    private final NpcsImpl        npcs;

    @Inject
    public BankImpl(Client client, GameObjectsImpl gameObjects, NpcsImpl npcs)
    {
        this.client      = client;
        this.gameObjects = gameObjects;
        this.npcs        = npcs;
    }

    @Override
    public boolean isOpen()
    {
        Widget root = client.getWidget(BANK_GROUP_ID, 0);
        return root != null && !root.isHidden();
    }

    @Override
    public boolean isNearBank()
    {
        // 25-tile Chebyshev radius — covers typical resource-to-bank distances
        // (Draynor fishing spots are ~16 tiles from the bank; mining, cooking, etc.
        // can be similarly spaced). The old threshold of 10 was too tight.
        SceneObject obj = findNearestBankObject();
        if (obj != null)
        {
            int dx = Math.abs(obj.getWorldX() - client.getLocalPlayer().getWorldLocation().getX());
            int dy = Math.abs(obj.getWorldY() - client.getLocalPlayer().getWorldLocation().getY());
            if (Math.max(dx, dy) <= 25) return true;
        }
        SceneObject banker = npcs.nearestByName("Banker");
        if (banker != null)
        {
            int dx = Math.abs(banker.getWorldX() - client.getLocalPlayer().getWorldLocation().getX());
            int dy = Math.abs(banker.getWorldY() - client.getLocalPlayer().getWorldLocation().getY());
            return Math.max(dx, dy) <= 25;
        }
        return false;
    }

    @Override
    public void openNearest()
    {
        SceneObject obj = findNearestBankObject();
        if (obj != null)
        {
            obj.interact("Bank");
            return;
        }
        SceneObject banker = npcs.nearestByName("Banker");
        if (banker != null)
        {
            banker.interact("Bank");
        }
    }

    @Override
    public void depositAll()
    {
        Widget depositBtn = client.getWidget(WidgetInfo.BANK_DEPOSIT_INVENTORY);
        if (depositBtn == null || depositBtn.isHidden())
        {
            log.warn("[BANKING] deposit widget null/hidden — bank not fully open");
            return;
        }
        log.info("[BANKING] clicking deposit inventory button (bounds={})", depositBtn.getBounds());
        robotClickWidget(depositBtn);
    }

    @Override
    public void deposit(int itemId)
    {
        client.menuAction(
            -1, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
            MenuAction.CC_OP,
            itemId, -1,
            "Deposit-All", ""
        );
    }

    @Override
    public void deposit(int itemId, int quantity)
    {
        int op = quantityToOp(quantity);
        client.menuAction(
            -1, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
            MenuAction.CC_OP,
            op, itemId,
            "", ""
        );
    }

    @Override
    public void withdraw(int itemId, int quantity)
    {
        Widget container = client.getWidget(BANK_GROUP_ID, ITEMS_CHILD_ID);
        if (container == null) { log.warn("BankImpl: bank items container not found"); return; }

        Widget[] children = container.getDynamicChildren();
        if (children == null) return;

        for (int slot = 0; slot < children.length; slot++)
        {
            Widget child = children[slot];
            if (child == null || child.getItemId() != itemId) continue;

            int op = quantityToOp(quantity);
            client.menuAction(
                slot, container.getId(),
                MenuAction.CC_OP,
                op, itemId,
                "", ""
            );
            return;
        }
        log.warn("BankImpl: item id={} not found in bank", itemId);
    }

    @Override
    public void withdrawAll(int itemId)
    {
        withdraw(itemId, Integer.MAX_VALUE);
    }

    @Override
    public boolean contains(int itemId)
    {
        Widget container = client.getWidget(BANK_GROUP_ID, ITEMS_CHILD_ID);
        if (container == null) return false;
        Widget[] children = container.getDynamicChildren();
        if (children == null) return false;
        for (Widget child : children)
        {
            if (child != null && child.getItemId() == itemId) return true;
        }
        return false;
    }

    @Override
    public void close()
    {
        Widget closeBtn = client.getWidget(BANK_GROUP_ID, CLOSE_CHILD_ID);
        if (closeBtn != null && !closeBtn.isHidden()) robotClickWidget(closeBtn);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private SceneObject findNearestBankObject()
    {
        // hasAction() uses the impostor-corrected actions cached in SceneObjectWrapper
        return gameObjects.nearest(o -> o.hasAction("Bank"));
    }

    /**
     * Moves the system mouse cursor to the center of the widget's canvas bounds
     * and fires a left-click. Mirrors the approach used in SceneObjectWrapper for
     * game objects — menuAction CC_OP does not work reliably for widget buttons
     * in this RuneLite build, so a real mouse event is required.
     */
    private void robotClickWidget(Widget widget)
    {
        try
        {
            java.awt.Rectangle bounds = widget.getBounds();
            java.awt.Canvas canvas = client.getCanvas();
            if (canvas == null) { log.warn("robotClickWidget: canvas is null"); return; }

            java.awt.Point origin = canvas.getLocationOnScreen();
            int x = origin.x + bounds.x + bounds.width  / 2;
            int y = origin.y + bounds.y + bounds.height / 2;

            java.awt.Robot robot = new java.awt.Robot();
            robot.mouseMove(x, y);
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            log.info("robotClickWidget: clicked ({}, {})", x, y);
        }
        catch (Exception e)
        {
            log.warn("robotClickWidget failed: {}", e.getMessage());
        }
    }

    private static int quantityToOp(int quantity)
    {
        if (quantity == 1)  return 1;
        if (quantity == 5)  return 2;
        if (quantity == 10) return 3;
        return 7; // Withdraw-All
    }
}
