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
        // Net spots (Draynor, Lumbridge Swamp) — 1518 + 1525 confirmed from wiki
        SHRIMP_ANCHOVIES("Fishing spot",            new int[]{ 1518, 1525 },
                         "Small Net", 1, new int[]{ 303 }),        // Small Fishing Net

        // Lure/Bait spots (Barbarian Village, Lumbridge) — full ID list from wiki
        // Feathers (314) protected alongside the rod — consumed as bait, mustn't be deposited
        TROUT_SALMON    ("Rod Fishing spot",         new int[]{
                             394, 1506, 1507, 1508, 1509, 1512, 1513,
                             1515, 1516, 1526, 1527, 1529, 3417, 3418
                         }, "Lure", 1, new int[]{ 309, 314 }),     // Fly Fishing Rod + Feathers

        // Cage/Harpoon spots (Catherby, Karamja)
        LOBSTER         ("Fishing spot",             new int[]{ 1510, 1519 },
                         "Cage",    1, new int[]{ 301 }),           // Lobster Pot
        SWORDFISH_TUNA  ("Fishing spot",             new int[]{ 1510, 1519 },
                         "Harpoon", 2, new int[]{ 311 }),           // Harpoon

        // Shark spots
        SHARK           ("Fishing spot",             new int[]{ 1535 },
                         "Harpoon", 1, new int[]{ 311 }),           // Harpoon

        // Monkfish (Swan Song quest required)
        MONKFISH        ("Fishing spot",             new int[]{ 4316 },
                         "Small Net", 1, new int[]{ 303 }),        // Small Fishing Net

        // Barbarian fishing (Otto's Grotto, Barbarian Training required)
        // Feathers (314) protected — used as bait with the rod
        BARBARIAN       ("Fishing spot (barbarian)", new int[]{ 4712, 4713, 7467, 7468 },
                         "Use-rod", 1, new int[]{ 11323, 314 });   // Barbarian Rod + Feathers

        public final String npcName;
        public final int[]  npcIds;
        public final String action;
        /** 1 = NPC_FIRST_OPTION, 2 = NPC_SECOND_OPTION. */
        public final int    actionIndex;
        /** Item IDs to protect from depositAllExcept() — tool(s) and consumable bait. */
        public final int[]  toolItemIds;

        SpotType(String npcName, int[] npcIds, String action, int actionIndex, int[] toolItemIds)
        {
            this.npcName      = npcName;
            this.npcIds       = npcIds;
            this.action       = action;
            this.actionIndex  = actionIndex;
            this.toolItemIds  = toolItemIds;
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
