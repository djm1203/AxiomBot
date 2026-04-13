package com.botengine.osrs.scripts.smithing;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Smithing AFK script.
 *
 * Standard mode state machine:
 *   FIND_ANVIL → USE_BAR → WAIT_DIALOGUE → SMITHING → (done) → BANKING or DONE
 *
 * Blast Furnace mode state machine:
 *   LOAD_COAL_BAG (if using coal bag)
 *   → LOAD_CONVEYOR  (use ore/coal on conveyor belt)
 *   → WAIT_SMELT     (wait for bars to appear on dispenser)
 *   → COLLECT_BARS   (click bar dispenser)
 *   → BANKING        (restock ore/coal)
 *   → loop
 *
 * Game object IDs (Blast Furnace, Keldagrim):
 *   Conveyor belt : 9100
 *   Bar dispenser : 9092
 *   BF coffer     : 10061
 *
 * Item IDs:
 *   Coal bag   : 12019
 *   Coal       : 453
 *   Bars produced depend on ore loaded — detected from inventory after collection.
 */
public class SmithingScript extends BotScript
{
    // ── Standard mode constants ───────────────────────────────────────────────
    private static final int[] BAR_IDS  = { 2349, 2351, 2353, 2359, 2361, 2363 };
    private static final int ANVIL_ID   = 2097;
    private static final int MAKE_ALL_WIDGET =
        net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    // ── Blast Furnace constants ───────────────────────────────────────────────
    private static final int CONVEYOR_BELT_ID = 9100;
    private static final int BAR_DISPENSER_ID = 9092;
    private static final int BF_COFFER_ID     = 10061;
    private static final int COAL_BAG_ID      = 12019;
    private static final int COAL_ID          = 453;

    /** Ore IDs that need to go into the furnace (we load all of them). */
    private static final int[] ORE_IDS = { 436, 438, 440, 444, 447, 449, 451, 453 };

    /**
     * BF coffer refill threshold in coins.
     * Pay 2,500 gp to the Foreman when the coffer drops low.
     * At 60+ Smithing the Foreman fee is waived but the coffer still runs the fans.
     */
    private static final int COFFER_REFILL_AMOUNT = 72_000; // ~30 min at 2.5k/min

    // ── State ─────────────────────────────────────────────────────────────────

    private enum State
    {
        // Standard mode
        FIND_ANVIL, USE_BAR, WAIT_DIALOGUE, SMITHING, BANKING, DONE,
        // Blast Furnace mode
        LOAD_COAL_BAG, LOAD_CONVEYOR, WAIT_SMELT, COLLECT_BARS
    }

    private State state       = State.FIND_ANVIL;
    private int   ticksWaited = 0;
    private int   waitSmeltTicks = 0;
    private boolean bankingMode        = false;
    private boolean blastFurnaceMode   = false;
    private boolean useCoalBag         = false;
    private WorldPoint homeTile;

    @Inject
    public SmithingScript() {}

    @Override
    public String getName() { return "Smithing"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        SmithingSettings s = (scriptSettings instanceof SmithingSettings)
            ? (SmithingSettings) scriptSettings : new SmithingSettings();
        bankingMode      = s.bankingMode;
        blastFurnaceMode = s.blastFurnaceMode;
        useCoalBag       = s.useCoalBag;
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent parent)
    {
        return new SmithingConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        log.info("Started — banking={} blastFurnace={} coalBag={}",
            bankingMode, blastFurnaceMode, useCoalBag);
        if (blastFurnaceMode)
            state = hasOreOrCoal() ? State.LOAD_CONVEYOR : State.BANKING;
        else
            state = State.FIND_ANVIL;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        if (blastFurnaceMode)
        {
            switch (state)
            {
                case LOAD_COAL_BAG:  loadCoalBag();    break;
                case LOAD_CONVEYOR:  loadConveyor();   break;
                case WAIT_SMELT:     waitForSmelt();   break;
                case COLLECT_BARS:   collectBars();    break;
                case BANKING:        handleBanking();  break;
                default: break;
            }
        }
        else
        {
            switch (state)
            {
                case FIND_ANVIL:    findAnvil();        break;
                case USE_BAR:       useBarOnAnvil();    break;
                case WAIT_DIALOGUE: waitForDialogue();  break;
                case SMITHING:      checkProgress();    break;
                case BANKING:       handleBanking();    break;
                case DONE:          log.info("Done");   break;
                default: break;
            }
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── Standard mode handlers ────────────────────────────────────────────────

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

    // ── Blast Furnace handlers ────────────────────────────────────────────────

    /**
     * Fills the coal bag before heading to the conveyor belt.
     * "Fill" right-click option on the coal bag item.
     */
    private void loadCoalBag()
    {
        if (!inventory.contains(COAL_BAG_ID))
        {
            log.debug("No coal bag in inventory — skipping fill");
            state = State.LOAD_CONVEYOR;
            return;
        }
        if (!inventory.contains(COAL_ID))
        {
            log.debug("No coal to fill bag with");
            state = State.LOAD_CONVEYOR;
            return;
        }

        interaction.clickInventoryItem(COAL_BAG_ID, "Fill");
        antiban.reactionDelay();
        state = State.LOAD_CONVEYOR;
        log.debug("Filled coal bag");
    }

    /**
     * Uses ore/coal from inventory on the conveyor belt.
     * This deposits all materials into the Blast Furnace.
     */
    private void loadConveyor()
    {
        if (!hasOreOrCoal())
        {
            log.info("No ore/coal — banking for more");
            state = State.BANKING;
            return;
        }

        GameObject belt = gameObjects.nearest(CONVEYOR_BELT_ID);
        if (belt == null)
        {
            log.debug("Conveyor belt not found — waiting");
            return;
        }

        // Find any ore or coal in inventory to use on the belt
        int oreId = findOre();
        if (oreId == -1)
        {
            // Empty coal bag onto belt if no direct ore
            if (inventory.contains(COAL_BAG_ID))
            {
                interaction.clickInventoryItem(COAL_BAG_ID, "Empty");
                antiban.reactionDelay();
                return;
            }
            state = State.WAIT_SMELT;
            return;
        }

        int oreSlot = inventory.getSlot(oreId);
        if (oreSlot == -1) { state = State.WAIT_SMELT; return; }

        interaction.useItemOn(oreId, oreSlot, belt);
        antiban.reactionDelay();
        log.debug("Used ore id={} on conveyor belt", oreId);

        // After using, check if inventory still has ore; if empty → wait for bars
        if (!hasOreOrCoal())
        {
            state = State.WAIT_SMELT;
            waitSmeltTicks = 0;
        }
    }

    /**
     * Waits for the Blast Furnace to smelt the ore into bars.
     * Typical smelting delay is 2–3 game ticks.
     */
    private void waitForSmelt()
    {
        waitSmeltTicks++;
        log.debug("Waiting for smelt tick {}", waitSmeltTicks);

        if (waitSmeltTicks >= 5)
        {
            state = State.COLLECT_BARS;
            waitSmeltTicks = 0;
        }
    }

    /**
     * Clicks the bar dispenser to collect smelted bars.
     * After collecting, if bars are now in inventory → bank them.
     */
    private void collectBars()
    {
        GameObject dispenser = gameObjects.nearest(BAR_DISPENSER_ID);
        if (dispenser == null)
        {
            log.debug("Bar dispenser not found");
            return;
        }

        interaction.click(dispenser, "Take");
        antiban.reactionDelay();
        log.debug("Collecting bars from dispenser");
        state = State.BANKING;
    }

    /**
     * Standard banking handler used by both modes.
     * For BF mode: deposits bars, withdraws ore/coal, returns to LOAD_CONVEYOR.
     * For standard mode: deposits smithed items, withdraws next bar type.
     */
    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();

            if (blastFurnaceMode)
            {
                // Withdraw ore for next BF run
                for (int oreId : ORE_IDS)
                {
                    if (bank.contains(oreId))
                    {
                        bank.withdraw(oreId, Integer.MAX_VALUE);
                        antiban.reactionDelay();
                        break;
                    }
                }
                // Withdraw coal if using coal bag
                if (useCoalBag && bank.contains(COAL_ID))
                {
                    bank.withdraw(COAL_ID, Integer.MAX_VALUE);
                    antiban.reactionDelay();
                }
            }
            else
            {
                for (int id : BAR_IDS)
                {
                    if (bank.contains(id))
                    {
                        bank.withdraw(id, Integer.MAX_VALUE);
                        antiban.reactionDelay();
                        break;
                    }
                }
            }

            bank.close();

            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
                state = blastFurnaceMode
                    ? (useCoalBag ? State.LOAD_COAL_BAG : State.LOAD_CONVEYOR)
                    : State.FIND_ANVIL;
            return;
        }

        if (bank.isNearBank()) bank.openNearest();
        else { movement.setRunning(true); log.debug("Looking for bank..."); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findBar()
    {
        for (int id : BAR_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }

    private int findOre()
    {
        for (int id : ORE_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }

    private boolean hasOreOrCoal()
    {
        for (int id : ORE_IDS) { if (inventory.contains(id)) return true; }
        return false;
    }
}
