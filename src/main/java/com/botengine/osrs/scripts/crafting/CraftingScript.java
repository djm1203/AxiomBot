package com.botengine.osrs.scripts.crafting;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Gem Cutting (Crafting) AFK script.
 *
 * State machine:
 *   FIND_CHISEL → CUTTING → WAIT_DIALOGUE → (inventory empty) → BANKING or DONE
 *
 * Features:
 *   - Banking loop to restock uncut gems
 *   - Make-All dialogue handling
 *   - Supports all standard uncut gems
 */
public class CraftingScript extends BotScript
{
    private static final int CHISEL_ID = 1755;

    private static final int[] UNCUT_GEMS = {
        1625, 1627, 1629,  // Opal, Jade, Red topaz (semi-precious)
        1623, 1621, 1619, 1617, 1631, 6571  // Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx
    };

    private static final int MAKE_ALL_WIDGET =
        net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State { FIND_CHISEL, CUTTING, WAIT_DIALOGUE, BANKING, DONE }

    private State state = State.FIND_CHISEL;
    private int ticksWaited = 0;
    private boolean bankingMode = false;
    private WorldPoint homeTile;

    @Inject
    public CraftingScript() {}

    @Override
    public String getName() { return "Gem Cutting"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        CraftingSettings s = (scriptSettings instanceof CraftingSettings)
            ? (CraftingSettings) scriptSettings : new CraftingSettings();
        bankingMode = s.bankingMode;
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent parent)
    {
        return new CraftingConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        log.info("Started — banking={}", bankingMode);
        state    = State.FIND_CHISEL;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_CHISEL:    checkAndStart();     break;
            case CUTTING:        startCutting();      break;
            case WAIT_DIALOGUE:  waitForDialogue();   break;
            case BANKING:        handleBanking();     break;
            case DONE:           log.info("Done");    break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void checkAndStart()
    {
        if (!inventory.contains(CHISEL_ID))
        {
            log.warn("No chisel in inventory — stopping");
            state = State.DONE;
            return;
        }

        int gemId = findUncutGem();
        if (gemId == -1)
        {
            if (bankingMode) { state = State.BANKING; return; }
            log.info("No uncut gems in inventory — done");
            state = State.DONE;
            return;
        }

        state = State.CUTTING;
    }

    private void startCutting()
    {
        int gemId = findUncutGem();
        if (gemId == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }

        int chiselSlot = inventory.getSlot(CHISEL_ID);
        int gemSlot    = inventory.getSlot(gemId);
        if (chiselSlot == -1 || gemSlot == -1) return;

        interaction.useItemOnItem(chiselSlot, gemSlot);
        antiban.reactionDelay();
        state = State.WAIT_DIALOGUE;
        ticksWaited = 0;
        log.debug("Used chisel on gem id={}", gemId);
    }

    private void waitForDialogue()
    {
        ticksWaited++;
        Widget makeAll = client.getWidget(270, 14);
        if (makeAll != null && !makeAll.isHidden())
        {
            interaction.clickWidget(MAKE_ALL_WIDGET);
            antiban.reactionDelay();
            state = State.CUTTING;
            log.debug("Clicked Make All");
            return;
        }
        if (!players.isIdle()) { state = State.CUTTING; return; }
        if (ticksWaited > 5) { log.debug("Dialogue timeout — retrying"); state = State.CUTTING; }
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            for (int id : UNCUT_GEMS)
            {
                if (bank.contains(id)) { bank.withdraw(id, Integer.MAX_VALUE); antiban.reactionDelay(); break; }
            }
            bank.close();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
                state = State.FIND_CHISEL;
            return;
        }
        if (bank.isNearBank()) bank.openNearest();
        else { movement.setRunning(true); log.debug("Looking for bank..."); }
    }

    private int findUncutGem()
    {
        for (int id : UNCUT_GEMS) { if (inventory.contains(id)) return id; }
        return -1;
    }
}
