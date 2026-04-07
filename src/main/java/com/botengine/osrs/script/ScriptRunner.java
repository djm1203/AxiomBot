package com.botengine.osrs.script;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.Bank;
import com.botengine.osrs.api.Camera;
import com.botengine.osrs.api.Combat;
import com.botengine.osrs.api.GameObjects;
import com.botengine.osrs.api.GrandExchange;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.api.Interaction;
import com.botengine.osrs.api.Inventory;
import com.botengine.osrs.api.Magic;
import com.botengine.osrs.api.Movement;
import com.botengine.osrs.api.Npcs;
import com.botengine.osrs.api.Players;
import com.botengine.osrs.api.Prayers;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.botengine.osrs.util.SessionStats;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;

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
@Singleton
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
    private final Camera camera;
    private final Prayers prayers;
    private final GroundItems groundItems;
    private final WorldHopper worldHopper;
    private final GrandExchange grandExchange;

    // ── Utilities ─────────────────────────────────────────────────────────────
    private final Antiban antiban;
    private final Time time;
    private final Log botLog;
    private final SessionStats sessionStats;

    // ── Plugin config ─────────────────────────────────────────────────────────
    private final BotEngineConfig config;

    // ── State ─────────────────────────────────────────────────────────────────
    // volatile: written on AWT thread (start/stop/pause), read on Client thread (onGameTick)
    @Getter
    private volatile BotScript activeScript;

    @Getter
    private volatile ScriptState state = ScriptState.STOPPED;

    /** State before entering a break — used to resume correctly (RUNNING vs PAUSED). */
    private volatile ScriptState stateBeforeBreak = ScriptState.STOPPED;

    /** Consecutive onLoop() errors — stops script after MAX_CONSECUTIVE_ERRORS. */
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    /** True when script was auto-paused due to logout — resumes on re-login. */
    private volatile boolean logoutPaused = false;

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
        Camera camera,
        Prayers prayers,
        GroundItems groundItems,
        WorldHopper worldHopper,
        GrandExchange grandExchange,
        Antiban antiban,
        Time time,
        Log botLog,
        SessionStats sessionStats,
        BotEngineConfig config
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
        this.camera = camera;
        this.prayers = prayers;
        this.groundItems = groundItems;
        this.worldHopper = worldHopper;
        this.grandExchange = grandExchange;
        this.antiban      = antiban;
        this.time         = time;
        this.botLog       = botLog;
        this.sessionStats = sessionStats;
        this.config       = config;
    }

    // ── GameState handler — logout detection ──────────────────────────────────

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState gs = event.getGameState();

        if (gs == GameState.LOGIN_SCREEN || gs == GameState.CONNECTION_LOST)
        {
            if (state == ScriptState.RUNNING || state == ScriptState.BREAKING)
            {
                botLog.info("Logged out — auto-pausing script");
                logoutPaused = true;
                state = ScriptState.PAUSED;
            }
        }
        else if (gs == GameState.LOGGED_IN && logoutPaused)
        {
            botLog.info("Logged back in — resuming script");
            logoutPaused = false;
            state = ScriptState.RUNNING;
        }
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
                    state = stateBeforeBreak; // return to RUNNING or PAUSED as appropriate
                }
                return;

            case RUNNING:
                if (antiban.shouldTakeBreak())
                {
                    botLog.info("Taking antiban break — pausing " + activeScript.getName());
                    stateBeforeBreak = ScriptState.RUNNING;
                    state = ScriptState.BREAKING;
                    antiban.startBreak();
                    return;
                }

                try
                {
                    activeScript.onLoop();
                    consecutiveErrors = 0; // reset on clean tick
                }
                catch (Exception e)
                {
                    consecutiveErrors++;
                    log.error("Script error in {} ({}/{}): {}",
                        activeScript.getName(), consecutiveErrors, MAX_CONSECUTIVE_ERRORS,
                        e.getMessage(), e);
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS)
                    {
                        botLog.info("Script stopped after " + MAX_CONSECUTIVE_ERRORS + " consecutive errors");
                        stop();
                    }
                }
                break;
        }
    }

    // ── Control methods (called by BotEnginePanel) ────────────────────────────

    /**
     * Sets the active script and starts it with per-script settings from the Axiom dialog.
     * If a script is already running, it is stopped first.
     */
    public void start(BotScript script, com.botengine.osrs.ui.ScriptSettings settings)
    {
        if (activeScript != null && state != ScriptState.STOPPED)
        {
            stop();
        }

        activeScript = script;
        activeScript.inject(
            client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera,
            prayers, groundItems, worldHopper, grandExchange, antiban, time, botLog
        );
        activeScript.configure(config, settings);

        botLog.info("Starting script: " + script.getName());
        activeScript.onStart();
        antiban.reset();
        sessionStats.reset(client);
        consecutiveErrors = 0;
        logoutPaused = false;
        state = ScriptState.RUNNING;
    }

    /**
     * Backward-compatible overload — starts without per-script settings (null).
     * Used by tests and any legacy callers.
     */
    public void start(BotScript script)
    {
        start(script, null);
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
        sessionStats.stop();
        logoutPaused = false;
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
            stateBeforeBreak = ScriptState.PAUSED; // if break fires while paused, stay paused on resume
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
