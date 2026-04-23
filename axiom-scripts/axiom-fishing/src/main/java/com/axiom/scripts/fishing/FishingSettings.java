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
                         new String[]{ "Small Net" }, new int[]{ 303 }, new int[0]),        // Small Fishing Net

        // Lure/Bait spots (Barbarian Village, Lumbridge) — full ID list from wiki
        // Feathers (314) protected alongside the rod — consumed as bait, mustn't be deposited
        TROUT_SALMON    ("Rod Fishing spot",         new int[]{
                             394, 1506, 1507, 1508, 1509, 1512, 1513,
                             1515, 1516, 1526, 1527, 1529, 3417, 3418
                         }, new String[]{ "Lure", "Bait" }, new int[]{ 309, 314 }, new int[]{ 314 }),     // Fly Fishing Rod + Feathers

        // Cage/Harpoon spots (Catherby, Karamja)
        LOBSTER         ("Fishing spot",             new int[]{ 1510, 1519 },
                         new String[]{ "Cage" }, new int[]{ 301 }, new int[0]),           // Lobster Pot
        SWORDFISH_TUNA  ("Fishing spot",             new int[]{ 1510, 1519 },
                         new String[]{ "Harpoon" }, new int[]{ 311 }, new int[0]),           // Harpoon

        // Shark spots
        SHARK           ("Fishing spot",             new int[]{ 1535 },
                         new String[]{ "Harpoon" }, new int[]{ 311 }, new int[0]),           // Harpoon

        // Monkfish (Swan Song quest required)
        MONKFISH        ("Fishing spot",             new int[]{ 4316 },
                         new String[]{ "Small Net" }, new int[]{ 303 }, new int[0]),        // Small Fishing Net

        // Barbarian fishing (Otto's Grotto, Barbarian Training required)
        // Feathers (314) protected — used as bait with the rod
        BARBARIAN       ("Fishing spot (barbarian)", new int[]{ 4712, 4713, 7467, 7468 },
                         new String[]{ "Use-rod" }, new int[]{ 11323, 314 }, new int[]{ 314 });   // Barbarian Rod + Feathers

        public final String npcName;
        public final int[]  npcIds;
        public final String[] preferredActions;
        /** Item IDs to protect from depositAllExcept() — tool(s) and consumable bait. */
        public final int[]  toolItemIds;
        /** Consumables required to keep fishing, such as feathers. */
        public final int[]  baitItemIds;

        SpotType(String npcName, int[] npcIds, String[] preferredActions, int[] toolItemIds, int[] baitItemIds)
        {
            this.npcName          = npcName;
            this.npcIds           = npcIds;
            this.preferredActions = preferredActions;
            this.toolItemIds      = toolItemIds;
            this.baitItemIds      = baitItemIds;
        }

        /** Returns true if the given NPC ID matches this spot type. */
        public boolean matches(int id)
        {
            for (int npcId : npcIds) if (npcId == id) return true;
            return false;
        }

        public boolean matchesAction(String[] actions)
        {
            return resolveAction(actions) != null;
        }

        public String resolveAction(String[] actions)
        {
            if (actions == null)
            {
                return null;
            }

            for (String preferredAction : preferredActions)
            {
                for (String action : actions)
                {
                    if (action != null && preferredAction.equalsIgnoreCase(action))
                    {
                        return action;
                    }
                }
            }

            return null;
        }
    }

    /** The action to take when the inventory is full. */
    public enum BankAction { DROP_FISH, BANK }

    public enum LocationPreset
    {
        CUSTOM_START       ("Custom / Start Tile", 10, new String[0], new String[]{ "Banker" }, new String[]{ "Bank" }),
        DRAYNOR_NETS       ("Draynor Nets",         9, new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        BARBARIAN_VILLAGE  ("Barbarian Village",   11, new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        CATHERBY           ("Catherby",            11, new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        PISCATORIS         ("Piscatoris",           9, new String[]{ "Bank deposit box", "Bank chest", "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Use", "Bank" });

        public final String displayName;
        public final int workAreaRadius;
        public final String[] bankObjectNames;
        public final String[] bankNpcNames;
        public final String[] bankActions;

        LocationPreset(String displayName, int workAreaRadius, String[] bankObjectNames, String[] bankNpcNames, String[] bankActions)
        {
            this.displayName = displayName;
            this.workAreaRadius = workAreaRadius;
            this.bankObjectNames = bankObjectNames;
            this.bankNpcNames = bankNpcNames;
            this.bankActions = bankActions;
        }
    }

    public final SpotType  spotType;
    public final BankAction bankAction;
    public final LocationPreset locationPreset;

    /** True = drop fish immediately; skips banking entirely. */
    public final boolean powerFish;

    /** Average minutes between antiban breaks. */
    public final int breakIntervalMinutes;

    /** Average duration of each antiban break. */
    public final int breakDurationMinutes;

    /** Use a fish barrel when available for longer banking cycles. */
    public final boolean useFishBarrel;

    public FishingSettings(
        SpotType  spotType,
        BankAction bankAction,
        LocationPreset locationPreset,
        boolean   powerFish,
        boolean   useFishBarrel,
        int       breakIntervalMinutes,
        int       breakDurationMinutes)
    {
        this.spotType             = spotType;
        this.bankAction           = bankAction;
        this.locationPreset       = locationPreset != null ? locationPreset : LocationPreset.CUSTOM_START;
        this.powerFish            = powerFish;
        this.useFishBarrel        = useFishBarrel;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Sensible defaults used when no config dialog is shown. */
    public static FishingSettings defaults()
    {
        return new FishingSettings(SpotType.SHRIMP_ANCHOVIES, BankAction.DROP_FISH,
            LocationPreset.CUSTOM_START, true, false, 60, 5);
    }
}
