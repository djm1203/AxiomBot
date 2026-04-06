package com.botengine.osrs.scripts.mining;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;

/**
 * Mining AFK script.
 *
 * State machine:
 *   FIND_ROCK → MINING → (full) → DROPPING or BANKING → FIND_ROCK
 *
 * Features:
 *   - Power-mine (drop) or banking mode
 *   - Optional ore name filter (e.g. "Iron", "Coal")
 *   - Animation-based mine detection
 */
public class MiningScript extends BotScript
{
    private static final int[] ROCK_IDS = {
        10943, 11960, 11961,
        11933, 11932, 11934,
        11954, 11955, 11956,
        11930, 11931, 17094,
        11957, 11958, 11959,
        11942, 11943, 11944,
        11939, 11940, 11941,
        11963, 11962
    };

    private static final int[] ORE_IDS = {
        436, 438, 440, 453, 444, 447, 449, 451
    };

    private static final int[] MINE_ANIMATIONS = {
        624, 625, 626, 627, 628, 629, 630,
        7139, 7158, 21174
    };

    private enum State { FIND_ROCK, MINING, DROPPING, BANKING }

    private State state = State.FIND_ROCK;
    private int   idleTickCount  = 0;
    private String rockNameFilter = "";
    private boolean bankingMode       = false;
    private boolean shiftDrop         = false;
    private boolean hopOnCompetition  = false;
    private WorldPoint homeTile;

    @Inject
    public MiningScript() {}

    @Override
    public String getName() { return "Mining"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        rockNameFilter   = config.miningRockName().trim();
        bankingMode      = config.miningBankingMode();
        shiftDrop        = config.miningShiftDrop();
        hopOnCompetition = config.miningHopOnCompetition();
    }

    @Override
    public void onStart()
    {
        log.info("Started — target='{}' banking={}",
            rockNameFilter.isEmpty() ? "any" : rockNameFilter, bankingMode);
        state    = State.FIND_ROCK;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_ROCK: findAndMineRock();   break;
            case MINING:    checkMiningState();  break;
            case DROPPING:  dropOre();           break;
            case BANKING:   handleBanking();     break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndMineRock()
    {
        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        GameObject rock = findRock();
        if (rock == null)
        {
            log.debug("No ore rock found — waiting");
            return;
        }

        interaction.click(rock, "Mine");
        antiban.reactionDelay();
        state = State.MINING;
        idleTickCount = 0;
        log.debug("Mining rock id={}", rock.getId());
    }

    private void checkMiningState()
    {
        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        if (hopOnCompetition && players.nearbyCount(5) > 0)
        {
            log.info("Competition detected — hopping world");
            worldHopper.hopToMembers();
            state = State.FIND_ROCK;
            return;
        }

        if (!isActivelyMining())
        {
            idleTickCount++;
            if (idleTickCount >= 2)
            {
                antiban.randomDelay(200, 600);
                state = State.FIND_ROCK;
                idleTickCount = 0;
            }
        }
        else
        {
            idleTickCount = 0;
        }
    }

    private void dropOre()
    {
        if (shiftDrop)
        {
            interaction.dropAll(ORE_IDS);
            antiban.reactionDelay();
            state = State.FIND_ROCK;
            return;
        }
        boolean droppedAny = false;
        for (int oreId : ORE_IDS)
        {
            if (inventory.contains(oreId))
            {
                interaction.clickInventoryItem(oreId, "Drop");
                antiban.randomDelay(80, 160);
                droppedAny = true;
            }
        }
        if (!droppedAny || inventory.isEmpty()) state = State.FIND_ROCK;
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
                state = State.FIND_ROCK;
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

    private GameObject findRock()
    {
        if (rockNameFilter.isEmpty()) return gameObjects.nearest(ROCK_IDS);
        final String filter = rockNameFilter.toLowerCase();
        return gameObjects.nearest(obj -> {
            for (int id : ROCK_IDS)
            {
                if (obj.getId() == id)
                {
                    ObjectComposition def = client.getObjectDefinition(obj.getId());
                    return def != null && def.getName() != null
                        && def.getName().toLowerCase().contains(filter);
                }
            }
            return false;
        });
    }

    private boolean isActivelyMining()
    {
        int anim = players.getAnimation();
        for (int a : MINE_ANIMATIONS) { if (anim == a) return true; }
        return false;
    }
}
