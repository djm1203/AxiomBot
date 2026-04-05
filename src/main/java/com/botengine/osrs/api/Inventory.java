package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for reading and interacting with the player's inventory.
 *
 * The inventory is a 28-slot ItemContainer accessed via InventoryID.INVENTORY.
 * Each slot is an Item with an ID and quantity.
 * Item ID -1 means the slot is empty.
 *
 * Item IDs can be found on the OSRS Wiki or with RuneLite's item examination.
 * Common IDs are documented in the scripts that use them.
 */
public class Inventory
{
    /** Maximum number of inventory slots. */
    public static final int SIZE = 28;

    private final Client client;

    @Inject
    public Inventory(Client client)
    {
        this.client = client;
    }

    // ── State checks ──────────────────────────────────────────────────────────

    /**
     * Returns true if all 28 inventory slots are occupied.
     */
    public boolean isFull()
    {
        return getFreeSlots() == 0;
    }

    /**
     * Returns true if all 28 slots are empty.
     */
    public boolean isEmpty()
    {
        ItemContainer inv = getContainer();
        if (inv == null) return true;
        return Arrays.stream(inv.getItems()).allMatch(item -> item == null || item.getId() == -1);
    }

    /**
     * Returns the number of empty slots remaining.
     */
    public int getFreeSlots()
    {
        ItemContainer inv = getContainer();
        if (inv == null) return SIZE;
        return (int) Arrays.stream(inv.getItems())
            .filter(item -> item == null || item.getId() == -1)
            .count();
    }

    /**
     * Returns the number of occupied slots.
     */
    public int getUsedSlots()
    {
        return SIZE - getFreeSlots();
    }

    // ── Item queries ──────────────────────────────────────────────────────────

    /**
     * Returns true if the inventory contains at least one of the given item ID.
     */
    public boolean contains(int itemId)
    {
        return count(itemId) > 0;
    }

    /**
     * Returns true if the inventory contains all of the given item IDs.
     */
    public boolean containsAll(int... itemIds)
    {
        for (int id : itemIds)
        {
            if (!contains(id)) return false;
        }
        return true;
    }

    /**
     * Returns the total quantity of the given item ID across all slots.
     * For stackable items (coins, runes), this returns the stack size.
     * For non-stackable items, this returns the number of slots with that ID.
     */
    public int count(int itemId)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return 0;
        return Arrays.stream(inv.getItems())
            .filter(item -> item != null && item.getId() == itemId)
            .mapToInt(Item::getQuantity)
            .sum();
    }

    /**
     * Returns a list of all items currently in the inventory (non-empty slots only).
     */
    public List<Item> getItems()
    {
        ItemContainer inv = getContainer();
        if (inv == null) return List.of();
        return Arrays.stream(inv.getItems())
            .filter(item -> item != null && item.getId() != -1)
            .collect(Collectors.toList());
    }

    /**
     * Returns the first Item with the given ID, or null if not found.
     */
    public Item getItem(int itemId)
    {
        ItemContainer inv = getContainer();
        if (inv == null) return null;
        return Arrays.stream(inv.getItems())
            .filter(item -> item != null && item.getId() == itemId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns the slot index (0–27) of the first occurrence of the given item ID.
     * Returns -1 if not found.
     */
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

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Returns the raw ItemContainer for the inventory, or null if unavailable.
     * (Unavailable during login screen, loading screens, etc.)
     */
    public ItemContainer getContainer()
    {
        return client.getItemContainer(InventoryID.INVENTORY);
    }
}
