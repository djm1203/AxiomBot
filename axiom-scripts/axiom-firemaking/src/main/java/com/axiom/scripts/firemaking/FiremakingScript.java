package com.axiom.scripts.firemaking;

import com.axiom.api.game.Players;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;

/**
 * Axiom Firemaking — validates Inventory.selectItem() + useSelectedItemOn()
 * split-tick item-on-item interaction.
 *
 * Item-on-item requires two separate game ticks:
 *   Tick N:   selectItem(tinderbox)        — CC_OP op=2, item cursor appears
 *   Tick N+1: useSelectedItemOn(log)       — WIDGET_TARGET_ON_WIDGET fires action
 *
 * State machine:
 *   LIGHT_LOG → SELECTING → WAITING → LIGHT_LOG
 *                                    ↓ (bankForLogs=true, no logs left)
 *                                  BANKING → LIGHT_LOG
 */
@ScriptManifest(
    name        = "Axiom Firemaking",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Burns logs with a tinderbox until inventory is empty, optionally banking for more."
)
public class FiremakingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Inventory inventory;
    private Players   players;
    private Bank      bank;
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private FiremakingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { LIGHT_LOG, SELECTING, WAITING, BANKING }
    private State state = State.LIGHT_LOG;

    // Animation tracking — same pattern as Woodcutting/Fishing.
    private boolean wasLighting      = false;
    private int     noAnimationTicks = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;

    // Banking state
    private boolean bankJustOpened   = false;
    private boolean bankWithdrawDone = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    private static final int TINDERBOX_ID = FiremakingSettings.LogType.TINDERBOX_ID;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Firemaking"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof FiremakingSettings)
            ? (FiremakingSettings) raw
            : FiremakingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        log.info("Firemaking started: log={} bankForLogs={}",
            settings.logType.name(), settings.bankForLogs);

        state = State.LIGHT_LOG;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case LIGHT_LOG:  handleLightLog();  break;
            case SELECTING:  handleSelecting(); break;
            case WAITING:    handleWaiting();   break;
            case BANKING:    handleBanking();   break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Firemaking stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleLightLog()
    {
        if (!inventory.contains(TINDERBOX_ID))
        {
            log.info("[LIGHT_LOG] No tinderbox in inventory — stopping");
            stop();
            return;
        }

        if (!inventory.contains(settings.logType.itemId))
        {
            if (settings.bankForLogs)
            {
                log.info("[LIGHT_LOG] No logs — transitioning to BANKING");
                state = State.BANKING;
            }
            else
            {
                log.info("[LIGHT_LOG] No logs remaining — script complete");
                stop();
            }
            return;
        }

        int tinderboxSlot = inventory.getSlot(TINDERBOX_ID);
        log.info("[LIGHT_LOG] Selecting tinderbox (slot={})", tinderboxSlot);
        inventory.selectItem(TINDERBOX_ID);
        setTickDelay(1);
        state = State.SELECTING;
    }

    private void handleSelecting()
    {
        // Tinderbox is now selected (cursor changed); use it on a log this tick.
        log.info("[SELECTING] Using tinderbox on {}", settings.logType.itemName);
        inventory.useSelectedItemOn(settings.logType.itemId);

        wasLighting      = false;
        noAnimationTicks = 0;

        // Wait at least 2 ticks for the lighting animation to register.
        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        log.debug("[SELECTING] Reaction delay: {} ticks", reactionTicks);
        setTickDelay(reactionTicks);

        state = State.WAITING;
    }

    private void handleWaiting()
    {
        if (players.isAnimating())
        {
            if (!wasLighting)
            {
                log.info("[WAITING] Lighting animation started");
            }
            else
            {
                log.debug("[WAITING] Still burning...");
            }
            wasLighting      = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasLighting)
        {
            // Animation stopped → log burned; character stepped east automatically.
            log.info("[WAITING] Log burned — lighting next");
            wasLighting = false;
            state       = State.LIGHT_LOG;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            // Interaction fired but animation never started — retry from scratch.
            log.info("[WAITING] Animation timeout — retrying");
            wasLighting      = false;
            noAnimationTicks = 0;
            state            = State.LIGHT_LOG;
        }
        else
        {
            log.debug("[WAITING] Waiting for animation ({}/{})", noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
        }
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            bankJustOpened   = false;
            bankWithdrawDone = false;

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.info("[BANKING] Could not open bank after {} attempts — stopping",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                stop();
                return;
            }

            log.info("[BANKING] Opening bank (attempt {}/{})",
                bankOpenAttempts + 1, MAX_BANK_OPEN_ATTEMPTS);
            bank.openNearest();
            bankOpenAttempts++;
            setTickDelay(3);
            return;
        }

        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        if (!bankWithdrawDone)
        {
            if (!bank.contains(settings.logType.itemId))
            {
                log.info("[BANKING] No {} in bank — stopping", settings.logType.itemName);
                bank.close();
                bankJustOpened   = false;
                bankWithdrawDone = false;
                stop();
                return;
            }

            log.info("[BANKING] Withdrawing all {}", settings.logType.itemName);
            bank.withdrawAll(settings.logType.itemId);
            bankWithdrawDone = true;
            setTickDelay(2);
            return;
        }

        // Withdraw done — close and return to lighting.
        log.info("[BANKING] Withdraw complete — closing bank");
        bank.close();
        bankJustOpened   = false;
        bankWithdrawDone = false;
        setTickDelay(2);
        state = State.LIGHT_LOG;
    }
}
