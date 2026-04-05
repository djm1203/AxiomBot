package com.botengine.osrs.scripts.cooking;

import com.botengine.osrs.script.BotScript;
import net.runelite.api.GameObject;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Cooking AFK script.
 *
 * State machine:
 *   FIND_FIRE → COOKING → WAIT_DIALOGUE → COOKING_IN_PROGRESS → (inventory empty) → DONE
 *
 * Cooks all raw fish/food in inventory on a fire or range.
 * Uses the Make-All production dialogue to cook the entire inventory at once.
 * Does not bank — intended for use after a fishing run or with pre-stocked inventory.
 *
 * Supported raw food item IDs:
 *   317 — Raw shrimps       321 — Raw anchovies
 *   327 — Raw sardine       335 — Raw herring
 *   339 — Raw pike          349 — Raw salmon
 *   351 — Raw trout         359 — Raw tuna
 *   371 — Raw swordfish     377 — Raw lobster
 *   383 — Raw shark
 *
 * Fire/Range GameObjects:
 *   Fires:    26185, 26186 (player-made fires)
 *   Ranges:   114 (Lumbridge), 9682 (various)
 */
public class CookingScript extends BotScript
{
    // Raw food item IDs to cook
    private static final int[] RAW_FOOD_IDS = {
        317, 321, 327, 335, 339,  // Shrimps, Anchovies, Sardine, Herring, Pike
        349, 351, 359, 371, 377, 383  // Salmon, Trout, Tuna, Swordfish, Lobster, Shark
    };

    // Fire / Range game object IDs
    private static final int[] FIRE_RANGE_IDS = {
        26185, 26186,   // Player fires
        114, 2728, 2729, 9682,  // Various ranges/fireplaces
        12269           // Lumbridge range (no-burn at high level)
    };

    // Cooking interface widget (interface 270 = Make-X, component 14 = Make All)
    private static final int MAKE_ALL_WIDGET = net.runelite.api.widgets.WidgetUtil.packComponentId(270, 14);

    private enum State { FIND_FIRE, COOKING, WAIT_DIALOGUE, COOKING_IN_PROGRESS, DONE }

    private State state = State.FIND_FIRE;
    private int ticksWaited = 0;

    @Inject
    public CookingScript() {}

    @Override
    public String getName() { return "Cooking"; }

    @Override
    public void onStart()
    {
        log.info("Started — cooking all raw food in inventory");
        state = State.FIND_FIRE;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_FIRE:
                findFireAndUseFood();
                break;

            case COOKING:
                useFoodOnFire();
                break;

            case WAIT_DIALOGUE:
                waitForCookDialogue();
                break;

            case COOKING_IN_PROGRESS:
                checkCookingProgress();
                break;

            case DONE:
                log.info("All food cooked — done");
                break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findFireAndUseFood()
    {
        int rawFood = findRawFood();
        if (rawFood == -1)
        {
            log.info("No raw food in inventory — done");
            state = State.DONE;
            return;
        }

        GameObject fire = gameObjects.nearest(FIRE_RANGE_IDS);
        if (fire == null)
        {
            log.debug("No fire/range nearby — waiting");
            return;
        }

        state = State.COOKING;
    }

    private void useFoodOnFire()
    {
        int rawFoodId = findRawFood();
        if (rawFoodId == -1)
        {
            state = State.DONE;
            return;
        }

        GameObject fire = gameObjects.nearest(FIRE_RANGE_IDS);
        if (fire == null)
        {
            state = State.FIND_FIRE;
            return;
        }

        int foodSlot = inventory.getSlot(rawFoodId);
        if (foodSlot == -1) return;

        // Use raw food item on fire/range to open cooking dialogue
        interaction.useItemOn(rawFoodId, foodSlot, fire);
        antiban.reactionDelay();
        state = State.WAIT_DIALOGUE;
        ticksWaited = 0;
        log.debug("Used raw food id={} on fire id={}", rawFoodId, fire.getId());
    }

    private void waitForCookDialogue()
    {
        ticksWaited++;

        // Check for Make-All button (interface 270)
        Widget makeAll = client.getWidget(270, 14);
        if (makeAll != null && !makeAll.isHidden())
        {
            interaction.clickWidget(MAKE_ALL_WIDGET);
            antiban.reactionDelay();
            state = State.COOKING_IN_PROGRESS;
            log.debug("Clicked Make All on cooking dialogue");
            return;
        }

        if (ticksWaited > 5)
        {
            log.debug("Cooking dialogue timeout — retrying");
            state = State.COOKING;
        }
    }

    private void checkCookingProgress()
    {
        int rawFood = findRawFood();
        if (rawFood == -1)
        {
            state = State.DONE;
            return;
        }

        // If player stopped animating but still has raw food, dialogue closed early
        if (players.isIdle())
        {
            antiban.randomDelay(300, 700);
            state = State.COOKING;
        }
    }

    private int findRawFood()
    {
        for (int id : RAW_FOOD_IDS)
        {
            if (inventory.contains(id)) return id;
        }
        return -1;
    }
}
