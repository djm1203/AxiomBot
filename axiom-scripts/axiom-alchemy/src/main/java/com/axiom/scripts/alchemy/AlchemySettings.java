package com.axiom.scripts.alchemy;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Alchemy script.
 * Immutable value object passed into onStart().
 *
 * Widget IDs sourced from the RuneLite widget inspector:
 *   Spellbook group 218 — High Alch child 29, Low Alch child 25.
 */
public class AlchemySettings extends ScriptSettings
{
    /** Alchemy spell variant with its widget location and level requirement. */
    public enum AlchemySpell
    {
        HIGH_ALCHEMY(55, "High Level Alchemy", 218, 29),
        LOW_ALCHEMY (21, "Low Level Alchemy",  218, 25);

        public final int    levelRequired;
        public final String displayName;
        /** Spellbook widget group ID. */
        public final int    widgetGroup;
        /** Spellbook widget child ID for this spell. */
        public final int    widgetChild;

        AlchemySpell(int levelRequired, String displayName, int widgetGroup, int widgetChild)
        {
            this.levelRequired = levelRequired;
            this.displayName   = displayName;
            this.widgetGroup   = widgetGroup;
            this.widgetChild   = widgetChild;
        }
    }

    public final AlchemySpell spell;

    /** Item ID of the item to alchemise. Set by the user in the config dialog. */
    public final int    targetItemId;

    /** Display name for the target item — used in log messages only. */
    public final String targetItemName;

    /** True = walk to bank for more items when inventory runs out. */
    public final boolean bankForItems;

    public final int breakIntervalMinutes;
    public final int breakDurationMinutes;

    public AlchemySettings(
        AlchemySpell spell,
        int          targetItemId,
        String       targetItemName,
        boolean      bankForItems,
        int          breakIntervalMinutes,
        int          breakDurationMinutes)
    {
        this.spell                = spell;
        this.targetItemId         = targetItemId;
        this.targetItemName       = targetItemName;
        this.bankForItems         = bankForItems;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static AlchemySettings defaults()
    {
        return new AlchemySettings(AlchemySpell.HIGH_ALCHEMY, 0, "", false, 60, 5);
    }
}
