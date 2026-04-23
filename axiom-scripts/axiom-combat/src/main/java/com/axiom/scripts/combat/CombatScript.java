package com.axiom.scripts.combat;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.GroundItems;
import com.axiom.api.game.Npcs;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.player.Prayer;
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
import com.axiom.api.world.WorldTile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Axiom Combat — attacks NPCs by name, eats food to stay alive, loots configured drops.
 *
 * State machine:
 *   FIND_TARGET — find the nearest free NPC matching the configured name; click Attack
 *   ATTACKING   — wait for combat to end (both isInCombat() and isAnimating() return false)
 *   LOOTING     — pick up any configured loot items from the ground; then return to FIND_TARGET
 *
 * Health is checked on every tick regardless of state. If HP drops below the configured
 * threshold and food is available, the script eats and skips the rest of that tick.
 *
 * "Free" NPC: one whose isInCombat() returns false — it is not currently fighting anyone else.
 * This prevents the script from targeting an NPC that another player is already fighting.
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Combat",
    version     = "1.0",
    category    = ScriptCategory.COMBAT,
    author      = "Axiom",
    description = "Attacks NPCs, eats food to stay alive, and loots configurable drops."
)
public class CombatScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Players     players;
    private Npcs        npcs;
    private GameObjects gameObjects;
    private GroundItems groundItems;
    private Inventory   inventory;
    private Prayer      prayer;
    private Bank        bank;
    private Pathfinder  pathfinder;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private CombatSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_TARGET, ATTACKING, LOOTING, BANKING }
    private State state = State.FIND_TARGET;

    // Idle tick counter — incremented when neither in combat nor animating.
    // Resets to 0 when combat action is detected.
    private int noActionTicks = 0;
    private static final int COMBAT_TIMEOUT_TICKS = 10;
    private static final int ENGAGEMENT_RETRY_AFTER_TICKS = 3;
    private static final int MAX_ENGAGEMENT_RETRIES       = 3;
    private static final int MAX_TARGET_DISTANCE          = 16;
    private static final int APPROACH_DISTANCE            = 8;
    private static final int TARGET_STICKINESS_RADIUS     = 3;
    private static final int MAX_BANK_OPEN_ATTEMPTS       = 20;
    private static final int RECENT_COMBAT_EVIDENCE_TICKS = 4;
    private static final int CANNONBALL_ID                = 2;
    private static final int CANNON_BASE_ID               = 6;
    private static final int CANNON_STAND_ID              = 8;
    private static final int CANNON_BARRELS_ID            = 10;
    private static final int CANNON_FURNACE_ID            = 12;
    private static final int MAX_SAFE_ANCHOR_DRIFT        = 1;

    private final RetryBudget engagementRetries = new RetryBudget(MAX_ENGAGEMENT_RETRIES);
    private int lastTargetId = -1;
    private int lastTargetX  = Integer.MIN_VALUE;
    private int lastTargetY  = Integer.MIN_VALUE;
    private int lastTargetPlane = Integer.MIN_VALUE;
    private boolean startTileRecorded = false;
    private LocationProfile locationProfile;
    private boolean bankJustOpened  = false;
    private boolean bankDepositDone = false;
    private int bankOpenAttempts    = 0;
    private boolean prayerOutOfPointsLogged = false;
    private int recentCombatEvidenceTicks = RECENT_COMBAT_EVIDENCE_TICKS + 1;
    private int noTargetTicks = 0;
    private boolean aggressionResetWalkingOut = true;
    private int cannonReloadTicks = 0;
    private int cannonSetupCooldownTicks = 0;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Combat"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof CombatSettings)
            ? (CombatSettings) raw
            : CombatSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        state        = State.FIND_TARGET;
        noActionTicks = 0;
        engagementRetries.reset();
        clearLastTarget();
        startTileRecorded = false;
        locationProfile = null;
        resetBankState();
        prayerOutOfPointsLogged = false;
        recentCombatEvidenceTicks = RECENT_COMBAT_EVIDENCE_TICKS + 1;
        noTargetTicks = 0;
        aggressionResetWalkingOut = true;
        cannonReloadTicks = 0;
        cannonSetupCooldownTicks = 0;

        log.info("Combat started: npc='{}' eatAt={}% foods={} loot={} maxLootDistance={} bankForFood={} prayer={} safespot={} cannon={} aggroReset={}",
            settings.npcName,
            settings.eatAtHpPercent,
            Arrays.toString(settings.foodIds),
            Arrays.toString(settings.lootItemIds),
            settings.maxLootDistance,
            settings.bankForFood,
            settings.combatPrayer.displayName,
            settings.safespotEnabled,
            settings.cannonEnabled,
            settings.aggressionResetEnabled);
    }

    @Override
    public void onLoop()
    {
        if (!startTileRecorded)
        {
            WorldTile startTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
            locationProfile = LocationProfile.centered("combat", startTile, 12);
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{},{})",
                startTile.getWorldX(), startTile.getWorldY(), startTile.getPlane());
        }

        updateCombatEvidence();
        tickCannonCounters();

        // Health check — eat before doing anything else this tick
        if (shouldEat())
        {
            eat();
            return;
        }

        if (managePrayer())
        {
            return;
        }

        if (manageCannon())
        {
            return;
        }

        switch (state)
        {
            case FIND_TARGET: handleFindTarget(); break;
            case ATTACKING:   handleAttacking();  break;
            case LOOTING:     handleLooting();    break;
            case BANKING:     handleBanking();    break;
        }
    }

    @Override
    public void onStop()
    {
        deactivateCombatPrayer();
        engagementRetries.reset();
        clearLastTarget();
        log.info("Combat stopped");
    }

    // ── Eating ────────────────────────────────────────────────────────────────

    private boolean shouldEat()
    {
        if (settings.foodIds.length == 0) return false;
        return players.getHealthPercent() <= settings.eatAtHpPercent
            && inventory.containsAny(settings.foodIds);
    }

    private void eat()
    {
        for (int foodId : settings.foodIds)
        {
            if (inventory.containsById(foodId))
            {
                log.info("[EAT] Eating food id={} at {}% HP",
                    foodId, players.getHealthPercent());
                inventory.clickItem(foodId);
                setTickDelay(1);
                return;
            }
        }
    }

    private boolean managePrayer()
    {
        if (!settings.combatPrayer.isEnabled())
        {
            return false;
        }

        Prayer.PrayerType prayerType = settings.combatPrayer.prayerType;
        boolean shouldBeActive = state == State.ATTACKING && (players.isInCombat() || players.isAnimating());

        if (shouldBeActive)
        {
            if (!prayer.hasPoints(1))
            {
                if (!prayerOutOfPointsLogged)
                {
                    log.warn("[PRAYER] No prayer points available for {}", settings.combatPrayer.displayName);
                    prayerOutOfPointsLogged = true;
                }
                return false;
            }

            prayerOutOfPointsLogged = false;
            if (!prayer.isActive(prayerType))
            {
                log.info("[PRAYER] Activating {}", settings.combatPrayer.displayName);
                prayer.activate(prayerType);
                setTickDelay(1);
                return true;
            }
            return false;
        }

        prayerOutOfPointsLogged = false;
        if (prayer.isActive(prayerType))
        {
            log.info("[PRAYER] Deactivating {}", settings.combatPrayer.displayName);
            prayer.deactivate(prayerType);
            setTickDelay(1);
            return true;
        }

        return false;
    }

    private void deactivateCombatPrayer()
    {
        if (settings == null || !settings.combatPrayer.isEnabled())
        {
            return;
        }

        Prayer.PrayerType prayerType = settings.combatPrayer.prayerType;
        if (prayer != null && prayer.isActive(prayerType))
        {
            prayer.deactivate(prayerType);
        }
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindTarget()
    {
        if (shouldBankForFood())
        {
            log.info("[FIND_TARGET] Out of food — transitioning to BANKING");
            resetBankState();
            state = State.BANKING;
            return;
        }

        if (settings.stopWhenOutOfFood
                && settings.foodIds.length > 0
                && !inventory.containsAny(settings.foodIds))
        {
            log.info("[FIND_TARGET] Out of food — stopping");
            stop();
            return;
        }

        if (returnToSafespotIfNeeded("[FIND_TARGET]"))
        {
            return;
        }

        // Already pulled into combat by an aggressive NPC — skip the search
        if (players.isInCombat())
        {
            log.info("[FIND_TARGET] Already in combat — transitioning to ATTACKING");
            noActionTicks = 0;
            noTargetTicks = 0;
            engagementRetries.reset();
            state = State.ATTACKING;
            return;
        }

        SceneObject target = selectBestTarget();

        if (target == null)
        {
            noTargetTicks++;
            if (shouldResetAggression())
            {
                performAggressionReset();
                return;
            }
            log.info("[FIND_TARGET] No free '{}' found nearby — waiting", settings.npcName);
            setTickDelay(3);
            return;
        }

        noTargetTicks = 0;

        if (players.distanceTo(target.getWorldX(), target.getWorldY()) > APPROACH_DISTANCE)
        {
            log.info("[FIND_TARGET] Approaching '{}' at ({},{}) before attacking",
                settings.npcName, target.getWorldX(), target.getWorldY());
            pathfinder.walkTo(target.getWorldX(), target.getWorldY(), target.getPlane());
            setTickDelay(2);
            return;
        }

        log.info("[FIND_TARGET] Attacking '{}' (id={}) at ({},{})",
            settings.npcName, target.getId(), target.getWorldX(), target.getWorldY());
        rememberTarget(target);
        engagementRetries.reset();
        target.interact("Attack");
        noActionTicks = 0;
        setTickDelay(2); // wait for combat animation to register
        state = State.ATTACKING;
    }

    private void handleAttacking()
    {
        if (hasCombatEvidence())
        {
            // Combat is ongoing — reset idle counter and let the fight continue
            noActionTicks = 0;
            noTargetTicks = 0;
            engagementRetries.reset();
            return;
        }

        noActionTicks++;

        if (noActionTicks >= ENGAGEMENT_RETRY_AFTER_TICKS && !engagementRetries.isExhausted())
        {
            SceneObject retryTarget = findRetryTarget();
            if (retryTarget != null)
            {
                engagementRetries.fail();
                log.info("[ATTACKING] Re-engaging '{}' at ({},{}) ({}/{})",
                    retryTarget.getName(),
                    retryTarget.getWorldX(),
                    retryTarget.getWorldY(),
                    engagementRetries.getAttempts(),
                    engagementRetries.getMaxAttempts());
                rememberTarget(retryTarget);
                retryTarget.interact("Attack");
                setTickDelay(2);
                return;
            }
        }

        if (noActionTicks >= COMBAT_TIMEOUT_TICKS)
        {
            // Target died or interaction was lost — move to looting
            log.info("[ATTACKING] No combat action for {} ticks — moving to LOOTING",
                COMBAT_TIMEOUT_TICKS);
            noActionTicks = 0;
            noTargetTicks = 0;
            engagementRetries.reset();
            state = State.LOOTING;
            setTickDelay(1); // brief pause for ground items to spawn
        }
        else
        {
            log.info("[ATTACKING] Waiting for combat action ({}/{})",
                noActionTicks, COMBAT_TIMEOUT_TICKS);
        }
    }

    private void handleLooting()
    {
        if (shouldBankForFood())
        {
            log.info("[LOOTING] Out of food — transitioning to BANKING");
            resetBankState();
            state = State.BANKING;
            return;
        }

        if (settings.lootItemIds.length == 0)
        {
            returnToSafespotIfNeeded("[LOOTING]");
            noTargetTicks = 0;
            state = State.FIND_TARGET;
            return;
        }

        SceneObject groundItem = selectBestLoot();
        if (groundItem != null)
        {
            if (players.distanceTo(groundItem.getWorldX(), groundItem.getWorldY()) > APPROACH_DISTANCE)
            {
                log.info("[LOOTING] Approaching loot id={} at ({},{})",
                    groundItem.getId(), groundItem.getWorldX(), groundItem.getWorldY());
                pathfinder.walkTo(groundItem.getWorldX(), groundItem.getWorldY(), groundItem.getPlane());
                setTickDelay(2);
                return;
            }

            log.info("[LOOTING] Picking up item id={} at ({},{})",
                groundItem.getId(), groundItem.getWorldX(), groundItem.getWorldY());
            groundItem.interact("Take");
            setTickDelay(2); // wait for item to be picked up before next check
            return;
        }

        log.info("[LOOTING] No configured loot found — finding next target");
        returnToSafespotIfNeeded("[LOOTING]");
        noTargetTicks = 0;
        state = State.FIND_TARGET;
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            bankJustOpened = false;
            bankDepositDone = false;

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.warn("[BANKING] Could not open bank after {} attempts — stopping",
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

        if (!bankDepositDone)
        {
            if (settings.cannonEnabled)
            {
                if (bank.depositAllExcept(getProtectedInventoryIds()))
                {
                    log.info("[BANKING] Depositing non-protected inventory");
                    setTickDelay(1);
                    return;
                }

                bankDepositDone = true;
                log.info("[BANKING] Inventory cleaned with protected cannon items kept");
                setTickDelay(1);
                return;
            }

            bank.depositAll();
            bankDepositDone = true;
            log.info("[BANKING] Depositing inventory");
            setTickDelay(1);
            return;
        }

        if (withdrawMissingCombatSupplies())
        {
            setTickDelay(1);
            return;
        }

        if (settings.foodIds.length > 0 && !inventory.containsAny(settings.foodIds))
        {
            log.warn("[BANKING] No configured food found in bank — stopping");
            bank.close();
            stop();
            return;
        }

        log.info("[BANKING] Withdrawing food id={}", foodIdToWithdraw);
        bank.withdrawAll(foodIdToWithdraw);
        setTickDelay(1);

        log.info("[BANKING] Closing bank");
        bank.close();
        resetBankState();
        clearLastTarget();
        noActionTicks = 0;
        noTargetTicks = 0;

        WorldTile currentTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
        if (locationProfile != null && locationProfile.shouldReturnToWorkArea(currentTile))
        {
            WorldTile returnAnchor = locationProfile.getReturnAnchor();
            log.info("[BANKING] Returning to combat area ({},{},{})",
                returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            pathfinder.walkTo(returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }

        state = State.FIND_TARGET;
    }

    private SceneObject selectBestTarget()
    {
        List<SceneObject> candidates = npcs.all(n ->
            n.getPlane() == players.getPlane()
                && n.getName().equalsIgnoreCase(settings.npcName)
                && n.hasAction("Attack")
                && !n.isInCombat()
                && players.distanceTo(n.getWorldX(), n.getWorldY()) <= MAX_TARGET_DISTANCE
                && isTargetWithinSafespot(n)
                && pathfinder.isReachable(n.getWorldX(), n.getWorldY(), n.getPlane()));

        return candidates.stream()
            .min(Comparator.comparingInt(this::scoreTarget))
            .orElse(null);
    }

    private SceneObject findRetryTarget()
    {
        List<SceneObject> stickyCandidates = npcs.all(n ->
            n.getPlane() == lastTargetPlane
                && n.getId() == lastTargetId
                && n.getName().equalsIgnoreCase(settings.npcName)
                && n.hasAction("Attack")
                && !n.isInCombat()
                && Math.max(Math.abs(n.getWorldX() - lastTargetX), Math.abs(n.getWorldY() - lastTargetY))
                    <= TARGET_STICKINESS_RADIUS
                && pathfinder.isReachable(n.getWorldX(), n.getWorldY(), n.getPlane()));

        if (!stickyCandidates.isEmpty())
        {
            return stickyCandidates.stream()
                .min(Comparator.comparingInt(this::scoreTarget))
                .orElse(null);
        }

        return selectBestTarget();
    }

    private int scoreTarget(SceneObject target)
    {
        int score = players.distanceTo(target.getWorldX(), target.getWorldY()) * 10;
        score += safespotDistance(target) * 6;

        if (target.getId() == lastTargetId && target.getPlane() == lastTargetPlane)
        {
            int stickyDistance = Math.max(
                Math.abs(target.getWorldX() - lastTargetX),
                Math.abs(target.getWorldY() - lastTargetY));
            if (stickyDistance == 0)
            {
                score -= 15;
            }
            else if (stickyDistance <= TARGET_STICKINESS_RADIUS)
            {
                score -= 8;
            }
        }

        return score;
    }

    private void rememberTarget(SceneObject target)
    {
        lastTargetId    = target.getId();
        lastTargetX     = target.getWorldX();
        lastTargetY     = target.getWorldY();
        lastTargetPlane = target.getPlane();
    }

    private void clearLastTarget()
    {
        lastTargetId    = -1;
        lastTargetX     = Integer.MIN_VALUE;
        lastTargetY     = Integer.MIN_VALUE;
        lastTargetPlane = Integer.MIN_VALUE;
    }

    private boolean shouldBankForFood()
    {
        return settings.bankForFood
            && settings.foodIds.length > 0
            && !inventory.containsAny(settings.foodIds);
    }

    private int findAvailableBankFoodId()
    {
        for (int foodId : settings.foodIds)
        {
            if (bank.contains(foodId))
            {
                return foodId;
            }
        }
        return -1;
    }

    private void resetBankState()
    {
        bankJustOpened = false;
        bankDepositDone = false;
        bankOpenAttempts = 0;
    }

    private void updateCombatEvidence()
    {
        if (players.isInCombat() || players.isAnimating())
        {
            recentCombatEvidenceTicks = 0;
        }
        else
        {
            recentCombatEvidenceTicks++;
        }
    }

    private boolean hasCombatEvidence()
    {
        return players.isInCombat()
            || players.isAnimating()
            || recentCombatEvidenceTicks <= RECENT_COMBAT_EVIDENCE_TICKS;
    }

    private void tickCannonCounters()
    {
        if (!settings.cannonEnabled)
        {
            return;
        }

        cannonReloadTicks++;
        if (cannonSetupCooldownTicks > 0)
        {
            cannonSetupCooldownTicks--;
        }
    }

    private boolean manageCannon()
    {
        if (!settings.cannonEnabled)
        {
            return false;
        }

        SceneObject cannon = findNearbyCannon();
        if (cannon != null)
        {
            if (cannon.hasAction("Repair"))
            {
                log.info("[CANNON] Repairing cannon at ({},{},{})",
                    cannon.getWorldX(), cannon.getWorldY(), cannon.getPlane());
                cannon.interact("Repair");
                setTickDelay(2);
                cannonReloadTicks = 0;
                return true;
            }

            if (inventory.count(CANNONBALL_ID) > 0
                    && cannonReloadTicks >= settings.cannonReloadIntervalTicks
                    && cannon.hasAction("Fire"))
            {
                log.info("[CANNON] Reloading cannon");
                cannon.interact("Fire");
                setTickDelay(2);
                cannonReloadTicks = 0;
                return true;
            }

            return false;
        }

        if (!settings.setupCannon
                || cannonSetupCooldownTicks > 0
                || !hasAllCannonParts())
        {
            return false;
        }

        if (state == State.BANKING || players.isInCombat())
        {
            return false;
        }

        if (settings.safespotEnabled && returnToSafespotIfNeeded("[CANNON]"))
        {
            return true;
        }

        log.info("[CANNON] Setting up cannon base");
        inventory.clickItemAction(CANNON_BASE_ID, "Set-up");
        cannonSetupCooldownTicks = 10;
        cannonReloadTicks = 0;
        setTickDelay(3);
        return true;
    }

    private SceneObject findNearbyCannon()
    {
        WorldTile anchor = locationProfile != null ? locationProfile.getReturnAnchor() : null;
        return gameObjects.nearest(obj ->
        {
            String name = obj.getName() != null ? obj.getName().toLowerCase() : "";
            boolean isCannonLike = name.contains("cannon")
                || obj.hasAction("Fire")
                || obj.hasAction("Repair")
                || obj.hasAction("Pick-up")
                || obj.hasAction("Dismantle");

            if (!isCannonLike || obj.getPlane() != players.getPlane())
            {
                return false;
            }

            if (anchor == null)
            {
                return players.distanceTo(obj.getWorldX(), obj.getWorldY()) <= settings.safespotTargetDistance + 4;
            }

            return chebyshev(anchor, obj.getWorldX(), obj.getWorldY(), obj.getPlane()) <= settings.safespotTargetDistance + 4;
        });
    }

    private boolean hasAllCannonParts()
    {
        return inventory.containsById(CANNON_BASE_ID)
            && inventory.containsById(CANNON_STAND_ID)
            && inventory.containsById(CANNON_BARRELS_ID)
            && inventory.containsById(CANNON_FURNACE_ID);
    }

    private boolean withdrawMissingCombatSupplies()
    {
        if (settings.cannonEnabled && settings.setupCannon)
        {
            int[] cannonParts = {CANNON_BASE_ID, CANNON_STAND_ID, CANNON_BARRELS_ID, CANNON_FURNACE_ID};
            for (int partId : cannonParts)
            {
                if (!inventory.containsById(partId) && bank.contains(partId))
                {
                    log.info("[BANKING] Withdrawing cannon part id={}", partId);
                    bank.withdraw(partId, 1);
                    return true;
                }
            }
        }

        if (settings.cannonEnabled
                && inventory.count(CANNONBALL_ID) == 0
                && bank.contains(CANNONBALL_ID))
        {
            log.info("[BANKING] Withdrawing cannonballs");
            bank.withdrawAll(CANNONBALL_ID);
            return true;
        }

        if (settings.foodIds.length == 0 || inventory.containsAny(settings.foodIds))
        {
            return false;
        }

        int foodIdToWithdraw = findAvailableBankFoodId();
        if (foodIdToWithdraw == -1)
        {
            return false;
        }

        log.info("[BANKING] Withdrawing food id={}", foodIdToWithdraw);
        bank.withdrawAll(foodIdToWithdraw);
        return true;
    }

    private int[] getProtectedInventoryIds()
    {
        if (!settings.cannonEnabled)
        {
            return new int[0];
        }

        if (settings.setupCannon)
        {
            return new int[]{
                CANNON_BASE_ID,
                CANNON_STAND_ID,
                CANNON_BARRELS_ID,
                CANNON_FURNACE_ID,
                CANNONBALL_ID
            };
        }

        return new int[]{CANNONBALL_ID};
    }

    private boolean returnToSafespotIfNeeded(String context)
    {
        if (!settings.safespotEnabled || locationProfile == null)
        {
            return false;
        }

        WorldTile anchor = locationProfile.getReturnAnchor();
        if (anchor == null || players.getPlane() != anchor.getPlane())
        {
            return false;
        }

        int distance = chebyshev(anchor, players.getWorldX(), players.getWorldY(), players.getPlane());
        if (distance <= MAX_SAFE_ANCHOR_DRIFT)
        {
            return false;
        }

        log.info("{} Returning to safespot anchor ({},{},{})",
            context, anchor.getWorldX(), anchor.getWorldY(), anchor.getPlane());
        pathfinder.walkTo(anchor.getWorldX(), anchor.getWorldY(), anchor.getPlane());
        setTickDelay(2);
        return true;
    }

    private boolean isTargetWithinSafespot(SceneObject target)
    {
        if (!settings.safespotEnabled || locationProfile == null)
        {
            return true;
        }

        WorldTile anchor = locationProfile.getReturnAnchor();
        if (anchor == null)
        {
            return true;
        }

        return chebyshev(anchor, target.getWorldX(), target.getWorldY(), target.getPlane())
            <= settings.safespotTargetDistance;
    }

    private int safespotDistance(SceneObject target)
    {
        if (!settings.safespotEnabled || locationProfile == null)
        {
            return 0;
        }

        WorldTile anchor = locationProfile.getReturnAnchor();
        if (anchor == null)
        {
            return 0;
        }

        return chebyshev(anchor, target.getWorldX(), target.getWorldY(), target.getPlane());
    }

    private int chebyshev(WorldTile anchor, int worldX, int worldY, int plane)
    {
        if (anchor == null || anchor.getPlane() != plane)
        {
            return Integer.MAX_VALUE;
        }

        return Math.max(Math.abs(anchor.getWorldX() - worldX), Math.abs(anchor.getWorldY() - worldY));
    }

    private boolean shouldResetAggression()
    {
        return settings.aggressionResetEnabled
            && locationProfile != null
            && noTargetTicks >= settings.aggressionResetIdleTicks
            && !players.isMoving();
    }

    private void performAggressionReset()
    {
        WorldTile anchor = locationProfile.getReturnAnchor();
        if (anchor == null)
        {
            return;
        }

        WorldTile resetTile = aggressionResetWalkingOut
            ? new WorldTile(anchor.getWorldX() + settings.aggressionResetDistance, anchor.getWorldY(), anchor.getPlane())
            : anchor;

        log.info("[FIND_TARGET] Aggression reset step: walking to ({},{},{})",
            resetTile.getWorldX(), resetTile.getWorldY(), resetTile.getPlane());
        pathfinder.walkTo(resetTile.getWorldX(), resetTile.getWorldY(), resetTile.getPlane());
        aggressionResetWalkingOut = !aggressionResetWalkingOut;
        noTargetTicks = aggressionResetWalkingOut ? 0 : noTargetTicks;
        setTickDelay(5);
    }

    private SceneObject selectBestLoot()
    {
        SceneObject best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int itemId : settings.lootItemIds)
        {
            SceneObject candidate = groundItems.nearest(itemId);
            if (candidate == null)
            {
                continue;
            }

            if (!pathfinder.isReachable(candidate.getWorldX(), candidate.getWorldY(), candidate.getPlane()))
            {
                continue;
            }

            int distance = players.distanceTo(candidate.getWorldX(), candidate.getWorldY());
            if (distance > settings.maxLootDistance)
            {
                continue;
            }
            if (distance < bestDistance)
            {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best;
    }
}
