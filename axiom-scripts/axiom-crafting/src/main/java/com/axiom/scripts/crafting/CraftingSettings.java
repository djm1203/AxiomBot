package com.axiom.scripts.crafting;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Crafting script.
 * Immutable value object passed into onStart().
 *
 * Item IDs sourced from OSRS Wiki.
 */
public class CraftingSettings extends ScriptSettings
{
    public enum CraftingMethod
    {
        GEM_CUTTING("Chisel on gems"),
        LEATHER    ("Needle on leather"),
        GLASSBLOWING("Glassblowing");

        public final String displayName;

        CraftingMethod(String displayName)
        {
            this.displayName = displayName;
        }
    }

    /** Uncut gems with their IDs, cut gem IDs, and level requirements. */
    public enum GemType
    {
        OPAL        ("Uncut opal",         1625, "Opal",         1609,  1),
        JADE        ("Uncut jade",         1627, "Jade",         1611, 13),
        RED_TOPAZ   ("Uncut red topaz",    1629, "Red topaz",    1613, 16),
        SAPPHIRE    ("Uncut sapphire",     1623, "Sapphire",     1607, 20),
        EMERALD     ("Uncut emerald",      1621, "Emerald",      1605, 27),
        RUBY        ("Uncut ruby",         1619, "Ruby",         1603, 34),
        DIAMOND     ("Uncut diamond",      1617, "Diamond",      1601, 43),
        DRAGONSTONE ("Uncut dragonstone",  1631, "Dragonstone",  1615, 55),
        ONYX        ("Uncut onyx",         6571, "Onyx",         6573, 67),
        ZENYTE      ("Uncut zenyte",      19529, "Zenyte",      19529, 89);

        public final String uncutName;
        public final int    uncutId;
        public final String cutName;
        public final int    cutId;
        public final int    levelRequired;

        /** Chisel — never consumed, always the same tool. */
        public static final int CHISEL_ID = 1755;

        GemType(String uncutName, int uncutId, String cutName, int cutId, int levelRequired)
        {
            this.uncutName     = uncutName;
            this.uncutId       = uncutId;
            this.cutName       = cutName;
            this.cutId         = cutId;
            this.levelRequired = levelRequired;
        }
    }

    /** Leather types with their material IDs, product IDs, and level requirements. */
    public enum LeatherType
    {
        LEATHER_GLOVES  ("Leather",      1741, "Leather gloves",     1059,  1),
        LEATHER_BOOTS   ("Leather",      1741, "Leather boots",      1061,  7),
        LEATHER_COWL    ("Leather",      1741, "Leather cowl",       1167,  9),
        LEATHER_VAMB    ("Leather",      1741, "Leather vambraces",  1063, 11),
        LEATHER_BODY    ("Leather",      1741, "Leather body",       1129, 14),
        LEATHER_CHAPS   ("Leather",      1741, "Leather chaps",      1095, 18),
        COIF            ("Leather",      1741, "Coif",               1169, 38),
        HARD_LEATHER    ("Hard leather", 1743, "Hard leather body",  1131, 28);

        public final String materialName;
        public final int    materialId;
        public final String productName;
        public final int    productId;
        public final int    levelRequired;

        /** Needle and thread — tools kept in inventory across banking cycles. */
        public static final int NEEDLE_ID = 1733;
        public static final int THREAD_ID = 1734;

        LeatherType(String materialName, int materialId, String productName, int productId, int levelRequired)
        {
            this.materialName  = materialName;
            this.materialId    = materialId;
            this.productName   = productName;
            this.productId     = productId;
            this.levelRequired = levelRequired;
        }
    }

    /** Molten glass products using a glassblowing pipe. */
    public enum GlassType
    {
        BEER_GLASS   ("Beer glass",     1),
        CANDLE_LANTERN("Candle lantern", 4),
        OIL_LAMP     ("Oil lamp",       12),
        VIAL         ("Vial",           33),
        UNPOWERED_ORB("Unpowered orb",  46),
        LANTERN_LENS ("Lantern lens",   49),
        LIGHT_ORB    ("Light orb",      87);

        public static final int GLASSBLOWING_PIPE_ID = 1785;
        public static final int MOLTEN_GLASS_ID = 1775;

        public final String productName;
        public final int levelRequired;

        GlassType(String productName, int levelRequired)
        {
            this.productName = productName;
            this.levelRequired = levelRequired;
        }
    }

    public final CraftingMethod method;
    public final GemType        gemType;
    public final LeatherType    leatherType;
    public final GlassType      glassType;

    /** True = walk to bank for more materials when inventory runs out. */
    public final boolean bankForMaterials;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public CraftingSettings(
        CraftingMethod method,
        GemType        gemType,
        LeatherType    leatherType,
        GlassType      glassType,
        boolean        bankForMaterials,
        int            breakIntervalMinutes,
        int            breakDurationMinutes)
    {
        this.method               = method;
        this.gemType              = gemType;
        this.leatherType          = leatherType;
        this.glassType            = glassType != null ? glassType : GlassType.VIAL;
        this.bankForMaterials     = bankForMaterials;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static CraftingSettings defaults()
    {
        return new CraftingSettings(
            CraftingMethod.GEM_CUTTING,
            GemType.SAPPHIRE,
            LeatherType.LEATHER_GLOVES,
            GlassType.VIAL,
            false, 60, 5
        );
    }
}
