package com.botengine.osrs.scripts.firemaking;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.swing.*;

/**
 * Firemaking script — lights fires using a tinderbox and configured log type.
 *
 * The game automatically pushes the player one tile east each time a fire is
 * lit on their current tile, so no manual walking is needed between fires.
 * When logs run out: banking mode banks and withdraws more; otherwise stops.
 *
 * Light animation ID: 733.
 * Tinderbox item ID:  590.
 */
public class FiremakingScript extends BotScript
{
    private static final int TINDERBOX_ID    = 590;
    private static final int LIGHT_ANIM_ID   = 733;
    private static final int TIMEOUT_TICKS   = 10;

    private enum State { FIND_LOGS, LIGHT_FIRE, WAIT_LIT, BANKING, DONE }

    private State      state       = State.FIND_LOGS;
    private int        logItemId   = 1511;
    private boolean    bankingMode = false;
    private WorldPoint homeTile;
    private int        ticksWaited = 0;

    @Inject
    public FiremakingScript() {}

    @Override
    public String getName() { return "Firemaking"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        if (scriptSettings instanceof FiremakingSettings)
        {
            FiremakingSettings s = (FiremakingSettings) scriptSettings;
            logItemId   = s.logItemId;
            bankingMode = s.bankingMode;
        }
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(JComponent parent)
    {
        return new FiremakingConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        homeTile = players.getLocation();
        state    = State.FIND_LOGS;
        log.info("Firemaking started — log={} banking={}", logItemId, bankingMode);
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_LOGS:   findLogs();   break;
            case LIGHT_FIRE:  lightFire();  break;
            case WAIT_LIT:    waitLit();    break;
            case BANKING:     handleBanking(); break;
            case DONE:        break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Firemaking stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findLogs()
    {
        if (!inventory.contains(TINDERBOX_ID))
        {
            log.warn("No tinderbox in inventory — stopping");
            state = State.DONE;
            return;
        }
        if (inventory.contains(logItemId))
        {
            state = State.LIGHT_FIRE;
        }
        else if (bankingMode)
        {
            state = State.BANKING;
        }
        else
        {
            log.info("Out of logs — stopping");
            state = State.DONE;
        }
    }

    private void lightFire()
    {
        int tinderSlot = inventory.getSlot(TINDERBOX_ID);
        int logSlot    = inventory.getSlot(logItemId);
        if (tinderSlot < 0 || logSlot < 0)
        {
            state = State.FIND_LOGS;
            return;
        }
        interaction.useItemOnItem(tinderSlot, logSlot);
        antiban.reactionDelay();
        ticksWaited = 0;
        state = State.WAIT_LIT;
    }

    private void waitLit()
    {
        ticksWaited++;
        int anim = players.getAnimation();

        if (anim == LIGHT_ANIM_ID)
        {
            // Still lighting — keep waiting
            return;
        }

        if (players.isIdle())
        {
            // Animation finished — check if log was consumed
            if (!inventory.contains(logItemId))
            {
                // All logs used up
                state = bankingMode ? State.BANKING : State.DONE;
            }
            else
            {
                // Log remains (fire lit, player pushed east) — light next
                antiban.gaussianDelay(300, 80);
                state = State.LIGHT_FIRE;
            }
            ticksWaited = 0;
            return;
        }

        if (ticksWaited >= TIMEOUT_TICKS)
        {
            log.warn("Light fire timed out — retrying");
            ticksWaited = 0;
            state = State.LIGHT_FIRE;
        }
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            if (bank.contains(logItemId))
            {
                bank.withdraw(logItemId, Integer.MAX_VALUE);
                antiban.reactionDelay();
            }
            else
            {
                log.warn("No logs in bank — stopping");
                bank.close();
                state = State.DONE;
                return;
            }
            bank.close();
            antiban.reactionDelay();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
                state = State.FIND_LOGS;
            return;
        }

        if (!bank.isNearBank())
        {
            movement.setRunning(true);
            log.debug("Walking to bank...");
            return;
        }
        bank.openNearest();
    }
}
