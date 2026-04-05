package com.botengine.osrs.scripts.smithing;

import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Smithing AFK script.
 *
 * State machine:
 *   FIND_ANVIL → SMITHING → WAIT_DIALOGUE → SMITHING_IN_PROGRESS → (bars gone) → DONE
 *
 * Smiths all bars in inventory into the selected item type using an anvil.
 * Opens the smithing dialogue and clicks Make-All to process the full inventory.
 *
 * Common bar item IDs:
 *   2349 — Bronze bar        2351 — Iron bar
 *   2353 — Steel bar         2359 — Mithril bar
 *   2361 — Adamantite bar    2363 — Runite bar
 *   9378 — Necronium bar     (for Runescape 3 compatibility — not used)
 *
 * Anvil game object IDs:
 *   2097 — Standard anvil
 *
 * Smithing interface widget IDs:
 *   Interface 312 = Smithing menu. Component varies by item being smithed.
 *   This script clicks "Smith All" using the Make-X widget (interface 270).
 *   The production dialogue Make-All button is at component 270/14.
 */
public class SmithingScript extends BotScript
{
    // All metal bars
    private static final int[] BAR_IDS = {
        2349, 2351, 2353, 2359, 2361, 2363
    };

    // Standard anvil game object ID
    private static final int ANVIL_ID = 2097;

    // Make-All button on the smithing production interface (interface 270)
    private static final int MAKE_ALL_WIDGET = net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State { FIND_ANVIL, SMITHING, WAIT_DIALOGUE, SMITHING_IN_PROGRESS, DONE }

    private State state = State.FIND_ANVIL;
    private int ticksWaited = 0;

    @Inject
    public SmithingScript() {}

    @Override
    public String getName() { return "Smithing"; }

    @Override
    public void onStart()
    {
        log.info("Started — smithing all bars in inventory");
        state = State.FIND_ANVIL;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_ANVIL:
                findAnvil();
                break;

            case SMITHING:
                useBarOnAnvil();
                break;

            case WAIT_DIALOGUE:
                waitForSmithDialogue();
                break;

            case SMITHING_IN_PROGRESS:
                checkSmithingProgress();
                break;

            case DONE:
                log.info("All bars smithed — done");
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findAnvil()
    {
        int barId = findBar();
        if (barId == -1)
        {
            log.info("No bars in inventory — done");
            state = State.DONE;
            return;
        }

        GameObject anvil = gameObjects.nearest(ANVIL_ID);
        if (anvil == null)
        {
            log.debug("No anvil nearby — waiting");
            return;
        }

        state = State.SMITHING;
    }

    private void useBarOnAnvil()
    {
        int barId = findBar();
        if (barId == -1)
        {
            state = State.DONE;
            return;
        }

        GameObject anvil = gameObjects.nearest(ANVIL_ID);
        if (anvil == null)
        {
            state = State.FIND_ANVIL;
            return;
        }

        int barSlot = inventory.getSlot(barId);
        if (barSlot == -1) return;

        // Use bar on anvil to open smithing production menu
        interaction.useItemOn(barId, barSlot, anvil);
        antiban.reactionDelay();
        state = State.WAIT_DIALOGUE;
        ticksWaited = 0;
        log.debug("Used bar id={} on anvil", barId);
    }

    private void waitForSmithDialogue()
    {
        ticksWaited++;

        // Production interface "Make All" button
        Widget makeAll = client.getWidget(270, 14);
        if (makeAll != null && !makeAll.isHidden())
        {
            interaction.clickWidget(MAKE_ALL_WIDGET);
            antiban.reactionDelay();
            state = State.SMITHING_IN_PROGRESS;
            log.debug("Clicked Make All on smithing dialogue");
            return;
        }

        if (ticksWaited > 5)
        {
            log.debug("Smithing dialogue timeout — retrying");
            state = State.SMITHING;
        }
    }

    private void checkSmithingProgress()
    {
        int barId = findBar();
        if (barId == -1)
        {
            state = State.DONE;
            return;
        }

        // If player has gone idle but bars remain, dialogue may have closed
        if (players.isIdle())
        {
            antiban.randomDelay(300, 700);
            state = State.SMITHING;
        }
    }

    private int findBar()
    {
        for (int id : BAR_IDS)
        {
            if (inventory.contains(id)) return id;
        }
        return -1;
    }
}
