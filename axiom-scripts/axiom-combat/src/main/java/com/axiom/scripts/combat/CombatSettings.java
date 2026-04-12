package com.axiom.scripts.combat;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Combat script.
 *
 * foodIds: item IDs to eat when HP drops below threshold (checked in priority order).
 * lootItemIds: item IDs to pick up from the ground after kills. Empty = no looting.
 * stopWhenOutOfFood: stop the script if no food remains in inventory.
 */
public class CombatSettings extends ScriptSettings
{
    /** NPC name to target (case-insensitive). */
    public final String npcName;

    /** Eat food when HP drops to or below this percentage (0–100). */
    public final int eatAtHpPercent;

    /**
     * Item IDs of food to eat, checked in order.
     * Example: {385, 379, 361} = shark, lobster, tuna.
     */
    public final int[] foodIds;

    /**
     * Item IDs to loot from the ground after kills.
     * Empty array disables looting.
     */
    public final int[] lootItemIds;

    /** If true, stop when no food items remain in inventory. */
    public final boolean stopWhenOutOfFood;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public CombatSettings(
        String  npcName,
        int     eatAtHpPercent,
        int[]   foodIds,
        int[]   lootItemIds,
        boolean stopWhenOutOfFood,
        int     breakIntervalMinutes,
        int     breakDurationMinutes)
    {
        this.npcName              = npcName;
        this.eatAtHpPercent       = eatAtHpPercent;
        this.foodIds              = foodIds != null      ? foodIds      : new int[0];
        this.lootItemIds          = lootItemIds != null  ? lootItemIds  : new int[0];
        this.stopWhenOutOfFood    = stopWhenOutOfFood;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static CombatSettings defaults()
    {
        return new CombatSettings(
            "Hill Giant",
            50,
            new int[]{379},   // lobster
            new int[0],
            true,
            60,
            5
        );
    }
}
