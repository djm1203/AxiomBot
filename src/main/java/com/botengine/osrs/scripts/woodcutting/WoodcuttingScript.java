package com.botengine.osrs.scripts.woodcutting;

import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;

import javax.inject.Inject;

/**
 * Woodcutting AFK script.
 *
 * State machine:
 *   FIND_TREE → CHOPPING → (inventory full) → DROPPING → FIND_TREE
 *
 * Supports all common tree types by NPC/object ID. Drops logs when inventory
 * is full (power-chopping mode). Does not bank — pure XP-focused loop.
 *
 * Tree IDs (GameObject):
 *   Regular: 1276, 1278, 1294, 1286
 *   Oak:     10820
 *   Willow:  10829, 10831, 10833
 *   Maple:   10832
 *   Yew:     10822, 10823
 *   Magic:   10834, 10835
 */
public class WoodcuttingScript extends BotScript
{
    // Common tree GameObject IDs (regular through magic)
    private static final int[] TREE_IDS = {
        1276, 1278, 1294, 1286,          // Regular trees
        10820,                            // Oak
        10829, 10831, 10833,              // Willow
        10832,                            // Maple
        10822, 10823,                     // Yew
        10834, 10835                      // Magic
    };

    // Log item IDs (all types, for drop-all)
    private static final int[] LOG_IDS = {
        1511,  // Logs
        1521,  // Oak logs
        1519,  // Willow logs
        1517,  // Maple logs
        1515,  // Yew logs
        1513   // Magic logs
    };

    // Animation IDs for woodcutting with various axes
    private static final int[] CHOP_ANIMATIONS = {
        867, 868, 869, 870, 871, 872, 873,  // Bronze–Dragon axe
        2846,                                // Infernal axe
        7251                                 // Crystal axe
    };

    private enum State { FIND_TREE, CHOPPING, DROPPING }

    private State state = State.FIND_TREE;

    @Inject
    public WoodcuttingScript() {}

    @Override
    public String getName() { return "Woodcutting"; }

    @Override
    public void onStart()
    {
        log.info("Started — power-chop mode (drop logs when full)");
        state = State.FIND_TREE;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_TREE:
                findAndChopTree();
                break;

            case CHOPPING:
                checkChoppingState();
                break;

            case DROPPING:
                dropLogs();
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAndChopTree()
    {
        if (inventory.isFull())
        {
            log.debug("Inventory full — dropping logs");
            state = State.DROPPING;
            return;
        }

        GameObject tree = gameObjects.nearest(TREE_IDS);
        if (tree == null)
        {
            log.debug("No tree found nearby — waiting");
            return;
        }

        interaction.click(tree, "Chop down");
        antiban.reactionDelay();
        state = State.CHOPPING;
        log.debug("Chopping tree (id={})", tree.getId());
    }

    private void checkChoppingState()
    {
        if (inventory.isFull())
        {
            state = State.DROPPING;
            return;
        }

        // If player is idle and not animating, tree may have depleted — find another
        if (players.isIdle())
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

        if (!droppedAny || inventory.isEmpty())
        {
            state = State.FIND_TREE;
        }
    }
}
