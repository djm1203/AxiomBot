package com.botengine.osrs.scripts.combat;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.NPC;

import javax.inject.Inject;

/**
 * Combat AFK script.
 *
 * State machine:
 *   FIND_TARGET → ATTACKING → (low HP) → EATING → ATTACKING
 *                           → (target dead) → FIND_TARGET
 *
 * Targets the nearest NPC matching a configurable set of IDs.
 * Eats food when HP drops below a configurable threshold.
 * Designed for Sand Crabs, Crabs, Slayer, or any aggressive mob.
 *
 * Default target IDs (Sand Crabs — common AFK training spot):
 *   1266 — Sand Crab (active)
 *   1267 — Sand Crab (dormant — need to walk away and return to wake)
 *
 * Default food: Shark (385) or any cooked food in inventory.
 * Default eat threshold: 50% HP.
 */
public class CombatScript extends BotScript
{
    // Food item IDs checked in order — first match is eaten
    private static final int[] FOOD_IDS = {
        385,  // Shark
        361,  // Tuna
        373,  // Swordfish
        379,  // Lobster
        329,  // Sardine (low-level)
        319,  // Shrimps
        2142  // Cooked karambwan
    };

    // Idle ticks before re-searching for a target after combat ends
    private static final int IDLE_TIMEOUT_TICKS = 4;

    // Max ticks to spend rotating the camera before giving up on an off-screen target
    private static final int MAX_CAMERA_RETRIES = 5;

    private enum State { FIND_TARGET, ATTACKING, EATING }

    // Configurable via BotEngineConfig — set in configure()
    private String targetNpcName = "Hill Giant";
    private int eatThresholdPercent = 50;

    private State state = State.FIND_TARGET;
    private int idleTickCount = 0;
    private int cameraRetryCount = 0;

    @Inject
    public CombatScript() {}

    @Override
    public String getName() { return "Combat"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        targetNpcName = config.combatTarget();
        eatThresholdPercent = config.combatEatPercent();
    }

    @Override
    public void onStart()
    {
        log.info("Started — targeting '{}', eat below {}% HP", targetNpcName, eatThresholdPercent);
        state = State.FIND_TARGET;
        cameraRetryCount = 0;
    }

    @Override
    public void onLoop()
    {
        log.info("onLoop state={} npcsInScene={}", state, npcs.all(npc -> true).size());

        // Eating takes priority — always check HP first
        if (shouldEat())
        {
            eatFood();
            return;
        }

        switch (state)
        {
            case FIND_TARGET:
                findAndAttack();
                break;

            case ATTACKING:
                checkCombatState();
                break;

            case EATING:
                // After eating, reassess
                state = State.FIND_TARGET;
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndAttack()
    {
        NPC target = npcs.nearest(targetNpcName);
        log.info("FIND_TARGET: nearest={}", target == null ? "null" : target.getName() + "/" + target.getId());
        if (target == null)
        {
            log.info("No target found nearby — all NPCs: {}", npcs.all(npc -> true).stream()
                .map(n -> n.getName() + "/" + n.getId()).collect(java.util.stream.Collectors.joining(", ")));
            return;
        }

        if (target.isDead())
        {
            log.info("Nearest target is dead — waiting for respawn");
            return;
        }

        boolean clicked = combat.attackNpc(target);
        if (!clicked)
        {
            if (cameraRetryCount < MAX_CAMERA_RETRIES)
            {
                log.debug("Target off-screen — rotating camera ({}/{})", cameraRetryCount + 1, MAX_CAMERA_RETRIES);
                camera.rotateTo(target.getWorldLocation());
                cameraRetryCount++;
            }
            else
            {
                log.debug("Target unreachable after {} camera retries — searching again", MAX_CAMERA_RETRIES);
                cameraRetryCount = 0;
            }
            return;
        }
        cameraRetryCount = 0;
        antiban.reactionDelay();
        state = State.ATTACKING;
        idleTickCount = 0;
        log.info("Attacking NPC id={} name={} index={}", target.getId(), target.getName(), target.getIndex());
    }

    private void checkCombatState()
    {
        net.runelite.api.Actor interacting = players.getTarget();
        int anim = players.getAnimation();
        log.info("checkCombat: interacting={} anim={} inCombat={}",
            interacting == null ? "null" : interacting.getName(),
            anim,
            players.isInCombat());
        if (players.isInCombat())
        {
            idleTickCount = 0;
            return;
        }

        idleTickCount++;
        if (idleTickCount >= IDLE_TIMEOUT_TICKS)
        {
            log.debug("No longer in combat — finding new target");
            state = State.FIND_TARGET;
            idleTickCount = 0;
        }
    }

    private boolean shouldEat()
    {
        return players.shouldEat(eatThresholdPercent);
    }

    private void eatFood()
    {
        for (int foodId : FOOD_IDS)
        {
            if (inventory.contains(foodId))
            {
                combat.eat(foodId);
                antiban.reactionDelay();
                state = State.EATING;
                log.debug("Ate food id={}", foodId);
                return;
            }
        }
        log.warn("No food in inventory — low HP, cannot eat!");
    }
}
