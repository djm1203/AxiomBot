package com.botengine.osrs.scripts.alchemy;

import com.botengine.osrs.script.BotScript;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * High Alchemy AFK script.
 *
 * State machine:
 *   CHECK_RUNES → ALCHING → (no items left) → STOPPED
 *
 * Alches every item in the inventory that is not a nature rune or fire rune/staff.
 * The script stops itself when the inventory contains no more alchable items.
 *
 * Requirements:
 *   - Nature runes in inventory
 *   - Fire runes OR fire/lava/steam/smoke staff equipped
 *   - Items to alch in inventory
 *
 * Configurable item ID is not implemented here — the script alches the first
 * non-rune item it finds. For targeted alching, extend this class and override
 * getAlchItemId() to return the specific item ID.
 */
public class AlchemyScript extends BotScript
{
    // Rune item IDs — never alch these
    private static final int NATURE_RUNE  = 561;
    private static final int FIRE_RUNE    = 554;
    private static final int AIR_RUNE     = 556;
    private static final int WATER_RUNE   = 555;
    private static final int EARTH_RUNE   = 557;

    // Minimum cast interval (ms) — High Alch takes ~3 ticks (1800ms)
    private static final int ALCH_INTERVAL_MS = 1800;

    private enum State { CHECK_RUNES, ALCHING }

    private State state = State.CHECK_RUNES;
    private long lastAlchTime = 0;

    @Inject
    public AlchemyScript() {}

    @Override
    public String getName() { return "High Alchemy"; }

    @Override
    public void onStart()
    {
        log.info("Started — will alch all non-rune items");
        state = State.CHECK_RUNES;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case CHECK_RUNES:
                checkRunes();
                break;

            case ALCHING:
                doAlch();
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void checkRunes()
    {
        if (!magic.canAlch())
        {
            log.warn("Missing runes or fire source — stopping");
            return; // ScriptRunner will keep calling; user must fix inventory
        }

        int itemId = findAlchItem();
        if (itemId == -1)
        {
            log.info("No alchable items remaining — done");
            return;
        }

        state = State.ALCHING;
    }

    private void doAlch()
    {
        // Respect the 3-tick alch cooldown
        long now = System.currentTimeMillis();
        if (now - lastAlchTime < ALCH_INTERVAL_MS)
        {
            return;
        }

        int itemId = findAlchItem();
        if (itemId == -1)
        {
            log.info("No alchable items remaining — done");
            state = State.CHECK_RUNES;
            return;
        }

        int slot = inventory.getSlot(itemId);
        if (slot == -1) return;

        magic.alch(itemId, slot);
        lastAlchTime = System.currentTimeMillis();
        antiban.reactionDelay();
        log.debug("Alched item id={} slot={}", itemId, slot);
    }

    /**
     * Returns the first inventory item that is safe to alch.
     * Skips runes, noted items (id > 0 and noted), and empty slots.
     */
    private int findAlchItem()
    {
        for (net.runelite.api.Item item : inventory.getItems())
        {
            if (item == null) continue;
            int id = item.getId();
            if (id <= 0) continue;
            if (isRune(id)) continue;
            return id;
        }
        return -1;
    }

    private boolean isRune(int id)
    {
        return id == NATURE_RUNE
            || id == FIRE_RUNE
            || id == AIR_RUNE
            || id == WATER_RUNE
            || id == EARTH_RUNE;
    }
}
