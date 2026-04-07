package com.botengine.osrs.scripts.herblore;

import com.botengine.osrs.ui.ScriptSettings;

public class HerbloreSettings extends ScriptSettings
{
    /** "Clean" or "Make Potion". */
    public String mode          = "Clean";

    /** Grimy herb item ID (Clean mode). */
    public int    herbItemId    = 169;  // Grimy guam

    /** Clean herb item ID (Make Potion mode). */
    public int    cleanHerbId   = 199;  // Clean guam

    /** Secondary ingredient item ID (Make Potion mode). */
    public int    secondaryId   = 221;  // Eye of newt (attack potion)

    /** Vial of water item ID. */
    public int    vialOfWaterId = 227;
}
