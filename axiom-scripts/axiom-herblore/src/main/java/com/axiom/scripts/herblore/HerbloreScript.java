package com.axiom.scripts.herblore;

import com.axiom.api.game.Players;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.InventoryDeltaTracker;
import com.axiom.api.util.Log;
import com.axiom.api.util.ProductionTickTracker;
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

    // 8 ticks gives enough grace for brief pauses between individual herb animations.
    private static final int ANIMATION_TIMEOUT_TICKS = 8;

    // 6 ticks to wait for make dialog after item-on-item fires.
    private static final int DIALOG_TIMEOUT_TICKS = 6;
    private final ProductionTickTracker productionTracker = new ProductionTickTracker();
    private final InventoryDeltaTracker cleaningTracker = new InventoryDeltaTracker();
    private int batchPrimaryCount = 0;
    private int batchSecondaryCount = 0;
    private int cleaningWaitTicks = 0;

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

        log.info("Herblore started: method={} primary={} secondary={} bankForIngredients={}",
            settings.method.displayName, getPrimaryItemLabel(), getSecondaryItemLabel(),
            settings.bankForIngredients);

        if (settings.method == HerbloreSettings.Method.FINISHED && settings.secondaryItemId <= 0)
        {
            log.warn("Finished-potion mode requires a secondary item ID — stopping");
            stop();
            return;
        }

        state = State.SELECT_HERB;
        productionTracker.resetDialog();
        productionTracker.resetAnimation();
        cleaningTracker.reset();
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
        int primaryItemId = getPrimaryItemId();
        int secondaryItemId = getSecondaryItemId();

        if (!inventory.contains(primaryItemId))
        {
            if (settings.bankForIngredients)
            {
                log.info("[SELECT_HERB] No {} — transitioning to BANKING", getPrimaryItemLabel());
                state = State.BANKING;
            }
            else
            {
                log.info("[SELECT_HERB] No {} remaining — script complete", getPrimaryItemLabel());
                stop();
            }
            return;
        }

        if (settings.method != HerbloreSettings.Method.CLEANING && !inventory.contains(secondaryItemId))
        {
            if (settings.bankForIngredients)
            {
                log.info("[SELECT_HERB] No {} — transitioning to BANKING", getSecondaryItemLabel());
                state = State.BANKING;
            }
            else
            {
                log.info("[SELECT_HERB] No {} remaining — script complete", getSecondaryItemLabel());
                stop();
            }
            return;
        }

        batchPrimaryCount = inventory.count(primaryItemId);
        batchSecondaryCount = inventory.count(secondaryItemId);

        if (settings.method == HerbloreSettings.Method.CLEANING)
        {
            log.info("[SELECT_HERB] Cleaning {} (id={})", getPrimaryItemLabel(), primaryItemId);
            cleaningTracker.capture(inventory, settings.herbType.grimyId, settings.herbType.herbId);
            cleaningWaitTicks = 0;
            inventory.clickItem(primaryItemId);
            setTickDelay(1);
            state = State.WAITING;
            return;
        }

        log.info("[SELECT_HERB] Selecting {} (id={})", getPrimaryItemLabel(), primaryItemId);
        inventory.selectItem(primaryItemId);
        setTickDelay(1);
        state = State.USE_ON_VIAL;
    }

    private void handleUseOnVial()
    {
        int secondaryItemId = getSecondaryItemId();
        log.info("[USE_ON_VIAL] Using {} on {}",
            getPrimaryItemLabel(), getSecondaryItemLabel());
        inventory.useSelectedItemOn(secondaryItemId);

        productionTracker.resetDialog();
        // Give the server 1+ ticks to open the make dialog.
        int reactionTicks = Math.max(antiban.reactionTicks(), 1);
        log.debug("[USE_ON_VIAL] Reaction delay: {} ticks", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.HANDLE_DIALOG;
    }

    private void handleDialog()
    {
        ProductionTickTracker.DialogStatus dialogStatus =
            productionTracker.observeDialog(widgets.isMakeDialogOpen(), DIALOG_TIMEOUT_TICKS);

        if (dialogStatus == ProductionTickTracker.DialogStatus.OPEN)
        {
            log.info("[HANDLE_DIALOG] Make dialog open — clicking Make All");
            widgets.clickMakeAll();
            productionTracker.resetDialog();
            productionTracker.resetAnimation();
            setTickDelay(1);
            state = State.WAITING;
            return;
        }

        if (dialogStatus == ProductionTickTracker.DialogStatus.TIMED_OUT)
        {
            log.warn("[HANDLE_DIALOG] Make dialog did not appear after {} ticks — retrying",
                productionTracker.getDialogWaitTicks());
            productionTracker.resetDialog();
            state = State.SELECT_HERB;
        }
        else
        {
            log.debug("[HANDLE_DIALOG] Waiting for make dialog ({}/{})",
                productionTracker.getDialogWaitTicks(), DIALOG_TIMEOUT_TICKS);
        }
    }

    private void handleWaiting()
    {
        if (settings.method == HerbloreSettings.Method.CLEANING)
        {
            handleCleaningWait();
            return;
        }

        ProductionTickTracker.BatchStatus batchStatus =
            productionTracker.observeAnimation(players.isAnimating(), ANIMATION_TIMEOUT_TICKS);

        if (batchStatus == ProductionTickTracker.BatchStatus.STARTED)
        {
            log.info("[WAITING] Mixing animation started");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.IN_PROGRESS)
        {
            log.debug("[WAITING] Still mixing...");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.COMPLETED)
        {
            log.info("[WAITING] Batch complete — checking inventory");
            state        = State.SELECT_HERB;
        }
        else if (batchStatus == ProductionTickTracker.BatchStatus.TIMEOUT)
        {
            if (madeBatchProgress())
            {
                log.info("[WAITING] Animation timed out after progress — resuming from SELECT_HERB");
            }
            else
            {
                log.info("[WAITING] Animation timeout with no progress — retrying from SELECT_HERB");
            }
            productionTracker.resetAnimation();
            state            = State.SELECT_HERB;
        }
        else
        {
            log.debug("[WAITING] Waiting for animation ({}/{})",
                productionTracker.getNoAnimationTicks(), ANIMATION_TIMEOUT_TICKS);
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
            int primaryItemId = getPrimaryItemId();
            if (!bank.contains(primaryItemId))
            {
                log.info("[BANKING] No {} in bank — stopping", getPrimaryItemLabel());
                bank.close();
                resetBankState();
                stop();
                return;
            }

            log.info("[BANKING] Withdrawing all {}", getPrimaryItemLabel());
            bank.withdrawAll(primaryItemId);
            bankHerbDone = true;
            setTickDelay(2);
            return;
        }

        if (settings.method != HerbloreSettings.Method.CLEANING && !bankVialDone)
        {
            int secondaryItemId = getSecondaryItemId();
            if (!bank.contains(secondaryItemId))
            {
                log.info("[BANKING] No {} in bank — stopping", getSecondaryItemLabel());
                bank.close();
                resetBankState();
                stop();
                return;
            }

            log.info("[BANKING] Withdrawing all {}", getSecondaryItemLabel());
            bank.withdrawAll(secondaryItemId);
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

    private int getPrimaryItemId()
    {
        if (settings.method == HerbloreSettings.Method.CLEANING)
        {
            return settings.herbType.grimyId;
        }
        return settings.method == HerbloreSettings.Method.UNFINISHED
            ? settings.herbType.herbId
            : settings.herbType.unfPotionId;
    }

    private int getSecondaryItemId()
    {
        return settings.method == HerbloreSettings.Method.UNFINISHED
            ? VIAL_ID
            : settings.secondaryItemId;
    }

    private String getPrimaryItemLabel()
    {
        if (settings.method == HerbloreSettings.Method.CLEANING)
        {
            return settings.herbType.grimyName;
        }
        return settings.method == HerbloreSettings.Method.UNFINISHED
            ? settings.herbType.herbName
            : settings.herbType.potionName;
    }

    private String getSecondaryItemLabel()
    {
        if (settings.method == HerbloreSettings.Method.CLEANING)
        {
            return "clean herb";
        }
        if (settings.method == HerbloreSettings.Method.UNFINISHED)
        {
            return "vial of water";
        }
        return settings.secondaryItemName.isEmpty()
            ? "secondary item " + settings.secondaryItemId
            : settings.secondaryItemName;
    }

    private boolean madeBatchProgress()
    {
        return inventory.count(getPrimaryItemId()) < batchPrimaryCount
            || inventory.count(getSecondaryItemId()) < batchSecondaryCount;
    }

    private void handleCleaningWait()
    {
        if (cleaningTracker.hasAnyChange(inventory))
        {
            log.info("[WAITING] Herb cleaned — continuing");
            cleaningTracker.reset();
            state = State.SELECT_HERB;
            return;
        }

        cleaningWaitTicks++;
        if (cleaningWaitTicks >= 4)
        {
            log.info("[WAITING] Cleaning produced no inventory delta — retrying");
            cleaningTracker.reset();
            state = State.SELECT_HERB;
            cleaningWaitTicks = 0;
            return;
        }

        log.debug("[WAITING] Waiting for cleaning delta ({}/4)", cleaningWaitTicks);
    }
}
