package com.axiom.scripts.woodcutting;

import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Progression;
import com.axiom.api.world.WorldTile;

/**
 * Configuration for the Woodcutting script.
 * Immutable value object passed into onStart().
 */
public class WoodcuttingSettings extends ScriptSettings
{
    /** The tree type to chop. */
    public enum TreeType
    {
        // Multiple IDs cover all visual variants (e.g. Draynor willows span 5551-5553).
        // Verify in-game with the RuneLite Object Markers plugin if a tree isn't found.
        NORMAL  ("Tree",        new int[]{ 1276, 1278 }),
        OAK     ("Oak tree",    new int[]{ 10820 }),
        WILLOW  ("Willow tree", new int[]{ 5551, 5552, 5553, 10819, 10829, 10831, 10833 }),
        // Maple is 10832 only. The previous list collided with MAGIC (10834);
        // including 10834 here meant a Maple session would lock onto Magic trees.
        MAPLE   ("Maple tree",  new int[]{ 10832 }),
        // VERIFY LIVE: yew tree IDs vary by location — confirm with Object Markers.
        YEW     ("Yew tree",    new int[]{ 10822, 10823 }),
        MAGIC   ("Magic tree",  new int[]{ 10834 }),
        // Teak and Mahogany were inverted in the original table.
        // Wiki: Teak = 9036, Mahogany = 9034 (both jungle hardwoods).
        TEAK    ("Teak",        new int[]{ 9036 }),
        MAHOGANY("Mahogany",    new int[]{ 9034 });

        public final String objectName;
        public final int[]  objectIds;

        TreeType(String objectName, int[] objectIds)
        {
            this.objectName = objectName;
            this.objectIds  = objectIds;
        }

        /** Returns true if the given object ID matches this tree type. */
        public boolean matches(int id)
        {
            for (int oid : objectIds) if (oid == id) return true;
            return false;
        }
    }

    public enum LocationPreset
    {
        CUSTOM_START     ("Custom / Start Tile", 10, null, new String[0], new String[]{ "Banker" }, new String[]{ "Bank" }),
        DRAYNOR_WILLOWS  ("Draynor Willows",      9, new WorldTile(3088, 3231, 0), new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        VARROCK_WEST_OAKS("Varrock West Oaks",    9, new WorldTile(3186, 3433, 0), new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        SEERS_MAPLES     ("Seers Maples",        11, new WorldTile(2729, 3480, 0), new String[]{ "Bank booth" }, new String[]{ "Banker" }, new String[]{ "Bank" }),
        WOODCUTTING_GUILD_YEWS("Woodcutting Guild Yews", 11, new WorldTile(1593, 3495, 0), new String[]{ "Bank chest" }, new String[]{ "Banker" }, new String[]{ "Use", "Bank" });

        public final String displayName;
        public final int workAreaRadius;
        public final WorldTile workAnchor;
        public final String[] bankObjectNames;
        public final String[] bankNpcNames;
        public final String[] bankActions;

        LocationPreset(String displayName, int workAreaRadius, WorldTile workAnchor, String[] bankObjectNames, String[] bankNpcNames, String[] bankActions)
        {
            this.displayName = displayName;
            this.workAreaRadius = workAreaRadius;
            this.workAnchor = workAnchor;
            this.bankObjectNames = bankObjectNames;
            this.bankNpcNames = bankNpcNames;
            this.bankActions = bankActions;
        }
    }

    /** The action to take when the inventory is full. */
    public enum BankAction { DROP_LOGS, BANK }

    final TreeType   treeType;
    final BankAction bankAction;
    final LocationPreset locationPreset;

    /** Whether to use power-chopping (drop logs immediately). */
    final boolean powerChop;

    /** Whether to opportunistically participate in nearby Forestry events. */
    final boolean forestryEnabled;

    /**
     * When true, {@code treeType} is ignored and the Progression string is
     * used to pick the best tree for the player's current Woodcutting level.
     */
    final boolean    autoMode;

    /**
     * Progression string for auto-mode. Parsed by
     * {@link Progression#parse(String)} at script start.
     * Default: {@code "1:Oak,30:Willow,45:Maple,60:Yew,75:Magic"}
     */
    final String     progressionString;

    /** Minimum break interval in minutes (antiban hint). */
    final int breakIntervalMinutes;

    /** Minimum break duration in minutes. */
    final int breakDurationMinutes;

    public WoodcuttingSettings(
        TreeType treeType,
        BankAction bankAction,
        LocationPreset locationPreset,
        boolean powerChop,
        boolean forestryEnabled,
        boolean autoMode,
        String progressionString,
        int breakIntervalMinutes,
        int breakDurationMinutes)
    {
        this.treeType             = treeType;
        this.bankAction           = bankAction;
        this.locationPreset       = locationPreset != null ? locationPreset : LocationPreset.CUSTOM_START;
        this.powerChop            = powerChop;
        this.forestryEnabled      = forestryEnabled;
        this.autoMode             = autoMode;
        this.progressionString    = progressionString;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Default settings used when no config dialog is shown. */
    public static WoodcuttingSettings defaults()
    {
        return new WoodcuttingSettings(
            TreeType.OAK, BankAction.DROP_LOGS, LocationPreset.CUSTOM_START, true,
            false,
            false, Progression.DEFAULT_WOODCUTTING,
            60, 5);
    }
}
