package com.botengine.osrs.scripts.mining;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.util.Progression;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;

/**
 * Mining AFK script.
 *
 * Standard mode state machine:
 *   FIND_ROCK → MINING → (full) → DROPPING or BANKING → FIND_ROCK
 *
 * Motherlode Mine state machine:
 *   FIND_VEIN → MINING_VEIN → DEPOSIT_HOPPER → WAIT_SACK → COLLECT_SACK
 *   → (full sack or banking) → BANKING → FIND_VEIN
 *
 * Game object IDs (Motherlode Mine):
 *   Ore veins       : 26661–26664  (upper floor: 26662, 26663, 26664, 26665)
 *   Hopper          : 26674
 *   Sack            : 26688
 *   Broken strut L  : 26670
 *   Broken strut R  : 26671
 *
 * Item IDs:
 *   Pay-dirt : 12011
 *   Coal sack: 12019 (coal bag — also used in BF)
 */
public class MiningScript extends BotScript
{
    // ── Standard mode rock/ore IDs ────────────────────────────────────────────
    private static final int[] ROCK_IDS = {
        10943, 11960, 11961,
        11933, 11932, 11934,
        11954, 11955, 11956,
        11930, 11931, 17094,
        11957, 11958, 11959,
        11942, 11943, 11944,
        11939, 11940, 11941,
        11963, 11962
    };

    private static final int[] ORE_IDS = {
        436, 438, 440, 453, 444, 447, 449, 451
    };

    private static final int[] MINE_ANIMATIONS = {
        624, 625, 626, 627, 628, 629, 630,
        7139, 7158, 21174
    };

    // ── Motherlode Mine IDs ───────────────────────────────────────────────────
    private static final int[] VEIN_IDS           = { 26661, 26662, 26663, 26664, 26665 };
    private static final int   HOPPER_ID          = 26674;
    private static final int   SACK_ID            = 26688;
    private static final int   BROKEN_STRUT_L_ID  = 26670;
    private static final int   BROKEN_STRUT_R_ID  = 26671;
    private static final int   PAYDIRT_ID         = 12011;

    /**
     * Minimum pay-dirt in inventory before walking to hopper.
     * Ensures we don't waste a hopper trip on a tiny load.
     */
    private static final int HOPPER_TRIP_THRESHOLD = 18;

    private enum State
    {
        // Standard mode
        FIND_ROCK, MINING, DROPPING, BANKING,
        // Motherlode Mine mode
        FIND_VEIN, MINING_VEIN, DEPOSIT_HOPPER, WAIT_SACK, COLLECT_SACK
    }

    private State   state            = State.FIND_ROCK;
    private int     idleTickCount    = 0;
    private int     waitSackTicks    = 0;
    private String  rockNameFilter   = "";
    private boolean bankingMode      = false;
    private boolean shiftDrop        = false;
    private boolean hopOnCompetition = false;
    private boolean motherlodeMode   = false;
    private WorldPoint homeTile;
    private Progression progression  = new Progression("", "");

    @Inject
    public MiningScript() {}

    @Override
    public String getName() { return "Mining"; }

    @Override
    public void configure(BotEngineConfig config)
    {
        rockNameFilter   = config.miningRockName().trim();
        bankingMode      = config.miningBankingMode();
        shiftDrop        = config.miningShiftDrop();
        hopOnCompetition = config.miningHopOnCompetition();
        motherlodeMode   = config.miningMotherlodeMode();
        progression      = new Progression(config.miningProgression(), rockNameFilter);
    }

    @Override
    public void onStart()
    {
        log.info("Started — target='{}' banking={} motherlode={}",
            rockNameFilter.isEmpty() ? "any" : rockNameFilter, bankingMode, motherlodeMode);
        state    = motherlodeMode ? State.FIND_VEIN : State.FIND_ROCK;
        homeTile = players.getLocation();
    }

    @Override
    public void onLoop()
    {
        if (!progression.isEmpty())
        {
            int lvl = client.getRealSkillLevel(net.runelite.api.Skill.MINING);
            rockNameFilter = progression.resolve(lvl);
        }

        if (motherlodeMode)
        {
            switch (state)
            {
                case FIND_VEIN:       findAndMineVein();   break;
                case MINING_VEIN:     checkVeinState();    break;
                case DEPOSIT_HOPPER:  depositHopper();     break;
                case WAIT_SACK:       waitForSack();       break;
                case COLLECT_SACK:    collectSack();       break;
                case BANKING:         handleBanking();     break;
                default: break;
            }
        }
        else
        {
            switch (state)
            {
                case FIND_ROCK: findAndMineRock();  break;
                case MINING:    checkMiningState(); break;
                case DROPPING:  dropOre();          break;
                case BANKING:   handleBanking();    break;
                default: break;
            }
        }
    }

    @Override
    public void onStop() { log.info("Stopped"); }

    // ── Standard mode handlers ────────────────────────────────────────────────

    private void findAndMineRock()
    {
        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        GameObject rock = findRock();
        if (rock == null) { log.debug("No ore rock found — waiting"); return; }

        interaction.click(rock, "Mine");
        antiban.reactionDelay();
        state = State.MINING;
        idleTickCount = 0;
        log.debug("Mining rock id={}", rock.getId());
    }

    private void checkMiningState()
    {
        if (inventory.isFull())
        {
            state = bankingMode ? State.BANKING : State.DROPPING;
            return;
        }

        if (hopOnCompetition && players.nearbyCount(5) > 0)
        {
            log.info("Competition detected — hopping world");
            worldHopper.hopToMembers();
            state = State.FIND_ROCK;
            return;
        }

        if (!isActivelyMining())
        {
            idleTickCount++;
            if (idleTickCount >= 2)
            {
                antiban.randomDelay(200, 600);
                state = State.FIND_ROCK;
                idleTickCount = 0;
            }
        }
        else
        {
            idleTickCount = 0;
        }
    }

    private void dropOre()
    {
        if (shiftDrop)
        {
            interaction.dropAll(ORE_IDS);
            antiban.reactionDelay();
            state = State.FIND_ROCK;
            return;
        }
        boolean droppedAny = false;
        for (int oreId : ORE_IDS)
        {
            if (inventory.contains(oreId))
            {
                interaction.clickInventoryItem(oreId, "Drop");
                antiban.randomDelay(80, 160);
                droppedAny = true;
            }
        }
        if (!droppedAny || inventory.isEmpty()) state = State.FIND_ROCK;
    }

    // ── Motherlode Mine handlers ──────────────────────────────────────────────

    /**
     * Checks for broken struts first (highest priority in MLM),
     * then finds and mines the nearest pay-dirt vein.
     */
    private void findAndMineVein()
    {
        // Repair broken struts — do this immediately when spotted
        if (repairBrokenStruts()) return;

        // If we have enough pay-dirt, head to the hopper
        if (payDirtCount() >= HOPPER_TRIP_THRESHOLD || inventory.isFull())
        {
            log.debug("Inventory has enough pay-dirt — heading to hopper");
            state = State.DEPOSIT_HOPPER;
            return;
        }

        GameObject vein = findVein();
        if (vein == null) { log.debug("No pay-dirt vein found — waiting"); return; }

        interaction.click(vein, "Mine");
        antiban.reactionDelay();
        state = State.MINING_VEIN;
        idleTickCount = 0;
        log.debug("Mining vein id={}", vein.getId());
    }

    /**
     * Monitors active vein mining.
     * Repairs broken struts if spotted, transitions to hopper when inventory has enough.
     */
    private void checkVeinState()
    {
        if (repairBrokenStruts()) return;

        if (payDirtCount() >= HOPPER_TRIP_THRESHOLD || inventory.isFull())
        {
            state = State.DEPOSIT_HOPPER;
            return;
        }

        if (!isActivelyMining())
        {
            idleTickCount++;
            if (idleTickCount >= 2)
            {
                antiban.randomDelay(200, 500);
                state = State.FIND_VEIN;
                idleTickCount = 0;
            }
        }
        else
        {
            idleTickCount = 0;
        }
    }

    /**
     * Walks to the hopper and deposits pay-dirt.
     * Uses "Put-pay-dirt-in" action on the hopper game object.
     */
    private void depositHopper()
    {
        if (!inventory.contains(PAYDIRT_ID))
        {
            log.debug("No pay-dirt to deposit");
            state = State.WAIT_SACK;
            waitSackTicks = 0;
            return;
        }

        GameObject hopper = gameObjects.nearest(HOPPER_ID);
        if (hopper == null)
        {
            log.debug("Hopper not found — waiting");
            return;
        }

        int dirtSlot = inventory.getSlot(PAYDIRT_ID);
        if (dirtSlot == -1) { state = State.WAIT_SACK; return; }

        interaction.useItemOn(PAYDIRT_ID, dirtSlot, hopper);
        antiban.reactionDelay();
        log.debug("Deposited pay-dirt in hopper");

        if (!inventory.contains(PAYDIRT_ID))
        {
            state = State.WAIT_SACK;
            waitSackTicks = 0;
        }
    }

    /**
     * Waits for the sack to fill with processed ores.
     * The sack processes at ~1 ore per 2 ticks. We wait a fixed number of ticks
     * proportional to how many pay-dirt we deposited, then check the sack.
     */
    private void waitForSack()
    {
        waitSackTicks++;
        // After enough ticks, go check/collect the sack
        if (waitSackTicks >= 8)
        {
            state = State.COLLECT_SACK;
            waitSackTicks = 0;
        }
    }

    /**
     * Clicks the sack to collect processed ores into inventory.
     * If inventory gets full during collection, bank before continuing.
     */
    private void collectSack()
    {
        GameObject sack = gameObjects.nearest(SACK_ID);
        if (sack == null)
        {
            log.debug("Sack not found — returning to vein");
            state = State.FIND_VEIN;
            return;
        }

        interaction.click(sack, "Search");
        antiban.reactionDelay();
        log.debug("Collecting from MLM sack");

        if (inventory.isFull())
            state = State.BANKING;
        else
            state = State.FIND_VEIN;
    }

    // ── Banking ───────────────────────────────────────────────────────────────

    private void handleBanking()
    {
        if (bank.isOpen())
        {
            bank.depositAll();
            antiban.reactionDelay();
            bank.close();

            if (homeTile != null && movement.distanceTo(homeTile) > 5)
            {
                movement.setRunning(true);
                movement.walkTo(homeTile);
            }
            else
            {
                state = motherlodeMode ? State.FIND_VEIN : State.FIND_ROCK;
            }
            return;
        }

        if (bank.isNearBank())
            bank.openNearest();
        else
        {
            movement.setRunning(true);
            log.debug("Walking to find bank...");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Checks for broken struts and repairs the nearest one.
     * Returns true if a repair action was fired (caller should return immediately).
     */
    private boolean repairBrokenStruts()
    {
        GameObject strut = gameObjects.nearest(BROKEN_STRUT_L_ID);
        if (strut == null) strut = gameObjects.nearest(BROKEN_STRUT_R_ID);
        if (strut == null) return false;

        interaction.click(strut, "Hammer");
        antiban.reactionDelay();
        log.info("Repairing broken strut id={}", strut.getId());
        return true;
    }

    /** Returns the number of pay-dirt items in the inventory. */
    private int payDirtCount()
    {
        net.runelite.api.ItemContainer inv =
            client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null) return 0;
        int count = 0;
        for (net.runelite.api.Item item : inv.getItems())
        {
            if (item != null && item.getId() == PAYDIRT_ID) count++;
        }
        return count;
    }

    /** Finds the nearest ore vein (MLM) by checking VEIN_IDS. */
    private GameObject findVein()
    {
        return gameObjects.nearest(VEIN_IDS);
    }

    /** Finds the nearest mineable rock for standard mode. */
    private GameObject findRock()
    {
        if (rockNameFilter.isEmpty()) return gameObjects.nearest(ROCK_IDS);
        final String filter = rockNameFilter.toLowerCase();
        return gameObjects.nearest(obj -> {
            for (int id : ROCK_IDS)
            {
                if (obj.getId() == id)
                {
                    ObjectComposition def = client.getObjectDefinition(obj.getId());
                    return def != null && def.getName() != null
                        && def.getName().toLowerCase().contains(filter);
                }
            }
            return false;
        });
    }

    private boolean isActivelyMining()
    {
        int anim = players.getAnimation();
        for (int a : MINE_ANIMATIONS) { if (anim == a) return true; }
        return false;
    }
}
