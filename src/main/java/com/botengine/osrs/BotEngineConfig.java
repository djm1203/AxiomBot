package com.botengine.osrs;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Top-level configuration interface for the Bot Engine plugin.
 *
 * RuneLite reads this interface and auto-generates a settings panel.
 * Values are persisted to ~/.runelite/config.ini across sessions.
 *
 * Sections:
 *   - Antiban   — break scheduling and human-like timing
 *   - Debug     — developer overlays and logging
 */
@ConfigGroup("botengine")
public interface BotEngineConfig extends Config
{
    // ── Antiban section ───────────────────────────────────────────────────────

    @ConfigSection(
        name = "Antiban",
        description = "Break scheduling and human-like behavior settings",
        position = 0
    )
    String antibanSection = "antiban";

    @ConfigItem(
        keyName = "breakInterval",
        name = "Break every (minutes)",
        description = "Average number of minutes between antiban breaks",
        section = antibanSection,
        position = 0
    )
    @Range(min = 5, max = 180)
    default int breakInterval() { return 45; }

    @ConfigItem(
        keyName = "breakDuration",
        name = "Break duration (minutes)",
        description = "Average length of each antiban break",
        section = antibanSection,
        position = 1
    )
    @Range(min = 1, max = 60)
    default int breakDuration() { return 7; }

    @ConfigItem(
        keyName = "mouseJitter",
        name = "Mouse jitter radius (px)",
        description = "Random pixel offset applied to click coordinates",
        section = antibanSection,
        position = 2
    )
    @Range(min = 0, max = 10)
    default int mouseJitter() { return 3; }

    @ConfigItem(
        keyName = "antibanEnabled",
        name = "Enable antiban",
        description = "When disabled, scripts run without breaks or timing randomization",
        section = antibanSection,
        position = 3
    )
    default boolean antibanEnabled() { return true; }

    // ── Debug section ─────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Debug",
        description = "Developer tools and debug overlays",
        position = 1
    )
    String debugSection = "debug";

    @ConfigItem(
        keyName = "debugOverlay",
        name = "Show debug overlay",
        description = "Renders tile highlights, NPC boxes, click targets, and state name",
        section = debugSection,
        position = 0
    )
    default boolean debugOverlay() { return false; }

    @ConfigItem(
        keyName = "debugLogging",
        name = "Verbose logging",
        description = "Enables DEBUG level log output (check RuneLite console)",
        section = debugSection,
        position = 1
    )
    default boolean debugLogging() { return false; }
}
