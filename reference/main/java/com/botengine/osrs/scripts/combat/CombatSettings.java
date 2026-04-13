package com.botengine.osrs.scripts.combat;

import com.botengine.osrs.ui.ScriptSettings;

/**
 * Persisted settings for CombatScript.
 * Populated by {@link CombatConfigDialog} and saved to
 * {@code ~/.runelite/axiom/combat.json}.
 */
public class CombatSettings extends ScriptSettings
{
    public String  targetNpcName           = "Hill Giant";
    public int     eatThresholdPercent     = 50;
    public boolean usePrayer               = false;
    public String  protectivePrayer        = "PROTECT_FROM_MELEE";
    public String  offensivePrayer         = "";
    public int     prayerPotPercent        = 30;
    public boolean lootEnabled             = false;
    public String  lootItemIds             = "995";
    public boolean useSpec                 = false;
    public int     specThreshold           = 100;
    public boolean bankingMode             = false;
    public boolean sandCrabsMode           = false;
    public boolean cannonMode              = false;
    public int     cannonRefillThreshold   = 20;
    public boolean emergencyLogoutEnabled  = false;
    public int     emergencyLogoutHpPercent = 10;
}
