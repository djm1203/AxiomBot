package com.axiom.scripts.thieving;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Messages;
import com.axiom.api.game.Npcs;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.util.RetryBudget;

import com.axiom.scripts.thieving.ThievingSettings.ThievingMethod;

/**
 * Axiom Thieving — two modes: steal from market stalls or pickpocket NPCs.
 *
 * State machine (shared for both modes):
 *   FIND_TARGET — locate the stall or NPC; click the relevant action
 *   ACTING      — wait for the steal/pickpocket to land using inventory change detection
 *
 * Success signal — inventory slot count:
 *   Inventory slot count is snapshotted immediately after clicking the action.
 *   On each subsequent tick the count is compared. When it increases, a new
 *   item landed in inventory → steal/pickpocket succeeded.
 *
 *   This is more reliable than animation tracking for stalls because the steal
 *   animation is 1–2 ticks long and often fires and completes between script ticks.
 *
 *   For pickpockets where the stolen item is coins already present in inventory,
 *   the slot count does not change (coins stack onto an existing slot). In that
 *   case the 10-tick timeout fires and the script retries — the coins were still
 *   added; the retry just starts the next pickpocket.
 *
 * Pickpocket stun (graphic 245):
 *   Checked at the top of both FIND_NPC and PICKPOCKETING handlers.
 *   A 4-tick delay clears the stun window before the next attempt.
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Thieving",
    version     = "1.1",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Steals from market stalls or pickpockets NPCs."
)
public class ThievingScript extends BotScript
{
    private static final String[] BLACKJACK_ACTIONS = { "Knock-Out", "Knock-out", "Knock out" };

    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Players     players;
    private GameObjects gameObjects;
    private Messages    messages;
    private Npcs        npcs;
    private Inventory   inventory;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private ThievingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_TARGET, ACTING, KNOCKOUT_WAIT, BLACKJACKING }
    private State state = State.FIND_TARGET;

    // Inventory change detection
    private int inventoryCountBefore = 0;
    private int trackedLootCountBefore = 0;
    private int noChangeTicks        = 0;
    private static final int NO_CHANGE_TICKS = 10;
    private static final int MESSAGE_WINDOW_TICKS = 8;
    private static final int STUN_EAT_HP_THRESHOLD = 55;
    private static final String COIN_POUCH_NAME = "coin pouch";
    private static final int MAX_COIN_POUCH_COUNT = 28;
    private static final int STICKY_TARGET_RADIUS = 2;
    private static final int STALL_GUARD_RADIUS = 5;
    private static final int BLACKJACK_WAIT_TICKS = 6;

    private final RetryBudget stickyTargetRetries = new RetryBudget(4);
    private int lastNpcId = -1;
    private int lastNpcX = Integer.MIN_VALUE;
    private int lastNpcY = Integer.MIN_VALUE;
    private int lastNpcPlane = Integer.MIN_VALUE;

    // Pickpocket stun graphic ID (THUMP overlay on player model)
    private static final int STUN_GRAPHIC = 245;
    private int blackjackWaitTicks = 0;
    private int blackjackPickpocketsRemaining = 0;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Thieving"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof ThievingSettings)
            ? (ThievingSettings) raw
            : ThievingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        state                = State.FIND_TARGET;
        inventoryCountBefore = 0;
        trackedLootCountBefore = 0;
        noChangeTicks        = 0;
        stickyTargetRetries.reset();
        clearLastNpc();
        blackjackWaitTicks = 0;
        blackjackPickpocketsRemaining = 0;

        if (settings.method == ThievingMethod.STALL)
        {
            log.info("Thieving started: method=STALL stall='{}'",
                settings.stallType.stallName);
        }
        else
        {
            log.info("Thieving started: method=PICKPOCKET npc='{}'",
                settings.npcTarget.npcName);
        }
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_TARGET:
                if (settings.method == ThievingMethod.STALL) handleFindStall();
                else if (settings.method == ThievingMethod.BLACKJACK) handleFindBlackjackNpc();
                else handleFindNpc();
                break;
            case ACTING:
                if (settings.method == ThievingMethod.STALL) handleWaitingStall();
                else if (settings.method == ThievingMethod.BLACKJACK) handleBlackjackPickpocketResult();
                else handlePickpocketing();
                break;
            case KNOCKOUT_WAIT:
                handleBlackjackKnockoutWait();
                break;
            case BLACKJACKING:
                handleBlackjacking();
                break;
        }
    }

    @Override
    public void onStop()
    {
        trackedLootCountBefore = 0;
        stickyTargetRetries.reset();
        clearLastNpc();
        blackjackWaitTicks = 0;
        blackjackPickpocketsRemaining = 0;
        log.info("Thieving stopped");
    }

    // ── Stall handlers ────────────────────────────────────────────────────────

    private void handleFindStall()
    {
        if (isStallGuardNearby())
        {
            log.info("[FIND_STALL] Guard too close to '{}' — waiting",
                settings.stallType.stallName);
            setTickDelay(3);
            return;
        }

        // Exact name match — avoids picking up unrelated objects
        SceneObject stall = gameObjects.nearest(
            o -> o.getName().equalsIgnoreCase(settings.stallType.stallName));

        if (stall == null)
        {
            log.info("[FIND_STALL] No '{}' found nearby — waiting",
                settings.stallType.stallName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_STALL] Found '{}' (id={}) — stealing",
            settings.stallType.stallName, stall.getId());
        stall.interact("Steal-from");

        // Snapshot inventory size immediately after the click so we can detect
        // when the stolen item lands in a new slot.
        inventoryCountBefore = inventory.getCount();
        trackedLootCountBefore = getTrackedLootCount();
        noChangeTicks        = 0;
        setTickDelay(1);
        state = State.ACTING;
    }

    private void handleWaitingStall()
    {
        int currentCount = inventory.getCount();
        int currentTrackedLoot = getTrackedLootCount();

        if (currentCount > inventoryCountBefore
            || currentTrackedLoot > trackedLootCountBefore
            || messages.pollRecentRegex("you steal from.*stall", MESSAGE_WINDOW_TICKS) != null)
        {
            log.info("[WAITING_STALL] Item stolen — slots {} -> {}, tracked loot {} -> {}",
                inventoryCountBefore, currentCount, trackedLootCountBefore, currentTrackedLoot);
            if (settings.dropJunk) dropJunk();
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(Math.max(antiban.reactionTicks(), 1));
            return;
        }

        noChangeTicks++;
        if (noChangeTicks >= NO_CHANGE_TICKS)
        {
            log.warn("[WAITING_STALL] No inventory change after {} ticks — retrying",
                NO_CHANGE_TICKS);
            noChangeTicks = 0;
            state = State.FIND_TARGET;
        }
        else
        {
            log.info("[WAITING_STALL] Waiting for stolen item ({}/{})",
                noChangeTicks, NO_CHANGE_TICKS);
        }
    }

    // ── Pickpocket handlers ───────────────────────────────────────────────────

    private void handleFindNpc()
    {
        if (shouldOpenCoinPouch())
        {
            openCoinPouch();
            return;
        }

        // Wait out stun before attempting next pickpocket
        if (players.getGraphic() == STUN_GRAPHIC)
        {
            if (eatOnStun())
            {
                return;
            }
            log.info("[FIND_NPC] Stunned — waiting");
            setTickDelay(4);
            return;
        }

        SceneObject target = findPickpocketTarget();

        if (target == null)
        {
            log.info("[FIND_NPC] No '{}' found nearby — waiting",
                settings.npcTarget.npcName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_NPC] Pickpocketing '{}' (id={}) at ({},{})",
            settings.npcTarget.npcName,
            target.getId(), target.getWorldX(), target.getWorldY());
        rememberNpc(target);
        target.interact("Pickpocket");

        // Snapshot inventory size — only detects the pickpocket if a new slot
        // opens (e.g. first coins, seeds, gems). If coins are already stacked,
        // count won't change; the 10-tick timeout retries cleanly.
        inventoryCountBefore = inventory.getCount();
        trackedLootCountBefore = getTrackedLootCount();
        noChangeTicks        = 0;
        setTickDelay(1);
        state = State.ACTING;
    }

    private void handleFindBlackjackNpc()
    {
        if (shouldOpenCoinPouch())
        {
            openCoinPouch();
            return;
        }

        if (players.getGraphic() == STUN_GRAPHIC)
        {
            if (eatOnStun())
            {
                return;
            }
            log.info("[BLACKJACK] Stunned — waiting");
            setTickDelay(4);
            return;
        }

        SceneObject target = findBlackjackTarget();
        if (target == null)
        {
            log.info("[BLACKJACK] No '{}' found nearby with a knock-out action — waiting",
                settings.npcTarget.npcName);
            setTickDelay(3);
            return;
        }

        String knockoutAction = findAvailableAction(target, BLACKJACK_ACTIONS);
        if (knockoutAction == null)
        {
            log.info("[BLACKJACK] Target '{}' is not currently knock-outable — waiting",
                settings.npcTarget.npcName);
            setTickDelay(2);
            return;
        }

        log.info("[BLACKJACK] Using '{}' on '{}' at ({},{})",
            knockoutAction, target.getName(), target.getWorldX(), target.getWorldY());
        rememberNpc(target);
        target.interact(knockoutAction);
        blackjackWaitTicks = 0;
        setTickDelay(2);
        state = State.KNOCKOUT_WAIT;
    }

    private void handlePickpocketing()
    {
        if (shouldOpenCoinPouch())
        {
            openCoinPouch();
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            return;
        }

        // Stun detected — abort and wait
        if (players.getGraphic() == STUN_GRAPHIC)
        {
            if (eatOnStun())
            {
                return;
            }
            log.info("[PICKPOCKETING] Stunned — waiting out stun");
            noChangeTicks = 0;
            setTickDelay(4);
            state = State.FIND_TARGET;
            return;
        }

        int currentCount = inventory.getCount();
        int currentTrackedLoot = getTrackedLootCount();
        String successMessage = messages.pollRecentRegex("you pick the .*pocket", MESSAGE_WINDOW_TICKS);
        String failureMessage = messages.pollRecentRegex("you fail to pick the .*pocket", MESSAGE_WINDOW_TICKS);

        if (currentCount > inventoryCountBefore
            || currentTrackedLoot > trackedLootCountBefore
            || successMessage != null)
        {
            log.info("[PICKPOCKETING] Pickpocket landed — slots {} -> {}, tracked loot {} -> {}",
                inventoryCountBefore, currentCount, trackedLootCountBefore, currentTrackedLoot);
            stickyTargetRetries.reset();
            if (settings.dropJunk) dropJunk();
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(Math.max(antiban.reactionTicks(), 1));
            return;
        }

        if (failureMessage != null)
        {
            log.info("[PICKPOCKETING] Pickpocket failed — retrying");
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(2);
            return;
        }

        noChangeTicks++;
        if (noChangeTicks >= NO_CHANGE_TICKS)
        {
            // Timeout — either coins stacked (already had coins) or missed.
            // Either way, retry immediately.
            log.info("[PICKPOCKETING] No new slot after {} ticks — retrying",
                NO_CHANGE_TICKS);
            noChangeTicks = 0;
            state = State.FIND_TARGET;
        }
        else
        {
            log.info("[PICKPOCKETING] Waiting for pickpocket result ({}/{})",
                noChangeTicks, NO_CHANGE_TICKS);
        }
    }

    private void handleBlackjackKnockoutWait()
    {
        if (players.getGraphic() == STUN_GRAPHIC)
        {
            if (eatOnStun())
            {
                return;
            }
            log.info("[BLACKJACK] Knock-out failed and stunned the player");
            blackjackPickpocketsRemaining = 0;
            blackjackWaitTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(4);
            return;
        }

        String failureMessage = messages.pollRecentRegex(
            "glances off.*|fails to knock.*out|resists being knocked out",
            MESSAGE_WINDOW_TICKS);
        if (failureMessage != null)
        {
            log.info("[BLACKJACK] Knock-out failed: {}", failureMessage);
            blackjackPickpocketsRemaining = 0;
            blackjackWaitTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(3);
            return;
        }

        String successMessage = messages.pollRecentRegex(
            "render.*unconscious|knock.*unconscious",
            MESSAGE_WINDOW_TICKS);
        SceneObject unconsciousTarget = findBlackjackPickpocketTarget();
        if (successMessage != null || unconsciousTarget != null)
        {
            blackjackPickpocketsRemaining = 2;
            blackjackWaitTicks = 0;
            log.info("[BLACKJACK] Knock-out landed — starting pickpockets");
            state = State.BLACKJACKING;
            return;
        }

        blackjackWaitTicks++;
        if (blackjackWaitTicks >= BLACKJACK_WAIT_TICKS)
        {
            log.info("[BLACKJACK] Knock-out window timed out — retrying");
            blackjackWaitTicks = 0;
            state = State.FIND_TARGET;
            return;
        }

        log.info("[BLACKJACK] Waiting for knock-out result ({}/{})",
            blackjackWaitTicks, BLACKJACK_WAIT_TICKS);
    }

    private void handleBlackjacking()
    {
        if (shouldOpenCoinPouch())
        {
            openCoinPouch();
            setTickDelay(1);
            return;
        }

        if (blackjackPickpocketsRemaining <= 0)
        {
            state = State.FIND_TARGET;
            return;
        }

        SceneObject target = findBlackjackPickpocketTarget();
        if (target == null)
        {
            log.info("[BLACKJACK] Unconscious target moved or woke up — re-acquiring");
            blackjackPickpocketsRemaining = 0;
            state = State.FIND_TARGET;
            return;
        }

        log.info("[BLACKJACK] Pickpocketing unconscious '{}' ({} remaining)",
            target.getName(), blackjackPickpocketsRemaining);
        target.interact("Pickpocket");
        inventoryCountBefore = inventory.getCount();
        trackedLootCountBefore = getTrackedLootCount();
        noChangeTicks = 0;
        setTickDelay(1);
        state = State.ACTING;
    }

    private void handleBlackjackPickpocketResult()
    {
        if (shouldOpenCoinPouch())
        {
            openCoinPouch();
            noChangeTicks = 0;
            return;
        }

        if (players.getGraphic() == STUN_GRAPHIC)
        {
            if (eatOnStun())
            {
                return;
            }
            log.info("[BLACKJACK] Stunned while blackjacking — resetting");
            blackjackPickpocketsRemaining = 0;
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(4);
            return;
        }

        int currentCount = inventory.getCount();
        int currentTrackedLoot = getTrackedLootCount();
        String successMessage = messages.pollRecentRegex("you pick the .*pocket", MESSAGE_WINDOW_TICKS);
        String failureMessage = messages.pollRecentRegex("you fail to pick the .*pocket", MESSAGE_WINDOW_TICKS);

        if (currentCount > inventoryCountBefore
            || currentTrackedLoot > trackedLootCountBefore
            || successMessage != null)
        {
            blackjackPickpocketsRemaining--;
            log.info("[BLACKJACK] Pickpocket landed — remaining={}", blackjackPickpocketsRemaining);
            if (settings.dropJunk)
            {
                dropJunk();
            }
            noChangeTicks = 0;
            state = blackjackPickpocketsRemaining > 0 ? State.BLACKJACKING : State.FIND_TARGET;
            setTickDelay(Math.max(antiban.reactionTicks(), 1));
            return;
        }

        if (failureMessage != null)
        {
            log.info("[BLACKJACK] Pickpocket failed — resetting target");
            blackjackPickpocketsRemaining = 0;
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(2);
            return;
        }

        noChangeTicks++;
        if (noChangeTicks >= NO_CHANGE_TICKS)
        {
            log.info("[BLACKJACK] No pickpocket result after {} ticks — resetting",
                NO_CHANGE_TICKS);
            blackjackPickpocketsRemaining = 0;
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            return;
        }

        log.info("[BLACKJACK] Waiting for pickpocket result ({}/{})",
            noChangeTicks, NO_CHANGE_TICKS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Drops configured stolen items when inventory is full, freeing space to
     * continue stealing in XP-focused sessions.
     */
    private void dropJunk()
    {
        if (inventory.isFull())
        {
            int[] junkItemIds = settings.method == ThievingMethod.STALL
                ? settings.stallType.stolenItemIds
                : settings.npcTarget.stolenItemIds;

            log.info("[DROP_JUNK] Inventory full — dropping configured junk items");
            for (int itemId : junkItemIds)
            {
                inventory.dropAll(itemId);
            }
        }
    }

    private int getTrackedLootCount()
    {
        int total = 0;
        int[] trackedItemIds = settings.method == ThievingMethod.STALL
            ? settings.stallType.stolenItemIds
            : settings.npcTarget.stolenItemIds;

        for (int itemId : trackedItemIds)
        {
            total += inventory.count(itemId);
        }
        return total;
    }

    private boolean eatOnStun()
    {
        if (settings.foodIds.length == 0
            || players.getHealthPercent() > STUN_EAT_HP_THRESHOLD
            || !inventory.containsAny(settings.foodIds))
        {
            return false;
        }

        for (int foodId : settings.foodIds)
        {
            if (inventory.containsById(foodId))
            {
                log.info("[THIEVING] Stunned at {}% HP — eating food id={}",
                    players.getHealthPercent(), foodId);
                inventory.clickItem(foodId);
                setTickDelay(1);
                return true;
            }
        }

        return false;
    }

    private SceneObject findPickpocketTarget()
    {
        SceneObject sticky = findStickyTarget();
        if (sticky != null)
        {
            return sticky;
        }

        return npcs.nearest(n -> n.getName().equalsIgnoreCase(settings.npcTarget.npcName));
    }

    private SceneObject findBlackjackTarget()
    {
        SceneObject sticky = settings.stickyTarget ? findStickyBlackjackTarget() : null;
        if (sticky != null)
        {
            return sticky;
        }

        return npcs.nearest(n ->
            n.getName().equalsIgnoreCase(settings.npcTarget.npcName)
                && findAvailableAction(n, BLACKJACK_ACTIONS) != null);
    }

    private SceneObject findBlackjackPickpocketTarget()
    {
        return npcs.nearest(n ->
            n.getName().equalsIgnoreCase(settings.npcTarget.npcName)
                && n.hasAction("Pickpocket"));
    }

    private SceneObject findStickyTarget()
    {
        if (!settings.stickyTarget
            || lastNpcId == -1
            || stickyTargetRetries.isExhausted())
        {
            return null;
        }

        SceneObject sticky = npcs.nearest(n ->
            n.getId() == lastNpcId
                && n.getPlane() == lastNpcPlane
                && n.getName().equalsIgnoreCase(settings.npcTarget.npcName)
                && Math.max(Math.abs(n.getWorldX() - lastNpcX), Math.abs(n.getWorldY() - lastNpcY))
                    <= STICKY_TARGET_RADIUS);

        if (sticky != null)
        {
            stickyTargetRetries.fail();
            log.info("[FIND_NPC] Reusing sticky target '{}' at ({},{}) ({}/{})",
                sticky.getName(),
                sticky.getWorldX(),
                sticky.getWorldY(),
                stickyTargetRetries.getAttempts(),
                stickyTargetRetries.getMaxAttempts());
        }

        return sticky;
    }

    private SceneObject findStickyBlackjackTarget()
    {
        if (!settings.stickyTarget
            || lastNpcId == -1
            || stickyTargetRetries.isExhausted())
        {
            return null;
        }

        SceneObject sticky = npcs.nearest(n ->
            n.getId() == lastNpcId
                && n.getPlane() == lastNpcPlane
                && n.getName().equalsIgnoreCase(settings.npcTarget.npcName)
                && Math.max(Math.abs(n.getWorldX() - lastNpcX), Math.abs(n.getWorldY() - lastNpcY))
                    <= STICKY_TARGET_RADIUS
                && findAvailableAction(n, BLACKJACK_ACTIONS) != null);

        if (sticky != null)
        {
            stickyTargetRetries.fail();
            log.info("[BLACKJACK] Reusing sticky target '{}' at ({},{}) ({}/{})",
                sticky.getName(),
                sticky.getWorldX(),
                sticky.getWorldY(),
                stickyTargetRetries.getAttempts(),
                stickyTargetRetries.getMaxAttempts());
        }

        return sticky;
    }

    private void rememberNpc(SceneObject target)
    {
        lastNpcId = target.getId();
        lastNpcX = target.getWorldX();
        lastNpcY = target.getWorldY();
        lastNpcPlane = target.getPlane();
    }

    private void clearLastNpc()
    {
        lastNpcId = -1;
        lastNpcX = Integer.MIN_VALUE;
        lastNpcY = Integer.MIN_VALUE;
        lastNpcPlane = Integer.MIN_VALUE;
    }

    private boolean shouldOpenCoinPouch()
    {
        if (settings.method != ThievingMethod.PICKPOCKET)
        {
            return false;
        }

        if (inventory.countByName(COIN_POUCH_NAME) >= MAX_COIN_POUCH_COUNT)
        {
            return true;
        }

        return messages.containsRecentRegex("coin pouch.*full|can't carry any more coin pouches", MESSAGE_WINDOW_TICKS);
    }

    private boolean isStallGuardNearby()
    {
        if (settings.method != ThievingMethod.STALL || settings.stallType.guardNpcNames.length == 0)
        {
            return false;
        }

        SceneObject guard = npcs.nearest(n -> matchesAnyName(n.getName(), settings.stallType.guardNpcNames)
            && players.distanceTo(n.getWorldX(), n.getWorldY()) <= STALL_GUARD_RADIUS);
        return guard != null;
    }

    private static boolean matchesAnyName(String name, String[] candidates)
    {
        if (name == null || candidates == null)
        {
            return false;
        }

        for (String candidate : candidates)
        {
            if (candidate != null && name.equalsIgnoreCase(candidate))
            {
                return true;
            }
        }
        return false;
    }

    private static String findAvailableAction(SceneObject object, String[] actions)
    {
        if (object == null || actions == null)
        {
            return null;
        }

        for (String action : actions)
        {
            if (action != null && object.hasAction(action))
            {
                return action;
            }
        }

        return null;
    }

    private void openCoinPouch()
    {
        log.info("[THIEVING] Opening coin pouch");
        inventory.clickItemByName(COIN_POUCH_NAME);
        setTickDelay(1);
    }
}
