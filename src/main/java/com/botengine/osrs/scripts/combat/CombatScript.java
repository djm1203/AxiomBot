package com.botengine.osrs.scripts.combat;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/**
 * Combat AFK script.
 *
 * State machine:
 *   FIND_TARGET → ATTACKING → (low HP) → EATING → ATTACKING
 *                           → (target dead + loot) → LOOTING → FIND_TARGET
 *                           → (banking mode + no food) → BANKING → FIND_TARGET
 *
 * Features:
 *   - Configurable target NPC name
 *   - Food eating at configurable HP threshold
 *   - Prayer activation (protective + offensive) with auto prayer pot drinking
 *   - Ground item loot pickup after kills (configurable item ID whitelist)
 *   - Special attack at configurable spec threshold
 *   - Banking mode — walks to nearest bank when out of food
 *   - Camera rotation for off-screen targets
 */
public class CombatScript extends BotScript
{
    // Food item IDs checked in priority order
    private static final int[] FOOD_IDS = {
        385,  // Shark
        361,  // Tuna
        373,  // Swordfish
        379,  // Lobster
        329,  // Sardine
        319,  // Shrimps
        2142  // Cooked karambwan
    };

    private static final int IDLE_TIMEOUT_TICKS = 4;
    private static final int MAX_CAMERA_RETRIES  = 5;

    private enum State { FIND_TARGET, ATTACKING, EATING, LOOTING, BANKING }

    // ── Config values (set in configure()) ───────────────────────────────────
    private String  targetNpcName      = "Hill Giant";
    private int     eatThresholdPercent = 50;
    private boolean usePrayer           = false;
    private Prayer  protectivePrayer    = Prayer.PROTECT_FROM_MELEE;
    private Prayer  offensivePrayer     = null;
    private int     prayerPotPercent    = 30;
    private boolean lootEnabled         = false;
    private Set<Integer> lootItemIds    = new HashSet<>();
    private boolean useSpec             = false;
    private int     specThreshold       = 100;
    private boolean bankingMode         = false;

    // ── Runtime state ─────────────────────────────────────────────────────────
    private State state = State.FIND_TARGET;
    private int   idleTickCount   = 0;
    private int   cameraRetryCount = 0;
    private WorldPoint homeTile;

    @Inject
    public CombatScript() {}

    @Override
    public String getName() { return "Combat"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        targetNpcName       = config.combatTarget();
        eatThresholdPercent = config.combatEatPercent();
        usePrayer           = config.combatUsePrayer();
        prayerPotPercent    = config.combatPrayerPotPercent();
        lootEnabled         = config.combatLootEnabled();
        useSpec             = config.combatUseSpec();
        specThreshold       = config.combatSpecPercent();
        bankingMode         = config.combatBankingMode();

        protectivePrayer = parsePrayer(config.combatPrayerType(), Prayer.PROTECT_FROM_MELEE);
        offensivePrayer  = parsePrayer(config.combatOffensivePrayer(), null);

        lootItemIds.clear();
        for (String part : config.combatLootItemIds().split(","))
        {
            try { lootItemIds.add(Integer.parseInt(part.trim())); }
            catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void onStart()
    {
        log.info("Started — target='{}' eat={}% prayer={} loot={} spec={} banking={}",
            targetNpcName, eatThresholdPercent, usePrayer, lootEnabled, useSpec, bankingMode);
        state = State.FIND_TARGET;
        cameraRetryCount = 0;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        // Prayer pot drinking has highest priority — keeps prayer active
        if (usePrayer && prayers.shouldDrinkPotion(prayerPotPercent) && prayers.hasPotion())
        {
            prayers.drinkPotion();
            antiban.reactionDelay();
            return;
        }

        // Food eating second highest priority
        if (shouldEat())
        {
            eatFood();
            return;
        }

        switch (state)
        {
            case FIND_TARGET:   findAndAttack();    break;
            case ATTACKING:     checkCombatState(); break;
            case EATING:        state = State.FIND_TARGET; break;
            case LOOTING:       lootItems();        break;
            case BANKING:       handleBanking();    break;
        }
    }

    @Override
    public void onStop()
    {
        if (usePrayer) prayers.deactivateAll();
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndAttack()
    {
        // Check if we should bank first (out of food, banking mode enabled)
        if (bankingMode && !hasFood() && !bank.isNearBank())
        {
            log.info("Out of food — banking");
            state = State.BANKING;
            return;
        }

        NPC target = npcs.nearest(targetNpcName);
        if (target == null || target.isDead()) return;

        // Activate spec before attacking if threshold met
        if (useSpec && combat.canSpec(specThreshold) && !combat.isSpecActive())
        {
            combat.activateSpec();
            antiban.reactionDelay();
        }

        boolean clicked = combat.attackNpc(target);
        if (!clicked)
        {
            if (cameraRetryCount < MAX_CAMERA_RETRIES)
            {
                camera.rotateTo(target.getWorldLocation());
                cameraRetryCount++;
            }
            else
            {
                cameraRetryCount = 0;
            }
            return;
        }

        cameraRetryCount = 0;

        // Activate prayers on first attack
        if (usePrayer)
        {
            if (!prayers.isActive(protectivePrayer))  prayers.activate(protectivePrayer);
            if (offensivePrayer != null && !prayers.isActive(offensivePrayer)) prayers.activate(offensivePrayer);
        }

        antiban.reactionDelay();
        state = State.ATTACKING;
        idleTickCount = 0;
        log.debug("Attacking {} (id={} index={})", target.getName(), target.getId(), target.getIndex());
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
            // Deactivate offensive prayer when not fighting to save points
            if (usePrayer && offensivePrayer != null) prayers.deactivate(offensivePrayer);

            // Check for loot before finding next target
            if (lootEnabled && hasLootNearby())
            {
                state = State.LOOTING;
            }
            else
            {
                state = State.FIND_TARGET;
            }
            idleTickCount = 0;
        }
    }

    private void lootItems()
    {
        boolean pickedUp = false;
        for (int itemId : lootItemIds)
        {
            GroundItems.TileItemOnTile result = groundItems.nearestWithTile(itemId);
            if (result != null)
            {
                interaction.click(result.item, result.tile);
                antiban.reactionDelay();
                pickedUp = true;
                break; // one item per tick
            }
        }

        if (!pickedUp || !hasLootNearby())
        {
            state = State.FIND_TARGET;
        }
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            // Withdraw food after depositing
            for (int foodId : FOOD_IDS)
            {
                if (bank.contains(foodId))
                {
                    bank.withdraw(foodId, Integer.MAX_VALUE);
                    antiban.reactionDelay();
                    break;
                }
            }
            bank.close();
            state = State.FIND_TARGET;
            return;
        }

        // Walk to bank or open it
        if (bank.isNearBank())
        {
            bank.openNearest();
        }
        else
        {
            // Walk toward home (nearest bank is near typical training spots)
            movement.walkTo(homeTile);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean shouldEat()
    {
        return players.shouldEat(eatThresholdPercent) && hasFood();
    }

    private boolean hasFood()
    {
        for (int foodId : FOOD_IDS)
        {
            if (inventory.contains(foodId)) return true;
        }
        return false;
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
        log.warn("No food in inventory — low HP!");
    }

    private boolean hasLootNearby()
    {
        for (int itemId : lootItemIds)
        {
            if (groundItems.exists(itemId)) return true;
        }
        return false;
    }

    private static Prayer parsePrayer(String name, Prayer fallback)
    {
        if (name == null || name.isBlank()) return fallback;
        try { return Prayer.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
