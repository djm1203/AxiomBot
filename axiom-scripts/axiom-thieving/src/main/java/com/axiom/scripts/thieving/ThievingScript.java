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
import com.axiom.scripts.thieving.ThievingSettings.StallType;

/**
 * Axiom Thieving — two modes: steal from market stalls or pickpocket NPCs.
 *
 * State machine (shared for both modes):
 *   FIND_TARGET — locate the stall or NPC; click the relevant action
 *   ACTING      — wait for animation to complete; handle stun for pickpocket
 *
 * The method field in ThievingSettings controls which logic runs in each state.
 *
 * Stall mode:
 *   FIND_TARGET: find the stall by name (equalsIgnoreCase) → "Steal-from"
 *   ACTING:      wait for animation → drop junk if configured → FIND_TARGET
 *
 * Pickpocket mode:
 *   FIND_TARGET: check stun graphic (245) → find NPC by name → "Pickpocket"
 *   ACTING:      check stun graphic → wait for animation → drop junk → FIND_TARGET
 *
 * Stun graphic 245 is the THUMP overlay that appears when a pickpocket fails.
 * A 4-tick delay clears the stun window before the next attempt.
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Thieving",
    version     = "1.0",
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

    // Animation tracking
    private boolean wasActing         = false;
    private int     noAnimationTicks  = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;

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

        state            = State.FIND_TARGET;
        wasActing        = false;
        noAnimationTicks = 0;

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

        wasActing        = false;
        noAnimationTicks = 0;
        setTickDelay(2);
        state = State.ACTING;
    }

    private void handleWaitingStall()
    {
        if (players.isAnimating())
        {
            if (!wasActing)
            {
                log.info("[WAITING_STALL] Steal animation started");
                wasActing = true;
            }
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasActing)
        {
            wasActing = false;
            log.info("[WAITING_STALL] Steal complete");
            if (settings.dropJunk) dropJunk();
            state = State.FIND_TARGET;
            return;
        }

        if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.warn("[WAITING_STALL] Steal animation timed out — retrying");
            noAnimationTicks = 0;
            state = State.FIND_TARGET;
        }
        else
        {
            log.info("[WAITING_STALL] Waiting for steal animation ({}/{})",
                noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
            setTickDelay(1);
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

        wasActing        = false;
        noAnimationTicks = 0;
        setTickDelay(1);
        state = State.ACTING;
    }

    private void handlePickpocketing()
    {
        // Stun detected — abort and wait
        if (players.getGraphic() == STUN_GRAPHIC)
        {
            log.info("[PICKPOCKETING] Stunned — waiting out stun");
            wasActing        = false;
            noAnimationTicks = 0;
            setTickDelay(4);
            state = State.FIND_TARGET;
            return;
        }

        if (players.isAnimating())
        {
            if (!wasActing)
            {
                log.info("[PICKPOCKETING] Pickpocket animation started");
                wasActing = true;
            }
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasActing)
        {
            wasActing = false;
            log.info("[PICKPOCKETING] Pickpocket complete");
            if (settings.dropJunk) dropJunk();
            state = State.FIND_TARGET;
            return;
        }

        if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.warn("[PICKPOCKETING] Pickpocket animation timed out — retrying");
            noAnimationTicks = 0;
            state = State.FIND_TARGET;
        }
        else
        {
            log.info("[PICKPOCKETING] Waiting for pickpocket animation ({}/{})",
                noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
            setTickDelay(1);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Drops all coins (id 995) when inventory is full, freeing space to continue
     * stealing. For XP-focused sessions where loot accumulation isn't the goal.
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
