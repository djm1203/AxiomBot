package com.axiom.scripts.fishing;

import com.axiom.api.game.Npcs;
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

import java.util.Comparator;
import java.util.List;

/**
 * Axiom Fishing — validates NPC interaction in the multi-module architecture.
 *
 * Key difference from Woodcutting: fishing spots are NPCs, not GameObjects.
 * Interaction is routed through NpcsImpl → SceneObjectWrapper(NPC constructor)
 * → client.menuAction(NPC_FIRST_OPTION / NPC_SECOND_OPTION) with npc.getIndex().
 *
 * Spot-moved detection mirrors the Woodcutting tree-depleted pattern:
 *   wasActing flag + isAnimating() — never checks NPC existence per tick.
 *
 * State machine:
 *   FIND_SPOT → FISHING → FULL (→ BANKING or DROPPING) → FIND_SPOT
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Fishing",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Fishes at spots and optionally banks or drops the catch."
)
public class FishingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Npcs      npcs;
    private Players   players;
    private Inventory inventory;
    private Bank      bank;
    private Pathfinder pathfinder;
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private FishingSettings settings;

    // ── Start location — recorded on first game tick, used to walk back after banking ──
    private boolean startTileRecorded = false;
    private LocationProfile locationProfile;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_SPOT, FISHING, FULL, DROPPING, BANKING }
    private State state = State.FIND_SPOT;

    // Animation tracking — see Woodcutting for rationale.
    private boolean wasFishing       = false;
    private int     noAnimationTicks = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;
    private static final int MAX_SPOT_DISTANCE = 18;
    private static final int TARGET_STICKINESS_RADIUS = 3;
    private static final int MAX_INTERACTION_RETRIES = 3;

    // Bank-open timing — deposit widget not available the same tick isOpen() first returns true.
    private boolean bankJustOpened   = false;
    private boolean bankRestockDone  = false;
    private boolean bankContainerDone = false;

    // How many times we have tried to open the bank this banking cycle.
    // Avoids the hard isNearBank() gate: instead we keep calling openNearest() and
    // fall back to drop only if it never succeeds after MAX_BANK_OPEN_ATTEMPTS.
    // 20 attempts × 3-tick delay ≈ 36 s — plenty of time to walk 15-20 tiles.
    private int bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Raw fish item IDs for power-fish drop ─────────────────────────────────
    private static final int[] FISH_IDS = {
        317,   // Raw shrimp
        321,   // Raw sardine
        327,   // Raw herring
        331,   // Raw anchovies
        335,   // Raw mackerel
        341,   // Raw trout
        345,   // Raw salmon
        347,   // Raw tuna
        349,   // Raw lobster
        351,   // Raw swordfish
        359,   // Raw monkfish
        383,   // Raw shark
        11934, // Raw karambwan
    };
    private static final int FISH_BARREL_CLOSED_ID = 25582;
    private static final int FISH_BARREL_OPEN_ID   = 25584;

    private int lastSpotId = -1;
    private int lastSpotX = Integer.MIN_VALUE;
    private int lastSpotY = Integer.MIN_VALUE;
    private int lastSpotPlane = Integer.MIN_VALUE;
    private final InteractionWatchdog interactionWatchdog =
        new InteractionWatchdog(ANIMATION_TIMEOUT_TICKS, MAX_INTERACTION_RETRIES);

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Fishing"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof FishingSettings)
            ? (FishingSettings) raw
            : FishingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        startTileRecorded = false;

        log.info("Fishing started: spot={} action={} powerFish={}",
            settings.spotType.name(), settings.bankAction.name(), settings.powerFish);

        state = State.FIND_SPOT;
        clearLastSpot();
        resetBankState();
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
            locationProfile = LocationProfile.centered(
                "fishing-" + settings.locationPreset.name().toLowerCase(),
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

        switch (state)
        {
            case FIND_SPOT: handleFindSpot(); break;
            case FISHING:   handleFishing();  break;
            case FULL:      handleFull();     break;
            case DROPPING:  handleDropping(); break;
            case BANKING:   handleBanking();  break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Fishing stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindSpot()
    {
        if (prepareFishBarrel())
        {
            return;
        }

        if (inventory.isFull())
        {
            log.info("[FIND_SPOT] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        if (!hasRequiredBait())
        {
            if (settings.powerFish || settings.bankAction == FishingSettings.BankAction.DROP_FISH)
            {
                log.info("[FIND_SPOT] Missing bait for {} — stopping", settings.spotType.name());
                stop();
            }
            else
            {
                log.info("[FIND_SPOT] Missing bait for {} — transitioning to BANKING", settings.spotType.name());
                state = State.BANKING;
            }
            return;
        }

        SceneObject spot = selectBestSpot();
        if (spot == null)
        {
            log.info("[FIND_SPOT] No fishing spot found nearby — waiting 3 ticks");
            setTickDelay(3);
            return;
        }

        String action = settings.spotType.resolveAction(spot.getActions());
        if (action == null)
        {
            log.warn("[FIND_SPOT] Spot found but no supported action was available — retrying");
            setTickDelay(2);
            return;
        }

        log.info("[FIND_SPOT] Found {} (id={}) at ({},{}) — clicking '{}'",
            spot.getName(), spot.getId(),
            spot.getWorldX(), spot.getWorldY(),
            action);

        rememberSpot(spot);
        spot.interact(action);
        wasFishing       = false;
        noAnimationTicks = 0;
        interactionWatchdog.begin();

        // Wait at least 2 ticks so the fishing animation has time to register.
        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        log.debug("[FIND_SPOT] Reaction delay: {} ticks before FISHING check", reactionTicks);
        setTickDelay(reactionTicks);

        state = State.FISHING;
    }

    private void handleFishing()
    {
        if (inventory.isFull())
        {
            log.info("[FISHING] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        if (players.isAnimating())
        {
            if (!wasFishing)
            {
                log.info("[FISHING] Fishing animation started");
                interactionWatchdog.markSuccess();
            }
            else
            {
                log.debug("[FISHING] Still fishing");
            }
            wasFishing       = true;
            noAnimationTicks = 0;
            return;
        }

        // Not animating:
        noAnimationTicks++;

        if (wasFishing)
        {
            // Was fishing and stopped → spot depleted or moved.
            log.info("[FISHING] Animation stopped (spot moved) — finding next spot");
            state      = State.FIND_SPOT;
            wasFishing = false;
            SceneObject movedSpot = selectBestSpot();
            if (movedSpot != null)
            {
                rememberSpot(movedSpot);
            }
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            InteractionWatchdog.Status status = interactionWatchdog.observe(players.isMoving());
            if (status == InteractionWatchdog.Status.EXHAUSTED)
            {
                log.warn("[FISHING] Interaction failed after {}/{} retries — re-acquiring spot",
                    interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                interactionWatchdog.reset();
                state = State.FIND_SPOT;
            }
            else if (status == InteractionWatchdog.Status.RETRY)
            {
                log.info("[FISHING] Animation never started — retrying interaction ({}/{})",
                    interactionWatchdog.getAttempts(), interactionWatchdog.getMaxAttempts());
                state = State.FIND_SPOT;
            }
            noAnimationTicks = 0;
        }
        else
        {
            log.debug("[FISHING] Waiting for animation ({}/{})", noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
        }
    }

    private SceneObject selectBestSpot()
    {
        List<SceneObject> spots = npcs.all(npc ->
            npc.getPlane() == players.getPlane()
                && settings.spotType.matches(npc.getId())
                && players.distanceTo(npc.getWorldX(), npc.getWorldY()) <= MAX_SPOT_DISTANCE
                && pathfinder.isReachable(npc.getWorldX(), npc.getWorldY(), npc.getPlane())
                && settings.spotType.matchesAction(npc.getActions()));

        return spots.stream()
            .min(Comparator.comparingInt(this::scoreSpot))
            .orElse(null);
    }

    private int scoreSpot(SceneObject spot)
    {
        int score = players.distanceTo(spot.getWorldX(), spot.getWorldY()) * 10;
        if (spot.getId() == lastSpotId && spot.getPlane() == lastSpotPlane)
        {
            int stickyDistance = Math.max(
                Math.abs(spot.getWorldX() - lastSpotX),
                Math.abs(spot.getWorldY() - lastSpotY));
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

    private boolean hasRequiredBait()
    {
        for (int baitId : settings.spotType.baitItemIds)
        {
            if (!inventory.containsById(baitId))
            {
                return false;
            }
        }
        return true;
    }

    private void rememberSpot(SceneObject spot)
    {
        lastSpotId = spot.getId();
        lastSpotX = spot.getWorldX();
        lastSpotY = spot.getWorldY();
        lastSpotPlane = spot.getPlane();
    }

    private void clearLastSpot()
    {
        lastSpotId = -1;
        lastSpotX = Integer.MIN_VALUE;
        lastSpotY = Integer.MIN_VALUE;
        lastSpotPlane = Integer.MIN_VALUE;
    }

    private void handleFull()
    {
        if (settings.powerFish || settings.bankAction == FishingSettings.BankAction.DROP_FISH)
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
        int dropped = 0;
        for (int fishId : FISH_IDS)
        {
            if (inventory.contains(fishId))
            {
                log.info("[DROPPING] Dropping fish id={}", fishId);
                inventory.dropAll(fishId);
                dropped++;
            }
        }
        if (dropped == 0) log.info("[DROPPING] No fish found in inventory to drop");
        setTickDelay(1);
        state = State.FIND_SPOT;
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            bankJustOpened = false;
            bankRestockDone = false;
            bankContainerDone = false;

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

        if (!bankContainerDone)
        {
            if (emptyFishBarrelAtBank())
            {
                setTickDelay(1);
                return;
            }
            bankContainerDone = true;
        }

        if (!bankRestockDone)
        {
            if (withdrawMissingSupportItems())
            {
                setTickDelay(1);
                return;
            }
            bankRestockDone = true;
        }

        // Deposit one item type per tick, protecting the fishing tool.
        // depositAllExcept returns true while work remains; false when clear.
        if (bank.depositAllExcept(getProtectedBankItemIds()))
        {
            log.debug("[BANKING] Depositing items...");
            setTickDelay(1);
            return;
        }

        log.info("[BANKING] Deposit complete — closing bank");
        bank.close();
        resetBankState();

        WorldTile currentTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
        if (locationProfile != null && locationProfile.shouldReturnToWorkArea(currentTile))
        {
            WorldTile returnAnchor = locationProfile.getReturnAnchor();
            int distToStart = currentTile.chebyshevDistanceTo(returnAnchor);
            log.info("[BANKING] Walking back to fishing area ({},{}) — distance={}",
                returnAnchor.getWorldX(), returnAnchor.getWorldY(), distToStart);
            pathfinder.walkTo(returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }
        state = State.FIND_SPOT;
    }

    private void resetBankState()
    {
        bankJustOpened = false;
        bankRestockDone = false;
        bankContainerDone = false;
    }

    private boolean withdrawMissingSupportItems()
    {
        for (int toolId : settings.spotType.toolItemIds)
        {
            if (!inventory.containsById(toolId))
            {
                if (!bank.contains(toolId))
                {
                    log.warn("[BANKING] Missing required tool id={} in both inventory and bank — stopping", toolId);
                    bank.close();
                    stop();
                    return false;
                }

                log.info("[BANKING] Restocking required tool id={}", toolId);
                bank.withdrawAll(toolId);
                return true;
            }
        }

        for (int baitId : settings.spotType.baitItemIds)
        {
            if (!inventory.containsById(baitId))
            {
                if (!bank.contains(baitId))
                {
                    log.warn("[BANKING] Missing required bait id={} in both inventory and bank — stopping", baitId);
                    bank.close();
                    stop();
                    return false;
                }

                log.info("[BANKING] Restocking bait id={}", baitId);
                bank.withdrawAll(baitId);
                return true;
            }
        }

        return false;
    }

    private boolean prepareFishBarrel()
    {
        if (!settings.useFishBarrel || !hasFishBarrel() || isFishBarrelOpen())
        {
            return false;
        }

        log.info("[FIND_SPOT] Opening fish barrel");
        inventory.clickItemActionByName("fish barrel", "Open");
        setTickDelay(1);
        return true;
    }

    private boolean emptyFishBarrelAtBank()
    {
        if (!settings.useFishBarrel || !hasFishBarrel())
        {
            return false;
        }

        if (inventory.containsById(FISH_BARREL_OPEN_ID))
        {
            log.info("[BANKING] Emptying fish barrel");
            inventory.clickItemAction(FISH_BARREL_OPEN_ID, "Empty");
            return true;
        }

        if (inventory.containsById(FISH_BARREL_CLOSED_ID))
        {
            log.info("[BANKING] Emptying fish barrel");
            inventory.clickItemAction(FISH_BARREL_CLOSED_ID, "Empty");
            return true;
        }

        return false;
    }

    private boolean hasFishBarrel()
    {
        return inventory.containsById(FISH_BARREL_CLOSED_ID) || inventory.containsById(FISH_BARREL_OPEN_ID);
    }

    private boolean isFishBarrelOpen()
    {
        return inventory.containsById(FISH_BARREL_OPEN_ID);
    }

    private int[] getProtectedBankItemIds()
    {
        if (!settings.useFishBarrel)
        {
            return settings.spotType.toolItemIds;
        }

        int[] protectedIds = new int[settings.spotType.toolItemIds.length + 2];
        System.arraycopy(settings.spotType.toolItemIds, 0, protectedIds, 0, settings.spotType.toolItemIds.length);
        protectedIds[protectedIds.length - 2] = FISH_BARREL_CLOSED_ID;
        protectedIds[protectedIds.length - 1] = FISH_BARREL_OPEN_ID;
        return protectedIds;
    }
}
