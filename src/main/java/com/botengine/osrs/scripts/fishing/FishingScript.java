package com.botengine.osrs.scripts.fishing;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.util.Progression;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;

/**
 * Fishing AFK script.
 *
 * State machine:
 *   FIND_SPOT → FISHING → (full) → DROPPING or BANKING → FIND_SPOT
 *
 * Features:
 *   - Configurable click action (Lure/Bait/Net/Cage/Harpoon)
 *   - Power-fish (drop) or banking mode
 *   - Animation-based fishing detection
 *   - Camera rotation for off-screen spots
 */
public class FishingScript extends BotScript
{
    private static final int[] SPOT_IDS = {
        1530, 1526, 1542, 1544,
        7730, 7731,
        1536, 1541,
        6825
    };

    private static final int[] FISH_IDS = {
        317, 321, 327, 335, 339, 349, 351, 359, 371, 377, 383, 11328
    };

    private static final int[] FISH_ANIMATIONS = {
        623, 622, 619, 618, 6705, 7331
    };

    private enum State { FIND_SPOT, FISHING, DROPPING, BANKING }

    private State state = State.FIND_SPOT;
    private int   idleTickCount = 0;
    private String  fishingAction = "Lure";
    private boolean bankingMode   = false;
    private boolean shiftDrop     = false;
    private WorldPoint homeTile;
    private Progression progression = new Progression("", "");

    @Inject
    public FishingScript() {}

    @Override
    public String getName() { return "Fishing"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        String action = config.fishingAction().trim();
        fishingAction = action.isEmpty() ? "Lure" : action;
        bankingMode   = config.fishingBankingMode();
        shiftDrop     = config.fishingShiftDrop();
        progression = new Progression(config.fishingProgression(), fishingAction);
    }

    @Override
    public void onStart()
    {
        log.info("Started — action='{}' banking={}", fishingAction, bankingMode);
        state    = State.FIND_SPOT;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        if (!progression.isEmpty())
        {
            int lvl = client.getRealSkillLevel(net.runelite.api.Skill.FISHING);
            fishingAction = progression.resolve(lvl);
        }

        switch (state)
        {
            case FIND_SPOT: findAndFishSpot();    break;
            case FISHING:   checkFishingState();  break;
            case DROPPING:  dropFish();           break;
            case BANKING:   handleBanking();      break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndFishSpot()
    {
        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
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
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        if (!isActivelyFishing())
        {
            idleTickCount++;
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

    private void dropFish()
    {
        if (shiftDrop)
        {
            interaction.dropAll(FISH_IDS);
            antiban.reactionDelay();
            state = State.FIND_SPOT;
            return;
        }
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
        if (!droppedAny || inventory.isEmpty()) state = State.FIND_SPOT;
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            bank.close();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
            {
                movement.setRunning(true);
                movement.walkTo(homeTile);
            }
            else
            {
                state = State.FIND_SPOT;
            }
            return;
        }

        if (bank.isNearBank())
        {
            bank.openNearest();
        }
        else
        {
            movement.setRunning(true);
            log.debug("Walking to find bank...");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isActivelyFishing()
    {
        int anim = players.getAnimation();
        for (int a : FISH_ANIMATIONS) { if (anim == a) return true; }
        return false;
    }
}
