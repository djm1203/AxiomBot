package com.axiom.scripts.combat;

import com.axiom.api.game.GroundItems;
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

import java.util.Arrays;

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
    private GroundItems groundItems;
    private Inventory   inventory;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private CombatSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_TARGET, ATTACKING, LOOTING }
    private State state = State.FIND_TARGET;

    // Idle tick counter — incremented when neither in combat nor animating.
    // Resets to 0 when combat action is detected.
    private int noActionTicks = 0;
    private static final int COMBAT_TIMEOUT_TICKS = 10;

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

        log.info("Combat started: npc='{}' eatAt={}% foods={} loot={}",
            settings.npcName,
            settings.eatAtHpPercent,
            Arrays.toString(settings.foodIds),
            Arrays.toString(settings.lootItemIds));
    }

    @Override
    public void onLoop()
    {
        // Health check — eat before doing anything else this tick
        if (shouldEat())
        {
            eat();
            return;
        }

        switch (state)
        {
            case FIND_TARGET: handleFindTarget(); break;
            case ATTACKING:   handleAttacking();  break;
            case LOOTING:     handleLooting();    break;
        }
    }

    @Override
    public void onStop()
    {
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

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindTarget()
    {
        // Stop if food has run out (when configured to do so)
        if (settings.stopWhenOutOfFood
                && settings.foodIds.length > 0
                && !inventory.containsAny(settings.foodIds))
        {
            log.info("[FIND_TARGET] Out of food — stopping");
            stop();
            return;
        }

        // Already pulled into combat by an aggressive NPC — skip the search
        if (players.isInCombat())
        {
            log.info("[FIND_TARGET] Already in combat — transitioning to ATTACKING");
            noActionTicks = 0;
            state = State.ATTACKING;
            return;
        }

        // Find nearest free NPC (not currently fighting another actor)
        SceneObject target = npcs.nearest(n ->
            n.getName().equalsIgnoreCase(settings.npcName) && !n.isInCombat());

        if (target == null)
        {
            log.info("[FIND_TARGET] No free '{}' found nearby — waiting", settings.npcName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_TARGET] Attacking '{}' (id={}) at ({},{})",
            settings.npcName, target.getId(), target.getWorldX(), target.getWorldY());
        target.interact("Attack");
        noActionTicks = 0;
        setTickDelay(2); // wait for combat animation to register
        state = State.ATTACKING;
    }

    private void handleAttacking()
    {
        if (players.isInCombat() || players.isAnimating())
        {
            // Combat is ongoing — reset idle counter and let the fight continue
            noActionTicks = 0;
            return;
        }

        noActionTicks++;

        if (noActionTicks >= COMBAT_TIMEOUT_TICKS)
        {
            // Target died or interaction was lost — move to looting
            log.info("[ATTACKING] No combat action for {} ticks — moving to LOOTING",
                COMBAT_TIMEOUT_TICKS);
            noActionTicks = 0;
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
        if (settings.lootItemIds.length == 0)
        {
            state = State.FIND_TARGET;
            return;
        }

        for (int itemId : settings.lootItemIds)
        {
            SceneObject groundItem = groundItems.nearest(itemId);
            if (groundItem != null)
            {
                log.info("[LOOTING] Picking up item id={} at ({},{})",
                    itemId, groundItem.getWorldX(), groundItem.getWorldY());
                groundItem.interact("Take");
                setTickDelay(2); // wait for item to be picked up before next check
                return;
            }
        }

        log.info("[LOOTING] No configured loot found — finding next target");
        state = State.FIND_TARGET;
    }
}
