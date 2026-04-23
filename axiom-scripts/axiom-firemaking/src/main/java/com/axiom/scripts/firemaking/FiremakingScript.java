package com.axiom.scripts.firemaking;

import com.axiom.api.game.Messages;
import com.axiom.api.game.Players;
import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.util.Pathfinder;
import com.axiom.api.util.RetryBudget;
import com.axiom.api.world.Bank;
import com.axiom.api.world.LocationProfile;
import com.axiom.api.world.Movement;
import com.axiom.api.world.WorldTile;
import com.axiom.scripts.firemaking.FiremakingSettings.FiremakingMode;

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
    private Messages  messages;
    private Players   players;
    private GameObjects gameObjects;
    private Widgets   widgets;
    private Bank      bank;
    private Movement  movement;
    private Pathfinder pathfinder;
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
    private int     expectedLogCount = -1;
    private int     sidestepIndex    = 0;
    private int     laneResetAttempts = 0;
    private boolean campfireDialogHandled = false;
    private boolean campfireBatchStarted = false;
    private final RetryBudget blockedLightRetries = new RetryBudget(MAX_BLOCKED_RETRIES);
    private boolean startTileRecorded = false;
    private LocationProfile locationProfile;

    // Banking state
    private boolean bankJustOpened   = false;
    private boolean bankDepositDone  = false;
    private boolean bankWithdrawDone = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    private static final int TINDERBOX_ID = FiremakingSettings.LogType.TINDERBOX_ID;
    private static final int MESSAGE_WINDOW_TICKS = 8;
    private static final int MAX_BLOCKED_RETRIES  = 3;
    private static final int MAX_LANE_RESET_ATTEMPTS = 3;
    private static final int[][] SIDESTEP_OFFSETS = {
        {0, -1},
        {0, 1},
        {1, 0},
        {-1, 0}
    };
    private static final int[] FIRE_OBJECT_IDS = {
        26185, 26186, 26187, 26574, 43475, 43476
    };
    private static final int[] CAMPFIRE_OBJECT_IDS = {
        49927, 49928, 49929, 49930, 49931, 49932
    };

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

        log.info("Firemaking started: mode={} lane={} log={} bankForLogs={}",
            settings.mode.displayName, settings.laneDirection.displayName,
            settings.logType.name(), settings.bankForLogs);

        state            = State.LIGHT_LOG;
        expectedLogCount = -1;
        sidestepIndex    = 0;
        laneResetAttempts = 0;
        campfireDialogHandled = false;
        campfireBatchStarted = false;
        blockedLightRetries.reset();
        startTileRecorded = false;
        locationProfile = null;
    }

    @Override
    public void onLoop()
    {
        if (!startTileRecorded)
        {
            WorldTile startTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
            locationProfile = LocationProfile.centered("firemaking", startTile, 12);
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{},{})",
                startTile.getWorldX(), startTile.getWorldY(), startTile.getPlane());
        }

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
        expectedLogCount = -1;
        laneResetAttempts = 0;
        campfireDialogHandled = false;
        campfireBatchStarted = false;
        blockedLightRetries.reset();
        log.info("Firemaking stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleLightLog()
    {
        if (settings.mode != FiremakingMode.CAMPFIRE && !inventory.contains(TINDERBOX_ID))
        {
            log.info("[LIGHT_LOG] No tinderbox in inventory — stopping");
            stop();
            return;
        }

        if (!inventory.contains(settings.logType.itemId))
        {
            if (settings.bankForLogs || settings.mode == FiremakingMode.GE_NOTED)
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

        if (settings.mode == FiremakingMode.CAMPFIRE)
        {
            SceneObject campfire = findCampfireTarget();
            if (campfire == null)
            {
                log.info("[LIGHT_LOG] No nearby fire or campfire found for tending — waiting");
                setTickDelay(3);
                return;
            }

            int logSlot = inventory.getSlot(settings.logType.itemId);
            log.info("[LIGHT_LOG] Selecting {} for campfire tending (slot={})",
                settings.logType.itemName, logSlot);
            inventory.selectItem(settings.logType.itemId);
            expectedLogCount = inventory.count(settings.logType.itemId);
            campfireDialogHandled = false;
            campfireBatchStarted = false;
            setTickDelay(1);
            state = State.SELECTING;
            return;
        }

        if (isTileBlockedForFire())
        {
            log.info("[LIGHT_LOG] Current tile is not valid for lighting — sidestepping");
            trySidestep();
            setTickDelay(2);
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
        if (settings.mode == FiremakingMode.CAMPFIRE)
        {
            SceneObject campfire = findCampfireTarget();
            if (campfire == null)
            {
                log.info("[SELECTING] Fire or campfire disappeared — retrying");
                state = State.LIGHT_LOG;
                return;
            }

            log.info("[SELECTING] Using {} on {}", settings.logType.itemName, campfire.getName());
            campfire.useSelected();
            wasLighting = false;
            noAnimationTicks = 0;
            campfireDialogHandled = false;
            campfireBatchStarted = false;
            setTickDelay(2);
            state = State.WAITING;
            return;
        }

        // Tinderbox is now selected (cursor changed); use it on a log this tick.
        log.info("[SELECTING] Using tinderbox on {}", settings.logType.itemName);
        expectedLogCount = inventory.count(settings.logType.itemId);
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
        if (settings.mode == FiremakingMode.CAMPFIRE)
        {
            handleCampfireWaiting();
            return;
        }

        String levelMessage = messages.pollRecentRegex(
            "need a firemaking level of|do not have a high enough firemaking level",
            MESSAGE_WINDOW_TICKS);
        if (levelMessage != null)
        {
            log.info("[WAITING] Firemaking level check failed: {} — stopping", levelMessage);
            stop();
            return;
        }

        String blockedMessage = messages.pollRecentRegex(
            "can't light.*here|cannot light.*here|no room to light.*here|something is blocking.*",
            MESSAGE_WINDOW_TICKS);
        if (blockedMessage != null)
        {
            handleBlockedLight(blockedMessage);
            return;
        }

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

        int currentLogCount = inventory.count(settings.logType.itemId);
        if (expectedLogCount >= 0 && currentLogCount < expectedLogCount)
        {
            log.info("[WAITING] Log consumed — continuing");
            expectedLogCount = currentLogCount;
            blockedLightRetries.reset();
            wasLighting      = false;
            noAnimationTicks = 0;
            if (stepAlongLane())
            {
                return;
            }
            state = State.LIGHT_LOG;
            return;
        }

        noAnimationTicks++;

        if (wasLighting)
        {
            // Animation stopped after the server finished the firemaking action.
            log.info("[WAITING] Log burned — lighting next");
            expectedLogCount = currentLogCount;
            blockedLightRetries.reset();
            wasLighting = false;
            if (stepAlongLane())
            {
                return;
            }
            state = State.LIGHT_LOG;
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
            bankDepositDone  = false;
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
            bank.openNearest(locationProfile);
            bankOpenAttempts++;
            setTickDelay(3);
            return;
        }

        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            if (locationProfile != null && !locationProfile.hasBankAnchor())
            {
                WorldTile bankTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
                locationProfile = locationProfile.withBankAnchor(bankTile);
                log.info("[BANKING] Bank anchor recorded: ({},{},{})",
                    bankTile.getWorldX(), bankTile.getWorldY(), bankTile.getPlane());
            }
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        if (!bankWithdrawDone)
        {
            if (settings.mode == FiremakingMode.GE_NOTED && !bankDepositDone)
            {
                if (bank.depositAllExcept(TINDERBOX_ID))
                {
                    log.debug("[BANKING] Depositing noted logs / extra items...");
                    setTickDelay(1);
                    return;
                }

                log.info("[BANKING] GE-noted deposit complete");
                bankDepositDone = true;
                setTickDelay(1);
                return;
            }

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
        bankDepositDone  = false;
        bankWithdrawDone = false;
        expectedLogCount = -1;
        campfireDialogHandled = false;
        campfireBatchStarted = false;
        blockedLightRetries.reset();
        WorldTile currentTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
        if (locationProfile != null && locationProfile.shouldReturnToWorkArea(currentTile))
        {
            WorldTile returnAnchor = locationProfile.getReturnAnchor();
            log.info("[BANKING] Returning to firemaking lane ({},{},{})",
                returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            pathfinder.walkTo(returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }
        state = State.LIGHT_LOG;
    }

    private void handleBlockedLight(String message)
    {
        wasLighting      = false;
        noAnimationTicks = 0;
        expectedLogCount = inventory.count(settings.logType.itemId);

        if (blockedLightRetries.fail() && trySidestep())
        {
            log.info("[WAITING] Fire blocked: {} — sidestepping to recover", message);
            blockedLightRetries.reset();
            setTickDelay(2);
            state = State.LIGHT_LOG;
            return;
        }

        if (settings.mode == FiremakingMode.LINE_FIRE && resetToLaneAnchor())
        {
            log.info("[WAITING] Fire blocked: {} — resetting to lane anchor", message);
            blockedLightRetries.reset();
            setTickDelay(3);
            state = State.LIGHT_LOG;
            return;
        }

        log.info("[WAITING] Fire blocked: {} — retrying ({}/{})",
            message, blockedLightRetries.getAttempts(), blockedLightRetries.getMaxAttempts());
        setTickDelay(1);
        state = State.LIGHT_LOG;
    }

    private boolean trySidestep()
    {
        int[] offset = SIDESTEP_OFFSETS[sidestepIndex % SIDESTEP_OFFSETS.length];
        sidestepIndex++;

        int targetX = players.getWorldX() + offset[0];
        int targetY = players.getWorldY() + offset[1];
        log.info("[WAITING] Walking to recovery tile ({}, {})", targetX, targetY);
        movement.walkTo(targetX, targetY);
        return true;
    }

    private boolean isTileBlockedForFire()
    {
        return gameObjects.nearest(obj ->
            obj.getPlane() == players.getPlane()
                && obj.getWorldX() == players.getWorldX()
                && obj.getWorldY() == players.getWorldY()
                && matchesAny(obj.getId(), FIRE_OBJECT_IDS)) != null;
    }

    private void handleCampfireWaiting()
    {
        if (!campfireDialogHandled && widgets.isMakeDialogOpen())
        {
            log.info("[WAITING] Campfire dialog open — clicking Make All");
            widgets.clickMakeAll();
            campfireDialogHandled = true;
            setTickDelay(2);
            return;
        }

        int currentLogCount = inventory.count(settings.logType.itemId);
        if (expectedLogCount >= 0 && currentLogCount < expectedLogCount)
        {
            log.info("[WAITING] Campfire consumed logs ({} -> {})", expectedLogCount, currentLogCount);
            expectedLogCount = currentLogCount;
            campfireBatchStarted = true;
            noAnimationTicks = 0;
            wasLighting = players.isAnimating();
            if (currentLogCount == 0)
            {
                state = State.LIGHT_LOG;
            }
            return;
        }

        if (players.isAnimating())
        {
            wasLighting = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;
        if (campfireBatchStarted && noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.info("[WAITING] Campfire batch finished — returning to LIGHT_LOG");
            state = State.LIGHT_LOG;
            noAnimationTicks = 0;
            wasLighting = false;
            campfireBatchStarted = false;
            campfireDialogHandled = false;
            return;
        }

        if (!campfireDialogHandled && noAnimationTicks >= 4)
        {
            log.info("[WAITING] Campfire dialog did not appear — retrying");
            state = State.LIGHT_LOG;
            noAnimationTicks = 0;
            return;
        }
    }

    private boolean stepAlongLane()
    {
        if (settings.mode != FiremakingMode.LINE_FIRE)
        {
            laneResetAttempts = 0;
            return false;
        }

        int nextX = players.getWorldX() + settings.laneDirection.dx;
        int nextY = players.getWorldY() + settings.laneDirection.dy;
        log.info("[WAITING] Advancing fire line to ({},{})", nextX, nextY);
        movement.walkTo(nextX, nextY);
        laneResetAttempts = 0;
        setTickDelay(2);
        state = State.LIGHT_LOG;
        return true;
    }

    private boolean resetToLaneAnchor()
    {
        if (settings.mode != FiremakingMode.LINE_FIRE || locationProfile == null)
        {
            return false;
        }

        if (laneResetAttempts >= MAX_LANE_RESET_ATTEMPTS)
        {
            return false;
        }

        WorldTile anchor = locationProfile.getReturnAnchor();
        if (anchor == null)
        {
            return false;
        }

        laneResetAttempts++;
        log.info("[WAITING] Returning to lane anchor ({},{},{}) attempt {}/{}",
            anchor.getWorldX(), anchor.getWorldY(), anchor.getPlane(),
            laneResetAttempts, MAX_LANE_RESET_ATTEMPTS);
        pathfinder.walkTo(anchor.getWorldX(), anchor.getWorldY(), anchor.getPlane());
        return true;
    }

    private SceneObject findCampfireTarget()
    {
        return gameObjects.nearest(obj ->
            obj.getPlane() == players.getPlane()
                && players.distanceTo(obj.getWorldX(), obj.getWorldY()) <= 4
                && (matchesAny(obj.getId(), CAMPFIRE_OBJECT_IDS)
                    || matchesAny(obj.getId(), FIRE_OBJECT_IDS)
                    || obj.getName().toLowerCase().contains("campfire")
                    || obj.getName().equalsIgnoreCase("Fire")));
    }

    private static boolean matchesAny(int id, int[] ids)
    {
        for (int candidate : ids)
        {
            if (candidate == id)
            {
                return true;
            }
        }
        return false;
    }
}
