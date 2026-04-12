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
import com.axiom.api.world.Bank;
import com.axiom.scripts.crafting.CraftingSettings.CraftingMethod;
import com.axiom.scripts.crafting.CraftingSettings.GemType;
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

    // Animation tracking
    private boolean wasAnimating     = false;
    private int     noAnimationTicks = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;

    // Dialog wait tracking
    private int dialogWaitTicks = 0;
    private static final int DIALOG_TIMEOUT_TICKS = 6;

    // Banking state
    private boolean bankJustOpened  = false;
    private boolean bankDepositDone = false;
    private boolean bankItem1Done   = false;
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
        }
    }

    private void handleSelectChisel()
    {
        if (!inventory.containsById(GemType.CHISEL_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] No chisel — script complete");
            stop();
            return;
        }
        if (!inventory.containsById(settings.gemType.uncutId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] No {} — script complete", settings.gemType.uncutName);
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
        if (!inventory.containsById(LeatherType.NEEDLE_ID))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] No needle — script complete");
            stop();
            return;
        }
        if (!inventory.containsById(settings.leatherType.materialId))
        {
            if (settings.bankForMaterials) { state = State.BANKING; return; }
            log.info("[SELECT_TOOL] No {} — script complete", settings.leatherType.materialName);
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

    private void handleUseOnMaterial()
    {
        int materialId = settings.method == CraftingMethod.GEM_CUTTING
            ? settings.gemType.uncutId
            : settings.leatherType.materialId;

        log.info("[USE_ON_MATERIAL] Using selected item on id={}", materialId);
        inventory.useSelectedItemOn(materialId);

        dialogWaitTicks = 0;
        int reactionTicks = Math.max(antiban.reactionTicks(), 1);
        log.debug("[USE_ON_MATERIAL] Reaction delay: {} ticks", reactionTicks);
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
            setTickDelay(2);
            state = State.WAITING;
            return;
        }

        dialogWaitTicks++;
        if (dialogWaitTicks >= DIALOG_TIMEOUT_TICKS)
        {
            log.warn("[HANDLE_DIALOG] Make dialog did not appear after {} ticks — retrying", dialogWaitTicks);
            dialogWaitTicks = 0;
            state = State.SELECT_TOOL;
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
            if (!wasAnimating) log.info("[WAITING] Crafting animation started");
            else               log.debug("[WAITING] Still crafting...");
            wasAnimating     = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasAnimating)
        {
            log.info("[WAITING] Batch complete — checking inventory");
            wasAnimating = false;
            state        = State.SELECT_TOOL;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.info("[WAITING] Animation timeout — retrying from SELECT_TOOL");
            wasAnimating     = false;
            noAnimationTicks = 0;
            state            = State.SELECT_TOOL;
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

        // Deposit all except tools — depositAllExcept returns true while items remain
        if (!bankDepositDone)
        {
            boolean moreToDeposit;
            if (settings.method == CraftingMethod.GEM_CUTTING)
                moreToDeposit = bank.depositAllExcept(GemType.CHISEL_ID);
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
            int materialId = settings.method == CraftingMethod.GEM_CUTTING
                ? settings.gemType.uncutId
                : settings.leatherType.materialId;

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
            bankItem1Done = true;
            setTickDelay(2);
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
    }
}
