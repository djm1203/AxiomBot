package com.axiom.scripts.crafting;

import com.axiom.api.game.Players;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.util.ProductionTickTracker;
import com.axiom.api.world.Bank;
import com.axiom.scripts.crafting.CraftingSettings.CraftingMethod;
import com.axiom.scripts.crafting.CraftingSettings.GemType;
import com.axiom.scripts.crafting.CraftingSettings.GlassType;
import com.axiom.scripts.crafting.CraftingSettings.LeatherType;

/**
 * Axiom Crafting — two methods, both using split-tick item-on-item:
 *
 *   GEM_CUTTING: chisel on uncut gem → make-all dialog → cut gems batch
 *   LEATHER:     needle on leather   → make-all dialog → leather items batch
 *
 * Split-tick pattern (same as Herblore/Fletching):
 *   Tick N:   selectItem(tool)         — selects chisel/needle, cursor changes
 *   Tick N+1: useSelectedItemOn(mat)   — triggers make-all dialog
 *   Then:     clickMakeAll()           — starts batch animation
 *
 * State machine:
 *   SELECT_TOOL → USE_ON_MATERIAL → HANDLE_DIALOG → WAITING → SELECT_TOOL
 *                                                            ↓ (bankForMaterials, empty)
 *                                                          BANKING → SELECT_TOOL
 *
 * Banking:
 *   GEM_CUTTING: depositAllExcept(CHISEL_ID), then withdrawAll(uncutId)
 *   LEATHER:     depositAllExcept(NEEDLE_ID, THREAD_ID), then withdrawAll(materialId)
 */
@ScriptManifest(
    name        = "Axiom Crafting",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Cuts uncut gems with a chisel, or crafts leather items with a needle."
)
public class CraftingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Inventory inventory;
    private Players   players;
    private Widgets   widgets;
    private Bank      bank;
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private CraftingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { SELECT_TOOL, USE_ON_MATERIAL, HANDLE_DIALOG, WAITING, BANKING }
    private State state = State.SELECT_TOOL;

    private static final int ANIMATION_TIMEOUT_TICKS = 10;
    private static final int DIALOG_TIMEOUT_TICKS = 6;
    private final ProductionTickTracker productionTracker = new ProductionTickTracker();

    // Banking state
    private boolean bankJustOpened  = false;
    private boolean bankDepositDone = false;
    private boolean bankItem1Done   = false;
    private boolean bankItem2Done   = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Crafting"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof CraftingSettings)
            ? (CraftingSettings) raw
            : CraftingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        log.info("Crafting started: method={} bankForMaterials={}",
            settings.method.displayName, settings.bankForMaterials);

        state = State.SELECT_TOOL;
        productionTracker.resetDialog();
        productionTracker.resetAnimation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case SELECT_TOOL:     handleSelectTool();     break;
            case USE_ON_MATERIAL: handleUseOnMaterial();  break;
            case HANDLE_DIALOG:   handleDialog();         break;
            case WAITING:         handleWaiting();        break;
            case BANKING:         handleBanking();        break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Crafting stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleSelectTool()
    {
        switch (settings.method)
        {
            case GEM_CUTTING: handleSelectChisel(); break;
            case LEATHER:     handleSelectNeedle(); break;
            case GLASSBLOWING: handleSelectGlassblowingPipe(); break;
        }
    }

    private void handleSelectChisel()
    {
        if (!hasGemBatchMaterials())
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] Missing gem-cutting materials — script complete");
            stop();
            return;
        }
        log.info("[SELECT_TOOL] Selecting chisel (id={})", GemType.CHISEL_ID);
        inventory.selectItem(GemType.CHISEL_ID);
        log.info("[SELECT_TOOL] after selectItem — stopRequested={}, containsGem={}",
            isStopRequested(), inventory.containsById(settings.gemType.uncutId));
        setTickDelay(1);
        state = State.USE_ON_MATERIAL;
    }

    private void handleSelectNeedle()
    {
        if (!hasLeatherBatchMaterials())
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] Missing leather-crafting materials — script complete");
            stop();
            return;
        }
        log.info("[SELECT_TOOL] Selecting needle (id={})", LeatherType.NEEDLE_ID);
        inventory.selectItem(LeatherType.NEEDLE_ID);
        log.info("[SELECT_TOOL] after selectItem — stopRequested={}, containsMaterial={}",
            isStopRequested(), inventory.containsById(settings.leatherType.materialId));
        setTickDelay(1);
        state = State.USE_ON_MATERIAL;
    }

    private void handleSelectGlassblowingPipe()
    {
        if (!hasGlassBatchMaterials())
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] Missing glassblowing materials — script complete");
            stop();
            return;
        }
        log.info("[SELECT_TOOL] Selecting glassblowing pipe (id={})", GlassType.GLASSBLOWING_PIPE_ID);
        inventory.selectItem(GlassType.GLASSBLOWING_PIPE_ID);
        setTickDelay(1);
        state = State.USE_ON_MATERIAL;
    }

    private void handleUseOnMaterial()
    {
        if (!hasMaterialsForCurrentMethod())
        {
            log.warn("[USE_ON_MATERIAL] Required materials disappeared before batch start");
            state = settings.bankForMaterials ? State.BANKING : State.SELECT_TOOL;
            return;
        }

        int materialId = settings.method == CraftingMethod.GEM_CUTTING
            ? settings.gemType.uncutId
            : settings.method == CraftingMethod.LEATHER
                ? settings.leatherType.materialId
                : GlassType.MOLTEN_GLASS_ID;

        log.info("[USE_ON_MATERIAL] Using selected item on id={}", materialId);
        inventory.useSelectedItemOn(materialId);

        productionTracker.resetDialog();
        int reactionTicks = Math.max(antiban.reactionTicks(), 1);
        log.debug("[USE_ON_MATERIAL] Reaction delay: {} ticks", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.HANDLE_DIALOG;
    }

    private void handleDialog()
    {
        ProductionTickTracker.DialogStatus dialogStatus =
            productionTracker.observeDialog(widgets.isMakeDialogOpen(), DIALOG_TIMEOUT_TICKS);

        if (dialogStatus == ProductionTickTracker.DialogStatus.OPEN)
        {
            if (settings.method == CraftingMethod.LEATHER)
            {
                log.info("[HANDLE_DIALOG] Make dialog open — selecting '{}'",
                    settings.leatherType.productName);
                if (!widgets.clickMakeOption(settings.leatherType.productName))
                {
                    log.warn("[HANDLE_DIALOG] Could not find leather product '{}' in make dialog — retrying",
                        settings.leatherType.productName);
                    productionTracker.resetDialog();
                    state = State.SELECT_TOOL;
                    return;
                }
            }
            else if (settings.method == CraftingMethod.GLASSBLOWING)
            {
                log.info("[HANDLE_DIALOG] Make dialog open — selecting '{}'",
                    settings.glassType.productName);
                if (!widgets.clickMakeOption(settings.glassType.productName))
                {
                    log.warn("[HANDLE_DIALOG] Could not find glass product '{}' in make dialog — retrying",
                        settings.glassType.productName);
                    productionTracker.resetDialog();
                    state = State.SELECT_TOOL;
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
            setTickDelay(2);
            state = State.WAITING;
            return;
        }

        if (dialogStatus == ProductionTickTracker.DialogStatus.TIMED_OUT)
        {
            log.warn("[HANDLE_DIALOG] Make dialog did not appear after {} ticks — retrying",
                productionTracker.getDialogWaitTicks());
            productionTracker.resetDialog();
            state = State.SELECT_TOOL;
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
            log.info("[WAITING] Crafting animation started");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.IN_PROGRESS)
        {
            log.debug("[WAITING] Still crafting...");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.COMPLETED)
        {
            log.info("[WAITING] Batch complete — checking inventory");
            state        = State.SELECT_TOOL;
        }
        else if (batchStatus == ProductionTickTracker.BatchStatus.TIMEOUT)
        {
            log.info("[WAITING] Animation timeout — retrying from SELECT_TOOL");
            productionTracker.resetAnimation();
            state            = State.SELECT_TOOL;
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

        // Deposit all except tools — depositAllExcept returns true while items remain
        if (!bankDepositDone)
        {
            boolean moreToDeposit;
            if (settings.method == CraftingMethod.GEM_CUTTING)
                moreToDeposit = bank.depositAllExcept(GemType.CHISEL_ID);
            else if (settings.method == CraftingMethod.GLASSBLOWING)
                moreToDeposit = bank.depositAllExcept(GlassType.GLASSBLOWING_PIPE_ID);
            else
                moreToDeposit = bank.depositAllExcept(LeatherType.NEEDLE_ID, LeatherType.THREAD_ID);

            if (!moreToDeposit)
            {
                log.info("[BANKING] Deposit complete (tools protected)");
                bankDepositDone = true;
            }
            setTickDelay(2);
            return;
        }

        // Withdraw material
        if (!bankItem1Done)
        {
            if (settings.method == CraftingMethod.LEATHER && !inventory.containsById(LeatherType.THREAD_ID))
            {
                if (!bank.contains(LeatherType.THREAD_ID))
                {
                    log.info("[BANKING] No thread in bank — stopping");
                    bank.close();
                    resetBankState();
                    stop();
                    return;
                }
                log.info("[BANKING] Withdrawing thread");
                bank.withdraw(LeatherType.THREAD_ID, 1);
                setTickDelay(2);
                bankItem1Done = true;
                return;
            }

            bankItem1Done = true;
            setTickDelay(1);
            return;
        }

        if (!bankItem2Done)
        {
            int materialId = getMaterialId();

            if (!bank.contains(materialId))
            {
                log.info("[BANKING] No material (id={}) in bank — stopping", materialId);
                bank.close();
                resetBankState();
                stop();
                return;
            }
            log.info("[BANKING] Withdrawing material (id={})", materialId);
            bank.withdrawAll(materialId);
            bankItem2Done = true;
            setTickDelay(2);
            return;
        }

        if (!hasMaterialsForCurrentMethod())
        {
            int materialId = getMaterialId();
            if (!inventory.containsById(materialId) && bank.contains(materialId))
            {
                log.info("[BANKING] Material still missing after withdraw — retrying");
                bankItem2Done = false;
                setTickDelay(2);
                return;
            }
            if (settings.method == CraftingMethod.LEATHER
                && !inventory.containsById(LeatherType.THREAD_ID)
                && bank.contains(LeatherType.THREAD_ID))
            {
                log.info("[BANKING] Thread missing after withdraw — retrying");
                bankItem1Done = false;
                setTickDelay(2);
                return;
            }

            log.info("[BANKING] Required materials not available after banking — stopping");
            bank.close();
            resetBankState();
            stop();
            return;
        }

        log.info("[BANKING] Withdraw complete — closing bank");
        bank.close();
        resetBankState();
        setTickDelay(2);
        state = State.SELECT_TOOL;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetBankState()
    {
        bankJustOpened  = false;
        bankDepositDone = false;
        bankItem1Done   = false;
        bankItem2Done   = false;
    }

    private int getMaterialId()
    {
        return settings.method == CraftingMethod.GEM_CUTTING
            ? settings.gemType.uncutId
            : settings.method == CraftingMethod.LEATHER
                ? settings.leatherType.materialId
                : GlassType.MOLTEN_GLASS_ID;
    }

    private boolean hasMaterialsForCurrentMethod()
    {
        return settings.method == CraftingMethod.GEM_CUTTING
            ? hasGemBatchMaterials()
            : settings.method == CraftingMethod.LEATHER
                ? hasLeatherBatchMaterials()
                : hasGlassBatchMaterials();
    }

    private boolean hasGemBatchMaterials()
    {
        return inventory.containsById(GemType.CHISEL_ID)
            && inventory.containsById(settings.gemType.uncutId);
    }

    private boolean hasLeatherBatchMaterials()
    {
        return inventory.containsById(LeatherType.NEEDLE_ID)
            && inventory.containsById(LeatherType.THREAD_ID)
            && inventory.containsById(settings.leatherType.materialId);
    }

    private boolean hasGlassBatchMaterials()
    {
        return inventory.containsById(GlassType.GLASSBLOWING_PIPE_ID)
            && inventory.containsById(GlassType.MOLTEN_GLASS_ID);
    }
}
