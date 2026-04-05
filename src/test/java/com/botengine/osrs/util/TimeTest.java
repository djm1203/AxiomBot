package com.botengine.osrs.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Time utility.
 *
 * Tests tick/ms conversions, elapsed formatting, and sleepUntil logic.
 * Actual sleep durations are kept short (< 300ms) to keep the test suite fast.
 */
class TimeTest
{
    private Time time;

    @BeforeEach
    void setUp()
    {
        time = new Time();
    }

    // ── ticksToMs (static) ────────────────────────────────────────────────────

    @Test
    void ticksToMs_oneTick_is600ms()
    {
        assertEquals(600L, Time.ticksToMs(1));
    }

    @Test
    void ticksToMs_zeroTicks_isZero()
    {
        assertEquals(0L, Time.ticksToMs(0));
    }

    @Test
    void ticksToMs_tenTicks_is6000ms()
    {
        assertEquals(6000L, Time.ticksToMs(10));
    }

    // ── msToTicks (static) ────────────────────────────────────────────────────

    @Test
    void msToTicks_600ms_isOneTick()
    {
        assertEquals(1, Time.msToTicks(600));
    }

    @Test
    void msToTicks_1200ms_isTwoTicks()
    {
        assertEquals(2, Time.msToTicks(1200));
    }

    @Test
    void msToTicks_rounds_to_nearest()
    {
        // 300ms is exactly 0.5 ticks → rounds to 1 (Math.round rounds half-up)
        assertEquals(1, Time.msToTicks(300));
        // 200ms is 0.333 ticks → rounds to 0
        assertEquals(0, Time.msToTicks(200));
    }

    @Test
    void ticksToMs_and_msToTicks_roundtrip()
    {
        int originalTicks = 5;
        long ms = Time.ticksToMs(originalTicks);
        assertEquals(originalTicks, Time.msToTicks(ms));
    }

    // ── elapsed ───────────────────────────────────────────────────────────────

    @Test
    void elapsed_returnsNonNegative()
    {
        long start = System.currentTimeMillis();
        long result = time.elapsed(start);
        assertTrue(result >= 0, "elapsed should be >= 0");
    }

    @Test
    void elapsed_futureStartTime_returnsNegative()
    {
        long futureStart = System.currentTimeMillis() + 60_000;
        long result = time.elapsed(futureStart);
        assertTrue(result < 0, "elapsed with future start should be negative");
    }

    // ── formatElapsed ─────────────────────────────────────────────────────────

    @Test
    void formatElapsed_recentStart_matchesHHMMSSPattern()
    {
        long start = System.currentTimeMillis();
        String result = time.formatElapsed(start);
        assertTrue(result.matches("\\d{2}:\\d{2}:\\d{2}"),
            "Expected HH:MM:SS format, got: " + result);
    }

    @Test
    void formatElapsed_90SecondsAgo_shows00_01_30()
    {
        long start = System.currentTimeMillis() - 90_000;
        String result = time.formatElapsed(start);
        assertEquals("00:01:30", result);
    }

    @Test
    void formatElapsed_3661SecondsAgo_shows01_01_01()
    {
        long start = System.currentTimeMillis() - 3_661_000;
        String result = time.formatElapsed(start);
        assertEquals("01:01:01", result);
    }

    @Test
    void formatElapsed_exactHour_shows01_00_00()
    {
        long start = System.currentTimeMillis() - 3_600_000;
        String result = time.formatElapsed(start);
        assertEquals("01:00:00", result);
    }

    // ── sleepUntil ────────────────────────────────────────────────────────────

    @Test
    void sleepUntil_conditionAlreadyTrue_returnsTrueQuickly()
    {
        long before = System.currentTimeMillis();
        boolean result = time.sleepUntil(() -> true, 5000);
        long elapsed = System.currentTimeMillis() - before;

        assertTrue(result, "sleepUntil should return true when condition immediately met");
        assertTrue(elapsed < 500, "Should not have waited long, elapsed=" + elapsed + "ms");
    }

    @Test
    void sleepUntil_conditionNeverTrue_timesOutAndReturnsFalse()
    {
        long before = System.currentTimeMillis();
        boolean result = time.sleepUntil(() -> false, 200);
        long elapsed = System.currentTimeMillis() - before;

        assertFalse(result, "sleepUntil should return false on timeout");
        assertTrue(elapsed >= 200, "Should have waited at least the timeout, elapsed=" + elapsed);
    }

    @Test
    void sleepUntil_conditionBecomesTrueBeforeTimeout_returnsTrue()
            throws InterruptedException
    {
        AtomicBoolean flag = new AtomicBoolean(false);

        Thread flipper = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            flag.set(true);
        });
        flipper.start();

        boolean result = time.sleepUntil(flag::get, 2000);
        flipper.join();

        assertTrue(result, "sleepUntil should return true when condition flips in time");
    }
}
