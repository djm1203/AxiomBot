package com.botengine.osrs.scripts.fletching;

import com.botengine.osrs.script.BotScript;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Fletching AFK script.
 *
 * State machine:
 *   CHECK_INVENTORY → FLETCHING → WAIT_DIALOGUE → FLETCHING_IN_PROGRESS → (inventory empty) → DONE
 *
 * Fletch logs into bows (or strings bows) entirely from inventory.
 * Uses a knife on logs to open the fletching dialogue, then clicks Make-All.
 *
 * Supported log IDs (knife + log → unstrung bow):
 *   1511 — Logs          → Arrow shaft / Shortbow (u)
 *   1521 — Oak logs      → Oak shortbow (u) / longbow (u)
 *   1519 — Willow logs   → Willow shortbow (u)
 *   1517 — Maple logs    → Maple shortbow (u)
 *   1515 — Yew logs      → Yew shortbow (u)
 *   1513 — Magic logs     → Magic shortbow (u)
 *
 * Stringing bows (bow string on unstrung bow) is also supported — the same
 * Make-All dialogue is used, so the same logic applies.
 *
 * Required items:
 *   946 — Knife (for log → bow)
 *   OR
 *   1777 — Bow string (for stringing; use on unstrung bows)
 *
 * The script auto-detects mode:
 *   - If knife + logs present → knife mode
 *   - If bow string + unstrung bows present → string mode
 */
public class FletchingScript extends BotScript
{
    private static final int KNIFE_ID      = 946;
    private static final int BOW_STRING_ID = 1777;

    // Log item IDs
    private static final int[] LOG_IDS = {
        1511, 1521, 1519, 1517, 1515, 1513
    };

    // Unstrung bow IDs (for stringing mode)
    private static final int[] UNSTRUNG_BOW_IDS = {
        50, 48, 54, 58, 62, 66,   // Shortbow (u) through Magic shortbow (u)
        52, 56, 60, 64, 68        // Longbow (u) variants
    };

    // Make-All widget (interface 270, component 14)
    private static final int MAKE_ALL_WIDGET = net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State { CHECK_INVENTORY, FLETCHING, WAIT_DIALOGUE, FLETCHING_IN_PROGRESS, DONE }
    private enum Mode  { KNIFE, STRING, UNKNOWN }

    private State state = State.CHECK_INVENTORY;
    private Mode  mode  = Mode.UNKNOWN;
    private int   ticksWaited = 0;

    @Inject
    public FletchingScript() {}

    @Override
    public String getName() { return "Fletching"; }

    @Override
    public void onStart()
    {
        log.info("Started — auto-detecting knife or stringing mode");
        state = State.CHECK_INVENTORY;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case CHECK_INVENTORY:
                detectMode();
                break;

            case FLETCHING:
                startFletching();
                break;

            case WAIT_DIALOGUE:
                waitForDialogue();
                break;

            case FLETCHING_IN_PROGRESS:
                checkProgress();
                break;

            case DONE:
                log.info("All items fletched — done");
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void detectMode()
    {
        if (inventory.contains(KNIFE_ID) && findLog() != -1)
        {
            mode = Mode.KNIFE;
            log.info("Mode: knife (fletching logs into bows)");
            state = State.FLETCHING;
        }
        else if (inventory.contains(BOW_STRING_ID) && findUnstrungBow() != -1)
        {
            mode = Mode.STRING;
            log.info("Mode: stringing (attaching bow strings)");
            state = State.FLETCHING;
        }
        else
        {
            log.warn("Cannot detect mode — need knife+logs or bow string+unstrung bows");
            state = State.DONE;
        }
    }

    private void startFletching()
    {
        int slot1, slot2;

        if (mode == Mode.KNIFE)
        {
            int logId = findLog();
            if (logId == -1) { state = State.DONE; return; }
            slot1 = inventory.getSlot(KNIFE_ID);
            slot2 = inventory.getSlot(logId);
            log.debug("Using knife on log id={}", logId);
        }
        else // STRING
        {
            int bowId = findUnstrungBow();
            if (bowId == -1) { state = State.DONE; return; }
            slot1 = inventory.getSlot(BOW_STRING_ID);
            slot2 = inventory.getSlot(bowId);
            log.debug("Stringing bow id={}", bowId);
        }

        if (slot1 == -1 || slot2 == -1) return;

        interaction.useItemOnItem(slot1, slot2);
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
            state = State.FLETCHING_IN_PROGRESS;
            log.debug("Clicked Make All");
            return;
        }

        if (ticksWaited > 5)
        {
            log.debug("Dialogue timeout — retrying");
            state = State.FLETCHING;
        }
    }

    private void checkProgress()
    {
        boolean hasWork = mode == Mode.KNIFE ? findLog() != -1 : findUnstrungBow() != -1;
        if (!hasWork)
        {
            state = State.DONE;
            return;
        }

        if (players.isIdle())
        {
            antiban.randomDelay(300, 700);
            state = State.FLETCHING;
        }
    }

    private int findLog()
    {
        for (int id : LOG_IDS)
        {
            if (inventory.contains(id)) return id;
        }
        return -1;
    }

    private int findUnstrungBow()
    {
        for (int id : UNSTRUNG_BOW_IDS)
        {
            if (inventory.contains(id)) return id;
        }
        return -1;
    }
}
