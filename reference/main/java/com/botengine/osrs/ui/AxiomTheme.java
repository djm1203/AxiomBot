package com.botengine.osrs.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;

/**
 * Central design token registry for Axiom.
 *
 * All colors and fonts in the Axiom UI must come from here — never hardcode
 * hex values or `new Font(...)` directly in component classes. This makes
 * visual updates (color scheme, accent changes) a single-file change.
 *
 * Colors are derived from RuneLite's {@link ColorScheme} where possible so
 * the plugin looks native to the client. Custom values fill the gaps.
 */
public final class AxiomTheme
{
    // ── Backgrounds ───────────────────────────────────────────────────────────

    /** Deepest background: borders, dividers, dialog chrome. */
    public static final Color BG_DEEP   = ColorScheme.DARKER_GRAY_COLOR;    // #1E1E1E

    /** Standard panel surface. Used for AxiomPanel body and dialog backgrounds. */
    public static final Color BG_PANEL  = ColorScheme.DARK_GRAY_COLOR;      // #282828

    /** Card / section surface. Slightly lighter than the panel background. */
    public static final Color BG_CARD   = new Color(50, 50, 50);

    /** Input field background (text fields, combo boxes, spinners). */
    public static final Color BG_INPUT  = new Color(60, 60, 60);

    /** Script row hover / selected background. */
    public static final Color BG_SELECTED = new Color(60, 50, 20);

    // ── Borders & separators ──────────────────────────────────────────────────

    public static final Color BORDER    = new Color(65, 65, 65);
    public static final Color SEPARATOR = ColorScheme.MEDIUM_GRAY_COLOR;    // #4D4D4D

    // ── Accent ────────────────────────────────────────────────────────────────

    /** Primary accent — RuneLite brand orange. Used for section headers, active indicators. */
    public static final Color ACCENT    = ColorScheme.BRAND_ORANGE;         // #DC8A00

    /** Darker accent for button hover/press states. */
    public static final Color ACCENT_DARK = new Color(180, 110, 0);

    // ── Text ──────────────────────────────────────────────────────────────────

    /** Primary body text. */
    public static final Color TEXT      = ColorScheme.TEXT_COLOR;            // #C6C6C6

    /** Secondary / label text. */
    public static final Color TEXT_DIM  = ColorScheme.LIGHT_GRAY_COLOR;     // #A5A5A5

    /** Text on colored (orange/green/red) button backgrounds. */
    public static final Color TEXT_ON_ACCENT = Color.WHITE;

    // ── Status indicators ─────────────────────────────────────────────────────

    public static final Color STATUS_RUNNING = new Color(0, 200, 90);
    public static final Color STATUS_PAUSED  = new Color(255, 165, 0);
    public static final Color STATUS_STOPPED = new Color(180, 60, 60);
    public static final Color STATUS_BREAKING = new Color(100, 160, 220);

    // ── Button backgrounds ────────────────────────────────────────────────────

    public static final Color BTN_PRIMARY   = ACCENT;
    public static final Color BTN_SECONDARY = new Color(70, 70, 70);
    public static final Color BTN_DANGER    = new Color(180, 50, 50);
    public static final Color BTN_NEUTRAL   = new Color(55, 55, 55);

    // ── Padding constants (px) ────────────────────────────────────────────────

    public static final int PAD_XS = 2;
    public static final int PAD_SM = 4;
    public static final int PAD_MD = 8;
    public static final int PAD_LG = 12;
    public static final int PAD_XL = 18;

    // ── Fonts ─────────────────────────────────────────────────────────────────

    /**
     * Large bold heading — script name in dialog header, section names.
     * Falls back to "SansSerif" bold 13pt if FontManager is unavailable.
     */
    public static Font fontHeading()
    {
        try { return FontManager.getRunescapeBoldFont(); }
        catch (Exception e) { return new Font("SansSerif", Font.BOLD, 13); }
    }

    /** Standard body text — script list rows, form labels. */
    public static Font fontBody()
    {
        try { return FontManager.getRunescapeFont(); }
        catch (Exception e) { return new Font("SansSerif", Font.PLAIN, 12); }
    }

    /** Small supplementary text — status badges, help hints. */
    public static Font fontSmall()
    {
        try { return FontManager.getRunescapeSmallFont(); }
        catch (Exception e) { return new Font("SansSerif", Font.PLAIN, 11); }
    }

    // ── Script icon letters ───────────────────────────────────────────────────

    /**
     * Returns the icon background color for a given script name.
     * Used in the AxiomPanel script list rows and dialog headers.
     */
    public static Color scriptIconColor(String scriptName)
    {
        if (scriptName == null) return BTN_SECONDARY;
        switch (scriptName.toLowerCase())
        {
            case "combat":        return new Color(180, 50,  50);
            case "woodcutting":   return new Color(50,  140, 50);
            case "mining":        return new Color(100, 100, 180);
            case "fishing":       return new Color(50,  120, 200);
            case "cooking":       return new Color(200, 100, 30);
            case "alchemy":
            case "high alchemy":  return new Color(160, 60,  200);
            case "smithing":      return new Color(120, 120, 50);
            case "fletching":     return new Color(50,  160, 140);
            case "crafting":
            case "gem cutting":   return new Color(190, 80,  120);
            case "firemaking":    return new Color(220, 80,  20);
            case "agility":       return new Color(60,  180, 80);
            case "thieving":      return new Color(100, 60,  160);
            case "herblore":      return new Color(60,  160, 60);
            default:              return BTN_SECONDARY;
        }
    }

    /**
     * Returns a 2-letter abbreviation for the script icon badge.
     */
    public static String scriptIconText(String scriptName)
    {
        if (scriptName == null) return "??";
        switch (scriptName.toLowerCase())
        {
            case "combat":        return "CB";
            case "woodcutting":   return "WC";
            case "mining":        return "MN";
            case "fishing":       return "FS";
            case "cooking":       return "CK";
            case "alchemy":
            case "high alchemy":  return "AL";
            case "smithing":      return "SM";
            case "fletching":     return "FL";
            case "crafting":
            case "gem cutting":   return "CR";
            case "firemaking":    return "FM";
            case "agility":       return "AG";
            case "thieving":      return "TH";
            case "herblore":      return "HB";
            default:            return scriptName.substring(0, Math.min(2, scriptName.length())).toUpperCase();
        }
    }

    private AxiomTheme() {}
}
