package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.widgets.WidgetUtil;

import javax.inject.Inject;
import java.util.EnumMap;
import java.util.Map;

/**
 * API for managing OSRS prayers.
 *
 * Widget IDs use the prayer book interface (77), with one component per prayer.
 * These are mapped via packed IDs (WidgetUtil.packComponentId) to avoid depending
 * on WidgetInfo constants that vary across RuneLite versions.
 *
 * Activating a prayer requires clicking its widget in the prayer book interface.
 * Checking whether a prayer is currently active uses {@link Client#isPrayerActive(Prayer)}.
 */
@Slf4j
public class Prayers
{
    // Prayer potion item IDs (all doses)
    private static final int[] PRAYER_POT_IDS    = { 2434, 2436, 2438, 2440 };
    private static final int[] SUPER_RESTORE_IDS = { 3024, 3026, 3028, 3030 };

    /**
     * Packed widget IDs for each prayer's button in interface 77 (prayer book).
     * Component numbers match the in-game prayer book layout.
     */
    private static final Map<Prayer, Integer> PRAYER_WIDGETS = new EnumMap<>(Prayer.class);

    static
    {
        PRAYER_WIDGETS.put(Prayer.THICK_SKIN,            WidgetUtil.packComponentId(77,  8));
        PRAYER_WIDGETS.put(Prayer.BURST_OF_STRENGTH,     WidgetUtil.packComponentId(77,  9));
        PRAYER_WIDGETS.put(Prayer.CLARITY_OF_THOUGHT,    WidgetUtil.packComponentId(77, 10));
        PRAYER_WIDGETS.put(Prayer.SHARP_EYE,             WidgetUtil.packComponentId(77, 11));
        PRAYER_WIDGETS.put(Prayer.MYSTIC_WILL,           WidgetUtil.packComponentId(77, 12));
        PRAYER_WIDGETS.put(Prayer.ROCK_SKIN,             WidgetUtil.packComponentId(77, 13));
        PRAYER_WIDGETS.put(Prayer.SUPERHUMAN_STRENGTH,   WidgetUtil.packComponentId(77, 14));
        PRAYER_WIDGETS.put(Prayer.IMPROVED_REFLEXES,     WidgetUtil.packComponentId(77, 15));
        PRAYER_WIDGETS.put(Prayer.RAPID_RESTORE,         WidgetUtil.packComponentId(77, 16));
        PRAYER_WIDGETS.put(Prayer.RAPID_HEAL,            WidgetUtil.packComponentId(77, 17));
        PRAYER_WIDGETS.put(Prayer.PROTECT_ITEM,          WidgetUtil.packComponentId(77, 18));
        PRAYER_WIDGETS.put(Prayer.HAWK_EYE,              WidgetUtil.packComponentId(77, 19));
        PRAYER_WIDGETS.put(Prayer.MYSTIC_LORE,           WidgetUtil.packComponentId(77, 20));
        PRAYER_WIDGETS.put(Prayer.STEEL_SKIN,            WidgetUtil.packComponentId(77, 21));
        PRAYER_WIDGETS.put(Prayer.ULTIMATE_STRENGTH,     WidgetUtil.packComponentId(77, 22));
        PRAYER_WIDGETS.put(Prayer.INCREDIBLE_REFLEXES,   WidgetUtil.packComponentId(77, 23));
        PRAYER_WIDGETS.put(Prayer.PROTECT_FROM_MAGIC,    WidgetUtil.packComponentId(77, 24));
        PRAYER_WIDGETS.put(Prayer.PROTECT_FROM_MISSILES, WidgetUtil.packComponentId(77, 25));
        PRAYER_WIDGETS.put(Prayer.PROTECT_FROM_MELEE,    WidgetUtil.packComponentId(77, 26));
        PRAYER_WIDGETS.put(Prayer.EAGLE_EYE,             WidgetUtil.packComponentId(77, 27));
        PRAYER_WIDGETS.put(Prayer.MYSTIC_MIGHT,          WidgetUtil.packComponentId(77, 28));
        PRAYER_WIDGETS.put(Prayer.RETRIBUTION,           WidgetUtil.packComponentId(77, 29));
        PRAYER_WIDGETS.put(Prayer.REDEMPTION,            WidgetUtil.packComponentId(77, 30));
        PRAYER_WIDGETS.put(Prayer.SMITE,                 WidgetUtil.packComponentId(77, 31));
        PRAYER_WIDGETS.put(Prayer.CHIVALRY,              WidgetUtil.packComponentId(77, 35));
        PRAYER_WIDGETS.put(Prayer.PIETY,                 WidgetUtil.packComponentId(77, 36));
        PRAYER_WIDGETS.put(Prayer.PRESERVE,              WidgetUtil.packComponentId(77, 37));
        PRAYER_WIDGETS.put(Prayer.RIGOUR,                WidgetUtil.packComponentId(77, 46));
        PRAYER_WIDGETS.put(Prayer.AUGURY,                WidgetUtil.packComponentId(77, 50));
    }

    private final Client client;
    private final Inventory inventory;

    @Inject
    public Prayers(Client client, Inventory inventory)
    {
        this.client = client;
        this.inventory = inventory;
    }

    // ── Activation ────────────────────────────────────────────────────────────

    /** Activates the given prayer. Does nothing if already active. */
    public void activate(Prayer prayer)
    {
        if (isActive(prayer)) return;
        clickPrayerWidget(prayer, "Activate");
    }

    /** Deactivates the given prayer. Does nothing if not active. */
    public void deactivate(Prayer prayer)
    {
        if (!isActive(prayer)) return;
        clickPrayerWidget(prayer, "Deactivate");
    }

    /** Deactivates all currently active prayers. */
    public void deactivateAll()
    {
        for (Prayer prayer : Prayer.values())
        {
            if (isActive(prayer)) clickPrayerWidget(prayer, "Deactivate");
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public boolean isActive(Prayer prayer)  { return client.isPrayerActive(prayer); }

    public boolean anyActive()
    {
        for (Prayer prayer : Prayer.values()) { if (isActive(prayer)) return true; }
        return false;
    }

    // ── Prayer points ─────────────────────────────────────────────────────────

    public int getPoints()        { return client.getBoostedSkillLevel(Skill.PRAYER); }
    public int getMaxPoints()     { return client.getRealSkillLevel(Skill.PRAYER); }

    public int getPointsPercent()
    {
        int max = getMaxPoints();
        if (max <= 0) return 0;
        return (int) Math.round((double) getPoints() / max * 100);
    }

    public boolean shouldDrinkPotion(int thresholdPercent)
    {
        return getPointsPercent() <= thresholdPercent;
    }

    // ── Potions ───────────────────────────────────────────────────────────────

    /**
     * Drinks a prayer potion or super restore if available.
     * Prefers super restore over regular prayer potions.
     */
    public boolean drinkPotion()
    {
        for (int id : SUPER_RESTORE_IDS)
        {
            if (inventory.contains(id)) { drinkItem(id); log.debug("Drank super restore id={}", id); return true; }
        }
        for (int id : PRAYER_POT_IDS)
        {
            if (inventory.contains(id)) { drinkItem(id); log.debug("Drank prayer potion id={}", id); return true; }
        }
        log.warn("No prayer potions or super restores in inventory");
        return false;
    }

    public boolean hasPotion()
    {
        for (int id : SUPER_RESTORE_IDS) { if (inventory.contains(id)) return true; }
        for (int id : PRAYER_POT_IDS)    { if (inventory.contains(id)) return true; }
        return false;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void clickPrayerWidget(Prayer prayer, String action)
    {
        Integer widgetId = PRAYER_WIDGETS.get(prayer);
        if (widgetId == null)
        {
            log.warn("No widget mapping for prayer: {}", prayer.name());
            return;
        }
        log.debug("{} prayer: {}", action, prayer.name());
        client.menuAction(-1, widgetId, MenuAction.CC_OP, 1, -1, action, "");
    }

    private void drinkItem(int itemId)
    {
        int slot = inventory.getSlot(itemId);
        if (slot == -1) return;
        client.menuAction(
            slot, net.runelite.api.widgets.WidgetInfo.INVENTORY.getId(),
            MenuAction.ITEM_FIRST_OPTION,
            itemId, -1,
            "Drink", ""
        );
    }
}
