package com.botengine.osrs.scripts.crafting;

import com.botengine.osrs.script.BotScript;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Gem Cutting (Crafting) AFK script.
 *
 * State machine:
 *   FIND_CHISEL → CUTTING → (inventory empty of gems) → STOPPED
 *
 * Cuts all uncut gems found in the inventory using a chisel.
 * Operates entirely from inventory — no banking (bank trips would be
 * handled by a subclass or a banking-capable variant).
 *
 * Supported uncut gems → cut gem pairs:
 *   Uncut opal    (1625) → Opal    (1609)
 *   Uncut jade    (1627) → Jade    (1611)
 *   Uncut red topaz (1629) → Red topaz (1613)
 *   Uncut sapphire (1623) → Sapphire (1607)
 *   Uncut emerald (1621) → Emerald (1605)
 *   Uncut ruby    (1619) → Ruby    (1603)
 *   Uncut diamond (1617) → Diamond (1601)
 *   Uncut dragonstone (1631) → Dragonstone (1615)
 *   Uncut onyx    (6571) → Onyx    (6573)
 *
 * The script uses the chisel on the first uncut gem in inventory via
 * useItemOnItem, which opens the Make-X dialogue; then it clicks "Make All"
 * via the production dialogue widget.
 */
public class CraftingScript extends BotScript
{
    private static final int CHISEL_ID = 1755;

    // Uncut gem IDs — script will look for any of these
    private static final int[] UNCUT_GEMS = {
        1625, 1627, 1629,  // Opal, Jade, Red topaz (semi-precious)
        1623, 1621, 1619, 1617, 1631, 6571  // Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx
    };

    // Production interface widget IDs for "Make All" button
    // Interface 270 = standard Make-X dialogue, component 14 = Make All button
    private static final int MAKE_ALL_WIDGET    = net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);
    // Make-X confirm button (for when quantity dialogue shows)
    private static final int MAKE_X_CONFIRM     = net.runelite.api.widgets.WidgetUtil.packComponentId(584, 6);

    private enum State { FIND_CHISEL, CUTTING, WAIT_DIALOGUE, DONE }

    private State state = State.FIND_CHISEL;
    private int ticksWaited = 0;

    @Inject
    public CraftingScript() {}

    @Override
    public String getName() { return "Gem Cutting"; }

    @Override
    public void onStart()
    {
        log.info("Started — cutting all uncut gems in inventory");
        state = State.FIND_CHISEL;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_CHISEL:
                checkAndStart();
                break;

            case CUTTING:
                startCutting();
                break;

            case WAIT_DIALOGUE:
                waitForDialogue();
                break;

            case DONE:
                log.info("All gems cut — done");
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void checkAndStart()
    {
        if (!inventory.contains(CHISEL_ID))
        {
            log.warn("No chisel in inventory — stopping");
            return;
        }

        int gemId = findUncutGem();
        if (gemId == -1)
        {
            log.info("No uncut gems in inventory — done");
            state = State.DONE;
            return;
        }

        state = State.CUTTING;
    }

    private void startCutting()
    {
        int gemId = findUncutGem();
        if (gemId == -1)
        {
            state = State.DONE;
            return;
        }

        // Use chisel on gem to open production dialogue
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

        // Try Make All button (interface 270)
        Widget makeAll = client.getWidget(270, 14);
        if (makeAll != null && !makeAll.isHidden())
        {
            interaction.clickWidget(MAKE_ALL_WIDGET);
            antiban.reactionDelay();
            state = State.CUTTING;
            log.debug("Clicked Make All");
            return;
        }

        // Fallback: if player animates, we're already cutting
        if (!players.isIdle())
        {
            state = State.CUTTING;
            return;
        }

        // Timeout after ~5 ticks (3 seconds)
        if (ticksWaited > 5)
        {
            log.debug("Dialogue timeout — retrying");
            state = State.CUTTING;
        }
    }

    private int findUncutGem()
    {
        for (int id : UNCUT_GEMS)
        {
            if (inventory.contains(id)) return id;
        }
        return -1;
    }
}
