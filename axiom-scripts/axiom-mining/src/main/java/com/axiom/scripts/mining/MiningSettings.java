package com.axiom.scripts.mining;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Mining script.
 * Immutable value object passed into onStart().
 *
 * Rock IDs sourced from OSRS Wiki (standard rocks — not motherlode mine).
 */
public class MiningSettings extends ScriptSettings
{
    public enum MiningAction
    {
        BANK("Bank ores"),
        DROP("Drop ores");

        public final String displayName;

        MiningAction(String displayName) { this.displayName = displayName; }
    }

    public enum OreType
    {
        COPPER    ("Copper ore",     436, new int[]{ 10943, 11161 }, new int[]{ 11189 },  1),
        TIN       ("Tin ore",        438, new int[]{ 11360, 11361 }, new int[]{ 11192 },  1),
        IRON      ("Iron ore",       440, new int[]{ 11364, 11365 }, new int[]{ 11193 }, 15),
        COAL      ("Coal",           453, new int[]{ 11366, 11367 }, new int[]{ 11190 }, 30),
        GOLD      ("Gold ore",       444, new int[]{ 11370, 11371 }, new int[]{ 11191 }, 40),
        MITHRIL   ("Mithril ore",    447, new int[]{ 11372, 11373 }, new int[]{ 11194 }, 55),
        ADAMANTITE("Adamantite ore", 449, new int[]{ 11374, 11375 }, new int[]{ 11195 }, 70),
        RUNITE    ("Runite ore",     451, new int[]{ 11376, 11377 }, new int[]{ 11196 }, 85);

        public final String oreName;
        public final int    oreId;
        public final int[]  rockIds;      // mineable rock object IDs
        public final int[]  depletedIds;  // depleted rock object IDs (different appearance)
        public final int    levelRequired;

        /** Pickaxes — never deposited when banking. Covers inventory and equipped. */
        public static final int[] PICKAXE_IDS = {
            1265,  // Bronze pickaxe
            1267,  // Iron pickaxe
            1269,  // Steel pickaxe
            1271,  // Mithril pickaxe
            1273,  // Adamant pickaxe
            1275,  // Rune pickaxe
            11920, // Dragon pickaxe
            23677, // Infernal pickaxe
            25063, // Crystal pickaxe
        };

        OreType(String oreName, int oreId, int[] rockIds, int[] depletedIds, int levelRequired)
        {
            this.oreName       = oreName;
            this.oreId         = oreId;
            this.rockIds       = rockIds;
            this.depletedIds   = depletedIds;
            this.levelRequired = levelRequired;
        }
    }

    public final OreType      oreType;
    public final MiningAction action;

    /** True = drop ore immediately on pickup instead of waiting for full inventory. */
    public final boolean powerMine;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public MiningSettings(
        OreType      oreType,
        MiningAction action,
        boolean      powerMine,
        int          breakIntervalMinutes,
        int          breakDurationMinutes)
    {
        this.oreType              = oreType;
        this.action               = action;
        this.powerMine            = powerMine;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static MiningSettings defaults()
    {
        return new MiningSettings(OreType.IRON, MiningAction.DROP, false, 60, 5);
    }
}
