package com.axiom.api.player;

/**
 * Inventory queries and actions.
 * Implementation lives in axiom-plugin.
 */
public interface Inventory
{
    /** Returns true if the inventory has at least one item with the given ID. */
    boolean contains(int itemId);

    /** Returns true if the inventory has at least one item whose name contains the string. */
    boolean containsByName(String name);

    /** Returns the count of items with the given ID in the inventory. */
    int count(int itemId);

    /** Returns the total number of occupied inventory slots. */
    int size();

    /** Returns true if all 28 inventory slots are occupied. */
    boolean isFull();

    /** Returns true if all 28 inventory slots are empty. */
    boolean isEmpty();

    /**
     * Returns the slot index (0–27) of the first item with the given ID,
     * or -1 if not found.
     */
    int getSlot(int itemId);

    /**
     * Returns the slot index of the first item whose name contains the string,
     * or -1 if not found.
     */
    int getSlotByName(String name);

    /**
     * Drops the item in the given slot.
     */
    void drop(int slot);

    /**
     * Drops all items with the given ID.
     */
    void dropAll(int itemId);

    /**
     * Drops all items whose name contains the given string.
     */
    void dropAllByName(String name);

    /**
     * Uses the item in {@code slot} on the item in {@code targetSlot}.
     * Fires the ITEM_USE + WIDGET_TARGET_ON_WIDGET sequence across two ticks.
     * The caller must handle the two-tick split via state machine states.
     */
    void useItemOn(int slot, int targetSlot);
}
