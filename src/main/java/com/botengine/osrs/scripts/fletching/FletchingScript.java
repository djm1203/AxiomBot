package com.botengine.osrs.scripts.fletching;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Fletching AFK script.
 *
 * State machine:
 *   DETECT → FLETCHING → WAIT_DIALOGUE → IN_PROGRESS → (done) → BANKING or DONE
 *
 * Features:
 *   - Auto-detects knife+logs (bow cutting) or bow string+unstrung bows (stringing)
 *   - Make-All dialogue handling
 *   - Banking loop to restock materials
 */
public class FletchingScript extends BotScript
{
    private static final int KNIFE_ID      = 946;
    private static final int BOW_STRING_ID = 1777;

    private static final int[] LOG_IDS = { 1511, 1521, 1519, 1517, 1515, 1513 };
    private static final int[] UNSTRUNG_BOW_IDS = {
        50, 48, 54, 58, 62, 66, 52, 56, 60, 64, 68
    };
    private static final int[] DART_TIP_IDS = {
        819, 820, 821, 822, 823, 824, 3093, 11232 // bronze through dragon + black + dragon
    };
    private static final int[] BOLT_TIP_IDS = {
        9375, 9376, 9377, 9378, 9379, 9380, 9381 // opal through dragonstone bolt tips
    };
    private static final int FEATHER_ID = 314;

    private static final int MAKE_ALL_WIDGET =
        net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State  { DETECT, FLETCHING, WAIT_DIALOGUE, IN_PROGRESS, BANKING, DONE }
    private enum Mode   { KNIFE, STRING, DARTS, BOLTS, UNKNOWN }

    private State state = State.DETECT;
    private Mode  mode  = Mode.UNKNOWN;
    private int   ticksWaited = 0;
    private boolean bankingMode = false;
    private WorldPoint homeTile;

    @Inject
    public FletchingScript() {}

    @Override
    public String getName() { return "Fletching"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        FletchingSettings s = (scriptSettings instanceof FletchingSettings)
            ? (FletchingSettings) scriptSettings : new FletchingSettings();
        bankingMode = s.bankingMode;
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent parent)
    {
        return new FletchingConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        log.info("Started — banking={}", bankingMode);
        state    = State.DETECT;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case DETECT:       detectMode();       break;
            case FLETCHING:    startFletching();   break;
            case WAIT_DIALOGUE:waitForDialogue();  break;
            case IN_PROGRESS:  checkProgress();    break;
            case BANKING:      handleBanking();    break;
            case DONE:         log.info("Done");   break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void detectMode()
    {
        if (inventory.contains(KNIFE_ID) && findLog() != -1)
        {
            mode = Mode.KNIFE;
            log.info("Mode: knife (cutting logs into bows)");
            state = State.FLETCHING;
        }
        else if (inventory.contains(BOW_STRING_ID) && findUnstrungBow() != -1)
        {
            mode = Mode.STRING;
            log.info("Mode: stringing (attaching bow strings)");
            state = State.FLETCHING;
        }
        else if (inventory.contains(FEATHER_ID) && findDartTip() != -1)
        {
            mode = Mode.DARTS;
            log.info("Mode: darts (combining dart tips + feathers)");
            state = State.FLETCHING;
        }
        else if (inventory.contains(FEATHER_ID) && findBoltTip() != -1)
        {
            mode = Mode.BOLTS;
            log.info("Mode: bolts (combining bolt tips + feathers)");
            state = State.FLETCHING;
        }
        else
        {
            if (bankingMode) { state = State.BANKING; return; }
            log.warn("No materials — need knife+logs or string+unstrung bows");
            state = State.DONE;
        }
    }

    private void startFletching()
    {
        if (mode == Mode.DARTS || mode == Mode.BOLTS)
        {
            int tipId = (mode == Mode.DARTS) ? findDartTip() : findBoltTip();
            if (tipId == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }
            int featherSlot = inventory.getSlot(FEATHER_ID);
            int tipSlot     = inventory.getSlot(tipId);
            if (featherSlot == -1 || tipSlot == -1) return;
            // No dialogue — combines immediately each tick
            interaction.useItemOnItem(featherSlot, tipSlot);
            antiban.reactionDelay();
            state = State.IN_PROGRESS; // will check progress next tick
            ticksWaited = 0;
            return;
        }

        int slot1, slot2;
        if (mode == Mode.KNIFE)
        {
            int logId = findLog();
            if (logId == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }
            slot1 = inventory.getSlot(KNIFE_ID);
            slot2 = inventory.getSlot(logId);
        }
        else
        {
            int bowId = findUnstrungBow();
            if (bowId == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }
            slot1 = inventory.getSlot(BOW_STRING_ID);
            slot2 = inventory.getSlot(bowId);
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
            state = State.IN_PROGRESS;
            return;
        }
        if (ticksWaited > 5) state = State.FLETCHING;
    }

    private void checkProgress()
    {
        boolean hasWork;
        switch (mode)
        {
            case KNIFE:  hasWork = findLog() != -1;         break;
            case STRING: hasWork = findUnstrungBow() != -1; break;
            case DARTS:  hasWork = findDartTip() != -1;     break;
            case BOLTS:  hasWork = findBoltTip() != -1;     break;
            default:     hasWork = false;
        }
        if (!hasWork) { state = bankingMode ? State.BANKING : State.DONE; return; }
        if (players.isIdle()) { antiban.randomDelay(300, 700); state = State.FLETCHING; }
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            if (mode == Mode.KNIFE || mode == Mode.UNKNOWN)
            {
                for (int id : LOG_IDS) { if (bank.contains(id)) { bank.withdraw(id, Integer.MAX_VALUE); break; } }
            }
            else if (mode == Mode.STRING)
            {
                for (int id : UNSTRUNG_BOW_IDS) { if (bank.contains(id)) { bank.withdraw(id, Integer.MAX_VALUE); break; } }
                if (bank.contains(BOW_STRING_ID)) bank.withdraw(BOW_STRING_ID, Integer.MAX_VALUE);
            }
            else if (mode == Mode.DARTS)
            {
                for (int id : DART_TIP_IDS) { if (bank.contains(id)) { bank.withdraw(id, Integer.MAX_VALUE); break; } }
                if (bank.contains(FEATHER_ID)) bank.withdraw(FEATHER_ID, Integer.MAX_VALUE);
            }
            else if (mode == Mode.BOLTS)
            {
                for (int id : BOLT_TIP_IDS) { if (bank.contains(id)) { bank.withdraw(id, Integer.MAX_VALUE); break; } }
                if (bank.contains(FEATHER_ID)) bank.withdraw(FEATHER_ID, Integer.MAX_VALUE);
            }
            antiban.reactionDelay();
            bank.close();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
                state = State.DETECT;
            return;
        }
        if (bank.isNearBank()) bank.openNearest();
        else { movement.setRunning(true); log.debug("Looking for bank..."); }
    }

    private int findLog()
    {
        for (int id : LOG_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }

    private int findUnstrungBow()
    {
        for (int id : UNSTRUNG_BOW_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }

    private int findDartTip()
    {
        for (int id : DART_TIP_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }

    private int findBoltTip()
    {
        for (int id : BOLT_TIP_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }
}
