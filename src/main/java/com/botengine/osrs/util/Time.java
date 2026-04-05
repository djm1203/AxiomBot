package com.botengine.osrs.util;

import lombok.extern.slf4j.Slf4j;

import java.util.function.BooleanSupplier;

/**
 * Timing and sleep utilities for use inside scripts.
 *
 * All sleep methods are safe to call from the game thread.
 * Scripts should prefer sleepUntil() over fixed sleep() wherever possible —
 * it exits as soon as the condition is true rather than waiting the full duration.
 *
 * Never call these from inside onLoop() with long durations.
 * onLoop() must return quickly so the game tick cycle is not blocked.
 * Use these for short waits (e.g. waiting for an animation to start).
 */
@Slf4j
public class Time
{
    /** One game tick in milliseconds. */
    public static final int GAME_TICK_MS = 600;

    // ── Sleep utilities ───────────────────────────────────────────────────────

    /**
     * Sleeps for exactly the given number of milliseconds.
     * Swallows InterruptedException and restores interrupt flag.
     */
    public void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleeps until the given condition returns true, or the timeout elapses.
     *
     * Polls the condition every 100ms.
     *
     * @param condition  returns true when the desired state is reached
     * @param timeoutMs  maximum time to wait in milliseconds
     * @return true if the condition became true before timeout, false if timed out
     *
     * Example:
     *   time.sleepUntil(() -> players.isIdle(), 3000);  // wait up to 3s for idle
     */
    public boolean sleepUntil(BooleanSupplier condition, long timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            if (condition.getAsBoolean())
            {
                return true;
            }
            sleep(100);
        }
        return false;
    }

    /**
     * Sleeps for the given number of game ticks.
     * 1 tick = 600ms, so sleepTicks(3) = ~1800ms.
     */
    public void sleepTicks(int ticks)
    {
        sleep((long) ticks * GAME_TICK_MS);
    }

    // ── Tick math ─────────────────────────────────────────────────────────────

    /**
     * Converts game ticks to milliseconds.
     * Example: ticksToMs(3) = 1800
     */
    public static long ticksToMs(int ticks)
    {
        return (long) ticks * GAME_TICK_MS;
    }

    /**
     * Converts milliseconds to the nearest number of game ticks.
     * Example: msToTicks(1800) = 3
     */
    public static int msToTicks(long ms)
    {
        return (int) Math.round((double) ms / GAME_TICK_MS);
    }

    // ── Elapsed time ─────────────────────────────────────────────────────────

    /**
     * Returns how many milliseconds have passed since startTimeMs.
     * Use System.currentTimeMillis() to capture a start time.
     *
     * Example:
     *   long start = System.currentTimeMillis();
     *   // ... do stuff ...
     *   long elapsed = time.elapsed(start);
     */
    public long elapsed(long startTimeMs)
    {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Returns elapsed time formatted as HH:MM:SS.
     * Useful for the overlay runtime display.
     */
    public String formatElapsed(long startTimeMs)
    {
        long totalSeconds = elapsed(startTimeMs) / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
