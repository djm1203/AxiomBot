package com.axiom.plugin.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;

/**
 * Central design token registry for Axiom.
 * Ported from the monolith AxiomTheme.java — visual design unchanged.
 */
public final class AxiomTheme
{
    public static final Color BG_DEEP      = ColorScheme.DARKER_GRAY_COLOR;
    public static final Color BG_PANEL     = ColorScheme.DARK_GRAY_COLOR;
    public static final Color BG_CARD      = new Color(50, 50, 50);
    public static final Color BG_INPUT     = new Color(60, 60, 60);
    public static final Color BG_SELECTED  = new Color(60, 50, 20);
    public static final Color BORDER       = new Color(65, 65, 65);
    public static final Color SEPARATOR    = ColorScheme.MEDIUM_GRAY_COLOR;
    public static final Color ACCENT       = ColorScheme.BRAND_ORANGE;
    public static final Color ACCENT_DARK  = new Color(180, 110, 0);
    public static final Color TEXT         = ColorScheme.TEXT_COLOR;
    public static final Color TEXT_DIM     = ColorScheme.LIGHT_GRAY_COLOR;
    public static final Color TEXT_ON_ACCENT = Color.WHITE;
    public static final Color STATUS_RUNNING  = new Color(0, 200, 90);
    public static final Color STATUS_PAUSED   = new Color(255, 165, 0);
    public static final Color STATUS_STOPPED  = new Color(180, 60, 60);
    public static final Color STATUS_BREAKING = new Color(100, 160, 220);
    public static final Color BTN_PRIMARY   = ACCENT;
    public static final Color BTN_SECONDARY = new Color(70, 70, 70);
    public static final Color BTN_DANGER    = new Color(180, 50, 50);
    public static final Color BTN_NEUTRAL   = new Color(55, 55, 55);
    public static final int PAD_XS = 2;
    public static final int PAD_SM = 4;
    public static final int PAD_MD = 8;
    public static final int PAD_LG = 12;
    public static final int PAD_XL = 18;

    public static Font fontHeading()
    {
        try { return FontManager.getRunescapeBoldFont(); }
        catch (Exception e) { return new Font("SansSerif", Font.BOLD, 13); }
    }

    public static Font fontBody()
    {
        try { return FontManager.getRunescapeFont(); }
        catch (Exception e) { return new Font("SansSerif", Font.PLAIN, 12); }
    }

    public static Font fontSmall()
    {
        try { return FontManager.getRunescapeSmallFont(); }
        catch (Exception e) { return new Font("SansSerif", Font.PLAIN, 11); }
    }

    public static Color scriptIconColor(String scriptName)
    {
        if (scriptName == null) return BTN_SECONDARY;
        switch (scriptName.toLowerCase())
        {
            case "axiom woodcutting":
            case "woodcutting":   return new Color(50,  140, 50);
            case "axiom mining":
            case "mining":        return new Color(100, 100, 180);
            case "axiom fishing":
            case "fishing":       return new Color(50,  120, 200);
            case "axiom cooking":
            case "cooking":       return new Color(200, 100, 30);
            case "axiom combat":
            case "combat":        return new Color(180, 50,  50);
            case "axiom alchemy":
            case "high alchemy":  return new Color(160, 60,  200);
            case "axiom smithing":
            case "smithing":      return new Color(120, 120, 50);
            case "axiom fletching":
            case "fletching":     return new Color(50,  160, 140);
            case "axiom crafting":
            case "crafting":      return new Color(190, 80,  120);
            case "axiom firemaking":
            case "firemaking":    return new Color(220, 80,  20);
            case "axiom agility":
            case "agility":       return new Color(60,  180, 80);
            case "axiom thieving":
            case "thieving":      return new Color(100, 60,  160);
            case "axiom herblore":
            case "herblore":      return new Color(60,  160, 60);
            default:              return BTN_SECONDARY;
        }
    }

    public static String scriptIconText(String scriptName)
    {
        if (scriptName == null) return "??";
        String lower = scriptName.toLowerCase()
            .replace("axiom ", "");
        switch (lower)
        {
            case "woodcutting": return "WC";
            case "mining":      return "MN";
            case "fishing":     return "FS";
            case "cooking":     return "CK";
            case "combat":      return "CB";
            case "high alchemy":
            case "alchemy":     return "AL";
            case "smithing":    return "SM";
            case "fletching":   return "FL";
            case "crafting":    return "CR";
            case "firemaking":  return "FM";
            case "agility":     return "AG";
            case "thieving":    return "TH";
            case "herblore":    return "HB";
            default:            return scriptName.substring(0, Math.min(2, scriptName.length())).toUpperCase();
        }
    }

    private AxiomTheme() {}
}
