package com.axiom.api.util;

import java.util.function.BooleanSupplier;

/**
 * Timing and sleep utilities for use inside scripts.
 * Ported from the monolith Time.java — all logic preserved.
 *
 * Scripts should prefer sleepUntil() over fixed sleep() wherever possible —
 * it exits as soon as the condition is true rather than waiting the full duration.
 *
 * IMPORTANT: Do not call these from inside onLoop() with long durations.
 * onLoop() must return quickly so the game tick cycle is not blocked.
 * Use tick delays (BotScript.setTickDelay) for inter-tick waits instead.
 */
public class Time
{
    /** One game tick in milliseconds. */
    public static final int GAME_TICK_MS = 600;

    /**
     * Sleeps for exactly the given number of milliseconds.
     * Swallows InterruptedException and restores interrupt flag.
     */
    public void sleep(long ms)
    {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Sleeps until the given condition returns true, or the timeout elapses.
     * Polls every 100ms.
     *
     * @return true if condition became true before timeout; false if timed out
     */
    public boolean sleepUntil(BooleanSupplier condition, long timeoutMs)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            if (condition.getAsBoolean()) return true;
            sleep(100);
        }
        return false;
    }

    /** Sleeps for the given number of game ticks. 1 tick = 600ms. */
    public void sleepTicks(int ticks)
    {
        sleep((long) ticks * GAME_TICK_MS);
    }

    /** Converts game ticks to milliseconds. */
    public static long ticksToMs(int ticks)
    {
        return (long) ticks * GAME_TICK_MS;
    }

    /** Converts milliseconds to the nearest number of game ticks. */
    public static int msToTicks(long ms)
    {
        return (int) Math.round((double) ms / GAME_TICK_MS);
    }

    /** Returns how many milliseconds have passed since startTimeMs. */
    public long elapsed(long startTimeMs)
    {
        return System.currentTimeMillis() - startTimeMs;
    }

    /** Returns elapsed time formatted as HH:MM:SS. Useful for overlay display. */
    public String formatElapsed(long startTimeMs)
    {
        long totalSeconds = elapsed(startTimeMs) / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
