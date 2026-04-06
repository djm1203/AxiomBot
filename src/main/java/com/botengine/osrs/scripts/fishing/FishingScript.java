package com.botengine.osrs.scripts.fishing;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.NPC;

import javax.inject.Inject;

/**
 * Fishing AFK script.
 *
 * State machine:
 *   FIND_SPOT → FISHING → (inventory full) → DROPPING → FIND_SPOT
 *
 * Targets fishing spots by NPC ID. Power-fishes (drops catch when inventory
 * is full) for maximum XP. Spots wander — when the current spot moves away
 * the script automatically finds the nearest active spot.
 *
 * Common fishing spot NPC IDs:
 *   1530 — Net/Bait (shrimp/anchovies/herring/pike)
 *   1526 — Lure/Bait (trout/salmon)
 *   1542 — Cage/Harpoon (lobster/swordfish/tuna)
 *   1544 — Big Net (mackerel/cod/bass/leaping)
 *   7730, 7731 — Barbarian rod spots
 *
 * Click action:
 *   Most spots use "Lure", "Bait", "Net", "Cage", or "Harpoon".
 *   The script clicks NPC_FIRST_OPTION which selects whatever is the first
 *   right-click option on the spot (matches the equipped tool).
 */
public class FishingScript extends BotScript
{
    // All common fishing spot NPC IDs
    private static final int[] SPOT_IDS = {
        1530, 1526, 1542, 1544,   // Common spots
        7730, 7731,               // Barbarian fishing
        1536, 1541,               // Fly fishing, cage
        6825                      // Aerial fishing
    };

    // Fish item IDs to drop (raw fish)
    private static final int[] FISH_IDS = {
        317,  // Raw shrimps
        321,  // Raw anchovies
        327,  // Raw sardine
        335,  // Raw herring
        339,  // Raw pike
        349,  // Raw salmon
        351,  // Raw trout
        359,  // Raw tuna
        371,  // Raw swordfish
        377,  // Raw lobster
        383,  // Raw shark
        11328 // Leaping trout/salmon/sturgeon
    };

    // Fishing animation IDs (various rods/nets)
    private static final int[] FISH_ANIMATIONS = {
        623,  // Fly fishing rod / lure
        622,  // Small net / bait
        619,  // Harpoon
        618,  // Cage
        6705, // Barbarian rod
        7331  // Aerial fishing
    };

    private enum State { FIND_SPOT, FISHING, DROPPING }

    private State state = State.FIND_SPOT;
    private int idleTickCount = 0;

    /** Click action from config — determines which fishing option to use. */
    private String fishingAction = "Lure";

    @Inject
    public FishingScript() {}

    @Override
    public String getName() { return "Fishing"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        String action = config.fishingAction().trim();
        fishingAction = action.isEmpty() ? "Lure" : action;
    }

    @Override
    public void onStart()
    {
        log.info("Started — power-fishing mode, action: {}", fishingAction);
        state = State.FIND_SPOT;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_SPOT:
                findAndFishSpot();
                break;

            case FISHING:
                checkFishingState();
                break;

            case DROPPING:
                dropFish();
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndFishSpot()
    {
        if (inventory.isFull())
        {
            state = State.DROPPING;
            return;
        }

        NPC spot = npcs.nearest(SPOT_IDS);
        if (spot == null)
        {
            log.debug("No fishing spot nearby — waiting");
            return;
        }

        boolean clicked = interaction.click(spot, fishingAction);
        if (!clicked)
        {
            log.debug("Fishing spot off-screen — rotating camera toward it");
            camera.rotateTo(spot.getWorldLocation());
            return;
        }
        antiban.reactionDelay();
        state = State.FISHING;
        idleTickCount = 0;
        log.debug("Fishing at spot id={}", spot.getId());
    }

    private void checkFishingState()
    {
        if (inventory.isFull())
        {
            state = State.DROPPING;
            return;
        }

        if (!isActivelyFishing())
        {
            idleTickCount++;
            // Give a tick or two of grace before re-clicking (spot may have just moved)
            if (idleTickCount >= 2)
            {
                antiban.randomDelay(200, 600);
                state = State.FIND_SPOT;
                idleTickCount = 0;
            }
        }
        else
        {
            idleTickCount = 0;
        }
    }

    private boolean isActivelyFishing()
    {
        int anim = players.getAnimation();
        for (int fishAnim : FISH_ANIMATIONS)
        {
            if (anim == fishAnim) return true;
        }
        return false;
    }

    private void dropFish()
    {
        boolean droppedAny = false;
        for (int fishId : FISH_IDS)
        {
            if (inventory.contains(fishId))
            {
                interaction.clickInventoryItem(fishId, "Drop");
                antiban.randomDelay(80, 160);
                droppedAny = true;
            }
        }

        if (!droppedAny || inventory.isEmpty())
        {
            state = State.FIND_SPOT;
        }
    }
}
