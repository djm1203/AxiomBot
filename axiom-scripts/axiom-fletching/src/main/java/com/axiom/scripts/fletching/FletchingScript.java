package com.axiom.scripts.fletching;

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
import com.axiom.scripts.fletching.FletchingSettings.BowType;
import com.axiom.scripts.fletching.FletchingSettings.DartType;
import com.axiom.scripts.fletching.FletchingSettings.FletchingMethod;
import com.axiom.scripts.fletching.FletchingSettings.LogType;

/**
 * Axiom Fletching — three distinct methods:
 *
 *   KNIFE_LOGS: knife on logs → make-all dialog → unstrung bows batch
 *   STRING_BOW: bowstring on unstrung bow → make-all dialog → strung bows batch
 *   DARTS:      dart tips on feathers → instant combine, no dialog, 2-tick loop
 *
 * KNIFE_LOGS / STRING_BOW use the split-tick item-on-item pattern:
 *   Tick N:   selectItem(knife or bow)      — selects item, cursor changes
 *   Tick N+1: useSelectedItemOn(target)     — triggers make-all dialog
 *   Then:     clickMakeAll()                — starts batch animation
 *
 * DARTS use a tight 2-tick loop (no dialog, darts combine instantly):
 *   Tick N:   selectItem(tipId)
 *   Tick N+1: useSelectedItemOn(FEATHER_ID) → straight back to SELECT_ITEM
 *
 * State machine (KNIFE/STRING):
 *   SELECT_ITEM → USE_ON_TARGET → HANDLE_DIALOG → WAITING → SELECT_ITEM
 *                                                          ↓ (bankForMaterials, empty)
 *                                                        BANKING → SELECT_ITEM
 *
 * State machine (DARTS):
 *   SELECT_ITEM → USE_ON_TARGET → SELECT_ITEM
 *              ↓ (bankForMaterials, empty)
 *            BANKING → SELECT_ITEM
 */
@ScriptManifest(
    name        = "Axiom Fletching",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Fletches logs into bows, strings unstrung bows, or makes darts."
)
public class FletchingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Inventory inventory;
    private Players   players;
    private Widgets   widgets;
    private Bank      bank;
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private FletchingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { SELECT_ITEM, USE_ON_TARGET, HANDLE_DIALOG, WAITING, FAST_WAITING, BANKING }
    private State state = State.SELECT_ITEM;

    // Animation tracking (KNIFE_LOGS / STRING_BOW only)
    private static final int ANIMATION_TIMEOUT_TICKS = 8;
    private static final int DIALOG_TIMEOUT_TICKS = 6;
    private static final int FAST_TIMEOUT_TICKS = 4;
    private final ProductionTickTracker productionTracker = new ProductionTickTracker();
    private final InventoryDeltaTracker fastDeltaTracker = new InventoryDeltaTracker();
    private int fastWaitTicks;

    // Banking state
    private boolean bankJustOpened  = false;
    private boolean bankDepositDone = false;
    private boolean bankItem1Done   = false;
    private boolean bankItem2Done   = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Fletching"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof FletchingSettings)
            ? (FletchingSettings) raw
            : FletchingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        log.info("Fletching started: method={} bankForMaterials={}",
            settings.method.displayName, settings.bankForMaterials);

        state = State.SELECT_ITEM;
        productionTracker.resetDialog();
        productionTracker.resetAnimation();
        fastDeltaTracker.reset();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case SELECT_ITEM:   handleSelectItem();  break;
            case USE_ON_TARGET: handleUseOnTarget(); break;
            case HANDLE_DIALOG: handleDialog();      break;
            case WAITING:       handleWaiting();     break;
            case FAST_WAITING:  handleFastWaiting(); break;
            case BANKING:       handleBanking();     break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Fletching stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleSelectItem()
    {
        switch (settings.method)
        {
            case KNIFE_LOGS:      handleSelectKnifeLogs();     break;
            case STRING_BOW:      handleSelectStringBow();     break;
            case DARTS:           handleSelectDartTip();       break;
            case ARROW_SHAFTS:    handleSelectArrowShafts();   break;
            case HEADLESS_ARROWS: handleSelectHeadlessArrows(); break;
        }
    }

    private void handleSelectKnifeLogs()
    {
        if (!inventory.contains(LogType.KNIFE_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No knife — script complete");
            stop();
            return;
        }
        // Diagnostic: show the exact logId being checked so we can confirm it matches
        // what's actually in the inventory (oak logs = 1521, normal logs = 1511, etc.)
        log.info("[SELECT_ITEM] Checking for {} (id={}) — contains={}",
            settings.logType.logName, settings.logType.logId,
            inventory.contains(settings.logType.logId));
        if (!inventory.contains(settings.logType.logId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No {} — script complete", settings.logType.logName);
            stop();
            return;
        }
        log.info("[SELECT_ITEM] Selecting knife (id={})", LogType.KNIFE_ID);
        inventory.selectItem(LogType.KNIFE_ID);
        log.info("[SELECT_ITEM] after selectItem — stopRequested={}, containsLog={}",
            isStopRequested(), inventory.contains(settings.logType.logId));
        setTickDelay(1);
        state = State.USE_ON_TARGET;
    }

    private void handleSelectStringBow()
    {
        if (!inventory.contains(settings.bowType.bowId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No {} — script complete", settings.bowType.bowName);
            stop();
            return;
        }
        if (!inventory.contains(settings.bowType.stringId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No bowstrings — script complete");
            stop();
            return;
        }
        log.info("[SELECT_ITEM] Selecting {} (id={})", settings.bowType.bowName, settings.bowType.bowId);
        inventory.selectItem(settings.bowType.bowId);
        setTickDelay(1);
        state = State.USE_ON_TARGET;
    }

    private void handleSelectDartTip()
    {
        if (!inventory.contains(settings.dartType.tipId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No {} — script complete", settings.dartType.tipName);
            stop();
            return;
        }
        if (!inventory.contains(DartType.FEATHER_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No feathers — script complete");
            stop();
            return;
        }
        log.info("[SELECT_ITEM] Selecting {} (id={})", settings.dartType.tipName, settings.dartType.tipId);
        inventory.selectItem(settings.dartType.tipId);
        setTickDelay(1);
        state = State.USE_ON_TARGET;
    }

    private void handleSelectArrowShafts()
    {
        if (!inventory.contains(LogType.KNIFE_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No knife — script complete");
            stop();
            return;
        }
        if (!inventory.contains(LogType.NORMAL.logId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No {} — script complete", LogType.NORMAL.logName);
            stop();
            return;
        }
        log.info("[SELECT_ITEM] Selecting knife for arrow shafts");
        inventory.selectItem(LogType.KNIFE_ID);
        setTickDelay(1);
        state = State.USE_ON_TARGET;
    }

    private void handleSelectHeadlessArrows()
    {
        if (!inventory.contains(FletchingSettings.ARROW_SHAFT_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No arrow shafts — script complete");
            stop();
            return;
        }
        if (!inventory.contains(DartType.FEATHER_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_ITEM] No feathers — script complete");
            stop();
            return;
        }
        log.info("[SELECT_ITEM] Selecting arrow shafts");
        inventory.selectItem(FletchingSettings.ARROW_SHAFT_ID);
        setTickDelay(1);
        state = State.USE_ON_TARGET;
    }

    private void handleUseOnTarget()
    {
        int targetId;
        switch (settings.method)
        {
            case KNIFE_LOGS:      targetId = settings.logType.logId;    break;
            case STRING_BOW:      targetId = settings.bowType.stringId; break;
            case DARTS:           targetId = DartType.FEATHER_ID;       break;
            case ARROW_SHAFTS:    targetId = LogType.NORMAL.logId;      break;
            case HEADLESS_ARROWS: targetId = DartType.FEATHER_ID;       break;
            default: return;
        }

        log.info("[USE_ON_TARGET] Using selected item on id={}", targetId);
        inventory.useSelectedItemOn(targetId);

        if (usesFastCompletionModel())
        {
            captureFastCounts();
            setTickDelay(1);
            state = State.FAST_WAITING;
        }
        else
        {
            productionTracker.resetDialog();
            int reactionTicks = Math.max(antiban.reactionTicks(), 1);
            log.debug("[USE_ON_TARGET] Reaction delay: {} ticks", reactionTicks);
            setTickDelay(reactionTicks);
            state = State.HANDLE_DIALOG;
        }
    }

    private void handleDialog()
    {
        ProductionTickTracker.DialogStatus dialogStatus =
            productionTracker.observeDialog(widgets.isMakeDialogOpen(), DIALOG_TIMEOUT_TICKS);

        if (dialogStatus == ProductionTickTracker.DialogStatus.OPEN)
        {
            if (settings.method == FletchingMethod.KNIFE_LOGS || settings.method == FletchingMethod.ARROW_SHAFTS)
            {
                String productName = settings.method == FletchingMethod.ARROW_SHAFTS
                    ? "Arrow shaft"
                    : settings.knifeProduct.getProductName(settings.logType);
                log.info("[HANDLE_DIALOG] Make dialog open — selecting '{}'", productName);
                if (!widgets.clickMakeOption(productName))
                {
                    log.warn("[HANDLE_DIALOG] Could not find knife product '{}' in make dialog — retrying",
                        productName);
                    productionTracker.resetDialog();
                    state = State.SELECT_ITEM;
                    return;
                }
            }
            else
            {
                log.info("[HANDLE_DIALOG] Make dialog open — clicking Make All");
                widgets.clickMakeAll();
            }
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
            state = State.SELECT_ITEM;
        }
        else
        {
            log.debug("[HANDLE_DIALOG] Waiting for make dialog ({}/{})",
                productionTracker.getDialogWaitTicks(), DIALOG_TIMEOUT_TICKS);
        }
    }

    private void handleWaiting()
    {
        ProductionTickTracker.BatchStatus batchStatus =
            productionTracker.observeAnimation(players.isAnimating(), ANIMATION_TIMEOUT_TICKS);

        if (batchStatus == ProductionTickTracker.BatchStatus.STARTED)
        {
            log.info("[WAITING] Fletching animation started");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.IN_PROGRESS)
        {
            log.debug("[WAITING] Still fletching...");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.COMPLETED)
        {
            log.info("[WAITING] Batch complete — checking inventory");
            state        = State.SELECT_ITEM;
        }
        else if (batchStatus == ProductionTickTracker.BatchStatus.TIMEOUT)
        {
            log.info("[WAITING] Animation timeout — retrying from SELECT_ITEM");
            productionTracker.resetAnimation();
            state            = State.SELECT_ITEM;
        }
        else
        {
            log.debug("[WAITING] Waiting for animation ({}/{})",
                productionTracker.getNoAnimationTicks(), ANIMATION_TIMEOUT_TICKS);
        }
    }

    private void handleFastWaiting()
    {
        if (hasFastProgress())
        {
            log.info("[FAST_WAITING] Fast fletching progress detected");
            resetFastTracking();
            state = State.SELECT_ITEM;
            return;
        }

        fastWaitTicks++;
        if (fastWaitTicks >= FAST_TIMEOUT_TICKS)
        {
            log.warn("[FAST_WAITING] No inventory delta after {} ticks — retrying", fastWaitTicks);
            resetFastTracking();
            state = State.SELECT_ITEM;
            return;
        }

        log.debug("[FAST_WAITING] Waiting for fast production delta ({}/{})",
            fastWaitTicks, FAST_TIMEOUT_TICKS);
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

        // Deposit: protect knife for knife method; deposit everything else.
        if (!bankDepositDone)
        {
            if (settings.method == FletchingMethod.KNIFE_LOGS)
            {
                // depositAllExcept returns true while non-knife items remain — called once per tick.
                if (!bank.depositAllExcept(LogType.KNIFE_ID))
                {
                    log.info("[BANKING] Deposit complete (knife protected)");
                    bankDepositDone = true;
                }
            }
            else
            {
                log.info("[BANKING] Depositing all items");
                bank.depositAll();
                bankDepositDone = true;
            }
            setTickDelay(2);
            return;
        }

        // Item 1: knife (knife method) / unstrung bow (string method) / dart tips (dart method)
        if (!bankItem1Done)
        {
            int item1Id = getItem1Id();
            if (inventory.contains(item1Id))
            {
                log.info("[BANKING] Item1 (id={}) already in inventory — skipping withdraw", item1Id);
                bankItem1Done = true;
                setTickDelay(1);
                return;
            }
            if (!bank.contains(item1Id))
            {
                log.info("[BANKING] Item1 (id={}) not in bank — stopping", item1Id);
                bank.close();
                resetBankState();
                stop();
                return;
            }
            log.info("[BANKING] Withdrawing item1 (id={})", item1Id);
            bank.withdrawAll(item1Id);
            bankItem1Done = true;
            setTickDelay(2);
            return;
        }

        // Item 2: logs (knife method) / bowstring (string method) / feathers (dart method)
        if (!bankItem2Done)
        {
            int item2Id = getItem2Id();
            if (!bank.contains(item2Id))
            {
                log.info("[BANKING] Item2 (id={}) not in bank — stopping", item2Id);
                bank.close();
                resetBankState();
                stop();
                return;
            }
            log.info("[BANKING] Withdrawing item2 (id={})", item2Id);
            bank.withdrawAll(item2Id);
            bankItem2Done = true;
            setTickDelay(2);
            return;
        }

        log.info("[BANKING] Withdraw complete — closing bank");
        bank.close();
        resetBankState();
        setTickDelay(2);
        state = State.SELECT_ITEM;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getItem1Id()
    {
        switch (settings.method)
        {
            case KNIFE_LOGS:
            case ARROW_SHAFTS:
                return LogType.KNIFE_ID;
            case STRING_BOW:
                return settings.bowType.bowId;
            case DARTS:
                return settings.dartType.tipId;
            case HEADLESS_ARROWS:
                return FletchingSettings.ARROW_SHAFT_ID;
            default:
                return -1;
        }
    }

    private int getItem2Id()
    {
        switch (settings.method)
        {
            case KNIFE_LOGS:
                return settings.logType.logId;
            case STRING_BOW:
                return settings.bowType.stringId;
            case DARTS:
            case HEADLESS_ARROWS:
                return DartType.FEATHER_ID;
            case ARROW_SHAFTS:
                return LogType.NORMAL.logId;
            default:
                return -1;
        }
    }

    private void resetBankState()
    {
        bankJustOpened  = false;
        bankDepositDone = false;
        bankItem1Done   = false;
        bankItem2Done   = false;
    }

    private boolean usesFastCompletionModel()
    {
        return settings.method == FletchingMethod.DARTS
            || settings.method == FletchingMethod.HEADLESS_ARROWS;
    }

    private void captureFastCounts()
    {
        fastWaitTicks = 0;
        fastDeltaTracker.capture(inventory, getFastSourceItemId(), getFastProductItemId());
    }

    private boolean hasFastProgress()
    {
        return fastDeltaTracker.hasAnyChange(inventory);
    }

    private int getFastSourceItemId()
    {
        return settings.method == FletchingMethod.HEADLESS_ARROWS
            ? FletchingSettings.ARROW_SHAFT_ID
            : settings.dartType.tipId;
    }

    private int getFastProductItemId()
    {
        return settings.method == FletchingMethod.HEADLESS_ARROWS
            ? FletchingSettings.HEADLESS_ARROW_ID
            : settings.dartType.dartId;
    }

    private void resetFastTracking()
    {
        fastDeltaTracker.reset();
        fastWaitTicks = 0;
    }
}
