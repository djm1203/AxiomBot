package com.botengine.osrs.scripts.woodcutting;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;

/**
 * Woodcutting AFK script.
 *
 * State machine:
 *   FIND_TREE → CHOPPING → (full) → DROPPING or BANKING → FIND_TREE
 *
 * Features:
 *   - Power-chop (drop) or banking mode
 *   - Optional tree name filter (e.g. "Yew", "Willow")
 *   - Bird nest detection — picks up nests before continuing
 *   - Animation-based chop detection
 */
public class WoodcuttingScript extends BotScript
{
    private static final int[] TREE_IDS = {
        1276, 1278, 1294, 1286,
        10820,
        10829, 10831, 10833,
        10832,
        10822, 10823,
        10834, 10835
    };

    private static final int[] LOG_IDS = {
        1511, 1521, 1519, 1517, 1515, 1513
    };

    /** Bird nest item ID dropped while chopping. */
    private static final int BIRD_NEST_ID = 5074;

    private static final int[] CHOP_ANIMATIONS = {
        867, 868, 869, 870, 871, 872, 873,
        2846, 7251
    };

    private enum State { FIND_TREE, CHOPPING, DROPPING, BANKING }

    private State state = State.FIND_TREE;
    private String  treeNameFilter  = "";
    private boolean bankingMode     = false;
    private boolean pickupNests     = true;
    private boolean hopOnCompetition = false;
    private WorldPoint homeTile;

    @Inject
    public WoodcuttingScript() {}

    @Override
    public String getName() { return "Woodcutting"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        treeNameFilter   = config.woodcuttingTreeName().trim();
        bankingMode      = config.woodcuttingBankingMode();
        pickupNests      = config.woodcuttingPickupNests();
        hopOnCompetition = config.woodcuttingHopOnCompetition();
    }

    @Override
    public void onStart()
    {
        log.info("Started — target='{}' banking={} nests={}",
            treeNameFilter.isEmpty() ? "any" : treeNameFilter, bankingMode, pickupNests);
        state    = State.FIND_TREE;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_TREE:  findAndChopTree();     break;
            case CHOPPING:   checkChoppingState();  break;
            case DROPPING:   dropLogs();            break;
            case BANKING:    handleBanking();        break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndChopTree()
    {
        // Pick up any bird nests before doing anything else
        if (pickupNests)
        {
            GroundItems.TileItemOnTile nest = groundItems.nearestWithTile(BIRD_NEST_ID);
            if (nest != null)
            {
                interaction.click(nest.item, nest.tile);
                antiban.reactionDelay();
                return;
            }
        }

        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        GameObject tree = findTree();
        if (tree == null)
        {
            log.debug("No tree found nearby — waiting");
            return;
        }

        interaction.click(tree, "Chop down");
        antiban.reactionDelay();
        state = State.CHOPPING;
        log.debug("Chopping tree id={}", tree.getId());
    }

    private void checkChoppingState()
    {
        // Pick up bird nests while waiting between chops
        if (pickupNests)
        {
            GroundItems.TileItemOnTile nest = groundItems.nearestWithTile(BIRD_NEST_ID);
            if (nest != null)
            {
                interaction.click(nest.item, nest.tile);
                antiban.reactionDelay();
                return;
            }
        }

        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        if (hopOnCompetition && players.nearbyCount(5) > 0)
        {
            log.info("Competition detected — hopping world");
            worldHopper.hopToMembers();
            state = State.FIND_TREE;
            return;
        }

        if (!isActivelyChopping())
        {
            antiban.randomDelay(300, 700);
            state = State.FIND_TREE;
        }
    }

    private void dropLogs()
    {
        boolean droppedAny = false;
        for (int logId : LOG_IDS)
        {
            if (inventory.contains(logId))
            {
                interaction.clickInventoryItem(logId, "Drop");
                antiban.randomDelay(80, 160);
                droppedAny = true;
            }
        }
        if (!droppedAny || inventory.isEmpty()) state = State.FIND_TREE;
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            bank.close();
            // Walk back to chopping spot
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
            {
                movement.walkTo(homeTile);
            }
            else
            {
                state = State.FIND_TREE;
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
            // Walk toward where we'll find a bank (use homeTile as reference,
            // but since we need a bank we just scan the scene each tick)
            log.debug("Walking to find bank...");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GameObject findTree()
    {
        if (treeNameFilter.isEmpty()) return gameObjects.nearest(TREE_IDS);
        final String filter = treeNameFilter.toLowerCase();
        return gameObjects.nearest(obj -> {
            for (int id : TREE_IDS)
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

    private boolean isActivelyChopping()
    {
        int anim = players.getAnimation();
        for (int a : CHOP_ANIMATIONS) { if (anim == a) return true; }
        return false;
    }
}
