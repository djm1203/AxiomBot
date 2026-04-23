package com.axiom.scripts.woodcutting;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.GroundItems;
import com.axiom.api.game.Npcs;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.player.Skills;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.InteractionWatchdog;
import com.axiom.api.util.Log;
import com.axiom.api.util.Pathfinder;
import com.axiom.api.util.Progression;
import com.axiom.api.world.Bank;
import com.axiom.api.world.LocationProfile;
import com.axiom.api.world.WorldTile;

import java.util.Comparator;
import java.util.List;

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
    private GroundItems groundItems;
    private Npcs        npcs;
    private Players     players;
    private Inventory   inventory;
    private Skills      skills;
    private Bank        bank;
    private Pathfinder  pathfinder;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private WoodcuttingSettings settings;

    // ── Auto-mode progression ─────────────────────────────────────────────────
    private Progression progression = null;

    // ── Start location — recorded on first game tick, used to walk back after banking ──
    private boolean startTileRecorded = false;
    private LocationProfile locationProfile;

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
    private static final int MAX_TREE_DISTANCE = 16;
    private static final int TREE_CLUSTER_RADIUS = 3;
    private static final int TARGET_STICKINESS_RADIUS = 2;
    private static final int MAX_NEST_LOOT_DISTANCE = 6;
    private static final int MAX_INTERACTION_RETRIES = 3;
    private static final int MAX_FORESTRY_EVENT_DISTANCE = 6;
    private static final int FORESTRY_EVENT_COOLDOWN_TICKS = 5;

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

    private int lastTreeId = -1;
    private int lastTreeX = Integer.MIN_VALUE;
    private int lastTreeY = Integer.MIN_VALUE;
    private int lastTreePlane = Integer.MIN_VALUE;
    private final InteractionWatchdog interactionWatchdog =
        new InteractionWatchdog(CHOP_TIMEOUT_TICKS, MAX_INTERACTION_RETRIES);
    private int forestryCooldownTicks = 0;
    private static final ForestryEvent[] FORESTRY_EVENTS = {
        new ForestryEvent("Friendly Forester", true, "Talk-to", "Help"),
        new ForestryEvent("Rising Roots", false, "Chop down", "Chop", "Hack"),
        new ForestryEvent("Beehive", false, "Feed", "Repair"),
        new ForestryEvent("Pheasant Nest", false, "Search", "Take"),
        new ForestryEvent("Ritual Circle", false, "Inspect", "Activate", "Touch")
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Woodcutting"; }

    @Override
    public WoodcuttingSettings getDefaultSettings() { return WoodcuttingSettings.defaults(); }

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

        if (settings.autoMode)
        {
            progression = Progression.parse(settings.progressionString,
                WoodcuttingSettings.TreeType.OAK.name());
            log.info("Woodcutting started (auto-mode): progression='{}' action={} powerChop={}",
                settings.progressionString, settings.bankAction.name(), settings.powerChop);
        }
        else
        {
            progression = null;
            log.info("Woodcutting started: tree={} action={} powerChop={}",
                settings.treeType.name(), settings.bankAction.name(), settings.powerChop);
        }

        state = State.FIND_TREE;
        clearLastTree();
        interactionWatchdog.reset();
        forestryCooldownTicks = 0;
    }

    @Override
    public void onLoop()
    {
        // Record start tile on the first game tick — onStart() runs on the Swing EDT
        // so RuneLite API calls there will crash with AssertionError.
        if (!startTileRecorded)
        {
            WorldTile startTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
            locationProfile = LocationProfile.centered(
                "woodcutting-" + settings.locationPreset.name().toLowerCase(),
                startTile,
                settings.locationPreset.workAreaRadius
            ).withBankTargets(
                settings.locationPreset.bankObjectNames,
                settings.locationPreset.bankNpcNames,
                settings.locationPreset.bankActions
            );
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{},{})",
                startTile.getWorldX(), startTile.getWorldY(), startTile.getPlane());
            log.info("[INIT] Location preset: {}", settings.locationPreset.displayName);
        }

        if (forestryCooldownTicks > 0)
        {
            forestryCooldownTicks--;
        }

        if (settings.forestryEnabled && handleForestryEvent())
        {
            return;
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
        forestryCooldownTicks = 0;
        log.info("Woodcutting stopped");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the tree type to target for the current tick.
     * In auto-mode, resolves the best tree for the player's current
     * Woodcutting level via the Progression. Falls back to settings.treeType
     * if the progression yields an unrecognised name.
     */
    private WoodcuttingSettings.TreeType getActiveTreeType()
    {
        if (progression == null) return settings.treeType;

        int level = skills.getBaseLevel(Skills.Skill.WOODCUTTING);
        String name = progression.getMethodForLevel(level);
        if (name == null) return settings.treeType;

        try
        {
            return WoodcuttingSettings.TreeType.valueOf(name.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            log.warn("[AUTO] Unknown tree type '{}' from progression — using {}",
                name, settings.treeType.name());
            return settings.treeType;
        }
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

        if (lootNearbyNest())
        {
            return;
        }

        WoodcuttingSettings.TreeType tree = getActiveTreeType();
        SceneObject treeObj = selectBestTree(tree);

        if (treeObj == null)
        {
            log.info("[FIND_TREE] No {} found nearby — waiting 3 ticks", tree.objectName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_TREE] Found {} (id={}) at ({},{}) — clicking Chop down",
            tree.objectName, treeObj.getId(), treeObj.getWorldX(), treeObj.getWorldY());

        rememberTree(treeObj);
        treeObj.interact("Chop down");
        state         = State.CHOPPING;
        wasChopping   = false;
        chopStartTick = 0;
        interactionWatchdog.begin();

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

        WoodcuttingSettings.TreeType tree = getActiveTreeType();
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
                interactionWatchdog.markSuccess();
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
            rememberTree(treeObj);
        }
        else
        {
            // Interact() fired but animation hasn't started yet (walking to tree).
            // Count ticks; if too long, assume interact failed and re-click.
            chopStartTick++;
            log.debug("[CHOPPING] Waiting for chop animation ({}/{})", chopStartTick, CHOP_TIMEOUT_TICKS);
            if (chopStartTick >= CHOP_TIMEOUT_TICKS)
            {
                InteractionWatchdog.Status status = interactionWatchdog.observe(players.isMoving());
                if (status == InteractionWatchdog.Status.EXHAUSTED)
                {
                    log.warn("[CHOPPING] Interaction failed after {}/{} retries — re-acquiring tree",
                        interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                    interactionWatchdog.reset();
                    state = State.FIND_TREE;
                }
                else if (status == InteractionWatchdog.Status.RETRY)
                {
                    log.info("[CHOPPING] Timeout waiting for animation — re-clicking ({}/{})",
                        interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                    state = State.FIND_TREE;
                }
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

            if (bank.openNearest(locationProfile))
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

        WorldTile currentTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
        if (locationProfile != null && locationProfile.shouldReturnToWorkArea(currentTile))
        {
            WorldTile returnAnchor = locationProfile.getReturnAnchor();
            int distToStart = currentTile.chebyshevDistanceTo(returnAnchor);
            log.info("[BANKING] Walking back to woodcutting area ({},{}) — distance={}",
                returnAnchor.getWorldX(), returnAnchor.getWorldY(), distToStart);
            pathfinder.walkTo(returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
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

    private SceneObject selectBestTree(WoodcuttingSettings.TreeType treeType)
    {
        List<SceneObject> trees = gameObjects.all(o ->
            o.getPlane() == players.getPlane()
                && players.distanceTo(o.getWorldX(), o.getWorldY()) <= MAX_TREE_DISTANCE
                && pathfinder.isReachable(o.getWorldX(), o.getWorldY(), o.getPlane())
                && (treeType.matches(o.getId()) || o.getName().equalsIgnoreCase(treeType.objectName))
                && o.hasAction("Chop down"));

        return trees.stream()
            .min(Comparator.comparingInt(tree -> scoreTree(tree, trees)))
            .orElse(null);
    }

    private int scoreTree(SceneObject tree, List<SceneObject> trees)
    {
        int score = players.distanceTo(tree.getWorldX(), tree.getWorldY()) * 10;

        int nearbyTrees = 0;
        for (SceneObject other : trees)
        {
            if (other == tree) continue;
            int clusterDistance = Math.max(
                Math.abs(other.getWorldX() - tree.getWorldX()),
                Math.abs(other.getWorldY() - tree.getWorldY()));
            if (clusterDistance <= TREE_CLUSTER_RADIUS)
            {
                nearbyTrees++;
            }
        }
        score -= nearbyTrees * 4;

        if (tree.getId() == lastTreeId && tree.getPlane() == lastTreePlane)
        {
            int stickyDistance = Math.max(
                Math.abs(tree.getWorldX() - lastTreeX),
                Math.abs(tree.getWorldY() - lastTreeY));
            if (stickyDistance == 0)
            {
                score -= 12;
            }
            else if (stickyDistance <= TARGET_STICKINESS_RADIUS)
            {
                score -= 6;
            }
        }

        return score;
    }

    private boolean lootNearbyNest()
    {
        SceneObject nest = groundItems.nearest(item ->
            item.getName().toLowerCase().contains("nest")
                && item.hasAction("Take")
                && item.getPlane() == players.getPlane()
                && players.distanceTo(item.getWorldX(), item.getWorldY()) <= MAX_NEST_LOOT_DISTANCE
                && pathfinder.isReachable(item.getWorldX(), item.getWorldY(), item.getPlane()));

        if (nest == null)
        {
            return false;
        }

        log.info("[NEST] Looting {} at ({},{})",
            nest.getName(), nest.getWorldX(), nest.getWorldY());
        nest.interact("Take");
        setTickDelay(2);
        return true;
    }

    private boolean handleForestryEvent()
    {
        if (forestryCooldownTicks > 0
                || inventory.isFull()
                || state == State.BANKING
                || state == State.DROPPING
                || players.isAnimating())
        {
            return false;
        }

        SceneObject eventTarget = selectBestForestryEvent();
        if (eventTarget == null)
        {
            return false;
        }

        String action = resolveForestryAction(eventTarget);
        if (action == null)
        {
            return false;
        }

        log.info("[FORESTRY] Handling {} via '{}'", eventTarget.getName(), action);
        eventTarget.interact(action);
        forestryCooldownTicks = FORESTRY_EVENT_COOLDOWN_TICKS;
        setTickDelay(2);
        return true;
    }

    private SceneObject selectBestForestryEvent()
    {
        SceneObject best = null;
        int bestScore = Integer.MAX_VALUE;

        for (ForestryEvent event : FORESTRY_EVENTS)
        {
            SceneObject candidate = event.isNpc
                ? npcs.nearest(obj -> matchesForestryEvent(obj, event))
                : gameObjects.nearest(obj -> matchesForestryEvent(obj, event));

            if (candidate == null)
            {
                continue;
            }

            int score = players.distanceTo(candidate.getWorldX(), candidate.getWorldY());
            if (score < bestScore)
            {
                best = candidate;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean matchesForestryEvent(SceneObject obj, ForestryEvent event)
    {
        if (obj == null || obj.getPlane() != players.getPlane())
        {
            return false;
        }

        if (players.distanceTo(obj.getWorldX(), obj.getWorldY()) > MAX_FORESTRY_EVENT_DISTANCE)
        {
            return false;
        }

        return obj.getName() != null
            && obj.getName().equalsIgnoreCase(event.name)
            && resolveForestryAction(obj) != null;
    }

    private String resolveForestryAction(SceneObject obj)
    {
        for (ForestryEvent event : FORESTRY_EVENTS)
        {
            if (!event.name.equalsIgnoreCase(obj.getName()))
            {
                continue;
            }

            for (String action : event.actions)
            {
                if (obj.hasAction(action))
                {
                    return action;
                }
            }
        }

        return null;
    }

    private void rememberTree(SceneObject tree)
    {
        lastTreeId = tree.getId();
        lastTreeX = tree.getWorldX();
        lastTreeY = tree.getWorldY();
        lastTreePlane = tree.getPlane();
    }

    private void clearLastTree()
    {
        lastTreeId = -1;
        lastTreeX = Integer.MIN_VALUE;
        lastTreeY = Integer.MIN_VALUE;
        lastTreePlane = Integer.MIN_VALUE;
    }

    private static final class ForestryEvent
    {
        private final String name;
        private final boolean isNpc;
        private final String[] actions;

        private ForestryEvent(String name, boolean isNpc, String... actions)
        {
            this.name = name;
            this.isNpc = isNpc;
            this.actions = actions;
        }
    }
}
