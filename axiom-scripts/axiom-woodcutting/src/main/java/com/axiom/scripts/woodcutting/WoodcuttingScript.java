package com.axiom.scripts.woodcutting;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.player.Skills;
import com.axiom.api.script.BotScript;
import com.axiom.api.world.Movement;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;

/**
 * Axiom Woodcutting — validation script for the multi-module architecture.
 *
 * State machine:
 *   FIND_TREE → CHOPPING → FULL (→ BANKING or DROPPING) → FIND_TREE
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Woodcutting",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Chops trees and optionally banks or drops logs."
)
public class WoodcuttingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private GameObjects gameObjects;
    private Players     players;
    private Inventory   inventory;
    private Skills      skills;
    private Bank        bank;
    private Movement    movement;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private WoodcuttingSettings settings;

    // ── Start location — recorded on first game tick, used to walk back after banking ──
    private boolean startTileRecorded = false;
    private int     startX, startY;

    // ── State ─────────────────────────────────────────────────────────────────
    private enum State { FIND_TREE, CHOPPING, FULL, BANKING, DROPPING }
    private State state = State.FIND_TREE;

    // Chop-start tracking: we must observe at least one animating tick before
    // treating animation=-1 as "tree depleted". Prevents false re-clicks while
    // walking to the tree after interact(). Timeout avoids getting stuck if
    // interact() fails silently.
    private boolean wasChopping       = false;
    private int     chopStartTick     = 0;
    private static final int CHOP_TIMEOUT_TICKS = 10;

    // Bank timing: the deposit-inventory button widget isn't available on the same
    // tick that isOpen() first returns true. We wait 2 extra ticks after detecting
    // the bank open before calling depositAll().
    private boolean bankJustOpened = false;

    // Attempt counter for walking-to-bank. Avoids the hard isNearBank() gate so the
    // script can handle being 10-20 tiles from the bank (e.g. fishing, mining).
    // 20 attempts × 3-tick delay ≈ 36 s.
    private int bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Axe item IDs — never deposited when banking ───────────────────────────
    // Bronze through Dragon axes + Crystal axe + Infernal axe
    private static final int[] AXE_IDS = {
        1351,  // Bronze axe
        1349,  // Iron axe
        1353,  // Steel axe
        1355,  // Mithril axe
        1357,  // Adamant axe
        1359,  // Rune axe
        6739,  // Dragon axe
        20014, // Infernal axe
        23975, // Crystal axe
    };

    // ── Log item IDs for power-chop drop ─────────────────────────────────────
    private static final int[] ALL_LOG_IDS = {
        1511, // Logs (normal)
        1521, // Oak logs
        1519, // Willow logs
        1517, // Maple logs
        1515, // Yew logs
        1513, // Magic logs
        6333, // Teak logs
        6332, // Mahogany logs
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Woodcutting"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        if (raw instanceof WoodcuttingSettings)
        {
            this.settings = (WoodcuttingSettings) raw;
        }
        else
        {
            this.settings = WoodcuttingSettings.defaults();
        }

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        startTileRecorded = false;

        log.info("Woodcutting started: tree={} action={} powerChop={}",
            settings.treeType.name(), settings.bankAction.name(), settings.powerChop);

        state = State.FIND_TREE;
    }

    @Override
    public void onLoop()
    {
        // Record start tile on the first game tick — onStart() runs on the Swing EDT
        // so RuneLite API calls there will crash with AssertionError.
        if (!startTileRecorded)
        {
            startX = players.getWorldX();
            startY = players.getWorldY();
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{})", startX, startY);
        }

        switch (state)
        {
            case FIND_TREE: findTree();   break;
            case CHOPPING:  checkChop();  break;
            case FULL:      handleFull(); break;
            case BANKING:   handleBank(); break;
            case DROPPING:  dropLogs();   break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Woodcutting stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findTree()
    {
        if (inventory.isFull())
        {
            log.info("[FIND_TREE] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        WoodcuttingSettings.TreeType tree = settings.treeType;
        SceneObject treeObj = gameObjects.nearest(
            o -> tree.matches(o.getId())
              || o.getName().equalsIgnoreCase(tree.objectName));

        if (treeObj == null)
        {
            log.info("[FIND_TREE] No {} found nearby — waiting 3 ticks", tree.objectName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_TREE] Found {} (id={}) at ({},{}) — clicking Chop down",
            tree.objectName, treeObj.getId(), treeObj.getWorldX(), treeObj.getWorldY());

        treeObj.interact("Chop down");
        state         = State.CHOPPING;
        wasChopping   = false;
        chopStartTick = 0;

        // Wait at least 2 ticks so the chop animation has time to register.
        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        log.debug("[FIND_TREE] Reaction delay: {} ticks before CHOPPING check", reactionTicks);
        setTickDelay(reactionTicks);
    }

    private void checkChop()
    {
        if (inventory.isFull())
        {
            log.info("[CHOPPING] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        WoodcuttingSettings.TreeType tree = settings.treeType;
        SceneObject treeObj = gameObjects.nearest(
            o -> tree.matches(o.getId())
              || o.getName().equalsIgnoreCase(tree.objectName));

        if (treeObj == null)
        {
            int idleTicks = antiban.randomIdleTicks(1, 3);
            log.info("[CHOPPING] No tree found — idling {} ticks then FIND_TREE", idleTicks);
            setTickDelay(idleTicks);
            state       = State.FIND_TREE;
            wasChopping = false;
            return;
        }

        if (players.isAnimating())
        {
            // Chop animation active — confirm we've started and keep waiting.
            if (!wasChopping)
            {
                log.info("[CHOPPING] Chop started on {} (id={})", tree.objectName, treeObj.getId());
                wasChopping = true;
            }
            else
            {
                log.debug("[CHOPPING] Still chopping {} (id={})", tree.objectName, treeObj.getId());
            }
            return;
        }

        // Not animating. Two cases:
        if (wasChopping)
        {
            // Was chopping → stopped → tree depleted. Re-click.
            log.info("[CHOPPING] Chop stopped (tree depleted) — transitioning to FIND_TREE");
            state       = State.FIND_TREE;
            wasChopping = false;
        }
        else
        {
            // Interact() fired but animation hasn't started yet (walking to tree).
            // Count ticks; if too long, assume interact failed and re-click.
            chopStartTick++;
            log.debug("[CHOPPING] Waiting for chop animation ({}/{})", chopStartTick, CHOP_TIMEOUT_TICKS);
            if (chopStartTick >= CHOP_TIMEOUT_TICKS)
            {
                log.info("[CHOPPING] Timeout waiting for animation — re-clicking");
                state         = State.FIND_TREE;
                chopStartTick = 0;
            }
        }
    }

    private void handleFull()
    {
        if (settings.powerChop || settings.bankAction == WoodcuttingSettings.BankAction.DROP_LOGS)
        {
            log.info("[FULL] Power-chop mode — transitioning to DROPPING");
            state = State.DROPPING;
        }
        else
        {
            log.info("[FULL] Bank mode — transitioning to BANKING");
            state = State.BANKING;
        }
    }

    private void handleBank()
    {
        if (!bank.isOpen())
        {
            bankJustOpened = false;

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.info("[BANKING] Could not open bank after {} attempts — dropping instead",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                state = State.DROPPING;
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

        // Bank is open — reset attempt counter for the next banking cycle.
        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        // Deposit one item type per tick, keeping all axes in the inventory.
        if (bank.depositAllExcept(AXE_IDS))
        {
            log.debug("[BANKING] Depositing items...");
            setTickDelay(1);
            return;
        }

        log.info("[BANKING] Deposit complete — closing bank");
        bank.close();
        bankJustOpened = false;

        int distToStart = players.distanceTo(startX, startY);
        if (distToStart > 10)
        {
            log.info("[BANKING] Walking back to woodcutting area ({},{}) — distance={}",
                startX, startY, distToStart);
            movement.walkTo(startX, startY);
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }
        state = State.FIND_TREE;
    }

    private void dropLogs()
    {
        int dropped = 0;
        for (int logId : ALL_LOG_IDS)
        {
            if (inventory.contains(logId))
            {
                log.info("[DROPPING] Dropping log item id={}", logId);
                inventory.dropAll(logId);
                dropped++;
            }
        }
        if (dropped == 0) log.info("[DROPPING] No logs found in inventory to drop");
        setTickDelay(1);
        state = State.FIND_TREE;
    }
}
