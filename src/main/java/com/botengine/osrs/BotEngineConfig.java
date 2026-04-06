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
 *   - Antiban    — break scheduling and human-like timing
 *   - Combat     — target NPC name, food, HP threshold
 *   - Woodcutting — tree type filter
 *   - Mining     — ore type filter
 *   - Fishing    — fishing action
 *   - Debug      — developer overlays and logging
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

    // ── Combat section ────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Combat",
        description = "Settings for the Combat script",
        position = 2
    )
    String combatSection = "combat";

    @ConfigItem(
        keyName = "combatTarget",
        name = "Target NPC name",
        description = "Name of the NPC to attack (e.g. Hill Giant, Sand Crab, Moss Giant)",
        section = combatSection,
        position = 0
    )
    default String combatTarget() { return "Hill Giant"; }

    @ConfigItem(
        keyName = "combatEatPercent",
        name = "Eat below HP %",
        description = "Eat food when HP drops at or below this percentage",
        section = combatSection,
        position = 1
    )
    @Range(min = 10, max = 95)
    default int combatEatPercent() { return 50; }

    // ── Woodcutting section ───────────────────────────────────────────────────

    @ConfigSection(
        name = "Woodcutting",
        description = "Settings for the Woodcutting script",
        position = 3
    )
    String woodcuttingSection = "woodcutting";

    @ConfigItem(
        keyName = "woodcuttingTreeName",
        name = "Tree name filter",
        description = "Only chop trees whose name contains this text (e.g. Oak, Willow, Yew). Leave blank for nearest tree of any type.",
        section = woodcuttingSection,
        position = 0
    )
    default String woodcuttingTreeName() { return ""; }

    // ── Mining section ────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Mining",
        description = "Settings for the Mining script",
        position = 4
    )
    String miningSection = "mining";

    @ConfigItem(
        keyName = "miningRockName",
        name = "Rock name filter",
        description = "Only mine rocks whose name contains this text (e.g. Iron, Coal, Mithril). Leave blank for nearest rock of any type.",
        section = miningSection,
        position = 0
    )
    default String miningRockName() { return ""; }

    // ── Fishing section ───────────────────────────────────────────────────────

    @ConfigSection(
        name = "Fishing",
        description = "Settings for the Fishing script",
        position = 5
    )
    String fishingSection = "fishing";

    @ConfigItem(
        keyName = "fishingAction",
        name = "Click action",
        description = "The menu option to use on the fishing spot (e.g. Lure, Bait, Net, Cage, Harpoon)",
        section = fishingSection,
        position = 0
    )
    default String fishingAction() { return "Lure"; }

    // ── Debug section ─────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Debug",
        description = "Developer tools and debug overlays",
        position = 6
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
