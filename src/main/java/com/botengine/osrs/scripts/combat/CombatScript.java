package com.botengine.osrs.scripts.combat;

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
    // Sand Crabs — most common AFK target. Override for other mobs.
    private static final int[] TARGET_NPC_IDS = { 1266, 1267 };

    // Food item IDs (checked in order — first match is eaten)
    private static final int[] FOOD_IDS = {
        385,  // Shark
        361,  // Tuna
        373,  // Swordfish
        379,  // Lobster
        329,  // Sardine (low-level)
        319,  // Shrimps
        2142  // Cooked karambwan
    };

    // Eat below this HP percentage
    private static final int EAT_THRESHOLD_PERCENT = 50;

    // Idle ticks before we look for a new target (to handle "no target" gracefully)
    private static final int IDLE_TIMEOUT_TICKS = 4;

    private enum State { FIND_TARGET, ATTACKING, EATING }

    private State state = State.FIND_TARGET;
    private int idleTickCount = 0;

    @Inject
    public CombatScript() {}

    @Override
    public String getName() { return "Combat"; }

    @Override
    public void onStart()
    {
        log.info("Started — targeting Sand Crabs, eat below {}% HP", EAT_THRESHOLD_PERCENT);
        state = State.FIND_TARGET;
    }

    @Override
    public void onLoop()
    {
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
        NPC target = npcs.nearest(TARGET_NPC_IDS);
        if (target == null)
        {
            log.debug("No target found nearby — waiting");
            return;
        }

        if (target.isDead())
        {
            log.debug("Nearest target is dead — waiting for respawn");
            return;
        }

        combat.attackNpc(target);
        antiban.reactionDelay();
        state = State.ATTACKING;
        idleTickCount = 0;
        log.debug("Attacking NPC id={} name={}", target.getId(), target.getName());
    }

    private void checkCombatState()
    {
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
        return players.shouldEat(EAT_THRESHOLD_PERCENT);
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
