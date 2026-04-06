package com.botengine.osrs.scripts.mining;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;

import javax.inject.Inject;

/**
 * Mining AFK script.
 *
 * State machine:
 *   FIND_ROCK → MINING → (inventory full) → DROPPING → FIND_ROCK
 *
 * Power-mines (drops ore when inventory is full) for maximum XP.
 * Supports all common ore rocks. When a rock depletes (becomes empty),
 * the script finds the next available ore rock nearby.
 *
 * Common ore rock GameObject IDs:
 *   Copper:    10943, 11960, 11961
 *   Tin:       11933, 11932, 11934
 *   Iron:      11954, 11955, 11956
 *   Coal:      11930, 11931, 17094
 *   Gold:      11957, 11958, 11959
 *   Mithril:   11942, 11943, 11944
 *   Adamantite: 11939, 11940, 11941
 *   Runite:    11963, 11962
 *
 * Empty rock IDs (after depletion):
 *   11552, 11553 — common depleted rock objects
 */
public class MiningScript extends BotScript
{
    // Active ore rock GameObjects
    private static final int[] ROCK_IDS = {
        10943, 11960, 11961,    // Copper
        11933, 11932, 11934,    // Tin
        11954, 11955, 11956,    // Iron
        11930, 11931, 17094,    // Coal
        11957, 11958, 11959,    // Gold
        11942, 11943, 11944,    // Mithril
        11939, 11940, 11941,    // Adamantite
        11963, 11962            // Runite
    };

    // Ore item IDs
    private static final int[] ORE_IDS = {
        436,  // Copper ore
        438,  // Tin ore
        440,  // Iron ore
        453,  // Coal
        444,  // Gold ore
        447,  // Mithril ore
        449,  // Adamantite ore
        451   // Runite ore
    };

    // Mining animation IDs (various pickaxes)
    private static final int[] MINE_ANIMATIONS = {
        624, 625, 626, 627, 628, 629, 630,  // Bronze–Dragon pickaxe
        7139, 7158,                          // Crystal, 3rd age
        21174                                // Infernal pickaxe
    };

    private enum State { FIND_ROCK, MINING, DROPPING }

    private State state = State.FIND_ROCK;
    private int idleTickCount = 0;

    /** Rock name filter from config — empty means accept any rock in ROCK_IDS. */
    private String rockNameFilter = "";

    @Inject
    public MiningScript() {}

    @Override
    public String getName() { return "Mining"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        rockNameFilter = config.miningRockName().trim();
    }

    @Override
    public void onStart()
    {
        String target = rockNameFilter.isEmpty() ? "any rock" : rockNameFilter;
        log.info("Started — power-mining mode, target: {}", target);
        state = State.FIND_ROCK;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_ROCK:
                findAndMineRock();
                break;

            case MINING:
                checkMiningState();
                break;

            case DROPPING:
                dropOre();
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndMineRock()
    {
        if (inventory.isFull())
        {
            state = State.DROPPING;
            return;
        }

        GameObject rock = findRock();
        if (rock == null)
        {
            log.debug("No ore rock found nearby — waiting");
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
            state = State.DROPPING;
            return;
        }

        if (!isActivelyMining())
        {
            idleTickCount++;
            if (idleTickCount >= 2)
            {
                // Rock likely depleted — find next one
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

    private GameObject findRock()
    {
        if (rockNameFilter.isEmpty())
        {
            return gameObjects.nearest(ROCK_IDS);
        }
        final String filter = rockNameFilter.toLowerCase();
        return gameObjects.nearest(obj -> {
            for (int id : ROCK_IDS)
            {
                if (obj.getId() == id)
                {
                    net.runelite.api.ObjectComposition def = client.getObjectDefinition(obj.getId());
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
        for (int mineAnim : MINE_ANIMATIONS)
        {
            if (anim == mineAnim) return true;
        }
        return false;
    }

    private void dropOre()
    {
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

        if (!droppedAny || inventory.isEmpty())
        {
            state = State.FIND_ROCK;
        }
    }
}
