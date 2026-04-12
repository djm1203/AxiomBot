package com.axiom.scripts.thieving;

import com.axiom.api.game.GameObjects;
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
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Players     players;
    private GameObjects gameObjects;
    private Npcs        npcs;
    private Inventory   inventory;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private ThievingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_TARGET, ACTING }
    private State state = State.FIND_TARGET;

    // Inventory change detection
    private int inventoryCountBefore = 0;
    private int noChangeTicks        = 0;
    private static final int NO_CHANGE_TICKS = 10;

    // Pickpocket stun graphic ID (THUMP overlay on player model)
    private static final int STUN_GRAPHIC = 245;

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
        noChangeTicks        = 0;

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
                else                                          handleFindNpc();
                break;
            case ACTING:
                if (settings.method == ThievingMethod.STALL) handleWaitingStall();
                else                                          handlePickpocketing();
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Thieving stopped");
    }

    // ── Stall handlers ────────────────────────────────────────────────────────

    private void handleFindStall()
    {
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
        noChangeTicks        = 0;
        setTickDelay(1);
        state = State.ACTING;
    }

    private void handleWaitingStall()
    {
        int currentCount = inventory.getCount();

        if (currentCount > inventoryCountBefore)
        {
            log.info("[WAITING_STALL] Item stolen — slots {} → {}",
                inventoryCountBefore, currentCount);
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
        // Wait out stun before attempting next pickpocket
        if (players.getGraphic() == STUN_GRAPHIC)
        {
            log.info("[FIND_NPC] Stunned — waiting");
            setTickDelay(4);
            return;
        }

        // Exact name match — prevents "Farmer" from matching "Master Farmer"
        SceneObject target = npcs.nearest(
            n -> n.getName().equalsIgnoreCase(settings.npcTarget.npcName));

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
        target.interact("Pickpocket");

        // Snapshot inventory size — only detects the pickpocket if a new slot
        // opens (e.g. first coins, seeds, gems). If coins are already stacked,
        // count won't change; the 10-tick timeout retries cleanly.
        inventoryCountBefore = inventory.getCount();
        noChangeTicks        = 0;
        setTickDelay(1);
        state = State.ACTING;
    }

    private void handlePickpocketing()
    {
        // Stun detected — abort and wait
        if (players.getGraphic() == STUN_GRAPHIC)
        {
            log.info("[PICKPOCKETING] Stunned — waiting out stun");
            noChangeTicks = 0;
            setTickDelay(4);
            state = State.FIND_TARGET;
            return;
        }

        int currentCount = inventory.getCount();

        if (currentCount > inventoryCountBefore)
        {
            log.info("[PICKPOCKETING] Pickpocket landed — slots {} → {}",
                inventoryCountBefore, currentCount);
            if (settings.dropJunk) dropJunk();
            noChangeTicks = 0;
            state = State.FIND_TARGET;
            setTickDelay(Math.max(antiban.reactionTicks(), 1));
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Drops all coins (id 995) when inventory is full, freeing space to continue
     * stealing. Intended for XP-focused sessions where loot is not the goal.
     */
    private void dropJunk()
    {
        if (inventory.isFull())
        {
            log.info("[DROP_JUNK] Inventory full — dropping coins");
            inventory.dropAll(995);
        }
    }
}
