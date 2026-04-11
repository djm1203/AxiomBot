package com.axiom.plugin.impl.interaction;

import com.axiom.api.game.SceneObject;
import com.axiom.api.interaction.Interaction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class InteractionImpl implements Interaction
{
    private final Client client;

    @Inject
    public InteractionImpl(Client client) { this.client = client; }

    @Override
    public void interact(SceneObject object, String action)
    {
        object.interact(action);
    }

    @Override
    public void clickInventoryItem(int slot, String action)
    {
        int op = inventoryOp(action);
        client.menuAction(
            slot, WidgetInfo.INVENTORY.getId(),
            MenuAction.CC_OP,
            op, -1,
            action, ""
        );
    }

    @Override
    public void selectItem(int slot)
    {
        client.menuAction(
            slot, WidgetInfo.INVENTORY.getId(),
            MenuAction.ITEM_USE,
            0, -1,
            "Use", ""
        );
    }

    @Override
    public void useSelectedItemOn(int targetSlot)
    {
        client.menuAction(
            targetSlot, WidgetInfo.INVENTORY.getId(),
            MenuAction.WIDGET_TARGET_ON_WIDGET,
            0, -1,
            "", ""
        );
    }

    @Override
    public void clickWidget(int packedWidgetId)
    {
        client.menuAction(
            1, packedWidgetId,
            MenuAction.CC_OP,
            1, -1,
            "", ""
        );
    }

    @Override
    public void clickWidget(int groupId, int componentId)
    {
        Widget widget = client.getWidget(groupId, componentId);
        if (widget == null)
        {
            log.warn("InteractionImpl: widget ({}, {}) not found", groupId, componentId);
            return;
        }
        clickWidget(widget.getId());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Maps common inventory action strings to CC_OP slot numbers. */
    private static int inventoryOp(String action)
    {
        if (action == null) return 1;
        switch (action.toLowerCase())
        {
            case "use":    return 2;
            case "eat":
            case "drink":  return 2;
            case "wield":
            case "wear":
            case "equip":  return 1;
            case "drop":   return 7;
            default:       return 1;
        }
    }
}
