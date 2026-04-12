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
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;

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
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private FishingSettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_SPOT, FISHING, FULL, DROPPING, BANKING }
    private State state = State.FIND_SPOT;

    // Animation tracking — see Woodcutting for rationale.
    private boolean wasFishing       = false;
    private int     noAnimationTicks = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;

    // Bank-open timing — deposit widget not available the same tick isOpen() first returns true.
    private boolean bankJustOpened   = false;

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

        log.info("Fishing started: spot={} action={} powerFish={}",
            settings.spotType.name(), settings.bankAction.name(), settings.powerFish);

        state = State.FIND_SPOT;
    }

    @Override
    public void onLoop()
    {
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
        if (inventory.isFull())
        {
            log.info("[FIND_SPOT] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        SceneObject spot = npcs.nearest(settings.spotType.npcIds);
        if (spot == null)
        {
            log.info("[FIND_SPOT] No fishing spot found nearby — waiting 3 ticks");
            setTickDelay(3);
            return;
        }

        log.info("[FIND_SPOT] Found {} (id={}) at ({},{}) — clicking '{}'",
            spot.getName(), spot.getId(),
            spot.getWorldX(), spot.getWorldY(),
            settings.spotType.action);

        spot.interact(settings.spotType.action);
        wasFishing       = false;
        noAnimationTicks = 0;

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
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            // Interact() was sent but animation never started → walk still in progress or missed click.
            log.info("[FISHING] Animation never started (timeout) — retrying");
            state            = State.FIND_SPOT;
            noAnimationTicks = 0;
        }
        else
        {
            log.debug("[FISHING] Waiting for animation ({}/{})", noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
        }
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

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.info("[BANKING] Could not open bank after {} attempts — dropping instead",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                state = State.DROPPING;
                return;
            }

            // Walk toward and open the bank. If the booth is in the scene (within ~50 tiles)
            // the game will path-find automatically. We re-click every 3 ticks rather than
            // spamming, giving the client time to start moving.
            log.info("[BANKING] Opening bank (attempt {}/{})",
                bankOpenAttempts + 1, MAX_BANK_OPEN_ATTEMPTS);
            bank.openNearest();
            bankOpenAttempts++;
            setTickDelay(3);
            return;
        }

        // Bank is open — reset attempt counter for the next banking cycle.
        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        log.info("[BANKING] Depositing all and closing");
        bank.depositAll();
        bank.close();
        bankJustOpened = false;
        setTickDelay(2);
        state = State.FIND_SPOT;
    }
}
