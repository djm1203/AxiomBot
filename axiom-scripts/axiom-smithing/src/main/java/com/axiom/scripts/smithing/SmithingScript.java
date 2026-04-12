package com.axiom.scripts.smithing;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;
import com.axiom.api.world.Movement;

/**
 * Axiom Smithing — smiths bars into items at an anvil and optionally banks for more bars.
 *
 * State machine:
 *   FIND_ANVIL    — locate nearest anvil, click it to open smithing dialog
 *   HANDLE_DIALOG — wait for smithing interface (group 312); click target item
 *   WAITING       — track smithing animation; transition when animation stops
 *   BANKING       — deposit finished items (keep hammer), optionally withdraw bars
 *
 * Clicking an item in the smithing interface starts smithing immediately —
 * there is no separate make-all dialog (unlike Herblore/Fletching). Animation
 * starting is the confirmation the click landed.
 *
 * Smithing dialog (group 312) contains a grid of item slots. The exact child
 * indices depend on the RS build. clickSmithingItem() scans all children/dynamic
 * children and logs diagnostics when the item is not found — run the script
 * once near an anvil to capture the correct widget layout in the logs.
 *
 * Anvil IDs: 2097, 2098 (standard anvils).
 * Hammer ID: 2347 — required in inventory; never deposited.
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Smithing",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Smiths bars into items at an anvil and optionally banks for more bars."
)
public class SmithingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private GameObjects gameObjects;
    private Players     players;
    private Inventory   inventory;
    private Widgets     widgets;
    private Bank        bank;
    private Movement    movement;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private SmithingSettings settings;

    // ── Start tile — recorded on first game tick ──────────────────────────────
    private boolean startTileRecorded = false;
    private int     startX, startY;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_ANVIL, HANDLE_DIALOG, WAITING, BANKING }
    private State state = State.FIND_ANVIL;

    // Animation tracking
    private boolean wasSmithing       = false;
    private int     noAnimationTicks  = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;

    // Banking state
    private boolean bankJustOpened  = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // Anvil object IDs
    private static final int[] ANVIL_IDS = { 2097, 2098 };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Smithing"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof SmithingSettings)
            ? (SmithingSettings) raw
            : SmithingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        startTileRecorded = false;
        state = State.FIND_ANVIL;

        log.info("Smithing started: bar={} item={} bankForBars={}",
            settings.barType.barName, settings.smithItem.displayName, settings.bankForBars);
    }

    @Override
    public void onLoop()
    {
        // Record start tile on the first game tick — onStart() runs on Swing EDT
        if (!startTileRecorded)
        {
            startX = players.getWorldX();
            startY = players.getWorldY();
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{})", startX, startY);
        }

        switch (state)
        {
            case FIND_ANVIL:    handleFindAnvil();   break;
            case HANDLE_DIALOG: handleSmithDialog(); break;
            case WAITING:       handleWaiting();     break;
            case BANKING:       handleBanking();     break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Smithing stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindAnvil()
    {
        // If the smithing dialog is already open (re-entered state after banking),
        // skip straight to clicking the item.
        if (widgets.isSmithingDialogOpen())
        {
            log.info("[FIND_ANVIL] Smithing dialog already open — skipping to HANDLE_DIALOG");
            state = State.HANDLE_DIALOG;
            return;
        }

        // Pre-flight checks
        if (!inventory.containsById(SmithingSettings.HAMMER_ID))
        {
            log.warn("[FIND_ANVIL] No hammer (id={}) in inventory — stopping script",
                SmithingSettings.HAMMER_ID);
            stop();
            return;
        }

        if (!inventory.containsById(settings.barType.barItemId))
        {
            if (settings.bankForBars)
            {
                log.info("[FIND_ANVIL] No {} in inventory — transitioning to BANKING",
                    settings.barType.barName);
                state = State.BANKING;
            }
            else
            {
                log.info("[FIND_ANVIL] No {} in inventory and bankForBars=false — stopping",
                    settings.barType.barName);
                stop();
            }
            return;
        }

        SceneObject anvil = findNearestAnvil();
        if (anvil == null)
        {
            log.info("[FIND_ANVIL] No anvil found nearby — waiting 3 ticks");
            setTickDelay(3);
            return;
        }

        log.info("[FIND_ANVIL] Clicking anvil (id={}) at ({},{}) — opening smithing dialog",
            anvil.getId(), anvil.getWorldX(), anvil.getWorldY());
        anvil.interact("Smith");

        wasSmithing      = false;
        noAnimationTicks = 0;

        // Wait for the smithing dialog to appear
        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        setTickDelay(reactionTicks);
        state = State.HANDLE_DIALOG;
    }

    private void handleSmithDialog()
    {
        if (!widgets.isSmithingDialogOpen())
        {
            noAnimationTicks++;
            if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
            {
                log.warn("[HANDLE_DIALOG] Smithing dialog did not open after {} ticks — retrying",
                    ANIMATION_TIMEOUT_TICKS);
                noAnimationTicks = 0;
                state = State.FIND_ANVIL;
            }
            else
            {
                log.info("[HANDLE_DIALOG] Waiting for smithing dialog ({}/{})",
                    noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
                setTickDelay(2);
            }
            return;
        }

        noAnimationTicks = 0;
        log.info("[HANDLE_DIALOG] Smithing dialog open — clicking {} (id={})",
            settings.smithItem.displayName, settings.smithItem.itemId);
        widgets.clickSmithingItem(settings.smithItem.itemId);

        // Smithing starts immediately after clicking the item — no make-all dialog.
        // Wait 2 ticks for the animation to register, then track it in WAITING.
        wasSmithing      = false;
        noAnimationTicks = 0;
        setTickDelay(2);
        state = State.WAITING;
    }

    private void handleWaiting()
    {
        if (players.isAnimating())
        {
            if (!wasSmithing)
            {
                log.info("[WAITING] Smithing animation started");
                wasSmithing = true;
            }
            else
            {
                log.info("[WAITING] Still smithing");
            }
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasSmithing)
        {
            // Was smithing and stopped — all bars used (or a full inventory of items).
            log.info("[WAITING] Smithing animation stopped");
            wasSmithing = false;
            transitionAfterSmithing();
            return;
        }

        if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            // Make All was clicked but animation never started — missed click or dialog issue.
            log.info("[WAITING] Animation never started (timeout) — retrying");
            noAnimationTicks = 0;
            state = State.FIND_ANVIL;
        }
        else
        {
            log.info("[WAITING] Waiting for smithing animation ({}/{})",
                noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
        }
    }

    private void transitionAfterSmithing()
    {
        boolean hasBars = inventory.containsById(settings.barType.barItemId);

        if (hasBars)
        {
            // Still have bars — continue smithing at the anvil.
            log.info("[WAITING] Bars remain — returning to FIND_ANVIL");
            state = State.FIND_ANVIL;
        }
        else if (settings.bankForBars)
        {
            log.info("[WAITING] No bars remaining — transitioning to BANKING");
            state = State.BANKING;
        }
        else
        {
            log.info("[WAITING] No bars remaining and bankForBars=false — stopping");
            stop();
        }
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            bankJustOpened = false;

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.warn("[BANKING] Could not open bank after {} attempts — stopping",
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

        // Deposit everything except the hammer.
        if (bank.depositAllExcept(SmithingSettings.HAMMER_ID))
        {
            log.debug("[BANKING] Depositing items...");
            setTickDelay(1);
            return;
        }

        // Withdraw a full load of bars.
        if (!bank.contains(settings.barType.barItemId))
        {
            log.warn("[BANKING] No {} left in bank — stopping", settings.barType.barName);
            bank.close();
            stop();
            return;
        }

        log.info("[BANKING] Withdrawing {}...", settings.barType.barName);
        bank.withdrawAll(settings.barType.barItemId);
        setTickDelay(1);

        log.info("[BANKING] Closing bank");
        bank.close();
        bankJustOpened = false;

        // Walk back to the smithing area if we drifted far from the start tile.
        int distToStart = players.distanceTo(startX, startY);
        if (distToStart > 10)
        {
            log.info("[BANKING] Walking back to smithing area ({},{}) — distance={}",
                startX, startY, distToStart);
            movement.walkTo(startX, startY);
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }

        state = State.FIND_ANVIL;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SceneObject findNearestAnvil()
    {
        return gameObjects.nearest(o -> {
            for (int id : ANVIL_IDS) if (o.getId() == id) return true;
            return false;
        });
    }
}
