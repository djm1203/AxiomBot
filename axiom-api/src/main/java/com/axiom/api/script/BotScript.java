package com.axiom.api.script;

/**
 * Abstract base class for all Axiom scripts.
 *
 * Every script extends this and implements the four lifecycle methods.
 * Scripts run as a state machine — onLoop() is called once per game tick
 * (~600ms) and must return immediately without blocking.
 *
 * Scripts access game state and actions exclusively through the API objects
 * injected by the plugin framework. No direct net.runelite.api imports.
 *
 * Tick-delay mechanism:
 *   Call setTickDelay(n) to pause execution for N game ticks without sleeping
 *   the client thread. ScriptRunner skips onLoop() while tickDelay > 0.
 *
 * Lifecycle (managed by ScriptRunner):
 *   inject(apis) → onStart(settings) → [onLoop() × N ticks] → onStop()
 */
public abstract class BotScript
{
    /**
     * Tick delay counter. When > 0, ScriptRunner skips onLoop() for that many
     * ticks, decrementing each time. Scripts call setTickDelay() to insert a
     * human-like pause between actions without sleeping the client thread.
     */
    private int     tickDelay    = 0;
    private boolean stopRequested = false;

    /** Returns true if this script is currently in a tick-delay pause. */
    public final boolean hasTickDelay()
    {
        return tickDelay > 0;
    }

    /** Called by ScriptRunner each tick when hasTickDelay() is true. */
    public final void decrementTickDelay()
    {
        if (tickDelay > 0) tickDelay--;
    }

    /**
     * Pauses script execution for the given number of game ticks.
     * Call this after firing an action to give the server time to respond.
     * Example: setTickDelay(antiban.reactionTicks());
     */
    protected final void setTickDelay(int ticks)
    {
        tickDelay = Math.max(0, ticks);
    }

    /**
     * Signals the framework to stop this script after the current onLoop() returns.
     * ScriptRunner checks isStopRequested() immediately after onLoop() completes.
     * Use this when the script has naturally finished (e.g. no more logs to burn).
     */
    protected final void stop()
    {
        stopRequested = true;
    }

    /** Called by ScriptRunner to check whether the script requested a self-stop. */
    public final boolean isStopRequested()
    {
        return stopRequested;
    }

    // ── Contract every script must implement ─────────────────────────────────

    /**
     * Human-readable name shown in the panel and overlay.
     * Must match the name in @ScriptManifest.
     */
    public abstract String getName();

    /**
     * Called once when the script is started.
     * Cast {@code settings} to the script's concrete settings class.
     * Set initial state here, validate requirements, log startup info.
     */
    public abstract void onStart(ScriptSettings settings);

    /**
     * Called once per game tick (~600ms) while state is RUNNING.
     * Implement your state machine here.
     * Must return quickly — never block or sleep inside onLoop().
     */
    public abstract void onLoop();

    /**
     * Called once when the script is stopped (by user or after too many errors).
     * Clean up any state here.
     */
    public abstract void onStop();
}
