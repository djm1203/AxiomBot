package com.axiom.plugin.impl.player;

import com.axiom.api.player.Inventory;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Slf4j
@Singleton
public class InventoryImpl implements Inventory
{
    private static final int INVENTORY_SIZE = 28;

    private final Client client;

    @Inject
    public InventoryImpl(Client client) { this.client = client; }

    @Override
    public boolean contains(int itemId)
    {
        return count(itemId) > 0;
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
        return Arrays.stream(inv.getItems())
            .filter(item -> item != null && item.getId() == itemId)
            .mapToInt(Item::getQuantity)
            .sum();
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
        // Tick 1: select the item
        client.menuAction(
            slot, WidgetInfo.INVENTORY.getId(),
            MenuAction.ITEM_USE,
            0, -1,
            "Use", ""
        );
        // Tick 2 must be handled by the caller via useSelectedItemOn()
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private ItemContainer getContainer()
    {
        return client.getItemContainer(InventoryID.INVENTORY);
    }
}
