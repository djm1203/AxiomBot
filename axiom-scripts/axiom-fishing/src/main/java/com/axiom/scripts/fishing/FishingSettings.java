package com.axiom.scripts.fishing;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Fishing script.
 * Immutable value object passed into onStart().
 *
 * NPC IDs sourced from OSRS Wiki — verify in-game with the RuneLite NPC Indicators
 * or developer overlay if a spot isn't found.
 */
public class FishingSettings extends ScriptSettings
{
    /** The fishing spot type — encodes the NPC ID(s), action name, and action index. */
    public enum SpotType
    {
        SHRIMP_ANCHOVIES("Fishing spot", new int[]{ 1525 },       "Small Net", 1),
        SARDINE_HERRING ("Fishing spot", new int[]{ 1525 },       "Bait",      1),
        TROUT_SALMON    ("Fishing spot", new int[]{ 1527 },       "Lure",      1),
        LOBSTER         ("Cage/Harpoon", new int[]{ 1510 },       "Cage",      1),
        SWORDFISH_TUNA  ("Cage/Harpoon", new int[]{ 1510 },       "Harpoon",   2),
        KARAMBWAN       ("Karambwan vessel", new int[]{ 4712, 4713 }, "Fish",   1),
        MONKFISH        ("Fishing spot", new int[]{ 4316 },       "Small Net", 1),
        SHARK           ("Fishing spot", new int[]{ 1535 },       "Harpoon",   1),
        BARBARIAN       ("Fishing spot", new int[]{ 1542 },       "Use-rod",   1);

        public final String npcName;
        public final int[]  npcIds;
        public final String action;
        /** 1 = NPC_FIRST_OPTION, 2 = NPC_SECOND_OPTION (used to pick the right menu action). */
        public final int    actionIndex;

        SpotType(String npcName, int[] npcIds, String action, int actionIndex)
        {
            this.npcName     = npcName;
            this.npcIds      = npcIds;
            this.action      = action;
            this.actionIndex = actionIndex;
        }

        /** Returns true if the given NPC ID matches this spot type. */
        public boolean matches(int id)
        {
            for (int npcId : npcIds) if (npcId == id) return true;
            return false;
        }
    }

    /** The action to take when the inventory is full. */
    public enum BankAction { DROP_FISH, BANK }

    public final SpotType  spotType;
    public final BankAction bankAction;

    /** True = drop fish immediately; skips banking entirely. */
    public final boolean powerFish;

    /** Average minutes between antiban breaks. */
    public final int breakIntervalMinutes;

    /** Average duration of each antiban break. */
    public final int breakDurationMinutes;

    public FishingSettings(
        SpotType  spotType,
        BankAction bankAction,
        boolean   powerFish,
        int       breakIntervalMinutes,
        int       breakDurationMinutes)
    {
        this.spotType             = spotType;
        this.bankAction           = bankAction;
        this.powerFish            = powerFish;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Sensible defaults used when no config dialog is shown. */
    public static FishingSettings defaults()
    {
        return new FishingSettings(SpotType.SHRIMP_ANCHOVIES, BankAction.DROP_FISH, true, 60, 5);
    }
}
