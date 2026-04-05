package com.botengine.osrs.script;

import com.botengine.osrs.api.Bank;
import com.botengine.osrs.api.Combat;
import com.botengine.osrs.api.GameObjects;
import com.botengine.osrs.api.Interaction;
import com.botengine.osrs.api.Inventory;
import com.botengine.osrs.api.Magic;
import com.botengine.osrs.api.Movement;
import com.botengine.osrs.api.Npcs;
import com.botengine.osrs.api.Players;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;

/**
 * Core execution engine. Subscribed to the RuneLite GameTick event.
 *
 * On every game tick (600ms), ScriptRunner checks the current state and
 * either does nothing (STOPPED/PAUSED) or calls the active script's onLoop().
 *
 * BotEnginePlugin registers this class with the EventBus on plugin startUp()
 * and unregisters it on shutDown().
 *
 * Dependency injection note:
 *   ScriptRunner is @Inject-constructed by BotEnginePlugin. All api/ and util/
 *   instances are injected here and forwarded to each script via BotScript.inject()
 *   when a new script is set. Scripts themselves do not use Guice.
 */
@Slf4j
public class ScriptRunner
{
    // ── RuneLite core ────────────────────────────────────────────────────────
    private final Client client;

    // ── API layer ─────────────────────────────────────────────────────────────
    private final Players players;
    private final Npcs npcs;
    private final GameObjects gameObjects;
    private final Inventory inventory;
    private final Bank bank;
    private final Movement movement;
    private final Interaction interaction;
    private final Magic magic;
    private final Combat combat;

    // ── Utilities ─────────────────────────────────────────────────────────────
    private final Antiban antiban;
    private final Time time;
    private final Log botLog;

    // ── State ─────────────────────────────────────────────────────────────────
    @Getter
    private BotScript activeScript;

    @Getter
    private ScriptState state = ScriptState.STOPPED;

    @Inject
    public ScriptRunner(
        Client client,
        Players players,
        Npcs npcs,
        GameObjects gameObjects,
        Inventory inventory,
        Bank bank,
        Movement movement,
        Interaction interaction,
        Magic magic,
        Combat combat,
        Antiban antiban,
        Time time,
        Log botLog
    )
    {
        this.client = client;
        this.players = players;
        this.npcs = npcs;
        this.gameObjects = gameObjects;
        this.inventory = inventory;
        this.bank = bank;
        this.movement = movement;
        this.interaction = interaction;
        this.magic = magic;
        this.combat = combat;
        this.antiban = antiban;
        this.time = time;
        this.botLog = botLog;
    }

    // ── GameTick handler ──────────────────────────────────────────────────────

    @Subscribe
    public void onGameTick(GameTick event)
    {
        switch (state)
        {
            case STOPPED:
            case PAUSED:
                return;

            case BREAKING:
                if (antiban.isBreakOver())
                {
                    botLog.info("Break complete — resuming " + activeScript.getName());
                    state = ScriptState.RUNNING;
                }
                return;

            case RUNNING:
                if (antiban.shouldTakeBreak())
                {
                    botLog.info("Taking antiban break — pausing " + activeScript.getName());
                    state = ScriptState.BREAKING;
                    antiban.startBreak();
                    return;
                }

                try
                {
                    activeScript.onLoop();
                }
                catch (Exception e)
                {
                    log.error("Script error in {}: {}", activeScript.getName(), e.getMessage(), e);
                    stop();
                }
                break;
        }
    }

    // ── Control methods (called by BotEnginePanel) ────────────────────────────

    /**
     * Sets the active script and immediately starts it.
     * If a script is already running, it is stopped first.
     */
    public void start(BotScript script)
    {
        if (activeScript != null && state != ScriptState.STOPPED)
        {
            stop();
        }

        activeScript = script;
        activeScript.inject(
            client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat,
            antiban, time, botLog
        );

        botLog.info("Starting script: " + script.getName());
        activeScript.onStart();
        antiban.reset();
        state = ScriptState.RUNNING;
    }

    /**
     * Stops the active script and resets state.
     */
    public void stop()
    {
        if (activeScript != null)
        {
            botLog.info("Stopping script: " + activeScript.getName());
            try
            {
                activeScript.onStop();
            }
            catch (Exception e)
            {
                log.error("Error during script stop: {}", e.getMessage(), e);
            }
        }
        state = ScriptState.STOPPED;
    }

    /**
     * Pauses the active script. State is preserved — resume() continues from here.
     */
    public void pause()
    {
        if (state == ScriptState.RUNNING)
        {
            botLog.info("Pausing script: " + activeScript.getName());
            state = ScriptState.PAUSED;
        }
    }

    /**
     * Resumes a paused script.
     */
    public void resume()
    {
        if (state == ScriptState.PAUSED)
        {
            botLog.info("Resuming script: " + activeScript.getName());
            state = ScriptState.RUNNING;
        }
    }
}
