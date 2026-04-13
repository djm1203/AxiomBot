package com.botengine.osrs.scripts.herblore;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;

import javax.inject.Inject;
import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Herblore script — two modes:
 *
 * CLEAN: clicks "Clean" (op=1) on each grimy herb in inventory until empty,
 *   then banks to withdraw more. Cleaning is instant with no animation.
 *
 * MAKE POTION: uses clean herb on vial of water (step 1) to produce an
 *   unfinished potion, then uses the secondary ingredient on the unfinished
 *   potion (step 2). Neither step opens a dialogue — both are instant.
 *   Banks when ingredients run out.
 */
public class HerbloreScript extends BotScript
{
    // ── Herb → unfinished potion ID map ──────────────────────────────────────

    private static final Map<Integer, Integer> HERB_TO_UNF = new HashMap<>();
    static
    {
        HERB_TO_UNF.put(199,  91);   // Guam      → Attack pot (unf)
        HERB_TO_UNF.put(201,  93);   // Marrentill → Antipoison (unf)
        HERB_TO_UNF.put(203,  95);   // Tarromin  → Strength pot (unf)
        HERB_TO_UNF.put(205,  97);   // Harralander → Restore pot (unf)
        HERB_TO_UNF.put(207,  99);   // Ranarr    → Prayer pot (unf)
        HERB_TO_UNF.put(209,  101);  // Irit      → Super attack (unf)
        HERB_TO_UNF.put(211,  103);  // Avantoe   → Super antipoison (unf)
        HERB_TO_UNF.put(213,  105);  // Kwuarm    → Super strength (unf)
        HERB_TO_UNF.put(3051, 3002); // Snapdragon → Super restore (unf)
        HERB_TO_UNF.put(215,  107);  // Cadantine → Super defence (unf)
        HERB_TO_UNF.put(2481, 2483); // Lantadyme → Antifire (unf)
        HERB_TO_UNF.put(217,  109);  // Dwarf Weed → Ranging pot (unf)
        HERB_TO_UNF.put(219,  111);  // Torstol   → Zamorak brew (unf)
    }

    private enum State { CLEANING, MAKE_POTION_STEP1, MAKE_POTION_STEP2, BANKING, DONE }

    private String  mode          = "Clean";
    private int     herbItemId    = 169;   // grimy guam
    private int     cleanHerbId   = 199;   // clean guam
    private int     secondaryId   = 221;   // eye of newt
    private int     vialOfWaterId = 227;
    private State   state         = State.CLEANING;

    @Inject
    public HerbloreScript() {}

    @Override
    public String getName() { return "Herblore"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        if (scriptSettings instanceof HerbloreSettings)
        {
            HerbloreSettings s = (HerbloreSettings) scriptSettings;
            mode          = s.mode;
            herbItemId    = s.herbItemId;
            cleanHerbId   = s.cleanHerbId;
            secondaryId   = s.secondaryId;
            vialOfWaterId = s.vialOfWaterId;
        }
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(JComponent parent)
    {
        return new HerbloreConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        state = "Clean".equals(mode) ? State.CLEANING : State.MAKE_POTION_STEP1;
        log.info("Herblore started — mode={}", mode);
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case CLEANING:           cleanHerb();        break;
            case MAKE_POTION_STEP1:  makePotionStep1();  break;
            case MAKE_POTION_STEP2:  makePotionStep2();  break;
            case BANKING:            handleBanking();    break;
            case DONE:               break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Herblore stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void cleanHerb()
    {
        if (!inventory.contains(herbItemId))
        {
            state = State.BANKING;
            return;
        }
        // Cleaning is instant (op=1 left-click) — fires one per tick
        interaction.clickInventoryItem(herbItemId, "Clean", 1);
        antiban.gaussianDelay(100, 30);
    }

    private void makePotionStep1()
    {
        if (!inventory.contains(cleanHerbId) || !inventory.contains(vialOfWaterId))
        {
            // Check if we can proceed to step 2 with an existing unfinished pot
            int unfPotId = HERB_TO_UNF.getOrDefault(cleanHerbId, -1);
            if (unfPotId > 0 && inventory.contains(unfPotId) && inventory.contains(secondaryId))
            {
                state = State.MAKE_POTION_STEP2;
            }
            else
            {
                state = State.BANKING;
            }
            return;
        }

        int herbSlot = inventory.getSlot(cleanHerbId);
        int vialSlot = inventory.getSlot(vialOfWaterId);
        if (herbSlot < 0 || vialSlot < 0)
        {
            state = State.BANKING;
            return;
        }

        interaction.useItemOnItem(herbSlot, vialSlot);
        antiban.reactionDelay();
        state = State.MAKE_POTION_STEP2;
    }

    private void makePotionStep2()
    {
        int unfPotId = HERB_TO_UNF.getOrDefault(cleanHerbId, -1);
        if (unfPotId < 0)
        {
            log.warn("Unknown unfinished pot for herb {}", cleanHerbId);
            state = State.DONE;
            return;
        }

        if (!inventory.contains(unfPotId) || !inventory.contains(secondaryId))
        {
            // Either step 2 finished or ingredients gone — go back to step 1 or bank
            if (inventory.contains(cleanHerbId) && inventory.contains(vialOfWaterId))
                state = State.MAKE_POTION_STEP1;
            else
                state = State.BANKING;
            return;
        }

        int potSlot = inventory.getSlot(unfPotId);
        int secSlot = inventory.getSlot(secondaryId);
        if (potSlot < 0 || secSlot < 0)
        {
            state = State.BANKING;
            return;
        }

        interaction.useItemOnItem(secSlot, potSlot);
        antiban.reactionDelay();
        // After combining, check next tick — will loop back here until ingredients gone
    }

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();

            if ("Clean".equals(mode))
            {
                if (bank.contains(herbItemId))
                {
                    bank.withdraw(herbItemId, Integer.MAX_VALUE);
                    antiban.reactionDelay();
                }
                else
                {
                    log.info("No grimy herbs left in bank — stopping");
                    bank.close();
                    state = State.DONE;
                    return;
                }
            }
            else
            {
                // Withdraw 14 herbs + 14 vials (fills inventory for 14 potions)
                boolean hasHerbs = bank.contains(cleanHerbId);
                boolean hasVials = bank.contains(vialOfWaterId);
                boolean hasSec   = bank.contains(secondaryId);
                if (!hasHerbs || !hasVials || !hasSec)
                {
                    log.info("Missing potion ingredients in bank — stopping");
                    bank.close();
                    state = State.DONE;
                    return;
                }
                bank.withdraw(cleanHerbId, 14);
                antiban.reactionDelay();
                bank.withdraw(vialOfWaterId, 14);
                antiban.reactionDelay();
                // Secondary is added in step 2 — withdraw enough for the batch
                bank.withdraw(secondaryId, 14);
                antiban.reactionDelay();
            }

            bank.close();
            antiban.reactionDelay();
            state = "Clean".equals(mode) ? State.CLEANING : State.MAKE_POTION_STEP1;
            return;
        }

        if (!bank.isNearBank())
        {
            movement.setRunning(true);
            return;
        }
        bank.openNearest();
    }
}
