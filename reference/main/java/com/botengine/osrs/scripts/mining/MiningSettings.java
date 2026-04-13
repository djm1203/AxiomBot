package com.botengine.osrs.scripts.mining;

import com.botengine.osrs.ui.ScriptSettings;

/** Persisted settings for MiningScript. */
public class MiningSettings extends ScriptSettings
{
    public String  rockNameFilter     = "";
    public boolean bankingMode        = false;
    public boolean shiftDrop          = false;
    public boolean hopOnCompetition   = false;
    public boolean motherlodeMode     = false;
    public String  progression        = "";  // e.g. "1:Copper,15:Iron,30:Coal"
}
