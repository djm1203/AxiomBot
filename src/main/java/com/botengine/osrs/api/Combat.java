package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;

/**
 * Combat helpers: attacking NPCs, eating food, toggling prayers.
 *
 * All actions use client.menuAction() — the correct approach for RuneLite plugins.
 *
 * menuAction signature:
 *   client.menuAction(p0, p1, MenuAction action, int id, int itemId, String option, String target)
 *
 * Prayer widget IDs: use WidgetInfo.PRAYER_* constants or raw packed IDs
 * found via RuneLite's developer widget inspector.
 */
public class Combat
{
    private final Client client;
    private final Inventory inventory;
    private final Players players;

    @Inject
    public Combat(Client client, Inventory inventory, Players players)
    {
        this.client = client;
        this.inventory = inventory;
        this.players = players;
    }

    // ── Attacking ─────────────────────────────────────────────────────────────

    /**
     * Attacks the given NPC.
     * Uses NPC_SECOND_OPTION which is the "Attack" option on most combat NPCs.
     */
    public void attackNpc(NPC npc)
    {
        String name = npc.getName() != null ? npc.getName() : "";
        client.menuAction(
            0, 0,
            MenuAction.NPC_SECOND_OPTION,
            npc.getIndex(), -1,
            "Attack", name
        );
    }

    /**
     * Returns true if the player is currently in combat.
     */
    public boolean isInCombat()
    {
        return players.isInCombat();
    }

    // ── Eating ────────────────────────────────────────────────────────────────

    /**
     * Eats the food item in the given inventory slot.
     * Does nothing if the item is not in inventory.
     *
     * @param foodItemId the item ID of the food to eat
     */
    public void eat(int foodItemId)
    {
        int slot = inventory.getSlot(foodItemId);
        if (slot == -1) return;

        client.menuAction(
            slot, WidgetInfo.INVENTORY.getId(),
            MenuAction.ITEM_FIRST_OPTION,
            foodItemId, -1,
            "Eat", ""
        );
    }

    /**
     * Eats if health is at or below the given threshold percent.
     * Example: eatIfNeeded(385, 50) — eat shark when HP is 50% or lower.
     */
    public void eatIfNeeded(int foodItemId, int healthThresholdPercent)
    {
        if (players.shouldEat(healthThresholdPercent) && inventory.contains(foodItemId))
        {
            eat(foodItemId);
        }
    }

    /** Returns true if the player has the given food item. */
    public boolean hasFood(int foodItemId)
    {
        return inventory.contains(foodItemId);
    }

    // ── Prayer ────────────────────────────────────────────────────────────────

    /**
     * Activates or deactivates a prayer by its packed widget ID.
     * Calling this on an active prayer deactivates it (toggle behavior).
     *
     * Common prayer widget IDs (WidgetInfo constants):
     *   PRAYER_PROTECT_FROM_MELEE    — level 43
     *   PRAYER_PROTECT_FROM_MISSILES — level 40
     *   PRAYER_PROTECT_FROM_MAGIC    — level 37
     *   PRAYER_PIETY                 — level 70
     *
     * @param prayerWidgetId packed widget ID of the prayer to toggle
     */
    public void togglePrayer(int prayerWidgetId)
    {
        client.menuAction(
            -1, prayerWidgetId,
            MenuAction.CC_OP,
            1, -1,
            "Activate", ""
        );
    }

    /** Returns the player's current prayer points. */
    public int getPrayerPoints()
    {
        return client.getBoostedSkillLevel(Skill.PRAYER);
    }
}
