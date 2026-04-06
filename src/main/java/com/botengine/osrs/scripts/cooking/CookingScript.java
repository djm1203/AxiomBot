package com.botengine.osrs.scripts.cooking;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Cooking AFK script.
 *
 * State machine:
 *   FIND_FIRE → USE_FOOD → WAIT_DIALOGUE → COOKING → (done) → BANKING or DONE
 *
 * Features:
 *   - Banking loop to restock raw food
 *   - Make-All dialogue handling
 *   - Fire/range detection
 */
public class CookingScript extends BotScript
{
    private static final int[] RAW_FOOD_IDS = {
        317, 321, 327, 335, 339, 349, 351, 359, 371, 377, 383
    };

    private static final int[] FIRE_RANGE_IDS = {
        26185, 26186,
        114, 2728, 2729, 9682,
        12269
    };

    private static final int MAKE_ALL_WIDGET =
        net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State { FIND_FIRE, USE_FOOD, WAIT_DIALOGUE, COOKING, BANKING, DONE }

    private State state = State.FIND_FIRE;
    private int   ticksWaited = 0;
    private boolean bankingMode = false;
    private WorldPoint homeTile;

    @Inject
    public CookingScript() {}

    @Override
    public String getName() { return "Cooking"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        bankingMode = config.productionBankingMode();
    }

    @Override
    public void onStart()
    {
        log.info("Started — banking={}", bankingMode);
        state    = State.FIND_FIRE;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_FIRE:      findFire();          break;
            case USE_FOOD:       useFoodOnFire();     break;
            case WAIT_DIALOGUE:  waitForDialogue();   break;
            case COOKING:        checkProgress();     break;
            case BANKING:        handleBanking();     break;
            case DONE:           log.info("Done");    break;
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findFire()
    {
        int rawFood = findRawFood();
        if (rawFood == -1)
        {
            if (bankingMode) { state = State.BANKING; return; }
            state = State.DONE;
            return;
        }

        GameObject fire = gameObjects.nearest(FIRE_RANGE_IDS);
        if (fire == null) { log.debug("No fire/range nearby — waiting"); return; }

        state = State.USE_FOOD;
    }

    private void useFoodOnFire()
    {
        int rawFoodId = findRawFood();
        if (rawFoodId == -1) { state = bankingMode ? State.BANKING : State.DONE; return; }

        GameObject fire = gameObjects.nearest(FIRE_RANGE_IDS);
        if (fire == null) { state = State.FIND_FIRE; return; }

        int foodSlot = inventory.getSlot(rawFoodId);
        if (foodSlot == -1) return;

        interaction.useItemOn(rawFoodId, foodSlot, fire);
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
            state = State.COOKING;
            return;
        }
        if (ticksWaited > 5) state = State.USE_FOOD;
    }

    private void checkProgress()
    {
        if (findRawFood() == -1)
        {
            state = bankingMode ? State.BANKING : State.DONE;
            return;
        }
        if (players.isIdle()) { antiban.randomDelay(300, 700); state = State.USE_FOOD; }
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            // Withdraw raw food (try each type)
            for (int id : RAW_FOOD_IDS)
            {
                if (bank.contains(id))
                {
                    bank.withdraw(id, Integer.MAX_VALUE);
                    antiban.reactionDelay();
                    break;
                }
            }
            bank.close();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
                state = State.FIND_FIRE;
            return;
        }

        if (bank.isNearBank()) bank.openNearest();
        else { movement.setRunning(true); log.debug("Looking for bank..."); }
    }

    private int findRawFood()
    {
        for (int id : RAW_FOOD_IDS) { if (inventory.contains(id)) return id; }
        return -1;
    }
}
