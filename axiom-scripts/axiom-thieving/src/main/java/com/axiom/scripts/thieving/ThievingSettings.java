package com.axiom.scripts.thieving;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Thieving script.
 *
 * Two methods are supported:
 *   STALL       — "Steal-from" a market stall and drop stolen goods for XP
 *   PICKPOCKET  — pickpocket an NPC; handle stun and drop junk items
 */
public class ThievingSettings extends ScriptSettings
{
    public final ThievingMethod method;
    public final StallType      stallType;
    public final NpcTarget      npcTarget;

    /** When true: drop the stolen items (coins/food) when inventory is full. */
    public final boolean dropJunk;

    /** When true, prefer re-clicking the same nearby NPC instead of nearest search. */
    public final boolean stickyTarget;

    /**
     * Item IDs of food available in inventory, checked in order.
     * Used for eat-while-stunned support.
     */
    public final int[] foodIds;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    // ── Method ────────────────────────────────────────────────────────────────

    public enum ThievingMethod
    {
        STALL      ("Steal from stall"),
        PICKPOCKET ("Pickpocket NPC"),
        BLACKJACK  ("Blackjack NPC");

        public final String displayName;
        ThievingMethod(String displayName) { this.displayName = displayName; }
    }

    // ── Stall types ───────────────────────────────────────────────────────────

    /**
     * Stalls are found by name, not by object ID, to avoid wrong-ID issues.
     * The stall name is used in a case-insensitive exact match against
     * SceneObject.getName().
     *
     * Stolen item IDs are metadata — they are not used for game logic,
     * only for the "drop junk" feature.
     */
    public enum StallType
    {
        BAKERS_STALL ("Baker's stall",  5,  new int[]{1891, 1897, 1885}, new String[0]),
        TEA_STALL    ("Tea stall",      5,  new int[]{1978},             new String[0]),
        SILK_STALL   ("Silk Stall",    20,  new int[]{950},              new String[]{ "Guard" }),
        FRUIT_STALL  ("Fruit Stall",   25,  new int[]{1963, 1965, 1967}, new String[]{ "Guard" }),
        GEM_STALL    ("Gem Stall",     75,  new int[]{1623, 1621, 1619}, new String[]{ "Guard" });

        public final String stallName;
        public final int    levelRequired;
        public final int[]  stolenItemIds;
        public final String[] guardNpcNames;

        StallType(String stallName, int levelRequired, int[] stolenItemIds, String[] guardNpcNames)
        {
            this.stallName      = stallName;
            this.levelRequired  = levelRequired;
            this.stolenItemIds  = stolenItemIds;
            this.guardNpcNames  = guardNpcNames != null ? guardNpcNames : new String[0];
        }
    }

    // ── NPC targets ───────────────────────────────────────────────────────────

    /**
     * NPC names are used in a case-insensitive exact match.
     * Using equalsIgnoreCase (not contains) prevents "Farmer" from
     * accidentally matching "Master Farmer".
     */
    public enum NpcTarget
    {
        MAN            ("Man",                  1,  new int[]{995}),
        WOMAN          ("Woman",                1,  new int[]{995}),
        FARMER         ("Farmer",              10,  new int[]{995}),
        HAM_MEMBER     ("H.A.M. Member",       15,  new int[]{995, 590, 1511}),
        WARRIOR_WOMAN  ("Warrior woman",       25,  new int[]{995}),
        ROGUE          ("Rogue",               32,  new int[]{995}),
        MASTER_FARMER  ("Master Farmer",       38,  new int[]{5318, 5319, 5324}),
        GUARD          ("Guard",               40,  new int[]{995}),
        KNIGHT         ("Knight of Ardougne",  55,  new int[]{995}),
        BANDIT         ("Bandit",              45,  new int[]{995}),
        MENAPHITE_THUG ("Menaphite Thug",      65,  new int[]{995});

        public final String npcName;
        public final int    levelRequired;
        public final int[]  stolenItemIds;

        NpcTarget(String npcName, int levelRequired, int[] stolenItemIds)
        {
            this.npcName        = npcName;
            this.levelRequired  = levelRequired;
            this.stolenItemIds  = stolenItemIds;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ThievingSettings(
        ThievingMethod method,
        StallType      stallType,
        NpcTarget      npcTarget,
        boolean        dropJunk,
        boolean        stickyTarget,
        int[]          foodIds,
        int            breakIntervalMinutes,
        int            breakDurationMinutes)
    {
        this.method               = method;
        this.stallType            = stallType;
        this.npcTarget            = npcTarget;
        this.dropJunk             = dropJunk;
        this.stickyTarget         = stickyTarget;
        this.foodIds              = foodIds != null ? foodIds : new int[0];
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static ThievingSettings defaults()
    {
        return new ThievingSettings(
            ThievingMethod.STALL,
            StallType.TEA_STALL,
            NpcTarget.MAN,
            true,
            false,
            new int[0],
            60,
            5
        );
    }
}
