package com.axiom.scripts.fletching;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Fletching script.
 * Immutable value object passed into onStart().
 *
 * Item IDs sourced from OSRS Wiki.
 */
public class FletchingSettings extends ScriptSettings
{
    public enum FletchingMethod
    {
        KNIFE_LOGS("Knife on logs"),
        STRING_BOW("String bow"),
        DARTS("Dart tips + feathers");

        public final String displayName;

        FletchingMethod(String displayName)
        {
            this.displayName = displayName;
        }
    }

    /** Logs with their IDs and fletching level requirements. */
    public enum LogType
    {
        NORMAL("Logs",        1511,  1),
        OAK   ("Oak logs",    1521, 15),
        WILLOW("Willow logs", 1519, 30),
        TEAK  ("Teak logs",   6333, 35),
        MAPLE ("Maple logs",  1517, 45),
        YEW   ("Yew logs",    1515, 60),
        MAGIC ("Magic logs",  1513, 75);

        public final String logName;
        public final int    logId;
        public final int    levelRequired;

        /** Knife — never consumed, reused each inventory. */
        public static final int KNIFE_ID = 946;

        LogType(String logName, int logId, int levelRequired)
        {
            this.logName       = logName;
            this.logId         = logId;
            this.levelRequired = levelRequired;
        }
    }

    /** Unstrung bows with their IDs, bowstring ID, and level requirements. */
    public enum BowType
    {
        OAK_SHORTBOW_U   ("Oak shortbow (u)",      54, "Bowstring", 1777, 25),
        OAK_LONGBOW_U    ("Oak longbow (u)",        48, "Bowstring", 1777, 25),
        WILLOW_SHORTBOW_U("Willow shortbow (u)",    60, "Bowstring", 1777, 35),
        WILLOW_LONGBOW_U ("Willow longbow (u)",     54, "Bowstring", 1777, 40),
        MAPLE_SHORTBOW_U ("Maple shortbow (u)",     64, "Bowstring", 1777, 50),
        MAPLE_LONGBOW_U  ("Maple longbow (u)",      69, "Bowstring", 1777, 55),
        YEW_SHORTBOW_U   ("Yew shortbow (u)",       68, "Bowstring", 1777, 65),
        YEW_LONGBOW_U    ("Yew longbow (u)",        62, "Bowstring", 1777, 70);

        public final String bowName;
        public final int    bowId;
        public final String stringName;
        public final int    stringId;
        public final int    levelRequired;

        BowType(String bowName, int bowId, String stringName, int stringId, int levelRequired)
        {
            this.bowName       = bowName;
            this.bowId         = bowId;
            this.stringName    = stringName;
            this.stringId      = stringId;
            this.levelRequired = levelRequired;
        }
    }

    /** Dart tips with their IDs, finished dart IDs, and level requirements. */
    public enum DartType
    {
        BRONZE  ("Bronze dart tip",   819, "Bronze dart",  882, 10),
        IRON    ("Iron dart tip",     820, "Iron dart",    863, 22),
        STEEL   ("Steel dart tip",    821, "Steel dart",   864, 37),
        MITHRIL ("Mithril dart tip",  822, "Mithril dart", 865, 52),
        ADAMANT ("Adamant dart tip",  823, "Adamant dart", 866, 67),
        RUNE    ("Rune dart tip",     824, "Rune dart",    867, 81),
        DRAGON  ("Dragon dart tip", 11232, "Dragon dart", 11230, 95);

        public final String tipName;
        public final int    tipId;
        public final String dartName;
        public final int    dartId;
        public final int    levelRequired;

        /** Feathers — always the same secondary material. */
        public static final int FEATHER_ID = 314;

        DartType(String tipName, int tipId, String dartName, int dartId, int levelRequired)
        {
            this.tipName       = tipName;
            this.tipId         = tipId;
            this.dartName      = dartName;
            this.dartId        = dartId;
            this.levelRequired = levelRequired;
        }
    }

    public final FletchingMethod method;
    public final LogType         logType;
    public final BowType         bowType;
    public final DartType        dartType;

    /** True = walk to bank for more materials when inventory runs out. */
    public final boolean bankForMaterials;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public FletchingSettings(
        FletchingMethod method,
        LogType         logType,
        BowType         bowType,
        DartType        dartType,
        boolean         bankForMaterials,
        int             breakIntervalMinutes,
        int             breakDurationMinutes)
    {
        this.method               = method;
        this.logType              = logType;
        this.bowType              = bowType;
        this.dartType             = dartType;
        this.bankForMaterials     = bankForMaterials;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static FletchingSettings defaults()
    {
        return new FletchingSettings(
            FletchingMethod.KNIFE_LOGS,
            LogType.OAK,
            BowType.OAK_SHORTBOW_U,
            DartType.BRONZE,
            false, 60, 5
        );
    }
}
