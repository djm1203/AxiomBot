package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
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
    private final Interaction interaction;
    private final Inventory inventory;
    private final Players players;

    @Inject
    public Combat(Client client, Interaction interaction, Inventory inventory, Players players)
    {
        this.client = client;
        this.interaction = interaction;
        this.inventory = inventory;
        this.players = players;
    }

    // ── Attacking ─────────────────────────────────────────────────────────────

    /**
     * Attacks the given NPC. Returns true if the click was fired (NPC was on-screen),
     * false if the NPC was off-screen and the click was skipped.
     *
     * Callers should only transition to an ATTACKING state when this returns true.
     */
    public boolean attackNpc(NPC npc)
    {
        return interaction.click(npc, "Attack");
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

    // ── Special attack ────────────────────────────────────────────────────────

    /**
     * Returns the current special attack energy as a percentage (0–100).
     * Reads VarPlayer 300, which stores energy * 10 (1000 = 100%).
     */
    public int getSpecPercent()
    {
        return client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
    }

    /**
     * Returns true if special attack energy is at or above the given threshold.
     */
    public boolean canSpec(int thresholdPercent)
    {
        return getSpecPercent() >= thresholdPercent;
    }

    /**
     * Activates the special attack by clicking the spec orb widget.
     * The player must then attack for the spec to fire.
     */
    public void activateSpec()
    {
        client.menuAction(
            -1, WidgetInfo.MINIMAP_SPEC_ORB.getId(),
            MenuAction.CC_OP,
            1, -1,
            "Use", ""
        );
    }

    /**
     * Returns true if the special attack is currently activated (ready to fire).
     * Reads VarPlayer 301 (SPECIAL_ATTACK_ENABLED): 1 = enabled, 0 = disabled.
     */
    public boolean isSpecActive()
    {
        return client.getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 1;
    }
}
