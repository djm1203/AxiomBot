package com.axiom.scripts.mining;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.world.Bank;
import com.axiom.api.world.Movement;
import com.axiom.scripts.mining.MiningSettings.MiningAction;
import com.axiom.scripts.mining.MiningSettings.OreType;

/**
 * Axiom Mining — finds rocks, mines ore, then banks or drops.
 *
 * Structurally identical to Woodcutting: rock depletes instead of falling,
 * but the animation-tracking loop is the same.
 *
 * State machine:
 *   FIND_ROCK → MINING → FULL → DROPPING / BANKING → FIND_ROCK
 *
 * Rock depletion detection:
 *   Tick N:   interact("Mine") fires → state = MINING, wasActing = false
 *   First tick with isAnimating() == true → wasActing = true
 *   First tick with isAnimating() == false AFTER wasActing == true → rock depleted → FIND_ROCK
 *   If animation never starts within ANIMATION_TIMEOUT_TICKS → timeout → FIND_ROCK
 *
 * Banking:
 *   depositAllExcept(PICKAXE_IDS) — pickaxe in inventory is always protected.
 *   Pickaxe equipped in weapon slot is not in inventory, so only inventory pickaxes
 *   need protection — equipping handles itself.
 */
@ScriptManifest(
    name        = "Axiom Mining",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Mines ores and optionally banks or drops them."
)
public class MiningScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private GameObjects gameObjects;
    private Players     players;
    private Inventory   inventory;
    private Bank        bank;
    private Movement    movement;
    private Antiban     antiban;
    private Log         log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private MiningSettings settings;

    // ── Start location — recorded on first game tick, used to walk back after banking ──
    private boolean startTileRecorded = false;
    private int     startX, startY;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { FIND_ROCK, MINING, FULL, DROPPING, BANKING }
    private State state = State.FIND_ROCK;

    // Animation tracking
    private boolean wasActing        = false;
    private int     noAnimationTicks = 0;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;

    // Banking state
    private boolean bankJustOpened  = false;
    private boolean bankDepositDone = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Mining"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof MiningSettings)
            ? (MiningSettings) raw
            : MiningSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        startTileRecorded = false;

        log.info("Mining started: ore={} action={} powerMine={}",
            settings.oreType.oreName, settings.action.displayName, settings.powerMine);

        state = State.FIND_ROCK;
    }

    @Override
    public void onLoop()
    {
        // Record start tile on the first game tick — onStart() runs on the Swing EDT
        // so RuneLite API calls there will crash with AssertionError.
        if (!startTileRecorded)
        {
            startX = players.getWorldX();
            startY = players.getWorldY();
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{})", startX, startY);
        }

        switch (state)
        {
            case FIND_ROCK: handleFindRock(); break;
            case MINING:    handleMining();   break;
            case FULL:      handleFull();     break;
            case DROPPING:  handleDropping(); break;
            case BANKING:   handleBanking();  break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Mining stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleFindRock()
    {
        if (inventory.isFull())
        {
            log.info("[FIND_ROCK] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        SceneObject rock = gameObjects.nearest(settings.oreType.rockIds);
        if (rock == null)
        {
            log.info("[FIND_ROCK] No {} rock found nearby — waiting 3 ticks",
                settings.oreType.oreName);
            setTickDelay(3);
            return;
        }

        log.info("[FIND_ROCK] Found {} (id={}) at ({},{}) — clicking Mine",
            settings.oreType.oreName, rock.getId(), rock.getWorldX(), rock.getWorldY());
        rock.interact("Mine");
        wasActing        = false;
        noAnimationTicks = 0;
        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        log.debug("[FIND_ROCK] Reaction delay: {} ticks", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.MINING;
    }

    private void handleMining()
    {
        if (inventory.isFull())
        {
            log.info("[MINING] Inventory full — transitioning to FULL");
            state = State.FULL;
            return;
        }

        if (players.isAnimating())
        {
            if (!wasActing) log.info("[MINING] Mining animation started");
            else            log.debug("[MINING] Still mining...");
            wasActing        = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasActing)
        {
            log.info("[MINING] Rock depleted — transitioning to FIND_ROCK");
            wasActing        = false;
            noAnimationTicks = 0;
            state            = State.FIND_ROCK;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.info("[MINING] Animation timeout ({} ticks) — retrying", ANIMATION_TIMEOUT_TICKS);
            wasActing        = false;
            noAnimationTicks = 0;
            state            = State.FIND_ROCK;
        }
        else
        {
            log.debug("[MINING] Waiting for animation ({}/{})", noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
        }
    }

    private void handleFull()
    {
        if (settings.action == MiningAction.DROP || settings.powerMine)
        {
            log.info("[FULL] Drop mode — transitioning to DROPPING");
            state = State.DROPPING;
        }
        else
        {
            log.info("[FULL] Bank mode — transitioning to BANKING");
            state = State.BANKING;
        }
    }

    private void handleDropping()
    {
        log.info("[DROPPING] Dropping {} (id={})", settings.oreType.oreName, settings.oreType.oreId);
        inventory.dropAll(settings.oreType.oreId);
        setTickDelay(1);
        state = State.FIND_ROCK;
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            resetBankState();

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.info("[BANKING] Could not open bank after {} attempts — stopping",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                stop();
                return;
            }

            if (bank.openNearest())
            {
                log.info("[BANKING] Clicked bank (attempt {}/{})",
                    bankOpenAttempts + 1, MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts++;
                setTickDelay(3);
            }
            else
            {
                log.debug("[BANKING] Walking to bank...");
                setTickDelay(2);
            }
            return;
        }

        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        if (!bankDepositDone)
        {
            if (bank.depositAllExcept(OreType.PICKAXE_IDS))
            {
                log.debug("[BANKING] Depositing items...");
                setTickDelay(1);
                return;
            }
            log.info("[BANKING] Deposit complete (pickaxe protected)");
            bankDepositDone = true;
        }

        log.info("[BANKING] Closing bank");
        bank.close();
        resetBankState();

        int distToStart = players.distanceTo(startX, startY);
        if (distToStart > 10)
        {
            log.info("[BANKING] Walking back to mining area ({},{}) — distance={}",
                startX, startY, distToStart);
            movement.walkTo(startX, startY);
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }
        state = State.FIND_ROCK;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetBankState()
    {
        bankJustOpened  = false;
        bankDepositDone = false;
    }
}
