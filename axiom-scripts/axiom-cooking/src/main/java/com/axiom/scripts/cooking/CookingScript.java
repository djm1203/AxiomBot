package com.axiom.scripts.cooking;

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

/**
 * Axiom Cooking — cooks raw food on a fire or range using the Make All dialog.
 *
 * State machine:
 *   FIND_OBJECT   — locate nearest fire/range by object ID; selectItem(rawFood); store ref
 *   USE_FOOD      — one tick later: cookObjRef.interact("Cook") opens the production dialog
 *   HANDLE_DIALOG — wait for the Make All dialog (group 270); click Make All
 *   WAITING       — track cooking animation; detect batch end via animation stop + inventory
 *   BANKING       — deposit all items; withdraw raw food; close bank
 *
 * The two-tick pattern (selectItem → interact) mirrors Herblore but targets a
 * game object instead of an inventory item. On the range/fire tick, the server
 * sees the cursor item context and opens the cooking dialog for that food type.
 *
 * Burnt food stays in inventory (different item ID) and does not break the loop —
 * the raw food count reaching zero is the signal that the batch is done.
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Cooking",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Cooks raw food on a fire or range using Make All, banks for more."
)
public class CookingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Players     players;
    private GameObjects gameObjects;
    private Inventory   inventory;
    private Widgets     widgets;
    private Bank        bank;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private CookingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_OBJECT, USE_FOOD, HANDLE_DIALOG, WAITING, BANKING }
    private State state = State.FIND_OBJECT;

    // Stored between FIND_OBJECT and USE_FOOD ticks
    private SceneObject cookObjRef = null;

    // Dialog wait timeout
    private int dialogWaitTicks = 0;
    private static final int DIALOG_TIMEOUT_TICKS = 10;

    // Animation tracking
    private boolean wasActing        = false;
    private int     noAnimationTicks = 0;
    private static final int ANIM_TIMEOUT_TICKS = 15; // cooking batches are long

    // Banking state
    private boolean bankJustOpened  = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Cooking"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof CookingSettings)
            ? (CookingSettings) raw
            : CookingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        state            = State.FIND_OBJECT;
        cookObjRef       = null;
        dialogWaitTicks  = 0;
        wasActing        = false;
        noAnimationTicks = 0;

        log.info("Cooking started: food={} location={} bankForFood={}",
            settings.foodType.rawName,
            settings.location.objectName,
            settings.bankForFood);
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_OBJECT:   handleFindObject();   break;
            case USE_FOOD:      handleUseFood();      break;
            case HANDLE_DIALOG: handleDialog();       break;
            case WAITING:       handleWaiting();      break;
            case BANKING:       handleBanking();      break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Cooking stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindObject()
    {
        // If the make-all dialog is already open (re-entered from banking),
        // skip straight to the dialog handler.
        if (widgets.isMakeDialogOpen())
        {
            log.info("[FIND_OBJECT] Make dialog already open — skipping to HANDLE_DIALOG");
            state = State.HANDLE_DIALOG;
            return;
        }

        if (!inventory.containsById(settings.foodType.rawId))
        {
            if (settings.bankForFood)
            {
                log.info("[FIND_OBJECT] No {} in inventory — going to BANKING",
                    settings.foodType.rawName);
                state = State.BANKING;
            }
            else
            {
                log.info("[FIND_OBJECT] No {} in inventory and bankForFood=false — stopping",
                    settings.foodType.rawName);
                stop();
            }
            return;
        }

        // Primary lookup: search by known object IDs
        SceneObject cookObj = gameObjects.nearest(settings.location.objectIds);

        // Fallback: scan for any object with a "Cook" action — works at any
        // location without knowing the ID in advance. Log the ID so it can
        // be added to the hardcoded list.
        if (cookObj == null)
        {
            final String action = settings.location.cookAction;
            cookObj = gameObjects.nearest(o -> o.hasAction(action));
            if (cookObj != null)
            {
                log.info("[FIND_OBJECT] Found via action '{}' fallback: id={} name='{}'",
                    action, cookObj.getId(), cookObj.getName());
            }
        }

        if (cookObj == null)
        {
            log.info("[FIND_OBJECT] No {} found nearby — waiting",
                settings.location.objectName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_OBJECT] Found {} (id={}) — selecting {} for use",
            settings.location.objectName, cookObj.getId(), settings.foodType.rawName);

        // Tick 1 of two-tick cook: select the raw food item (puts cursor item on screen)
        inventory.selectItem(settings.foodType.rawId);
        log.info("[FIND_OBJECT] After selectItem — inventory has {}: {}",
            settings.foodType.rawName, inventory.containsById(settings.foodType.rawId));

        cookObjRef       = cookObj;
        wasActing        = false;
        noAnimationTicks = 0;
        setTickDelay(1);
        state = State.USE_FOOD;
    }

    private void handleUseFood()
    {
        if (cookObjRef == null)
        {
            log.warn("[USE_FOOD] No cook object reference — returning to FIND_OBJECT");
            state = State.FIND_OBJECT;
            return;
        }

        // Tick 2 of two-tick cook: click the fire/range to open the cooking dialog.
        // The server reads the cursor item context and opens the production dialog
        // for the selected food type.
        log.info("[USE_FOOD] Clicking {} (id={}) to open cook dialog",
            settings.location.objectName, cookObjRef.getId());
        cookObjRef.interact(settings.location.cookAction);
        // cookObjRef intentionally kept alive — WAITING reuses it on animation pause
        dialogWaitTicks = 0;
        setTickDelay(1);
        state = State.HANDLE_DIALOG;
    }

    private void handleDialog()
    {
        if (!widgets.isMakeDialogOpen())
        {
            dialogWaitTicks++;
            if (dialogWaitTicks >= DIALOG_TIMEOUT_TICKS)
            {
                log.warn("[HANDLE_DIALOG] Cook dialog did not open after {} ticks — retrying",
                    DIALOG_TIMEOUT_TICKS);
                dialogWaitTicks = 0;
                state = State.FIND_OBJECT;
            }
            else
            {
                log.info("[HANDLE_DIALOG] Waiting for cook dialog ({}/{})",
                    dialogWaitTicks, DIALOG_TIMEOUT_TICKS);
                setTickDelay(1);
            }
            return;
        }

        log.info("[HANDLE_DIALOG] Cook dialog open — clicking Make All");
        widgets.clickMakeAll();
        dialogWaitTicks  = 0;
        wasActing        = false;
        noAnimationTicks = 0;
        setTickDelay(2);
        state = State.WAITING;
    }

    private void handleWaiting()
    {
        if (players.isAnimating())
        {
            if (!wasActing) log.info("[WAITING] Cooking animation started");
            wasActing        = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasActing)
        {
            // Animation stopped — check what happened
            if (!inventory.containsById(settings.foodType.rawId))
            {
                // All raw food consumed (cooked or burnt)
                log.info("[WAITING] No raw food remaining — going to {}",
                    settings.bankForFood ? "BANKING" : "FIND_OBJECT");
                wasActing  = false;
                cookObjRef = null; // clear stale ref before banking
                state = settings.bankForFood ? State.BANKING : State.FIND_OBJECT;
                return;
            }

            if (noAnimationTicks >= 3)
            {
                // Animation paused with raw food remaining (e.g. level-up).
                // Reuse stored cookObjRef to skip the object search entirely.
                wasActing        = false;
                noAnimationTicks = 0;
                if (cookObjRef != null)
                {
                    log.info("[WAITING] Cooking paused — re-clicking stored {} ref",
                        settings.location.objectName);
                    inventory.selectItem(settings.foodType.rawId);
                    setTickDelay(1);
                    state = State.USE_FOOD;
                }
                else
                {
                    log.info("[WAITING] Cooking paused — no stored ref, searching again");
                    state = State.FIND_OBJECT;
                }
                return;
            }
        }

        if (noAnimationTicks >= ANIM_TIMEOUT_TICKS)
        {
            log.warn("[WAITING] Animation never started after {} ticks — retrying",
                ANIM_TIMEOUT_TICKS);
            wasActing        = false;
            noAnimationTicks = 0;
            state = State.FIND_OBJECT;
        }
        else
        {
            log.info("[WAITING] Waiting for cooking animation ({}/{})",
                noAnimationTicks, ANIM_TIMEOUT_TICKS);
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

        // Deposit everything — cooked fish, burnt fish, all of it
        bank.depositAll();
        log.info("[BANKING] Depositing all items");
        setTickDelay(1);

        // Verify raw food is available before withdrawing
        if (!bank.contains(settings.foodType.rawId))
        {
            log.warn("[BANKING] No {} left in bank — stopping", settings.foodType.rawName);
            bank.close();
            stop();
            return;
        }

        log.info("[BANKING] Withdrawing {}...", settings.foodType.rawName);
        bank.withdrawAll(settings.foodType.rawId);
        setTickDelay(1);

        log.info("[BANKING] Closing bank");
        bank.close();
        bankJustOpened = false;

        setTickDelay(2);
        state = State.FIND_OBJECT;
    }
}
