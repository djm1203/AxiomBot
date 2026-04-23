package com.axiom.api.util;

/**
 * Small bounded retry tracker for tick-based state machines.
 *
 * Scripts use this instead of open-coded attempt counters so retry behavior is
 * consistent and easier to reset when progress is observed.
 */
public final class RetryBudget
{
    private final int maxAttempts;
    private int attempts;

    public RetryBudget(int maxAttempts)
    {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** Clears the current failure count. */
    public void reset()
    {
        attempts = 0;
    }

    /**
     * Records a failed attempt and returns true when the retry budget is now
     * exhausted.
     */
    public boolean fail()
    {
        if (attempts < Integer.MAX_VALUE)
        {
            attempts++;
        }
        return isExhausted();
    }

    /** Returns true once the configured retry budget has been used up. */
    public boolean isExhausted()
    {
        return attempts >= maxAttempts;
    }

    /** Number of failures recorded since the last reset. */
    public int getAttempts()
    {
        return attempts;
    }

    /** Configured maximum before the budget is exhausted. */
    public int getMaxAttempts()
    {
        return maxAttempts;
    }
}
