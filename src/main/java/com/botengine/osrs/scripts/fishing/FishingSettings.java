package com.botengine.osrs.scripts.fishing;

import com.botengine.osrs.ui.ScriptSettings;

/** Persisted settings for FishingScript. */
public class FishingSettings extends ScriptSettings
{
    public String  fishingAction      = "Lure";
    public boolean bankingMode        = false;
    public boolean shiftDrop          = false;
    public String  progression        = "";  // e.g. "1:Net,20:Fly,40:Harpoon"
}
