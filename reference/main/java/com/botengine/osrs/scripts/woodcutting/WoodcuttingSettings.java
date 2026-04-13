package com.botengine.osrs.scripts.woodcutting;

import com.botengine.osrs.ui.ScriptSettings;

/** Persisted settings for WoodcuttingScript. */
public class WoodcuttingSettings extends ScriptSettings
{
    public String  treeNameFilter     = "";
    public boolean bankingMode        = false;
    public boolean pickupNests        = true;
    public boolean hopOnCompetition   = false;
    public String  progression        = "";  // e.g. "1:Oak,30:Willow,45:Maple"
}
