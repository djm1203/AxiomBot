package com.axiom.scripts.firemaking;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Firemaking script.
 * Immutable value object passed into onStart().
 *
 * Item IDs sourced from OSRS Wiki.
 */
public class FiremakingSettings extends ScriptSettings
{
    /** Log types with their inventory item IDs and required Firemaking level. */
    public enum LogType
    {
        NORMAL   ("Logs",           1511,  1),
        OAK      ("Oak logs",       1521, 15),
        WILLOW   ("Willow logs",    1519, 30),
        TEAK     ("Teak logs",      6333, 35),
        MAPLE    ("Maple logs",     1517, 45),
        MAHOGANY ("Mahogany logs",  6332, 50),
        YEW      ("Yew logs",       1515, 60),
        MAGIC    ("Magic logs",     1513, 75),
        REDWOOD  ("Redwood logs",  19669, 90);

        public final String itemName;
        public final int    itemId;
        public final int    levelRequired;

        /** Tinderbox item ID — same for all log types. */
        public static final int TINDERBOX_ID = 590;

        LogType(String itemName, int itemId, int levelRequired)
        {
            this.itemName      = itemName;
            this.itemId        = itemId;
            this.levelRequired = levelRequired;
        }
    }

    public final LogType logType;

    /** True = walk to bank for more logs when inventory runs out. */
    public final boolean bankForLogs;

    /** Average minutes between antiban breaks. */
    public final int breakIntervalMinutes;

    /** Average duration of each antiban break. */
    public final int breakDurationMinutes;

    public FiremakingSettings(
        LogType logType,
        boolean bankForLogs,
        int     breakIntervalMinutes,
        int     breakDurationMinutes)
    {
        this.logType              = logType;
        this.bankForLogs          = bankForLogs;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Sensible defaults used when no config dialog is shown. */
    public static FiremakingSettings defaults()
    {
        return new FiremakingSettings(LogType.NORMAL, false, 60, 5);
    }
}
