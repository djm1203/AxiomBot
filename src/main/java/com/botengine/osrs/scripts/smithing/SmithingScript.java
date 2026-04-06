package com.botengine.osrs.scripts.smithing;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Smithing AFK script.
 *
 * State machine:
 *   FIND_ANVIL → USE_BAR → WAIT_DIALOGUE → SMITHING → (done) → BANKING or DONE
 *
 * Features:
 *   - Banking loop to restock bars
 *   - Make-All dialogue handling
 *   - Configurable item name (via config panel)
 */
public class SmithingScript extends BotScript
{
    private static final int[] BAR_IDS = { 2349, 2351, 2353, 2359, 2361, 2363 };
    private static final int ANVIL_ID  = 2097;

    private static final int MAKE_ALL_WIDGET =
        net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State { FIND_ANVIL, USE_BAR, WAIT_DIALOGUE, SMITHING, BANKING, DONE }

    private State state = State.FIND_ANVIL;
    private int   ticksWaited = 0;
    private boolean bankingMode = false;
    private WorldPoint homeTile;

    @Inject
    public SmithingScript() {}

    @Override
    public String getName() { return "Smithing"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        bankingMode = config.productionBankingMode();
    }

    @Override
    public void onStart()
    {
        log.info("Started — banking={}", bankingMode);
        state    = State.FIND_ANVIL;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_ANVIL:    findAnvil();        break;
            case USE_BAR:       useBarOnAnvil();    break;
            case WAIT_DIALOGUE: waitForDialogue();  break;
            case SMITHING:      checkProgress();    break;
            case BANKING:       handleBanking();    break;
            case DONE:          log.info("Done");   break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAnvil()
    {
        if (findBar() == -1)
        {
            if (bankingMode) { state = State.BANKING; return; }
            state = State.DONE;
            return;
        }
        if (gameObjects.nearest(ANVIL_ID) != null) state = State.USE_BAR;
        else log.debug("No anvil nearby — waiting");
    }

    private void useBarOnAnvil()
    {
        int barId = findBar();
        if (barId == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }

        GameObject anvil = gameObjects.nearest(ANVIL_ID);
        if (anvil == null) { state = State.FIND_ANVIL; return; }

        int barSlot = inventory.getSlot(barId);
        if (barSlot == -1) return;

        interaction.useItemOn(barId, barSlot, anvil);
        antiban.reactionDelay();
        state = State.WAIT_DIALOGUE;
        ticksWaited = 0;
    }

    private void waitForDialogue()
    {
        ticksWaited++;
        Widget makeAll = client.getWidget(270, 14);
        if (makeAll != null && !makeAll.isHidden())
        {
            interaction.clickWidget(MAKE_ALL_WIDGET);
            antiban.reactionDelay();
            state = State.SMITHING;
            return;
        }
        if (ticksWaited > 5) state = State.USE_BAR;
    }

    private void checkProgress()
    {
        if (findBar() == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }
        if (players.isIdle()) { antiban.randomDelay(300, 700); state = State.USE_BAR; }
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            for (int id : BAR_IDS)
            {
                if (bank.contains(id)) { bank.withdraw(id, Integer.MAX_VALUE); antiban.reactionDelay(); break; }
            }
            bank.close();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
                state = State.FIND_ANVIL;
            return;
        }
        if (bank.isNearBank()) bank.openNearest();
        else { movement.setRunning(true); log.debug("Looking for bank..."); }
    }

    private int findBar()
    {
        for (int id : BAR_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }
}
