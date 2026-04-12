package com.axiom.scripts.herblore;

import com.axiom.api.game.Players;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;

/**
 * Axiom Herblore — makes unfinished potions via herb + vial of water.
 *
 * Uses the same split-tick item-on-item pattern as Firemaking:
 *   Tick N:   selectItem(herb)           — selects herb, cursor changes
 *   Tick N+1: useSelectedItemOn(vial)    — triggers make-all dialog
 *   Then:     clickMakeAll()             — starts batch animation
 *
 * State machine:
 *   SELECT_HERB → USE_ON_VIAL → HANDLE_DIALOG → WAITING → SELECT_HERB
 *                                                        ↓ (bankForIngredients, empty)
 *                                                      BANKING → SELECT_HERB
 */
@ScriptManifest(
    name        = "Axiom Herblore",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Makes unfinished potions by combining clean herbs with vials of water."
)
public class HerbloreScript extends BotScript
{
    private static final int VIAL_ID = HerbloreSettings.HerbType.VIAL_OF_WATER_ID;

    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Inventory inventory;
    private Players   players;
    private Widgets   widgets;
    private Bank      bank;
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private HerbloreSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { SELECT_HERB, USE_ON_VIAL, HANDLE_DIALOG, WAITING, BANKING }
    private State state = State.SELECT_HERB;

    // Animation tracking
    private boolean wasAnimating     = false;
    private int     noAnimationTicks = 0;
    // 8 ticks gives enough grace for brief pauses between individual herb animations.
    private static final int ANIMATION_TIMEOUT_TICKS = 8;

    // Dialog wait tracking
    private int     dialogWaitTicks = 0;
    // 6 ticks to wait for make dialog after item-on-item fires.
    private static final int DIALOG_TIMEOUT_TICKS = 6;

    // Banking state
    private boolean bankJustOpened   = false;
    private boolean bankDepositDone  = false;
    private boolean bankHerbDone     = false;
    private boolean bankVialDone     = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Herblore"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof HerbloreSettings)
            ? (HerbloreSettings) raw
            : HerbloreSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        log.info("Herblore started: herb={} (id={}) bankForIngredients={}",
            settings.herbType.herbName, settings.herbType.herbId,
            settings.bankForIngredients);

        state = State.SELECT_HERB;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case SELECT_HERB:   handleSelectHerb();  break;
            case USE_ON_VIAL:   handleUseOnVial();   break;
            case HANDLE_DIALOG: handleDialog();      break;
            case WAITING:       handleWaiting();     break;
            case BANKING:       handleBanking();     break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Herblore stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleSelectHerb()
    {
        if (!inventory.contains(settings.herbType.herbId))
        {
            if (settings.bankForIngredients)
            {
                log.info("[SELECT_HERB] No herbs — transitioning to BANKING");
                state = State.BANKING;
            }
            else
            {
                log.info("[SELECT_HERB] No herbs remaining — script complete");
                stop();
            }
            return;
        }

        if (!inventory.contains(VIAL_ID))
        {
            if (settings.bankForIngredients)
            {
                log.info("[SELECT_HERB] No vials — transitioning to BANKING");
                state = State.BANKING;
            }
            else
            {
                log.info("[SELECT_HERB] No vials remaining — script complete");
                stop();
            }
            return;
        }

        log.info("[SELECT_HERB] Selecting {} (id={})", settings.herbType.herbName, settings.herbType.herbId);
        inventory.selectItem(settings.herbType.herbId);
        setTickDelay(1);
        state = State.USE_ON_VIAL;
    }

    private void handleUseOnVial()
    {
        log.info("[USE_ON_VIAL] Using {} on vial of water", settings.herbType.herbName);
        inventory.useSelectedItemOn(VIAL_ID);

        dialogWaitTicks = 0;
        // Give the server 1+ ticks to open the make dialog.
        int reactionTicks = Math.max(antiban.reactionTicks(), 1);
        log.debug("[USE_ON_VIAL] Reaction delay: {} ticks", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.HANDLE_DIALOG;
    }

    private void handleDialog()
    {
        if (widgets.isMakeDialogOpen())
        {
            log.info("[HANDLE_DIALOG] Make dialog open — clicking Make All");
            widgets.clickMakeAll();
            dialogWaitTicks  = 0;
            wasAnimating     = false;
            noAnimationTicks = 0;
            setTickDelay(1);
            state = State.WAITING;
            return;
        }

        dialogWaitTicks++;
        if (dialogWaitTicks >= DIALOG_TIMEOUT_TICKS)
        {
            log.warn("[HANDLE_DIALOG] Make dialog did not appear after {} ticks — retrying", dialogWaitTicks);
            dialogWaitTicks = 0;
            state = State.SELECT_HERB;
        }
        else
        {
            log.debug("[HANDLE_DIALOG] Waiting for make dialog ({}/{})", dialogWaitTicks, DIALOG_TIMEOUT_TICKS);
        }
    }

    private void handleWaiting()
    {
        if (players.isAnimating())
        {
            if (!wasAnimating) log.info("[WAITING] Mixing animation started");
            else               log.debug("[WAITING] Still mixing...");
            wasAnimating     = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasAnimating)
        {
            log.info("[WAITING] Batch complete — checking inventory");
            wasAnimating = false;
            state        = State.SELECT_HERB;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.info("[WAITING] Animation timeout — retrying from SELECT_HERB");
            wasAnimating     = false;
            noAnimationTicks = 0;
            state            = State.SELECT_HERB;
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
            resetBankState();

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.info("[BANKING] Could not open bank after {} attempts — stopping",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                stop();
                return;
            }

            if (bank.openNearest())
            {
                log.info("[BANKING] Clicked bank (attempt {}/{})",
                    bankOpenAttempts + 1, MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts++;
                setTickDelay(3);
            }
            else
            {
                log.debug("[BANKING] Walking to bank...");
                setTickDelay(2);
            }
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

        if (!bankDepositDone)
        {
            log.info("[BANKING] Depositing all items");
            bank.depositAll();
            bankDepositDone = true;
            setTickDelay(2);
            return;
        }

        if (!bankHerbDone)
        {
            if (!bank.contains(settings.herbType.herbId))
            {
                log.info("[BANKING] No {} in bank — stopping", settings.herbType.herbName);
                bank.close();
                resetBankState();
                stop();
                return;
            }

            log.info("[BANKING] Withdrawing all {}", settings.herbType.herbName);
            bank.withdrawAll(settings.herbType.herbId);
            bankHerbDone = true;
            setTickDelay(2);
            return;
        }

        if (!bankVialDone)
        {
            if (!bank.contains(VIAL_ID))
            {
                log.info("[BANKING] No vials of water in bank — stopping");
                bank.close();
                resetBankState();
                stop();
                return;
            }

            log.info("[BANKING] Withdrawing all vials of water");
            bank.withdrawAll(VIAL_ID);
            bankVialDone = true;
            setTickDelay(2);
            return;
        }

        log.info("[BANKING] Withdraw complete — closing bank");
        bank.close();
        resetBankState();
        setTickDelay(2);
        state = State.SELECT_HERB;
    }

    private void resetBankState()
    {
        bankJustOpened  = false;
        bankDepositDone = false;
        bankHerbDone    = false;
        bankVialDone    = false;
    }
}
