package com.botengine.osrs.script;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.*;
import com.botengine.osrs.api.GrandExchange;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.api.Prayers;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.SessionStats;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScriptRunner.
 *
 * Uses LENIENT strictness because the setUp() stubs for antiban are only
 * needed by tick-dispatch tests, not state-transition tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScriptRunnerTest
{
    @Mock private Client client;
    @Mock private Players players;
    @Mock private Npcs npcs;
    @Mock private GameObjects gameObjects;
    @Mock private Inventory inventory;
    @Mock private Bank bank;
    @Mock private Movement movement;
    @Mock private Interaction interaction;
    @Mock private Magic magic;
    @Mock private Combat combat;
    @Mock private Camera camera;
    @Mock private Prayers prayers;
    @Mock private GroundItems groundItems;
    @Mock private WorldHopper worldHopper;
    @Mock private GrandExchange grandExchange;
    @Mock private Antiban antiban;
    @Mock private Time time;
    @Mock private Log botLog;
    @Mock private SessionStats sessionStats;
    @Mock private BotEngineConfig config;

    private ScriptRunner runner;
    private GameTick tick;

    @BeforeEach
    void setUp()
    {
        runner = new ScriptRunner(
            client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera,
            prayers, groundItems, worldHopper, grandExchange, antiban, time, botLog, sessionStats, config
        );
        tick = new GameTick();
        // Default: no break pending, no break over
        when(antiban.shouldTakeBreak()).thenReturn(false);
        when(antiban.isBreakOver()).thenReturn(false);
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void initialState_isStopped()
    {
        assertEquals(ScriptState.STOPPED, runner.getState());
    }

    @Test
    void initialActiveScript_isNull()
    {
        assertNull(runner.getActiveScript());
    }

    // ── start ─────────────────────────────────────────────────────────────────

    @Test
    void start_transitionsToRunning()
    {
        runner.start(makeScript());
        assertEquals(ScriptState.RUNNING, runner.getState());
    }

    @Test
    void start_callsOnStart()
    {
        BotScript script = spy(makeScript());
        runner.start(script);
        verify(script).onStart();
    }

    @Test
    void start_setsActiveScript()
    {
        BotScript script = makeScript();
        runner.start(script);
        assertSame(script, runner.getActiveScript());
    }

    @Test
    void start_whenAlreadyRunning_stopsOldScriptFirst()
    {
        BotScript old = spy(makeScript());
        BotScript next = spy(makeScript());
        runner.start(old);
        runner.start(next);

        verify(old).onStop();
        assertSame(next, runner.getActiveScript());
    }

    // ── stop ──────────────────────────────────────────────────────────────────

    @Test
    void stop_transitionsToStopped()
    {
        runner.start(makeScript());
        runner.stop();
        assertEquals(ScriptState.STOPPED, runner.getState());
    }

    @Test
    void stop_callsOnStop()
    {
        BotScript script = spy(makeScript());
        runner.start(script);
        runner.stop();
        verify(script).onStop();
    }

    @Test
    void stop_whenAlreadyStopped_doesNotThrow()
    {
        assertDoesNotThrow(() -> runner.stop());
    }

    // ── pause / resume ────────────────────────────────────────────────────────

    @Test
    void pause_whenRunning_transitionsToPaused()
    {
        runner.start(makeScript());
        runner.pause();
        assertEquals(ScriptState.PAUSED, runner.getState());
    }

    @Test
    void pause_whenStopped_hasNoEffect()
    {
        runner.pause();
        assertEquals(ScriptState.STOPPED, runner.getState());
    }

    @Test
    void resume_whenPaused_transitionsToRunning()
    {
        runner.start(makeScript());
        runner.pause();
        runner.resume();
        assertEquals(ScriptState.RUNNING, runner.getState());
    }

    @Test
    void resume_whenStopped_hasNoEffect()
    {
        runner.resume();
        assertEquals(ScriptState.STOPPED, runner.getState());
    }

    // ── GameTick dispatch ─────────────────────────────────────────────────────

    @Test
    void onGameTick_whenRunning_callsOnLoop()
    {
        BotScript script = spy(makeScript());
        runner.start(script);
        runner.onGameTick(tick);
        verify(script).onLoop();
    }

    @Test
    void onGameTick_whenStopped_doesNotCallOnLoop()
    {
        BotScript script = spy(makeScript());
        runner.start(script);
        runner.stop();
        runner.onGameTick(tick);
        verify(script, never()).onLoop();
    }

    @Test
    void onGameTick_whenPaused_doesNotCallOnLoop()
    {
        BotScript script = spy(makeScript());
        runner.start(script);
        runner.pause();
        runner.onGameTick(tick);
        verify(script, never()).onLoop();
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void onGameTick_onLoopException_stopsRunnerAfterMaxErrors()
    {
        BotScript script = makeThrowingScript(new RuntimeException("script crash"));
        runner.start(script);
        // ScriptRunner stops after MAX_CONSECUTIVE_ERRORS (5) consecutive failures
        for (int i = 0; i < 5; i++)
        {
            runner.onGameTick(tick);
        }
        assertEquals(ScriptState.STOPPED, runner.getState());
    }

    // ── Antiban break cycle ───────────────────────────────────────────────────

    @Test
    void onGameTick_whenBreakDue_transitionsToBreaking()
    {
        when(antiban.shouldTakeBreak()).thenReturn(true);
        runner.start(makeScript());
        runner.onGameTick(tick);
        assertEquals(ScriptState.BREAKING, runner.getState());
    }

    @Test
    void onGameTick_whenBreaking_andBreakOver_transitionsToRunning()
    {
        when(antiban.shouldTakeBreak()).thenReturn(true);
        runner.start(makeScript());
        runner.onGameTick(tick); // → BREAKING

        when(antiban.shouldTakeBreak()).thenReturn(false);
        when(antiban.isBreakOver()).thenReturn(true);
        runner.onGameTick(tick); // → RUNNING

        assertEquals(ScriptState.RUNNING, runner.getState());
    }

    @Test
    void onGameTick_whenBreaking_andBreakNotOver_staysBreaking()
    {
        when(antiban.shouldTakeBreak()).thenReturn(true);
        runner.start(makeScript());
        runner.onGameTick(tick); // → BREAKING

        when(antiban.isBreakOver()).thenReturn(false);
        runner.onGameTick(tick); // stays BREAKING

        assertEquals(ScriptState.BREAKING, runner.getState());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BotScript makeScript()
    {
        return new BotScript()
        {
            public String getName()  { return "TestScript"; }
            public void onStart()    {}
            public void onLoop()     {}
            public void onStop()     {}
            public com.botengine.osrs.ui.ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent p) { return null; }
        };
    }

    private BotScript makeThrowingScript(RuntimeException ex)
    {
        return new BotScript()
        {
            public String getName()  { return "ThrowingScript"; }
            public void onStart()    {}
            public void onLoop()     { throw ex; }
            public void onStop()     {}
            public com.botengine.osrs.ui.ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent p) { return null; }
        };
    }
}
