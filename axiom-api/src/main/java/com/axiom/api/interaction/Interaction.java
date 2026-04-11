package com.axiom.api.interaction;

import com.axiom.api.game.SceneObject;

/**
 * Entity interaction API.
 * Implementation lives in axiom-plugin (uses client.menuAction()).
 *
 * All interactions fire a direct server packet — the mouse cursor does not move.
 * For click-and-move interactions with NPC visuals, use Mouse in axiom-plugin.
 */
public interface Interaction
{
    /**
     * Fires the given action on a SceneObject.
     * Works for game objects (trees, rocks, furnaces) and NPCs.
     *
     * @param object the target object
     * @param action the action string (e.g. "Chop down", "Mine", "Attack", "Talk-to")
     */
    void interact(SceneObject object, String action);

    /**
     * Clicks an inventory item by slot index with the given action.
     * Common actions: "Use", "Eat", "Drink", "Drop", "Wield".
     *
     * @param slot   inventory slot (0–27)
     * @param action the action string
     */
    void clickInventoryItem(int slot, String action);

    /**
     * Fires the ITEM_USE action on the inventory item in the given slot.
     * This is tick 1 of the two-tick item-on-item sequence.
     * Call useSelectedItemOn() on the next tick to complete the interaction.
     *
     * @param slot the inventory slot of the item to select
     */
    void selectItem(int slot);

    /**
     * Fires the WIDGET_TARGET_ON_WIDGET action on the target inventory slot.
     * This is tick 2 of the two-tick item-on-item sequence.
     * Must be called the tick AFTER selectItem().
     *
     * @param targetSlot the inventory slot of the item to use on
     */
    void useSelectedItemOn(int targetSlot);

    /**
     * Clicks a widget by its packed widget ID.
     * Used for spells, dialogs, and other interface elements.
     *
     * @param packedWidgetId  (groupId << 16) | componentId
     */
    void clickWidget(int packedWidgetId);

    /**
     * Clicks a widget by group and component ID.
     * Equivalent to clickWidget(WidgetUtil.packComponentId(groupId, componentId)).
     */
    void clickWidget(int groupId, int componentId);
}
