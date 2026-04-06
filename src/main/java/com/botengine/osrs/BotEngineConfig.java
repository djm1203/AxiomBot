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

    @ConfigItem(
        keyName = "combatUsePrayer",
        name = "Use protective prayer",
        description = "Activate a protective prayer while in combat",
        section = combatSection,
        position = 2
    )
    default boolean combatUsePrayer() { return false; }

    @ConfigItem(
        keyName = "combatPrayerType",
        name = "Protective prayer",
        description = "Which prayer to activate (PROTECT_FROM_MELEE, PROTECT_FROM_MISSILES, PROTECT_FROM_MAGIC)",
        section = combatSection,
        position = 3
    )
    default String combatPrayerType() { return "PROTECT_FROM_MELEE"; }

    @ConfigItem(
        keyName = "combatOffensivePrayer",
        name = "Offensive prayer",
        description = "Offensive prayer to activate while attacking (PIETY, RIGOUR, AUGURY, or blank for none)",
        section = combatSection,
        position = 4
    )
    default String combatOffensivePrayer() { return ""; }

    @ConfigItem(
        keyName = "combatPrayerPotPercent",
        name = "Drink prayer pot below %",
        description = "Drink prayer potion when prayer points drop at or below this percentage",
        section = combatSection,
        position = 5
    )
    @Range(min = 5, max = 80)
    default int combatPrayerPotPercent() { return 30; }

    @ConfigItem(
        keyName = "combatLootEnabled",
        name = "Pick up loot",
        description = "Pick up ground items after kills",
        section = combatSection,
        position = 6
    )
    default boolean combatLootEnabled() { return false; }

    @ConfigItem(
        keyName = "combatLootItemIds",
        name = "Loot item IDs",
        description = "Comma-separated item IDs to loot (e.g. 995,4151,1079). Leave blank to loot nothing.",
        section = combatSection,
        position = 7
    )
    default String combatLootItemIds() { return "995"; }

    @ConfigItem(
        keyName = "combatUseSpec",
        name = "Use special attack",
        description = "Activate special attack when spec bar reaches threshold",
        section = combatSection,
        position = 8
    )
    default boolean combatUseSpec() { return false; }

    @ConfigItem(
        keyName = "combatSpecPercent",
        name = "Spec at % energy",
        description = "Use special attack when spec bar is at or above this percentage",
        section = combatSection,
        position = 9
    )
    @Range(min = 25, max = 100)
    default int combatSpecPercent() { return 100; }

    @ConfigItem(
        keyName = "combatBankingMode",
        name = "Banking mode",
        description = "When inventory is full, walk to nearest bank instead of dropping",
        section = combatSection,
        position = 10
    )
    default boolean combatBankingMode() { return false; }

    @ConfigItem(
        keyName = "combatSandCrabsMode",
        name = "Sand Crabs mode",
        description = "Walk away and return to reset crab aggro when crabs go passive",
        section = combatSection,
        position = 11
    )
    default boolean combatSandCrabsMode() { return false; }

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

    @ConfigItem(
        keyName = "woodcuttingBankingMode",
        name = "Banking mode",
        description = "Walk to bank and deposit logs instead of dropping them",
        section = woodcuttingSection,
        position = 1
    )
    default boolean woodcuttingBankingMode() { return false; }

    @ConfigItem(
        keyName = "woodcuttingPickupNests",
        name = "Pick up bird nests",
        description = "Pick up bird nests that drop while chopping",
        section = woodcuttingSection,
        position = 2
    )
    default boolean woodcuttingPickupNests() { return true; }

    @ConfigItem(
        keyName = "woodcuttingHopOnCompetition",
        name = "Hop on competition",
        description = "World hop when another player is chopping within 5 tiles",
        section = woodcuttingSection,
        position = 3
    )
    default boolean woodcuttingHopOnCompetition() { return false; }

    @ConfigItem(
        keyName = "woodcuttingProgression",
        name = "Progression (level:tree,...)",
        description = "Level-based tree progression. Format: '1:Oak,30:Willow,45:Maple,60:Yew'. Leave blank to use tree name filter only.",
        section = woodcuttingSection,
        position = 4
    )
    default String woodcuttingProgression() { return ""; }

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

    @ConfigItem(
        keyName = "miningBankingMode",
        name = "Banking mode",
        description = "Walk to bank and deposit ore instead of dropping it",
        section = miningSection,
        position = 1
    )
    default boolean miningBankingMode() { return false; }

    @ConfigItem(
        keyName = "miningShiftDrop",
        name = "Shift-drop mode",
        description = "Drop all ore in one pass without delays (faster power-mining)",
        section = miningSection,
        position = 2
    )
    default boolean miningShiftDrop() { return false; }

    @ConfigItem(
        keyName = "miningHopOnCompetition",
        name = "Hop on competition",
        description = "World hop when another player is mining within 5 tiles",
        section = miningSection,
        position = 3
    )
    default boolean miningHopOnCompetition() { return false; }

    @ConfigItem(
        keyName = "miningProgression",
        name = "Progression (level:ore,...)",
        description = "Level-based ore progression. Format: '1:Copper,15:Iron,30:Coal,50:Mithril'. Leave blank to use rock name filter only.",
        section = miningSection,
        position = 4
    )
    default String miningProgression() { return ""; }

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

    @ConfigItem(
        keyName = "fishingBankingMode",
        name = "Banking mode",
        description = "Walk to bank and deposit fish instead of dropping them",
        section = fishingSection,
        position = 1
    )
    default boolean fishingBankingMode() { return false; }

    @ConfigItem(
        keyName = "fishingShiftDrop",
        name = "Shift-drop mode",
        description = "Drop all fish in one pass without delays (faster power-fishing)",
        section = fishingSection,
        position = 2
    )
    default boolean fishingShiftDrop() { return false; }

    @ConfigItem(
        keyName = "fishingProgression",
        name = "Progression (level:action,...)",
        description = "Level-based fishing progression. Format: '1:Net,20:Fly,40:Harpoon'. Leave blank to use click action only.",
        section = fishingSection,
        position = 3
    )
    default String fishingProgression() { return ""; }

    // ── Alchemy section ───────────────────────────────────────────────────────

    @ConfigSection(
        name = "Alchemy",
        description = "Settings for the High Alchemy script",
        position = 6
    )
    String alchemySection = "alchemy";

    @ConfigItem(
        keyName = "alchemyItemId",
        name = "Item ID to alch",
        description = "Specific item ID to high alch. Set to 0 to auto-detect non-rune items in inventory.",
        section = alchemySection,
        position = 0
    )
    default int alchemyItemId() { return 0; }

    @ConfigItem(
        keyName = "alchemyBankingMode",
        name = "Banking mode",
        description = "Walk to bank to restock nature runes and items when supply runs out",
        section = alchemySection,
        position = 1
    )
    default boolean alchemyBankingMode() { return false; }

    // ── General banking section ───────────────────────────────────────────────

    @ConfigSection(
        name = "Cooking / Smithing / Fletching / Crafting",
        description = "Shared settings for production scripts",
        position = 7
    )
    String productionSection = "production";

    @ConfigItem(
        keyName = "productionBankingMode",
        name = "Banking mode",
        description = "Walk to bank to restock materials when inventory is empty",
        section = productionSection,
        position = 0
    )
    default boolean productionBankingMode() { return false; }

    @ConfigItem(
        keyName = "smithingItemName",
        name = "Smithing item name",
        description = "Item to smith at the anvil (e.g. Platebody, Platelegs, Helm). Leave blank to smith the first available item.",
        section = productionSection,
        position = 1
    )
    default String smithingItemName() { return ""; }

    // ── Safety section ────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Safety",
        description = "Emergency logout and safety stop conditions",
        position = 8
    )
    String safetySection = "safety";

    @ConfigItem(
        keyName = "emergencyLogoutEnabled",
        name = "Emergency logout",
        description = "Log out of the game when HP is critically low and no food is available",
        section = safetySection,
        position = 0
    )
    default boolean emergencyLogoutEnabled() { return false; }

    @ConfigItem(
        keyName = "emergencyLogoutHpPercent",
        name = "Logout below HP %",
        description = "Log out when HP drops at or below this percentage and no food remains",
        section = safetySection,
        position = 1
    )
    @Range(min = 1, max = 30)
    default int emergencyLogoutHpPercent() { return 10; }

    // ── Debug section ─────────────────────────────────────────────────────────

    @ConfigSection(
        name = "Debug",
        description = "Developer tools and debug overlays",
        position = 9
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
