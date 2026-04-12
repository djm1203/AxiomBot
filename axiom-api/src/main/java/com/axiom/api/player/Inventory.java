package com.axiom.api.player;

/**
 * Inventory queries and actions.
 * Implementation lives in axiom-plugin.
 */
public interface Inventory
{
    /** Returns true if the inventory has at least one item with the given ID. */
    boolean contains(int itemId);

    /**
     * Explicit ID-based lookup — use this instead of {@link #contains(int)} to
     * make intent unambiguous. Delegates to {@code contains(itemId)}.
     */
    default boolean containsById(int itemId) {
        return contains(itemId);
    }

    /**
     * Returns true if the inventory contains at least one item whose ID matches
     * any entry in {@code itemIds}. Useful for food checks where multiple food
     * types are acceptable.
     */
    default boolean containsAny(int[] itemIds) {
        for (int id : itemIds) {
            if (containsById(id)) return true;
        }
        return false;
    }

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
     * Fires CC_OP "Use" on the source followed immediately by
     * WIDGET_TARGET_ON_WIDGET on the target — both in the same call.
     */
    void useItemOn(int slot, int targetSlot);

    /**
     * Resolves both item IDs to their inventory slots, then calls
     * {@link #useItemOn(int, int)}. No-op if either item is not in inventory.
     */
    void useItemOnById(int sourceItemId, int targetItemId);

    /**
     * Tick 1 of item-on-item: selects the item with the given ID for use
     * (CC_OP op=2 = "Use"). The item cursor appears on the client.
     * Call {@link #useSelectedItemOn(int)} on the following tick to complete
     * the interaction.
     */
    void selectItem(int itemId);

    /**
     * Tick 2 of item-on-item: fires WIDGET_TARGET_ON_WIDGET on the target item
     * using the previously selected item. Must be called exactly one tick after
     * {@link #selectItem(int)}.
     */
    void useSelectedItemOn(int targetItemId);

    /**
     * Robot-clicks the inventory slot containing the item with the given ID.
     * General-purpose click for spell-on-item (Alchemy), use-item, and similar
     * interactions. No-op if the item is not in inventory.
     */
    void clickItem(int itemId);
}
