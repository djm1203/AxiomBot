package com.axiom.scripts.woodcutting;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.player.Skills;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;

/**
 * Axiom Woodcutting — validation script for the multi-module architecture.
 *
 * State machine:
 *   FIND_TREE → CHOPPING → FULL (→ BANKING or DROPPING) → FIND_TREE
 *
 * API fields are injected by ScriptRunner.injectApis() before onStart().
 * Zero RuneLite imports — axiom-api only.
 */
@ScriptManifest(
    name        = "Axiom Woodcutting",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Chops trees and optionally banks or drops logs."
)
public class WoodcuttingScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private GameObjects gameObjects;
    private Inventory   inventory;
    private Skills      skills;
    private Bank        bank;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private WoodcuttingSettings settings;

    // ── State ─────────────────────────────────────────────────────────────────
    private enum State { FIND_TREE, CHOPPING, FULL, BANKING, DROPPING }
    private State state = State.FIND_TREE;

    // ── Log item IDs for power-chop drop ─────────────────────────────────────
    private static final int[] ALL_LOG_IDS = {
        1511, // Logs (normal)
        1521, // Oak logs
        1519, // Willow logs
        1517, // Maple logs
        1515, // Yew logs
        1513, // Magic logs
        6333, // Teak logs
        6332, // Mahogany logs
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Woodcutting"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        if (raw instanceof WoodcuttingSettings)
        {
            this.settings = (WoodcuttingSettings) raw;
        }
        else
        {
            this.settings = WoodcuttingSettings.defaults();
        }

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        log.info("Woodcutting started: tree=" + settings.treeType.name()
            + " powerChop=" + settings.powerChop);

        state = State.FIND_TREE;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_TREE: findTree();   break;
            case CHOPPING:  checkChop();  break;
            case FULL:      handleFull(); break;
            case BANKING:   handleBank(); break;
            case DROPPING:  dropLogs();   break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Woodcutting stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findTree()
    {
        if (inventory.isFull())
        {
            state = State.FULL;
            return;
        }

        WoodcuttingSettings.TreeType tree = settings.treeType;
        SceneObject treeObj = gameObjects.nearest(
            o -> o.getId() == tree.objectId
              || o.getName().equalsIgnoreCase(tree.objectName));

        if (treeObj == null)
        {
            log.info("No " + tree.objectName + " found nearby — waiting");
            setTickDelay(3);
            return;
        }

        treeObj.interact("Chop down");
        state = State.CHOPPING;

        // Human reaction delay before animation registers
        int reactionTicks = antiban.reactionTicks();
        if (reactionTicks > 0) setTickDelay(reactionTicks);
    }

    private void checkChop()
    {
        if (inventory.isFull())
        {
            state = State.FULL;
            return;
        }

        WoodcuttingSettings.TreeType tree = settings.treeType;
        SceneObject treeObj = gameObjects.nearest(
            o -> o.getId() == tree.objectId
              || o.getName().equalsIgnoreCase(tree.objectName));

        if (treeObj == null)
        {
            // Tree depleted — idle a moment then find the next one
            setTickDelay(antiban.randomIdleTicks(1, 3));
            state = State.FIND_TREE;
        }
        // else still chopping — stay in CHOPPING state and wait
    }

    private void handleFull()
    {
        if (settings.powerChop || settings.bankAction == WoodcuttingSettings.BankAction.DROP_LOGS)
        {
            state = State.DROPPING;
        }
        else
        {
            state = State.BANKING;
        }
    }

    private void handleBank()
    {
        if (!bank.isOpen())
        {
            if (bank.isNearBank())
            {
                bank.openNearest();
                setTickDelay(2);
            }
            else
            {
                log.info("Not near a bank — dropping logs instead");
                state = State.DROPPING;
            }
            return;
        }

        bank.depositAll();
        bank.close();
        setTickDelay(2);
        state = State.FIND_TREE;
    }

    private void dropLogs()
    {
        for (int logId : ALL_LOG_IDS)
        {
            if (inventory.contains(logId))
            {
                inventory.dropAll(logId);
            }
        }
        setTickDelay(1);
        state = State.FIND_TREE;
    }
}
