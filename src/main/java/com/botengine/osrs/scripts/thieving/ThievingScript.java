package com.botengine.osrs.scripts.thieving;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.swing.*;

/**
 * Thieving script — supports two modes:
 *
 * STALL mode: steals from a market stall repeatedly, dropping loot when
 *   inventory fills unless banking is enabled.
 *
 * PICKPOCKET mode: pickpockets an NPC. Handles the stun animation (ID 424)
 *   by waiting before retrying. Eats food when HP drops below threshold.
 */
public class ThievingScript extends BotScript
{
    private static final int STUN_ANIM_ID   = 424;
    private static final int STALL_COOLDOWN = 5;   // ticks between stall steals
    private static final int STUN_WAIT      = 5;   // ticks to wait after stun

    // Stall mode states
    private enum StallState  { FIND_STALL, STEALING, WAIT_COOLDOWN, DROPPING, BANKING }
    // Pickpocket mode states
    private enum PocketState { FIND_TARGET, PICKPOCKETING, WAIT, EAT, BANKING }

    private String    mode           = "Stall";
    private int       stallId        = 11730;
    private int       targetNpcId    = 1;
    private int       eatThresholdPct = 50;
    private int       foodItemId     = 1993;
    private boolean   bankWhenFull   = false;

    private StallState  stallState  = StallState.FIND_STALL;
    private PocketState pocketState = PocketState.FIND_TARGET;
    private int         ticksWaited = 0;
    private WorldPoint  homeTile;

    @Inject
    public ThievingScript() {}

    @Override
    public String getName() { return "Thieving"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        if (scriptSettings instanceof ThievingSettings)
        {
            ThievingSettings s = (ThievingSettings) scriptSettings;
            mode            = s.mode;
            stallId         = s.stallId;
            targetNpcId     = s.targetNpcId;
            eatThresholdPct = s.eatThresholdPct;
            foodItemId      = s.foodItemId;
            bankWhenFull    = s.bankWhenFull;
        }
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(JComponent parent)
    {
        return new ThievingConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        homeTile    = players.getLocation();
        stallState  = StallState.FIND_STALL;
        pocketState = PocketState.FIND_TARGET;
        ticksWaited = 0;
        log.info("Thieving started — mode={}", mode);
    }

    @Override
    public void onLoop()
    {
        if ("Stall".equals(mode))
            runStallMode();
        else
            runPickpocketMode();
    }

    @Override
    public void onStop()
    {
        log.info("Thieving stopped");
    }

    // ── Stall mode ────────────────────────────────────────────────────────────

    private void runStallMode()
    {
        switch (stallState)
        {
            case FIND_STALL:    findStall();    break;
            case STEALING:      stealFromStall(); break;
            case WAIT_COOLDOWN: waitStallCooldown(); break;
            case DROPPING:      dropLoot();     break;
            case BANKING:       handleBanking(true); break;
        }
    }

    private void findStall()
    {
        if (inventory.isFull())
        {
            stallState = bankWhenFull ? StallState.BANKING : StallState.DROPPING;
            return;
        }
        GameObject stall = gameObjects.nearest(stallId);
        if (stall == null)
        {
            log.debug("Stall not found — waiting");
            return;
        }
        interaction.click(stall, "Steal-from");
        antiban.reactionDelay();
        ticksWaited = 0;
        stallState = StallState.STEALING;
    }

    private void stealFromStall()
    {
        ticksWaited++;
        if (players.isIdle() && ticksWaited >= 2)
        {
            ticksWaited = 0;
            stallState = StallState.WAIT_COOLDOWN;
        }
    }

    private void waitStallCooldown()
    {
        ticksWaited++;
        if (ticksWaited >= STALL_COOLDOWN)
        {
            ticksWaited = 0;
            stallState = inventory.isFull()
                ? (bankWhenFull ? StallState.BANKING : StallState.DROPPING)
                : StallState.FIND_STALL;
        }
    }

    private void dropLoot()
    {
        // Drop everything except food
        net.runelite.api.ItemContainer inv =
            client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null) { stallState = StallState.FIND_STALL; return; }

        for (net.runelite.api.Item item : inv.getItems())
        {
            if (item == null || item.getId() <= 0) continue;
            if (item.getId() == foodItemId) continue;
            interaction.dropItem(item.getId());
        }
        antiban.reactionDelay();
        stallState = StallState.FIND_STALL;
    }

    // ── Pickpocket mode ───────────────────────────────────────────────────────

    private void runPickpocketMode()
    {
        switch (pocketState)
        {
            case FIND_TARGET:    findTarget();    break;
            case PICKPOCKETING:  pickpocketing(); break;
            case WAIT:           waitAfterPick(); break;
            case EAT:            eatFood();       break;
            case BANKING:        handleBanking(false); break;
        }
    }

    private void findTarget()
    {
        if (inventory.isFull() && bankWhenFull)
        {
            pocketState = PocketState.BANKING;
            return;
        }
        NPC target = npcs.nearest(npc -> npc.getId() == targetNpcId);
        if (target == null)
        {
            log.debug("Target NPC {} not found", targetNpcId);
            return;
        }
        boolean clicked = interaction.click(target, "Pickpocket");
        if (clicked)
        {
            antiban.reactionDelay();
            ticksWaited = 0;
            pocketState = PocketState.PICKPOCKETING;
        }
    }

    private void pickpocketing()
    {
        ticksWaited++;
        if (players.isIdle() && ticksWaited >= 2)
        {
            ticksWaited = 0;
            pocketState = PocketState.WAIT;
        }
    }

    private void waitAfterPick()
    {
        // Check for stun
        if (players.getAnimation() == STUN_ANIM_ID)
        {
            ticksWaited++;
            if (ticksWaited < STUN_WAIT) return;
            ticksWaited = 0;
        }

        // Check if we need to eat
        if (players.shouldEat(eatThresholdPct) && inventory.contains(foodItemId))
        {
            pocketState = PocketState.EAT;
            return;
        }

        if (inventory.isFull() && bankWhenFull)
        {
            pocketState = PocketState.BANKING;
            return;
        }

        pocketState = PocketState.FIND_TARGET;
    }

    private void eatFood()
    {
        interaction.clickInventoryItem(foodItemId, "Eat");
        antiban.reactionDelay();
        pocketState = PocketState.WAIT;
    }

    // ── Shared banking handler ────────────────────────────────────────────────

    private void handleBanking(boolean isStallMode)
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            if (!isStallMode && inventory.count(foodItemId) == 0 && bank.contains(foodItemId))
            {
                bank.withdraw(foodItemId, 10);
                antiban.reactionDelay();
            }
            bank.close();
            antiban.reactionDelay();
            if (homeTile != null && movement.distanceTo(homeTile) > 5)
                movement.walkTo(homeTile);
            else
            {
                if (isStallMode) stallState  = StallState.FIND_STALL;
                else             pocketState = PocketState.FIND_TARGET;
            }
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
