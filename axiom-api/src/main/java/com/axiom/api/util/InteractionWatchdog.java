package com.axiom.api.util;

/**
 * Shared bounded retry helper for "clicked but nothing happened" loops.
 *
 * Typical usage:
 * 1. call {@link #begin()} after sending an interaction
 * 2. on each following tick, call {@link #observe(boolean)} with a light-weight
 *    progress signal such as "player is moving"
 * 3. call {@link #markSuccess()} once the real success signal lands
 * 4. on RETRY / EXHAUSTED, re-acquire the target or transition state
 */
public final class InteractionWatchdog
{
    public enum Status
    {
        WAITING,
        RETRY,
        EXHAUSTED
    }

    private final int timeoutTicks;
    private final RetryBudget retryBudget;
    private int idleTicks;

    public InteractionWatchdog(int timeoutTicks, int maxRetries)
    {
        this.timeoutTicks = Math.max(1, timeoutTicks);
        this.retryBudget = new RetryBudget(maxRetries);
    }

    public void begin()
    {
        idleTicks = 0;
    }

    /**
     * Observes a tick after an interaction.
     *
     * @param progressing true when there is partial progress such as walking
     *                    toward the target even if the final animation has not
     *                    started yet.
     */
    public Status observe(boolean progressing)
    {
        if (progressing)
        {
            idleTicks = 0;
            return Status.WAITING;
        }

        idleTicks++;
        if (idleTicks < timeoutTicks)
        {
            return Status.WAITING;
        }

        idleTicks = 0;
        return retryBudget.fail() ? Status.EXHAUSTED : Status.RETRY;
    }

    /**
     * Marks a real success signal such as animation start. This clears both the
     * timeout and accumulated retries.
     */
    public void markSuccess()
    {
        idleTicks = 0;
        retryBudget.reset();
    }

    public void reset()
    {
        idleTicks = 0;
        retryBudget.reset();
    }

    public int getIdleTicks()
    {
        return idleTicks;
    }

    public int getAttempts()
    {
        return retryBudget.getAttempts();
    }

    public int getMaxAttempts()
    {
        return retryBudget.getMaxAttempts();
    }
}
