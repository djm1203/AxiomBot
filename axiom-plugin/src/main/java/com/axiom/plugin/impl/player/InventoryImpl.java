package com.axiom.plugin.impl.player;

import com.axiom.api.player.Inventory;
import com.axiom.api.util.Antiban;
import com.axiom.plugin.util.RobotClick;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Slf4j
@Singleton
public class InventoryImpl implements Inventory
{
    private static final int INVENTORY_SIZE = 28;

    private final Client  client;
    private final Antiban antiban;

    @Inject
    public InventoryImpl(Client client, Antiban antiban)
    {
        this.client  = client;
        this.antiban = antiban;
    }

    @Override
    public boolean contains(int itemId)
    {
        // Bypass count() — checking slot presence never depends on quantity.
        return getSlot(itemId) != -1;
    }

    @Override
    public boolean containsByName(String name)
    {
        return getSlotByName(name) >= 0;
    }

    @Override
    public int count(int itemId)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return 0;
        Item[] items = inv.getItems();
        if (items == null) return 0;
        int total = 0;
        for (Item item : items)
        {
            if (item == null || item.getId() == -1) continue;
            if (item.getId() == itemId)
            {
                // Use quantity for stackable items; treat 0-quantity as 1 (item is present).
                total += item.getQuantity() > 0 ? item.getQuantity() : 1;
            }
        }
        return total;
    }

    @Override
    public int countByName(String name)
    {
        ItemContainer inv = getContainer();
        if (inv == null || name == null || name.isBlank()) return 0;
        Item[] items = inv.getItems();
        if (items == null) return 0;

        int total = 0;
        for (Item item : items)
        {
            if (item == null || item.getId() == -1) continue;
            ItemComposition def = client.getItemDefinition(item.getId());
            if (def != null && def.getName() != null
                && def.getName().toLowerCase().contains(name.toLowerCase()))
            {
                total += item.getQuantity() > 0 ? item.getQuantity() : 1;
            }
        }
        return total;
    }

    @Override
    public int size()
    {
        ItemContainer inv = getContainer();
        if (inv == null) return 0;
        return (int) Arrays.stream(inv.getItems())
            .filter(item -> item != null && item.getId() != -1)
            .count();
    }

    @Override
    public boolean isFull()
    {
        return size() >= INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }

    @Override
    public int getSlot(int itemId)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return -1;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i] != null && items[i].getId() == itemId) return i;
        }
        return -1;
    }

    @Override
    public int getSlotByName(String name)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return -1;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i] == null || items[i].getId() == -1) continue;
            ItemComposition def = client.getItemDefinition(items[i].getId());
            if (def != null && def.getName() != null
                && def.getName().toLowerCase().contains(name.toLowerCase()))
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void drop(int slot)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return;
        Item[] items = inv.getItems();
        if (slot < 0 || slot >= items.length) return;
        Item item = items[slot];
        if (item == null || item.getId() == -1) return;

        int itemId = item.getId();
        String itemName = "";
        ItemComposition def = client.getItemDefinition(itemId);
        if (def != null && def.getName() != null) itemName = def.getName();

        client.menuAction(
            slot, WidgetInfo.INVENTORY.getId(),
            MenuAction.CC_OP,
            7, itemId,
            "Drop", itemName
        );
    }

    @Override
    public void dropAll(int itemId)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i] != null && items[i].getId() == itemId) drop(i);
        }
    }

    @Override
    public void dropAllByName(String name)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return;
        Item[] items = inv.getItems();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i] == null || items[i].getId() == -1) continue;
            ItemComposition def = client.getItemDefinition(items[i].getId());
            if (def != null && def.getName() != null
                && def.getName().toLowerCase().contains(name.toLowerCase()))
            {
                drop(i);
            }
        }
    }

    @Override
    public void useItemOn(int slot, int targetSlot)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return;
        Item[] items = inv.getItems();
        if (slot < 0 || slot >= items.length || items[slot] == null || items[slot].getId() == -1) return;
        if (targetSlot < 0 || targetSlot >= items.length || items[targetSlot] == null || items[targetSlot].getId() == -1) return;

        int sourceId = items[slot].getId();
        int targetId = items[targetSlot].getId();
        int containerId = WidgetInfo.INVENTORY.getId();

        // Step 1: select the source item for use (CC_OP op=2 = "Use")
        client.menuAction(slot, containerId, MenuAction.CC_OP, 2, sourceId, "Use", getItemName(sourceId));

        // Step 2: use on target (same call — both fire in the same tick)
        // If the game requires two separate ticks, split into a SELECT state + this state.
        client.menuAction(targetSlot, containerId, MenuAction.WIDGET_TARGET_ON_WIDGET, 0,
            targetId, getItemName(targetId), "");
    }

    @Override
    public void useItemOnById(int sourceItemId, int targetItemId)
    {
        int sourceSlot = getSlot(sourceItemId);
        int targetSlot = getSlot(targetItemId);
        if (sourceSlot == -1)
        {
            log.warn("[INVENTORY] useItemOnById: item {} not in inventory", sourceItemId);
            return;
        }
        if (targetSlot == -1)
        {
            log.warn("[INVENTORY] useItemOnById: item {} not in inventory", targetItemId);
            return;
        }
        useItemOn(sourceSlot, targetSlot);
    }

    @Override
    public void selectItem(int itemId)
    {
        int slot = getSlot(itemId);
        if (slot == -1)
        {
            log.warn("[INVENTORY] selectItem: item {} not in inventory", itemId);
            return;
        }
        Widget slotWidget = getInventorySlotWidget(slot);
        if (slotWidget == null) return;
        log.info("[INVENTORY] selectItem: slot={} itemId={}", slot, itemId);
        RobotClick.click(slotWidget, client, antiban);
    }

    @Override
    public void useSelectedItemOn(int targetItemId)
    {
        // Delegates to clickItem — same Robot click, different semantic context.
        clickItem(targetItemId);
    }

    @Override
    public void clickItem(int itemId)
    {
        int slot = getSlot(itemId);
        if (slot == -1)
        {
            log.warn("[INVENTORY] clickItem: item {} not in inventory", itemId);
            return;
        }
        Widget slotWidget = getInventorySlotWidget(slot);
        if (slotWidget == null) return;
        log.info("[INVENTORY] clickItem: slot={} itemId={}", slot, itemId);
        RobotClick.click(slotWidget, client, antiban);
    }

    @Override
    public void clickItemAction(int itemId, String action)
    {
        int slot = getSlot(itemId);
        if (slot == -1)
        {
            log.warn("[INVENTORY] clickItemAction: item {} not in inventory", itemId);
            return;
        }

        int op = resolveInventoryActionOp(itemId, action);
        if (op == -1)
        {
            log.warn("[INVENTORY] clickItemAction: action '{}' unavailable for item {}", action, itemId);
            return;
        }

        String itemName = getItemName(itemId);
        log.info("[INVENTORY] clickItemAction: slot={} itemId={} action='{}' op={}",
            slot, itemId, action, op);
        client.menuAction(
            slot, WidgetInfo.INVENTORY.getId(),
            MenuAction.CC_OP,
            op, itemId,
            action, itemName
        );
    }

    @Override
    public void clickItemByName(String name)
    {
        int slot = getSlotByName(name);
        if (slot == -1)
        {
            log.warn("[INVENTORY] clickItemByName: '{}' not in inventory", name);
            return;
        }

        Widget slotWidget = getInventorySlotWidget(slot);
        if (slotWidget == null) return;
        log.info("[INVENTORY] clickItemByName: slot={} name={}", slot, name);
        RobotClick.click(slotWidget, client, antiban);
    }

    @Override
    public void clickItemActionByName(String name, String action)
    {
        int slot = getSlotByName(name);
        if (slot == -1)
        {
            log.warn("[INVENTORY] clickItemActionByName: '{}' not in inventory", name);
            return;
        }

        ItemContainer inv = getContainer();
        if (inv == null)
        {
            return;
        }

        Item[] items = inv.getItems();
        if (slot >= items.length || items[slot] == null || items[slot].getId() == -1)
        {
            return;
        }

        clickItemAction(items[slot].getId(), action);
    }

    /**
     * Returns the Widget for the given inventory slot (0–27), or null if unavailable.
     * The inventory container's dynamic children are the 28 item slots.
     */
    private Widget getInventorySlotWidget(int slot)
    {
        Widget container = client.getWidget(WidgetInfo.INVENTORY);
        if (container == null)
        {
            log.warn("[INVENTORY] getInventorySlotWidget: inventory widget null");
            return null;
        }
        Widget[] children = container.getDynamicChildren();
        if (children == null || slot >= children.length)
        {
            log.warn("[INVENTORY] getInventorySlotWidget: slot {} out of range (children={})",
                slot, children == null ? "null" : children.length);
            return null;
        }
        return children[slot];
    }

    private String getItemName(int itemId)
    {
        ItemComposition def = client.getItemDefinition(itemId);
        return (def != null && def.getName() != null) ? def.getName() : "";
    }

    private int resolveInventoryActionOp(int itemId, String action)
    {
        if (action == null || action.isBlank())
        {
            return -1;
        }

        String normalized = action.trim();
        if ("drop".equalsIgnoreCase(normalized))
        {
            return 7;
        }
        if ("use".equalsIgnoreCase(normalized))
        {
            return 2;
        }

        ItemComposition def = client.getItemDefinition(itemId);
        if (def == null)
        {
            return -1;
        }

        String[] actions = def.getInventoryActions();
        if (actions == null)
        {
            return -1;
        }

        for (int i = 0; i < actions.length; i++)
        {
            String candidate = actions[i];
            if (candidate != null && candidate.equalsIgnoreCase(normalized))
            {
                return i + 1;
            }
        }

        return -1;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private ItemContainer getContainer()
    {
        return client.getItemContainer(InventoryID.INVENTORY);
    }
}
