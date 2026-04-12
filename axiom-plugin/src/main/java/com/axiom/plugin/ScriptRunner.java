package com.axiom.plugin;

import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.plugin.impl.game.PlayersImpl;
import com.axiom.plugin.impl.game.GameObjectsImpl;
import com.axiom.plugin.impl.game.NpcsImpl;
import com.axiom.plugin.impl.game.GroundItemsImpl;
import com.axiom.plugin.impl.game.WidgetsImpl;
import com.axiom.plugin.impl.player.InventoryImpl;
import com.axiom.plugin.impl.player.EquipmentImpl;
import com.axiom.plugin.impl.player.SkillsImpl;
import com.axiom.plugin.impl.player.PrayerImpl;
import com.axiom.plugin.impl.world.BankImpl;
import com.axiom.plugin.impl.world.MovementImpl;
import com.axiom.plugin.impl.world.CameraImpl;
import com.axiom.plugin.impl.world.WorldHopperImpl;
import com.axiom.plugin.impl.interaction.InteractionImpl;
import com.axiom.plugin.impl.interaction.MenuImpl;
import com.axiom.plugin.impl.PathfinderImpl;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Core execution engine. Subscribed to the RuneLite GameTick event.
 *
 * State machine:
 *   STOPPED → start() → RUNNING → shouldBreak() → BREAKING → breakOver() → RUNNING
 *                                      ↓ logout
 *                                   PAUSED → login → RUNNING
 *
 * Per tick (RUNNING):
 *   1. Check if it's time for a break (antiban.shouldTakeBreak())
 *   2. Honor script tick-delay pauses (BotScript.hasTickDelay())
 *   3. Call activeScript.onLoop()
 *   4. Track consecutive errors — stop after MAX_CONSECUTIVE_ERRORS
 */
@Slf4j
@Singleton
public class ScriptRunner
{
    // ── Injected API implementations ──────────────────────────────────────────
    private final PlayersImpl       players;
    private final GameObjectsImpl   gameObjects;
    private final NpcsImpl          npcs;
    private final GroundItemsImpl   groundItems;
    private final WidgetsImpl       widgets;
    private final InventoryImpl     inventory;
    private final EquipmentImpl     equipment;
    private final SkillsImpl        skills;
    private final PrayerImpl        prayer;
    private final BankImpl          bank;
    private final MovementImpl      movement;
    private final CameraImpl        camera;
    private final WorldHopperImpl   worldHopper;
    private final InteractionImpl   interaction;
    private final MenuImpl          menu;
    private final PathfinderImpl    pathfinder;
    private final Antiban           antiban;
    private final Log               botLog;

    // ── State ─────────────────────────────────────────────────────────────────
    @Getter private volatile BotScript   activeScript;
    @Getter private volatile ScriptState state = ScriptState.STOPPED;

    private volatile ScriptState stateBeforeBreak = ScriptState.STOPPED;
    private volatile boolean     logoutPaused     = false;

    private int consecutiveErrors  = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    @Inject
    public ScriptRunner(
        PlayersImpl players, GameObjectsImpl gameObjects, NpcsImpl npcs,
        GroundItemsImpl groundItems, WidgetsImpl widgets,
        InventoryImpl inventory, EquipmentImpl equipment,
        SkillsImpl skills, PrayerImpl prayer,
        BankImpl bank, MovementImpl movement, CameraImpl camera,
        WorldHopperImpl worldHopper, InteractionImpl interaction,
        MenuImpl menu, PathfinderImpl pathfinder,
        Antiban antiban, Log botLog
    )
    {
        this.players     = players;
        this.gameObjects = gameObjects;
        this.npcs        = npcs;
        this.groundItems = groundItems;
        this.widgets     = widgets;
        this.inventory   = inventory;
        this.equipment   = equipment;
        this.skills      = skills;
        this.prayer      = prayer;
        this.bank        = bank;
        this.movement    = movement;
        this.camera      = camera;
        this.worldHopper = worldHopper;
        this.interaction = interaction;
        this.menu        = menu;
        this.pathfinder  = pathfinder;
        this.antiban     = antiban;
        this.botLog      = botLog;
    }

    // ── Event handlers ────────────────────────────────────────────────────────

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
                state        = ScriptState.PAUSED;
            }
        }
        else if (gs == GameState.LOGGED_IN && logoutPaused)
        {
            botLog.info("Logged back in — resuming script");
            logoutPaused = false;
            state        = ScriptState.RUNNING;
        }
    }

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
                    state = stateBeforeBreak;
                }
                return;

            case RUNNING:
                if (antiban.shouldTakeBreak())
                {
                    botLog.info("Taking antiban break — pausing " + activeScript.getName());
                    stateBeforeBreak = ScriptState.RUNNING;
                    state            = ScriptState.BREAKING;
                    antiban.startBreak();
                    return;
                }

                if (activeScript.hasTickDelay())
                {
                    activeScript.decrementTickDelay();
                    return;
                }

                try
                {
                    activeScript.onLoop();
                    consecutiveErrors = 0;
                    if (activeScript.isStopRequested())
                    {
                        botLog.info(activeScript.getName() + " requested stop — stopping");
                        stop();
                        return;
                    }
                }
                catch (Exception e)
                {
                    consecutiveErrors++;
                    log.error("Script error in {} ({}/{}): {} — {}",
                        activeScript.getName(), consecutiveErrors, MAX_CONSECUTIVE_ERRORS,
                        e.getClass().getSimpleName(), e.getMessage(), e);
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS)
                    {
                        botLog.info("Script stopped after " + MAX_CONSECUTIVE_ERRORS + " consecutive errors");
                        stop();
                    }
                }
                break;
        }
    }

    // ── Control ───────────────────────────────────────────────────────────────

    /**
     * Starts the given script. Injects all API objects before calling onStart().
     * If another script is running, stops it first.
     */
    public void start(BotScript script, ScriptSettings settings)
    {
        if (activeScript != null && state != ScriptState.STOPPED) stop();

        activeScript = script;
        injectApis(script);

        botLog.setScriptName(script.getName());
        botLog.setLoggerClass(script.getClass());
        botLog.info("Starting script: " + script.getName());

        camera.setupForScripting();
        script.onStart(settings);
        antiban.reset();
        consecutiveErrors = 0;
        logoutPaused      = false;
        state             = ScriptState.RUNNING;
    }

    /** Stops the active script and resets to STOPPED. */
    public void stop()
    {
        if (activeScript != null)
        {
            botLog.info("Stopping script: " + activeScript.getName());
            try { activeScript.onStop(); }
            catch (Exception e) { log.error("Error during script stop: {}", e.getMessage(), e); }
        }
        botLog.setScriptName(null);
        botLog.setLoggerClass(com.axiom.api.util.Log.class);
        logoutPaused = false;
        state        = ScriptState.STOPPED;
    }

    /** Pauses the running script. Resume resumes from the same state. */
    public void pause()
    {
        if (state == ScriptState.RUNNING)
        {
            botLog.info("Pausing script: " + activeScript.getName());
            stateBeforeBreak = ScriptState.PAUSED;
            state            = ScriptState.PAUSED;
        }
    }

    /** Resumes a paused script. */
    public void resume()
    {
        if (state == ScriptState.PAUSED)
        {
            botLog.info("Resuming script: " + activeScript.getName());
            state = ScriptState.RUNNING;
        }
    }

    // ── Dependency injection into scripts ─────────────────────────────────────

    /**
     * Injects all API implementations into the script via reflection on the
     * script's declared fields. Scripts declare typed fields for each API
     * they use; the framework fills them here.
     *
     * This avoids coupling BotScript (in axiom-api) to RuneLite types.
     */
    private void injectApis(BotScript script)
    {
        injectField(script, "players",     players);
        injectField(script, "gameObjects", gameObjects);
        injectField(script, "npcs",        npcs);
        injectField(script, "groundItems", groundItems);
        injectField(script, "widgets",     widgets);
        injectField(script, "inventory",   inventory);
        injectField(script, "equipment",   equipment);
        injectField(script, "skills",      skills);
        injectField(script, "prayer",      prayer);
        injectField(script, "bank",        bank);
        injectField(script, "movement",    movement);
        injectField(script, "camera",      camera);
        injectField(script, "worldHopper", worldHopper);
        injectField(script, "interaction", interaction);
        injectField(script, "menu",        menu);
        injectField(script, "pathfinder",  pathfinder);
        injectField(script, "antiban",     antiban);
        injectField(script, "log",         botLog);
    }

    private void injectField(BotScript script, String fieldName, Object value)
    {
        try
        {
            java.lang.reflect.Field f = script.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(script, value);
        }
        catch (NoSuchFieldException ignored)
        {
            // Script doesn't use this API — fine
        }
        catch (Exception e)
        {
            log.warn("ScriptRunner: could not inject '{}' into {}: {}", fieldName,
                script.getClass().getSimpleName(), e.getMessage());
        }
    }
}
