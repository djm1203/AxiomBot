package com.botengine.osrs.scripts.thieving;

import com.botengine.osrs.ui.ScriptSettings;

public class ThievingSettings extends ScriptSettings
{
    /** "Stall" or "Pickpocket". */
    public String mode           = "Stall";

    /** GameObject ID of the stall to steal from (Stall mode). */
    public int    stallId        = 11730; // Bakery stall

    /** NPC ID to pickpocket (Pickpocket mode). */
    public int    targetNpcId    = 1;     // Man

    /** Eat food when HP% drops to or below this value. */
    public int    eatThresholdPct = 50;

    /** Item ID of the food to eat. */
    public int    foodItemId     = 1993;  // Cooked chicken

    /** Bank when inventory is full. */
    public boolean bankWhenFull  = false;
}
