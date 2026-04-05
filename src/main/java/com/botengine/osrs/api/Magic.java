package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

import javax.inject.Inject;

/**
 * API for casting Magic spells via the spellbook interface.
 *
 * Spells are widgets in the Magic spellbook panel (interface 218).
 * Casting uses MenuAction.CC_OP on the spell's packed widget ID.
 *
 * High Alchemy spell widget:
 *   InterfaceID.Magic_Spellbook.HIGH_ALCHEMY = 0x00da_0030
 *   Interface 0xda = 218, component 0x30 = 48
 *   Packed: WidgetUtil.packComponentId(218, 48)
 *
 * Rune checks are against inventory only.
 * TODO: also check equipment slot for elemental staves.
 *
 * Item IDs:
 *   561 — Nature rune
 *   554 — Fire rune
 *   1387 — Fire staff
 *   1401 — Mystic fire staff
 *   3053 — Staff of fire
 */
@Slf4j
public class Magic
{
    /** Nature rune — required for High Alchemy (1 per cast). */
    private static final int NATURE_RUNE_ID = 561;

    /** Fire rune — required for High Alchemy (5 per cast without staff). */
    private static final int FIRE_RUNE_ID = 554;

    private static final int HIGH_ALCH_FIRE_RUNE_COUNT = 5;

    /**
     * High Alchemy spell packed widget ID.
     * Interface 218 (Magic spellbook), component 48.
     */
    private static final int HIGH_ALCH_WIDGET_ID =
        WidgetUtil.packComponentId(218, 48);

    /** Fire-providing staff IDs (inventory proxy until equipment check is added). */
    private static final int[] FIRE_STAFF_IDS = {
        1387,  // Fire staff
        1401,  // Mystic fire staff
        3053,  // Staff of fire
        11998, // Tome of fire
        20714, // Kodai wand
    };

    private final Client client;
    private final Inventory inventory;

    @Inject
    public Magic(Client client, Inventory inventory)
    {
        this.client = client;
        this.inventory = inventory;
    }

    // ── High Alchemy ──────────────────────────────────────────────────────────

    /**
     * Casts High Level Alchemy on the given inventory item.
     *
     * Fires the spell widget action. The server processes the alchemy cast
     * on the item in the specified inventory slot.
     * Callers should check canAlch() and wait ~600ms between casts.
     *
     * @param itemId     the item ID to alchemize
     * @param itemSlot   the inventory slot (0–27) the item is in
     */
    public void alch(int itemId, int itemSlot)
    {
        log.debug("High Alchemy itemId={} slot={}", itemId, itemSlot);
        client.menuAction(
            -1, HIGH_ALCH_WIDGET_ID,
            MenuAction.CC_OP,
            1, -1,
            "Cast", "<col=00ff00>High Level Alchemy</col>"
        );
    }

    // ── Rune checks ───────────────────────────────────────────────────────────

    /**
     * Returns true if the inventory contains at least 1 of every given rune ID.
     */
    public boolean hasRunes(int... runeItemIds)
    {
        for (int id : runeItemIds)
        {
            if (inventory.count(id) < 1) return false;
        }
        return true;
    }

    /**
     * Returns true if the player can cast High Level Alchemy:
     *   - at least 1 Nature rune
     *   - at least 5 Fire runes OR a fire staff in inventory
     */
    public boolean canAlch()
    {
        if (inventory.count(NATURE_RUNE_ID) < 1) return false;
        if (inventory.count(FIRE_RUNE_ID) >= HIGH_ALCH_FIRE_RUNE_COUNT) return true;
        for (int staffId : FIRE_STAFF_IDS)
        {
            if (inventory.contains(staffId)) return true;
        }
        return false;
    }

    // ── Generic spell casting ─────────────────────────────────────────────────

    /**
     * Casts any spell by its packed widget ID.
     * Use WidgetUtil.packComponentId(interfaceId, componentId) to build the ID.
     *
     * @param spellWidgetId packed widget ID of the spell
     */
    public void castSpell(int spellWidgetId)
    {
        log.debug("Cast spell widgetId={}", spellWidgetId);
        client.menuAction(
            -1, spellWidgetId,
            MenuAction.CC_OP,
            1, -1,
            "Cast", ""
        );
    }
}
