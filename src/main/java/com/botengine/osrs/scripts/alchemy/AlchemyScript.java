package com.botengine.osrs.scripts.alchemy;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;

import javax.inject.Inject;

/**
 * High Alchemy AFK script.
 *
 * State machine:
 *   CHECK → ALCHING → (no supplies) → BANKING (optional) → CHECK
 *
 * Features:
 *   - Configurable item ID (0 = auto-detect first non-rune item)
 *   - Nature rune tracking — stops/banks when out
 *   - Staff of Fire detection (skips fire rune check if equipped)
 *   - Optional banking loop to restock
 */
public class AlchemyScript extends BotScript
{
    private static final int NATURE_RUNE = 561;
    private static final int FIRE_RUNE   = 554;
    private static final int AIR_RUNE    = 556;
    private static final int WATER_RUNE  = 555;
    private static final int EARTH_RUNE  = 557;

    // Alch cast interval (High Alch = 3 ticks = ~1800ms)
    private static final int ALCH_INTERVAL_MS = 1800;

    private enum State { CHECK, ALCHING, BANKING }

    private State state = State.CHECK;
    private long  lastAlchTime = 0;
    private int   configuredItemId = 0; // 0 = auto
    private boolean bankingMode    = false;

    @Inject
    public AlchemyScript() {}

    @Override
    public String getName() { return "High Alchemy"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        configuredItemId = config.alchemyItemId();
        bankingMode      = config.alchemyBankingMode();
    }

    @Override
    public void onStart()
    {
        log.info("Started — itemId={} banking={}", configuredItemId == 0 ? "auto" : configuredItemId, bankingMode);
        state = State.CHECK;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case CHECK:   checkSupplies(); break;
            case ALCHING: doAlch();        break;
            case BANKING: handleBanking(); break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void checkSupplies()
    {
        if (!magic.canAlch())
        {
            if (bankingMode)
            {
                log.info("Missing runes — banking to restock");
                state = State.BANKING;
            }
            else
            {
                log.warn("Missing nature runes or fire source — waiting");
            }
            return;
        }

        int itemId = findAlchItem();
        if (itemId == -1)
        {
            if (bankingMode)
            {
                log.info("No alchable items — banking to restock");
                state = State.BANKING;
            }
            else
            {
                log.info("No alchable items remaining — done");
            }
            return;
        }

        state = State.ALCHING;
    }

    private void doAlch()
    {
        long now = System.currentTimeMillis();
        if (now - lastAlchTime < ALCH_INTERVAL_MS) return;

        int itemId = findAlchItem();
        if (itemId == -1)
        {
            state = State.CHECK;
            return;
        }

        if (!magic.canAlch())
        {
            state = State.CHECK;
            return;
        }

        int slot = inventory.getSlot(itemId);
        if (slot == -1) return;

        magic.alch(itemId, slot);
        lastAlchTime = System.currentTimeMillis();
        antiban.reactionDelay();
        log.debug("Alched item id={} slot={}", itemId, slot);
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            // Deposit everything except runes
            bank.depositAll();
            antiban.reactionDelay();
            // Withdraw nature runes (stack of 1000)
            if (bank.contains(NATURE_RUNE))
            {
                bank.withdraw(NATURE_RUNE, Integer.MAX_VALUE);
                antiban.reactionDelay();
            }
            // If specific item configured, withdraw it
            if (configuredItemId > 0 && bank.contains(configuredItemId))
            {
                bank.withdraw(configuredItemId, Integer.MAX_VALUE);
                antiban.reactionDelay();
            }
            bank.close();
            state = State.CHECK;
            return;
        }

        if (bank.isNearBank())
        {
            bank.openNearest();
        }
        else
        {
            log.debug("Looking for bank...");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findAlchItem()
    {
        if (configuredItemId > 0)
        {
            return inventory.contains(configuredItemId) ? configuredItemId : -1;
        }
        // Auto-detect: first non-rune item
        for (net.runelite.api.Item item : inventory.getItems())
        {
            if (item == null) continue;
            int id = item.getId();
            if (id <= 0 || isRune(id)) continue;
            return id;
        }
        return -1;
    }

    private boolean isRune(int id)
    {
        return id == NATURE_RUNE || id == FIRE_RUNE
            || id == AIR_RUNE   || id == WATER_RUNE
            || id == EARTH_RUNE;
    }
}
