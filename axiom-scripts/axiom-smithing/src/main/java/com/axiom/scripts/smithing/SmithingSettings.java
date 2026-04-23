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
    public static final int AMMO_MOULD_ID = 4;

    public enum SmithingMethod
    {
        ANVIL("Anvil smithing"),
        FURNACE("Furnace smelting"),
        BLAST_FURNACE("Blast Furnace");

        public final String displayName;

        SmithingMethod(String displayName)
        {
            this.displayName = displayName;
        }
    }

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

    public enum LocationPreset
    {
        CUSTOM_START ("Custom / Start Tile", 10, new String[0], new String[]{ "Banker" }, new String[]{ "Bank" }),
        VARROCK_WEST ("Varrock West",        8,  new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        EDGEVILLE    ("Edgeville",           8,  new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        AL_KHARID    ("Al Kharid",           8,  new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        BLAST_FURNACE("Blast Furnace",      12, new String[]{ "Bank chest" }, new String[]{ "Banker" }, new String[]{ "Use", "Bank" });

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

    public enum FurnaceProduct
    {
        BRONZE_BAR ("Bronze bar", "Bronze bar", BarType.BRONZE, 436, 438, 0, 0, false, 1),
        IRON_BAR   ("Iron bar",   "Iron bar",   BarType.IRON,   440, -1,  0, 0, false, 1),
        STEEL_BAR  ("Steel bar",  "Steel bar",  BarType.STEEL,  440, -1,  453, 2, false, 1),
        MITHRIL_BAR("Mithril bar","Mithril bar",BarType.MITHRIL,447, -1,  453, 4, false, 1),
        ADAMANT_BAR("Adamant bar","Adamantite bar",BarType.ADAMANT,449,-1,453,6,false,1),
        RUNITE_BAR ("Runite bar", "Runite bar", BarType.RUNE,   451, -1,  453, 8, false, 1),
        CANNONBALL ("Cannonball", "Cannonball", BarType.STEEL,  2353, -1, -1,  0, true, 1);

        public final String displayName;
        public final String makeOptionText;
        public final BarType outputBarType;
        public final int primaryMaterialId;
        public final int secondaryMaterialId;
        public final int coalItemId;
        public final int coalPerPrimary;
        public final boolean requiresAmmoMould;
        public final int outputCount;

        FurnaceProduct(
            String displayName,
            String makeOptionText,
            BarType outputBarType,
            int primaryMaterialId,
            int secondaryMaterialId,
            int coalItemId,
            int coalPerPrimary,
            boolean requiresAmmoMould,
            int outputCount)
        {
            this.displayName = displayName;
            this.makeOptionText = makeOptionText;
            this.outputBarType = outputBarType;
            this.primaryMaterialId = primaryMaterialId;
            this.secondaryMaterialId = secondaryMaterialId;
            this.coalItemId = coalItemId;
            this.coalPerPrimary = coalPerPrimary;
            this.requiresAmmoMould = requiresAmmoMould;
            this.outputCount = outputCount;
        }

        public boolean requiresCoal()
        {
            return coalItemId > 0 && coalPerPrimary > 0;
        }

        public boolean requiresSecondaryOre()
        {
            return secondaryMaterialId > 0;
        }
    }

    // ── Settings fields ───────────────────────────────────────────────────────

    public final SmithingMethod method;
    public final BarType   barType;
    public final SmithItem smithItem;
    public final FurnaceProduct furnaceProduct;
    public final LocationPreset locationPreset;

    /**
     * When true and bars run out, bank to withdraw more bars and continue.
     * When false, the script stops when bars are depleted.
     */
    public final boolean bankForBars;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public SmithingSettings(
        SmithingMethod method,
        BarType   barType,
        SmithItem smithItem,
        FurnaceProduct furnaceProduct,
        LocationPreset locationPreset,
        boolean   bankForBars,
        int       breakIntervalMinutes,
        int       breakDurationMinutes)
    {
        this.method               = method != null ? method : SmithingMethod.ANVIL;
        this.barType              = barType;
        this.smithItem            = smithItem;
        this.furnaceProduct       = furnaceProduct != null ? furnaceProduct : FurnaceProduct.IRON_BAR;
        this.locationPreset       = locationPreset != null ? locationPreset : LocationPreset.CUSTOM_START;
        this.bankForBars          = bankForBars;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static SmithingSettings defaults()
    {
        return new SmithingSettings(
            SmithingMethod.ANVIL,
            BarType.IRON, SmithItem.IRON_DAGGER, FurnaceProduct.IRON_BAR, LocationPreset.CUSTOM_START,
            true, 60, 5);
    }
}
