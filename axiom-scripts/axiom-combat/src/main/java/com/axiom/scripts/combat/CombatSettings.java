package com.axiom.scripts.combat;

import com.axiom.api.player.Prayer;
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
    public enum CombatPrayer
    {
        NONE("None", null),
        THICK_SKIN("Thick Skin", Prayer.PrayerType.THICK_SKIN),
        BURST_OF_STRENGTH("Burst of Strength", Prayer.PrayerType.BURST_OF_STRENGTH),
        CLARITY_OF_THOUGHT("Clarity of Thought", Prayer.PrayerType.CLARITY_OF_THOUGHT),
        ULTIMATE_STRENGTH("Ultimate Strength", Prayer.PrayerType.ULTIMATE_STRENGTH),
        INCREDIBLE_REFLEXES("Incredible Reflexes", Prayer.PrayerType.INCREDIBLE_REFLEXES),
        PROTECT_FROM_MELEE("Protect from Melee", Prayer.PrayerType.PROTECT_FROM_MELEE),
        PROTECT_FROM_MISSILES("Protect from Missiles", Prayer.PrayerType.PROTECT_FROM_MISSILES),
        PROTECT_FROM_MAGIC("Protect from Magic", Prayer.PrayerType.PROTECT_FROM_MAGIC),
        CHIVALRY("Chivalry", Prayer.PrayerType.CHIVALRY),
        PIETY("Piety", Prayer.PrayerType.PIETY),
        EAGLE_EYE("Eagle Eye", Prayer.PrayerType.EAGLE_EYE),
        RIGOUR("Rigour", Prayer.PrayerType.RIGOUR),
        MYSTIC_MIGHT("Mystic Might", Prayer.PrayerType.MYSTIC_MIGHT),
        AUGURY("Augury", Prayer.PrayerType.AUGURY);

        public final String displayName;
        public final Prayer.PrayerType prayerType;

        CombatPrayer(String displayName, Prayer.PrayerType prayerType)
        {
            this.displayName = displayName;
            this.prayerType = prayerType;
        }

        public boolean isEnabled()
        {
            return prayerType != null;
        }
    }

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

    /** Maximum distance to travel for configured loot. */
    public final int maxLootDistance;

    /** If true, bank for more food instead of stopping or continuing without it. */
    public final boolean bankForFood;

    /** If true, stop when no food items remain in inventory. */
    public final boolean stopWhenOutOfFood;

    /** Optional prayer to enable while actively fighting. */
    public final CombatPrayer combatPrayer;

    /** If true, hold the recorded start tile as a safespot anchor between actions. */
    public final boolean safespotEnabled;

    /** Maximum distance a target may be from the safespot anchor. */
    public final int safespotTargetDistance;

    /** If true, manage a dwarf multicannon alongside normal combat. */
    public final boolean cannonEnabled;

    /** If true, place a cannon automatically when one is not deployed. */
    public final boolean setupCannon;

    /** Ticks between cannon reload / fire attempts while ammo is available. */
    public final int cannonReloadIntervalTicks;

    /** If true, perform a simple walk-away reset after long idle periods with no targets. */
    public final boolean aggressionResetEnabled;

    /** Idle ticks with no targets before attempting an aggression reset. */
    public final int aggressionResetIdleTicks;

    /** Number of tiles to walk away from the combat anchor during the reset. */
    public final int aggressionResetDistance;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public CombatSettings(
        String  npcName,
        int     eatAtHpPercent,
        int[]   foodIds,
        int[]   lootItemIds,
        int     maxLootDistance,
        boolean bankForFood,
        boolean stopWhenOutOfFood,
        CombatPrayer combatPrayer,
        boolean safespotEnabled,
        int     safespotTargetDistance,
        boolean cannonEnabled,
        boolean setupCannon,
        int     cannonReloadIntervalTicks,
        boolean aggressionResetEnabled,
        int     aggressionResetIdleTicks,
        int     aggressionResetDistance,
        int     breakIntervalMinutes,
        int     breakDurationMinutes)
    {
        this.npcName              = npcName;
        this.eatAtHpPercent       = eatAtHpPercent;
        this.foodIds              = foodIds != null      ? foodIds      : new int[0];
        this.lootItemIds          = lootItemIds != null  ? lootItemIds  : new int[0];
        this.maxLootDistance      = Math.max(1, maxLootDistance);
        this.bankForFood          = bankForFood;
        this.stopWhenOutOfFood    = stopWhenOutOfFood;
        this.combatPrayer         = combatPrayer != null ? combatPrayer : CombatPrayer.NONE;
        this.safespotEnabled      = safespotEnabled;
        this.safespotTargetDistance = Math.max(1, safespotTargetDistance);
        this.cannonEnabled        = cannonEnabled;
        this.setupCannon          = setupCannon;
        this.cannonReloadIntervalTicks = Math.max(5, cannonReloadIntervalTicks);
        this.aggressionResetEnabled = aggressionResetEnabled;
        this.aggressionResetIdleTicks = Math.max(5, aggressionResetIdleTicks);
        this.aggressionResetDistance = Math.max(3, aggressionResetDistance);
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
            10,
            true,
            true,
            CombatPrayer.NONE,
            false,
            6,
            false,
            false,
            15,
            false,
            30,
            12,
            60,
            5
        );
    }
}
