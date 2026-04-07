package com.botengine.osrs.scripts.firemaking;

import com.botengine.osrs.ui.ScriptSettings;

public class FiremakingSettings extends ScriptSettings
{
    /** Item ID of the logs to burn. */
    public int     logItemId   = 1511; // Normal logs

    /** If true, bank when logs run out. If false, stop when logs run out. */
    public boolean bankingMode = false;
}
