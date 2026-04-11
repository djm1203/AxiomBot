package com.axiom.scripts.woodcutting;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Woodcutting script.
 * Immutable value object passed into onStart().
 */
public class WoodcuttingSettings extends ScriptSettings
{
    /** The tree type to chop. */
    public enum TreeType
    {
        NORMAL("Tree",         1278),
        OAK("Oak tree",        1751),
        WILLOW("Willow tree",  5551),
        MAPLE("Maple tree",    5765),
        YEW("Yew tree",        38502),
        MAGIC("Magic tree",    38504),
        TEAK("Teak",           9036),
        MAHOGANY("Mahogany",   9034);

        public final String objectName;
        public final int    objectId;

        TreeType(String objectName, int objectId)
        {
            this.objectName = objectName;
            this.objectId   = objectId;
        }
    }

    /** The action to take when the inventory is full. */
    public enum BankAction { DROP_LOGS, BANK }

    final TreeType   treeType;
    final BankAction bankAction;

    /** Whether to use power-chopping (drop logs immediately). */
    final boolean powerChop;

    /** Minimum break interval in minutes (antiban hint). */
    final int breakIntervalMinutes;

    /** Minimum break duration in minutes. */
    final int breakDurationMinutes;

    public WoodcuttingSettings(
        TreeType treeType,
        BankAction bankAction,
        boolean powerChop,
        int breakIntervalMinutes,
        int breakDurationMinutes)
    {
        this.treeType             = treeType;
        this.bankAction           = bankAction;
        this.powerChop            = powerChop;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Default settings used when no config dialog is shown. */
    public static WoodcuttingSettings defaults()
    {
        return new WoodcuttingSettings(TreeType.OAK, BankAction.DROP_LOGS, true, 60, 5);
    }
}
