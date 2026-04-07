package com.botengine.osrs.scripts.combat;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
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

    private enum State { FIND_TARGET, ATTACKING, EATING, LOOTING, BANKING, RESETTING, FILLING_CANNON, BUYING_SUPPLIES }

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
    private boolean emergencyLogout    = false;
    private int     emergencyLogoutHp  = 10;
    private boolean sandCrabsMode      = false;
    private boolean cannonMode         = false;
    private int     cannonRefillThreshold = 20; // ticks between refills
    private int     noTargetTickCount = 0;
    private int     ticksSinceCannonFill = 0;
    private static final int PASSIVE_THRESHOLD = 10;
    // Cannon item IDs (inventory): base=6, stand=8, barrel=10, furnace=12
    private static final int[] CANNON_PARTS = { 6, 8, 10, 12 };
    // Cannonball item ID
    private static final int CANNONBALL_ID  = 2;
    private WorldPoint resetTile;

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
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        configRef = globalConfig;
        CombatSettings s = (scriptSettings instanceof CombatSettings)
            ? (CombatSettings) scriptSettings : new CombatSettings();
        targetNpcName         = s.targetNpcName;
        eatThresholdPercent   = s.eatThresholdPercent;
        usePrayer             = s.usePrayer;
        prayerPotPercent      = s.prayerPotPercent;
        lootEnabled           = s.lootEnabled;
        useSpec               = s.useSpec;
        specThreshold         = s.specThreshold;
        bankingMode           = s.bankingMode;
        sandCrabsMode         = s.sandCrabsMode;
        cannonMode            = s.cannonMode;
        cannonRefillThreshold = s.cannonRefillThreshold;
        emergencyLogout       = s.emergencyLogoutEnabled;
        emergencyLogoutHp     = s.emergencyLogoutHpPercent;
        protectivePrayer      = parsePrayer(s.protectivePrayer, Prayer.PROTECT_FROM_MELEE);
        offensivePrayer       = parsePrayer(s.offensivePrayer, null);
        lootItemIds.clear();
        for (String part : s.lootItemIds.split(","))
        {
            try { lootItemIds.add(Integer.parseInt(part.trim())); }
            catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent parent)
    {
        return new CombatConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        log.info("Started — target='{}' eat={}% prayer={} loot={} spec={} banking={} cannon={}",
            targetNpcName, eatThresholdPercent, usePrayer, lootEnabled, useSpec, bankingMode, cannonMode);
        state = State.FIND_TARGET;
        cameraRetryCount = 0;
        homeTile = players.getLocation();
        noTargetTickCount = 0;
        ticksSinceCannonFill = 0;
        resetTile = null;
    }

    @Override
    public void onLoop()
    {
        // Emergency logout — highest priority
        if (emergencyLogout
            && players.shouldEat(emergencyLogoutHp)
            && !hasFood())
        {
            log.warn("EMERGENCY LOGOUT — HP critically low, no food");
            movement.logout();
            return;
        }

        // Cannon maintenance — check before combat actions
        if (cannonMode)
        {
            ticksSinceCannonFill++;
            if (state == State.BUYING_SUPPLIES) { buySupplies(); return; }
            if (shouldRefillCannon())           { state = State.FILLING_CANNON; }
            if (state == State.FILLING_CANNON)  { fillCannon(); return; }
        }

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
            case LOOTING:          lootItems();        break;
            case BANKING:          handleBanking();    break;
            case RESETTING:        resetAggro();       break;
            case FILLING_CANNON:   fillCannon();       break;
            case BUYING_SUPPLIES:  buySupplies();      break;
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
        if (target == null || target.isDead())
        {
            if (sandCrabsMode)
            {
                noTargetTickCount++;
                if (noTargetTickCount >= PASSIVE_THRESHOLD)
                {
                    log.info("Crabs appear passive — resetting aggro");
                    state = State.RESETTING;
                    noTargetTickCount = 0;
                }
            }
            return;
        }
        noTargetTickCount = 0; // found a target — reset counter

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

    private void resetAggro()
    {
        // Walk ~15 tiles away from home tile to reset aggro, then return
        if (resetTile == null)
        {
            // Pick a tile 15 steps north of home
            resetTile = new WorldPoint(
                homeTile.getX(),
                homeTile.getY() + 15,
                homeTile.getPlane()
            );
        }

        int distToReset = players.distanceTo(resetTile);
        int distToHome  = players.distanceTo(homeTile);

        if (distToReset > 3)
        {
            // First leg: walk away
            movement.walkTo(resetTile);
        }
        else if (distToHome > 3)
        {
            // Second leg: walk back
            antiban.randomDelay(1000, 2000); // wait a moment before returning
            movement.walkTo(homeTile);
        }
        else
        {
            // Back home — crabs should be aggressive again
            log.info("Aggro reset complete");
            resetTile = null;
            state = State.FIND_TARGET;
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

    // ── Cannon handlers ───────────────────────────────────────────────────────

    /**
     * Returns true when the cannon should be refilled.
     * Triggers after {@code cannonRefillThreshold} ticks since the last fill,
     * provided a cannon object is nearby and cannonballs are in the inventory.
     */
    private boolean shouldRefillCannon()
    {
        if (!inventory.contains(CANNONBALL_ID)) return false;
        if (ticksSinceCannonFill < cannonRefillThreshold) return false;
        return findCannon() != null;
    }

    /**
     * Finds the placed dwarf cannon nearby by looking for a GameObject whose
     * definition includes a "Fire" action (unique to the assembled cannon).
     */
    private GameObject findCannon()
    {
        return gameObjects.nearest(obj -> {
            ObjectComposition def = client.getObjectDefinition(obj.getId());
            if (def == null) return false;
            String[] actions = def.getActions();
            if (actions == null) return false;
            for (String action : actions)
            {
                if ("Fire".equals(action)) return true;
            }
            return false;
        });
    }

    /**
     * Places the cannon from inventory if all 4 parts are present and no cannon
     * is already deployed nearby.  Uses "Set-up" on the cannon base item.
     */
    private void placeCannon()
    {
        boolean hasParts = true;
        for (int partId : CANNON_PARTS)
        {
            if (!inventory.contains(partId)) { hasParts = false; break; }
        }
        if (!hasParts) return;
        if (findCannon() != null) return; // already placed

        interaction.clickInventoryItem(CANNON_PARTS[0], "Set-up");
        antiban.reactionDelay();
        log.info("Setting up dwarf cannon");
    }

    /**
     * Walks to the cannon and uses cannonballs on it to refill.
     * Transitions back to FIND_TARGET on success.
     */
    private void fillCannon()
    {
        if (!inventory.contains(CANNONBALL_ID))
        {
            if (config() != null && config().geRestockEnabled())
            {
                state = State.BUYING_SUPPLIES;
            }
            else
            {
                log.warn("Out of cannonballs — stopping cannon mode");
                state = State.FIND_TARGET;
            }
            return;
        }

        GameObject cannon = findCannon();
        if (cannon == null)
        {
            // Cannon might need placing on this run
            placeCannon();
            return;
        }

        int ballSlot = inventory.getSlot(CANNONBALL_ID);
        if (ballSlot == -1) return;

        interaction.useItemOn(CANNONBALL_ID, ballSlot, cannon);
        antiban.reactionDelay();
        ticksSinceCannonFill = 0;
        log.debug("Refilled cannon with cannonballs");
        state = State.FIND_TARGET;
    }

    /**
     * Walks to the Grand Exchange and buys cannonballs when out of stock.
     * Uses the GE restock settings from config.
     */
    private void buySupplies()
    {
        if (!grandExchange.isOpen())
        {
            grandExchange.openNearest();
            antiban.reactionDelay();
            return;
        }

        // Collect any completed offers first
        if (grandExchange.hasItemsToCollect())
        {
            grandExchange.collectAll();
            antiban.reactionDelay();
            if (inventory.contains(CANNONBALL_ID))
            {
                log.info("Collected cannonballs — resuming combat");
                state = State.FIND_TARGET;
            }
            return;
        }

        // If no active offer, create one
        if (!grandExchange.hasActiveOffer())
        {
            int slot = grandExchange.findEmptySlot();
            if (slot == -1) { log.warn("No empty GE slots"); return; }
            grandExchange.clickSlot(slot);
            antiban.reactionDelay();
            grandExchange.clickBuy();
            antiban.reactionDelay();
        }

        // If search is open, click first result
        if (grandExchange.isSearchOpen())
        {
            grandExchange.clickFirstResult();
            antiban.reactionDelay();
            return;
        }

        // If offer setup is open, set quantity and confirm
        if (grandExchange.isOfferSetupOpen())
        {
            grandExchange.setQuantity(1000);
            antiban.reactionDelay();
            grandExchange.setPriceAboveGuide();
            antiban.reactionDelay();
            grandExchange.confirmOffer();
            antiban.reactionDelay();
        }
    }

    /** Returns the stored config reference (may be null if configure() not called). */
    private com.botengine.osrs.BotEngineConfig config()
    {
        return configRef;
    }

    private com.botengine.osrs.BotEngineConfig configRef = null;

    private static Prayer parsePrayer(String name, Prayer fallback)
    {
        if (name == null || name.isBlank()) return fallback;
        try { return Prayer.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
