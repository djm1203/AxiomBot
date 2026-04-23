package com.axiom.scripts.mining;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.InteractionWatchdog;
import com.axiom.api.util.Log;
import com.axiom.api.util.Pathfinder;
import com.axiom.api.world.Bank;
import com.axiom.api.world.LocationProfile;
import com.axiom.api.world.WorldTile;
import com.axiom.scripts.mining.MiningSettings.MiningAction;
import com.axiom.scripts.mining.MiningSettings.MiningMethod;
import com.axiom.scripts.mining.MiningSettings.OreType;

import java.util.Comparator;
import java.util.List;

/**
 * Axiom Mining — finds rocks, mines ore, then banks or drops.
 *
 * Structurally identical to Woodcutting: rock depletes instead of falling,
 * but the animation-tracking loop is the same.
 *
 * State machine:
 *   FIND_ROCK → MINING → FULL → DROPPING / BANKING → FIND_ROCK
 *
 * Rock depletion detection:
 *   Tick N:   interact("Mine") fires → state = MINING, wasActing = false
 *   First tick with isAnimating() == true → wasActing = true
 *   First tick with isAnimating() == false AFTER wasActing == true → rock depleted → FIND_ROCK
 *   If animation never starts within ANIMATION_TIMEOUT_TICKS → timeout → FIND_ROCK
 *
 * Banking:
 *   depositAllExcept(PICKAXE_IDS) — pickaxe in inventory is always protected.
 *   Pickaxe equipped in weapon slot is not in inventory, so only inventory pickaxes
 *   need protection — equipping handles itself.
 */
@ScriptManifest(
    name        = "Axiom Mining",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Mines ores and optionally banks or drops them."
)
public class MiningScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private GameObjects gameObjects;
    private Players     players;
    private Inventory   inventory;
    private Bank        bank;
    private Pathfinder  pathfinder;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private MiningSettings settings;

    // ── Start location — recorded on first game tick, used to walk back after banking ──
    private boolean startTileRecorded = false;
    private LocationProfile locationProfile;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State {
        FIND_ROCK, MINING, FULL, DROPPING, BANKING,
        MLM_FIND_VEIN, MLM_MINING, MLM_DEPOSIT_HOPPER, MLM_COLLECT_SACK
    }
    private State state = State.FIND_ROCK;

    // Animation tracking
    private boolean wasActing        = false;
    private int     noAnimationTicks = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;
    private static final int MAX_ROCK_DISTANCE = 18;
    private static final int ROCK_CLUSTER_RADIUS = 2;
    private static final int TARGET_STICKINESS_RADIUS = 2;
    private static final int MAX_INTERACTION_RETRIES = 3;
    private static final int COAL_BAG_FILL_THRESHOLD = 9;
    private static final int COAL_BAG_CLOSED_ID = 12019;
    private static final int COAL_BAG_OPEN_ID = 24480;
    private static final int PAY_DIRT_ID = 12011;
    private static final int[] MLM_VEIN_IDS = {26661, 26662, 26663, 26664, 26665, 26666};
    private static final int[] MLM_ROCKFALL_IDS = {26679, 26680};
    private static final int[] MLM_STRUT_IDS = {26670, 26671};

    // Banking state
    private boolean bankJustOpened  = false;
    private boolean bankDepositDone = false;
    private boolean bankBagDone     = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    private int lastRockId = -1;
    private int lastRockX = Integer.MIN_VALUE;
    private int lastRockY = Integer.MIN_VALUE;
    private int lastRockPlane = Integer.MIN_VALUE;
    private final InteractionWatchdog interactionWatchdog =
        new InteractionWatchdog(ANIMATION_TIMEOUT_TICKS, MAX_INTERACTION_RETRIES);

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Mining"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof MiningSettings)
            ? (MiningSettings) raw
            : MiningSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        startTileRecorded = false;

        log.info("Mining started: method={} ore={} action={} powerMine={}",
            settings.method.displayName, settings.oreType.oreName, settings.action.displayName, settings.powerMine);

        state = isMotherlodeMode() ? State.MLM_FIND_VEIN : State.FIND_ROCK;
        clearLastRock();
        interactionWatchdog.reset();
    }

    @Override
    public void onLoop()
    {
        // Record start tile on the first game tick — onStart() runs on the Swing EDT
        // so RuneLite API calls there will crash with AssertionError.
        if (!startTileRecorded)
        {
            WorldTile startTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
            locationProfile = buildLocationProfile(startTile);
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{},{})",
                startTile.getWorldX(), startTile.getWorldY(), startTile.getPlane());
        }

        switch (state)
        {
            case FIND_ROCK: handleFindRock(); break;
            case MINING:    handleMining();   break;
            case FULL:      handleFull();     break;
            case DROPPING:  handleDropping(); break;
            case BANKING:   handleBanking();  break;
            case MLM_FIND_VEIN: handleMotherlodeFindVein(); break;
            case MLM_MINING: handleMotherlodeMining(); break;
            case MLM_DEPOSIT_HOPPER: handleMotherlodeDepositHopper(); break;
            case MLM_COLLECT_SACK: handleMotherlodeCollectSack(); break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Mining stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindRock()
    {
        if (tryFillCoalBag(false))
        {
            return;
        }

        if (inventory.isFull())
        {
            log.info("[FIND_ROCK] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        SceneObject rock = selectBestRock();
        if (rock == null)
        {
            if (hasNearbyDepletedRock())
            {
                log.info("[FIND_ROCK] Nearby {} rocks are depleted — waiting for respawn",
                    settings.oreType.oreName);
                setTickDelay(2);
                return;
            }

            log.info("[FIND_ROCK] No {} rock found nearby — waiting 3 ticks",
                settings.oreType.oreName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_ROCK] Found {} (id={}) at ({},{}) — clicking Mine",
            settings.oreType.oreName, rock.getId(), rock.getWorldX(), rock.getWorldY());
        rememberRock(rock);
        rock.interact("Mine");
        wasActing        = false;
        noAnimationTicks = 0;
        interactionWatchdog.begin();
        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        log.debug("[FIND_ROCK] Reaction delay: {} ticks", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.MINING;
    }

    private void handleMining()
    {
        if (tryFillCoalBag(true))
        {
            wasActing = false;
            noAnimationTicks = 0;
            state = State.FIND_ROCK;
            return;
        }

        if (inventory.isFull())
        {
            log.info("[MINING] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        if (players.isAnimating())
        {
            if (!wasActing)
            {
                log.info("[MINING] Mining animation started");
                interactionWatchdog.markSuccess();
            }
            else            log.debug("[MINING] Still mining...");
            wasActing        = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasActing)
        {
            log.info("[MINING] Rock depleted — transitioning to FIND_ROCK");
            wasActing        = false;
            noAnimationTicks = 0;
            SceneObject nextRock = selectBestRock();
            if (nextRock != null)
            {
                rememberRock(nextRock);
            }
            state            = State.FIND_ROCK;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            InteractionWatchdog.Status status = interactionWatchdog.observe(players.isMoving());
            if (status == InteractionWatchdog.Status.EXHAUSTED)
            {
                log.warn("[MINING] Interaction failed after {}/{} retries — re-acquiring rock",
                    interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                interactionWatchdog.reset();
                state = State.FIND_ROCK;
            }
            else if (status == InteractionWatchdog.Status.RETRY)
            {
                log.info("[MINING] Animation timeout — retrying interaction ({}/{})",
                    interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                state = State.FIND_ROCK;
            }
            wasActing        = false;
            noAnimationTicks = 0;
        }
        else
        {
            log.debug("[MINING] Waiting for animation ({}/{})", noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
        }
    }

    private void handleFull()
    {
        if (settings.action == MiningAction.DROP || settings.powerMine)
        {
            log.info("[FULL] Drop mode — transitioning to DROPPING");
            state = State.DROPPING;
        }
        else
        {
            log.info("[FULL] Bank mode — transitioning to BANKING");
            state = State.BANKING;
        }
    }

    private void handleDropping()
    {
        log.info("[DROPPING] Dropping {} (id={})", settings.oreType.oreName, settings.oreType.oreId);
        inventory.dropAll(settings.oreType.oreId);
        setTickDelay(1);
        state = State.FIND_ROCK;
    }

    private void handleMotherlodeFindVein()
    {
        if (inventory.containsById(PAY_DIRT_ID) && inventory.isFull())
        {
            log.info("[MLM] Inventory full of pay-dirt — moving to hopper");
            state = State.MLM_DEPOSIT_HOPPER;
            return;
        }

        if (inventory.isFull())
        {
            log.info("[MLM] Inventory full of processed ores — banking");
            state = State.BANKING;
            return;
        }

        SceneObject blocker = nearestMotherlodeObstacle();
        if (blocker != null)
        {
            String action = blocker.hasAction("Hammer") ? "Hammer" : "Mine";
            log.info("[MLM] Clearing obstacle {} via '{}'", blocker.getName(), action);
            blocker.interact(action);
            setTickDelay(2);
            return;
        }

        SceneObject vein = findNearestMotherlodeVein();
        if (vein == null)
        {
            log.info("[MLM] No nearby ore vein found — waiting");
            setTickDelay(3);
            return;
        }

        log.info("[MLM] Mining ore vein at ({},{})", vein.getWorldX(), vein.getWorldY());
        vein.interact("Mine");
        wasActing = false;
        noAnimationTicks = 0;
        interactionWatchdog.begin();
        setTickDelay(Math.max(antiban.reactionTicks(), 2));
        state = State.MLM_MINING;
    }

    private void handleMotherlodeMining()
    {
        if (inventory.containsById(PAY_DIRT_ID) && inventory.isFull())
        {
            log.info("[MLM] Inventory full during mining — moving to hopper");
            state = State.MLM_DEPOSIT_HOPPER;
            return;
        }

        if (players.isAnimating())
        {
            if (!wasActing)
            {
                log.info("[MLM] Motherlode mining animation started");
                interactionWatchdog.markSuccess();
            }
            wasActing = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasActing)
        {
            wasActing = false;
            noAnimationTicks = 0;
            state = State.MLM_FIND_VEIN;
            return;
        }

        if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            InteractionWatchdog.Status status = interactionWatchdog.observe(players.isMoving());
            if (status == InteractionWatchdog.Status.EXHAUSTED)
            {
                log.warn("[MLM] Vein interaction failed after {}/{} retries",
                    interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                interactionWatchdog.reset();
                state = State.MLM_FIND_VEIN;
            }
            else if (status == InteractionWatchdog.Status.RETRY)
            {
                log.info("[MLM] Vein interaction timed out — retrying");
                state = State.MLM_FIND_VEIN;
            }
            noAnimationTicks = 0;
        }
    }

    private void handleMotherlodeDepositHopper()
    {
        if (!inventory.containsById(PAY_DIRT_ID))
        {
            log.info("[MLM] Pay-dirt deposited — checking sack");
            state = State.MLM_COLLECT_SACK;
            return;
        }

        SceneObject hopper = findMotherlodeObject("Hopper", "Deposit");
        if (hopper == null)
        {
            log.info("[MLM] Hopper not found — waiting");
            setTickDelay(3);
            return;
        }

        String action = hopper.hasAction("Deposit") ? "Deposit" : "Use";
        log.info("[MLM] Depositing pay-dirt into hopper");
        hopper.interact(action);
        setTickDelay(2);
    }

    private void handleMotherlodeCollectSack()
    {
        if (inventory.containsById(PAY_DIRT_ID))
        {
            state = State.MLM_DEPOSIT_HOPPER;
            return;
        }

        if (!inventory.isEmpty())
        {
            log.info("[MLM] Sack collection yielded ores — banking");
            state = State.BANKING;
            return;
        }

        SceneObject sack = findMotherlodeObject("Sack", "Search", "Collect");
        if (sack == null)
        {
            log.info("[MLM] Sack not ready — returning to veins");
            state = State.MLM_FIND_VEIN;
            setTickDelay(2);
            return;
        }

        String action = sack.hasAction("Search") ? "Search" : "Collect";
        log.info("[MLM] Collecting ores from sack");
        sack.interact(action);
        setTickDelay(2);
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

        if (!bankBagDone)
        {
            if (emptyCoalBagAtBank())
            {
                setTickDelay(1);
                return;
            }
            bankBagDone = true;
        }

        if (!bankDepositDone)
        {
            if (bank.depositAllExcept(getProtectedBankItemIds()))
            {
                log.debug("[BANKING] Depositing items...");
                setTickDelay(1);
                return;
            }
            log.info("[BANKING] Deposit complete (pickaxe protected)");
            bankDepositDone = true;
        }

        if (isMotherlodeMode())
        {
            log.info("[BANKING] Motherlode inventory cleared — closing bank");
            bank.close();
            resetBankState();
            setTickDelay(2);
            state = State.MLM_FIND_VEIN;
            return;
        }

        log.info("[BANKING] Closing bank");
        bank.close();
        resetBankState();

        WorldTile currentTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
        if (locationProfile != null && locationProfile.shouldReturnToWorkArea(currentTile))
        {
            WorldTile returnAnchor = locationProfile.getReturnAnchor();
            int distToStart = currentTile.chebyshevDistanceTo(returnAnchor);
            log.info("[BANKING] Walking back to mining area ({},{}) — distance={}",
                returnAnchor.getWorldX(), returnAnchor.getWorldY(), distToStart);
            pathfinder.walkTo(returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }
        state = State.FIND_ROCK;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetBankState()
    {
        bankJustOpened  = false;
        bankDepositDone = false;
        bankBagDone     = false;
    }

    private LocationProfile buildLocationProfile(WorldTile startTile)
    {
        if (isMotherlodeMode())
        {
            return LocationProfile.centered("motherlode", startTile, 18)
                .withBankTargets(
                    new String[]{"Bank chest"},
                    new String[]{"Banker"},
                    new String[]{"Use", "Bank"}
                );
        }

        return LocationProfile.centered("mining", startTile, 10);
    }

    private SceneObject selectBestRock()
    {
        List<SceneObject> rocks = gameObjects.all(rock ->
            rock.getPlane() == players.getPlane()
                && players.distanceTo(rock.getWorldX(), rock.getWorldY()) <= MAX_ROCK_DISTANCE
                && pathfinder.isReachable(rock.getWorldX(), rock.getWorldY(), rock.getPlane())
                && matchesAny(rock.getId(), settings.oreType.rockIds)
                && rock.hasAction("Mine"));

        return rocks.stream()
            .min(Comparator.comparingInt(rock -> scoreRock(rock, rocks)))
            .orElse(null);
    }

    private int scoreRock(SceneObject rock, List<SceneObject> rocks)
    {
        int score = players.distanceTo(rock.getWorldX(), rock.getWorldY()) * 10;

        int nearbyRocks = 0;
        for (SceneObject other : rocks)
        {
            if (other == rock) continue;
            int clusterDistance = Math.max(
                Math.abs(other.getWorldX() - rock.getWorldX()),
                Math.abs(other.getWorldY() - rock.getWorldY()));
            if (clusterDistance <= ROCK_CLUSTER_RADIUS)
            {
                nearbyRocks++;
            }
        }
        score -= nearbyRocks * 4;

        if (rock.getId() == lastRockId && rock.getPlane() == lastRockPlane)
        {
            int stickyDistance = Math.max(
                Math.abs(rock.getWorldX() - lastRockX),
                Math.abs(rock.getWorldY() - lastRockY));
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

    private boolean hasNearbyDepletedRock()
    {
        return gameObjects.nearest(rock ->
            rock.getPlane() == players.getPlane()
                && players.distanceTo(rock.getWorldX(), rock.getWorldY()) <= MAX_ROCK_DISTANCE
                && matchesAny(rock.getId(), settings.oreType.depletedIds)) != null;
    }

    private SceneObject findNearestMotherlodeVein()
    {
        return gameObjects.nearest(obj ->
            obj.getPlane() == players.getPlane()
                && players.distanceTo(obj.getWorldX(), obj.getWorldY()) <= MAX_ROCK_DISTANCE
                && pathfinder.isReachable(obj.getWorldX(), obj.getWorldY(), obj.getPlane())
                && (matchesAny(obj.getId(), MLM_VEIN_IDS) || obj.getName().equalsIgnoreCase("Ore vein"))
                && obj.hasAction("Mine"));
    }

    private SceneObject nearestMotherlodeObstacle()
    {
        SceneObject rockfall = gameObjects.nearest(obj ->
            obj.getPlane() == players.getPlane()
                && players.distanceTo(obj.getWorldX(), obj.getWorldY()) <= 2
                && (matchesAny(obj.getId(), MLM_ROCKFALL_IDS) || obj.getName().equalsIgnoreCase("Rockfall"))
                && obj.hasAction("Mine"));
        if (rockfall != null)
        {
            return rockfall;
        }

        return gameObjects.nearest(obj ->
            obj.getPlane() == players.getPlane()
                && players.distanceTo(obj.getWorldX(), obj.getWorldY()) <= 3
                && (matchesAny(obj.getId(), MLM_STRUT_IDS) || obj.getName().toLowerCase().contains("strut"))
                && obj.hasAction("Hammer"));
    }

    private SceneObject findMotherlodeObject(String name, String... actions)
    {
        return gameObjects.nearest(obj ->
            obj.getPlane() == players.getPlane()
                && players.distanceTo(obj.getWorldX(), obj.getWorldY()) <= 12
                && obj.getName().equalsIgnoreCase(name)
                && hasAnyAction(obj, actions));
    }

    private void rememberRock(SceneObject rock)
    {
        lastRockId = rock.getId();
        lastRockX = rock.getWorldX();
        lastRockY = rock.getWorldY();
        lastRockPlane = rock.getPlane();
    }

    private void clearLastRock()
    {
        lastRockId = -1;
        lastRockX = Integer.MIN_VALUE;
        lastRockY = Integer.MIN_VALUE;
        lastRockPlane = Integer.MIN_VALUE;
    }

    private static boolean matchesAny(int id, int[] ids)
    {
        for (int candidateId : ids)
        {
            if (candidateId == id)
            {
                return true;
            }
        }
        return false;
    }

    private boolean tryFillCoalBag(boolean inventoryFullOverride)
    {
        if (!settings.useCoalBag
            || settings.oreType != OreType.COAL
            || settings.action != MiningAction.BANK
            || !hasCoalBag())
        {
            return false;
        }

        int coalCount = inventory.count(settings.oreType.oreId);
        if (coalCount == 0)
        {
            return false;
        }

        if (!inventoryFullOverride && coalCount < COAL_BAG_FILL_THRESHOLD)
        {
            return false;
        }

        log.info("[MINING] Filling coal bag (coal count={})", coalCount);
        inventory.clickItemActionByName("coal bag", "Fill");
        setTickDelay(1);
        return true;
    }

    private boolean emptyCoalBagAtBank()
    {
        if (!settings.useCoalBag || !hasCoalBag())
        {
            return false;
        }

        log.info("[BANKING] Emptying coal bag");
        inventory.clickItemActionByName("coal bag", "Empty");
        return true;
    }

    private boolean hasCoalBag()
    {
        return inventory.containsById(COAL_BAG_CLOSED_ID) || inventory.containsById(COAL_BAG_OPEN_ID);
    }

    private boolean isMotherlodeMode()
    {
        return settings.method == MiningMethod.MOTHERLODE;
    }

    private int[] getProtectedBankItemIds()
    {
        if (!settings.useCoalBag)
        {
            return OreType.PICKAXE_IDS;
        }

        int[] protectedIds = new int[OreType.PICKAXE_IDS.length + 2];
        System.arraycopy(OreType.PICKAXE_IDS, 0, protectedIds, 0, OreType.PICKAXE_IDS.length);
        protectedIds[protectedIds.length - 2] = COAL_BAG_CLOSED_ID;
        protectedIds[protectedIds.length - 1] = COAL_BAG_OPEN_ID;
        return protectedIds;
    }

    private static boolean hasAnyAction(SceneObject object, String... actions)
    {
        for (String action : actions)
        {
            if (object.hasAction(action))
            {
                return true;
            }
        }
        return false;
    }
}
