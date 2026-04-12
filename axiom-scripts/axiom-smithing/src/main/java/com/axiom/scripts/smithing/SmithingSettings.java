package com.axiom.scripts.smithing;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Smithing script.
 * Immutable value object passed into onStart().
 *
 * BarType → SmithItem is a cascading selection: the config dialog filters
 * SmithItem values to only those whose requiredBar matches the chosen BarType.
 *
 * Item IDs sourced from OSRS Wiki.
 */
public class SmithingSettings extends ScriptSettings
{
    /** The hammer item ID — never deposited when banking. */
    public static final int HAMMER_ID = 2347;

    // ── Bar types ─────────────────────────────────────────────────────────────

    public enum BarType
    {
        BRONZE  ("Bronze bar",     2349,  1),
        IRON    ("Iron bar",       2351, 15),
        STEEL   ("Steel bar",      2353, 30),
        MITHRIL ("Mithril bar",    2359, 50),
        ADAMANT ("Adamantite bar", 2361, 70),
        RUNE    ("Runite bar",     2363, 85);

        public final String barName;
        public final int    barItemId;
        public final int    levelRequired;

        BarType(String barName, int barItemId, int levelRequired)
        {
            this.barName       = barName;
            this.barItemId     = barItemId;
            this.levelRequired = levelRequired;
        }
    }

    // ── Smithable items ───────────────────────────────────────────────────────

    /**
     * Items that can be smithed at an anvil.
     * Each entry knows which bar it requires and how many bars per item.
     * This drives both the config dialog filter and the BANKING withdrawal.
     */
    public enum SmithItem
    {
        // ── Bronze ────────────────────────────────────────────────────────────
        BRONZE_DAGGER     ("Bronze dagger",      1205, BarType.BRONZE,  1),
        BRONZE_SWORD      ("Bronze sword",       1277, BarType.BRONZE,  1),
        BRONZE_LONGSWORD  ("Bronze longsword",   1291, BarType.BRONZE,  2),
        BRONZE_2H_SWORD   ("Bronze 2h sword",    1307, BarType.BRONZE,  3),
        BRONZE_PLATEBODY  ("Bronze platebody",   1117, BarType.BRONZE,  5),

        // ── Iron ──────────────────────────────────────────────────────────────
        IRON_DAGGER       ("Iron dagger",        1203, BarType.IRON,    1),
        IRON_SWORD        ("Iron sword",         1279, BarType.IRON,    1),
        IRON_LONGSWORD    ("Iron longsword",     1293, BarType.IRON,    2),
        IRON_2H_SWORD     ("Iron 2h sword",      1309, BarType.IRON,    3),
        IRON_PLATEBODY    ("Iron platebody",     1115, BarType.IRON,    5),

        // ── Steel ─────────────────────────────────────────────────────────────
        STEEL_DAGGER      ("Steel dagger",       1207, BarType.STEEL,   1),
        STEEL_SWORD       ("Steel sword",        1281, BarType.STEEL,   1),
        STEEL_LONGSWORD   ("Steel longsword",    1295, BarType.STEEL,   2),
        STEEL_2H_SWORD    ("Steel 2h sword",     1311, BarType.STEEL,   3),
        STEEL_PLATEBODY   ("Steel platebody",    1119, BarType.STEEL,   5),

        // ── Mithril ───────────────────────────────────────────────────────────
        MITHRIL_DAGGER    ("Mithril dagger",     1209, BarType.MITHRIL, 1),
        MITHRIL_SWORD     ("Mithril sword",      1283, BarType.MITHRIL, 1),
        MITHRIL_LONGSWORD ("Mithril longsword",  1297, BarType.MITHRIL, 2),
        MITHRIL_2H_SWORD  ("Mithril 2h sword",   1313, BarType.MITHRIL, 3),
        MITHRIL_PLATEBODY ("Mithril platebody",  1121, BarType.MITHRIL, 5),

        // ── Adamant ───────────────────────────────────────────────────────────
        ADAMANT_DAGGER    ("Adamant dagger",     1211, BarType.ADAMANT, 1),
        ADAMANT_SWORD     ("Adamant sword",      1285, BarType.ADAMANT, 1),
        ADAMANT_LONGSWORD ("Adamant longsword",  1299, BarType.ADAMANT, 2),
        ADAMANT_2H_SWORD  ("Adamant 2h sword",   1315, BarType.ADAMANT, 3),
        ADAMANT_PLATEBODY ("Adamant platebody",  1123, BarType.ADAMANT, 5),

        // ── Rune ──────────────────────────────────────────────────────────────
        RUNE_DAGGER       ("Rune dagger",        1213, BarType.RUNE,    1),
        RUNE_SWORD        ("Rune sword",         1289, BarType.RUNE,    1),
        RUNE_LONGSWORD    ("Rune longsword",     1303, BarType.RUNE,    2),
        RUNE_2H_SWORD     ("Rune 2h sword",      1319, BarType.RUNE,    3),
        RUNE_PLATEBODY    ("Rune platebody",     1127, BarType.RUNE,    5);

        public final String   displayName;
        public final int      itemId;
        public final BarType  requiredBar;
        public final int      barsPerItem;

        SmithItem(String displayName, int itemId, BarType requiredBar, int barsPerItem)
        {
            this.displayName  = displayName;
            this.itemId       = itemId;
            this.requiredBar  = requiredBar;
            this.barsPerItem  = barsPerItem;
        }
    }

    // ── Settings fields ───────────────────────────────────────────────────────

    public final BarType   barType;
    public final SmithItem smithItem;

    /**
     * When true and bars run out, bank to withdraw more bars and continue.
     * When false, the script stops when bars are depleted.
     */
    public final boolean bankForBars;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public SmithingSettings(
        BarType   barType,
        SmithItem smithItem,
        boolean   bankForBars,
        int       breakIntervalMinutes,
        int       breakDurationMinutes)
    {
        this.barType              = barType;
        this.smithItem            = smithItem;
        this.bankForBars          = bankForBars;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static SmithingSettings defaults()
    {
        return new SmithingSettings(
            BarType.IRON, SmithItem.IRON_DAGGER,
            true, 60, 5);
    }
}
