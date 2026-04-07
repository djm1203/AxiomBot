package com.botengine.osrs.util;

import net.runelite.api.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Antiban utility.
 *
 * Tests that delay generators stay within expected bounds, that jitter
 * offsets are within the configured radius, and that break scheduling
 * logic transitions correctly between the active and break states.
 *
 * Uses RepeatedTest for random output checks since a single sample is
 * insufficient to verify probabilistic bounds.
 */
class AntibanTest
{
    private Antiban antiban;

    @BeforeEach
    void setUp()
    {
        antiban = new Antiban();
        antiban.reset(); // initializes session timer and schedules first break
    }

    // ── gaussianDelay ─────────────────────────────────────────────────────────

    @RepeatedTest(50)
    void gaussianDelay_staysWithinClampBounds()
    {
        long mean = 1000;
        long stdDev = 200;
        long result = antiban.gaussianDelay(mean, stdDev);

        long lower = (long)(mean * 0.4);
        long upper = (long)(mean * 2.0);

        assertTrue(result >= lower && result <= upper,
            "gaussianDelay=" + result + " out of clamp range [" + lower + ", " + upper + "]");
    }

    @Test
    void gaussianDelay_zeroMean_returnsZero()
    {
        // 0 * 0.4 = 0 and 0 * 2.0 = 0, so clamp forces 0
        assertEquals(0, antiban.gaussianDelay(0, 100));
    }

    // ── randomDelay ───────────────────────────────────────────────────────────

    @RepeatedTest(50)
    void randomDelay_alwaysWithinMinMax()
    {
        long result = antiban.randomDelay(100, 500);
        assertTrue(result >= 100 && result <= 500,
            "randomDelay=" + result + " outside [100, 500]");
    }

    @Test
    void randomDelay_sameMinMax_returnsExactValue()
    {
        assertEquals(250, antiban.randomDelay(250, 250));
    }

    // ── reactionDelay ─────────────────────────────────────────────────────────

    @RepeatedTest(20)
    void reactionDelay_isPositive()
    {
        long result = antiban.reactionDelay();
        assertTrue(result >= 0, "reactionDelay should be non-negative, got " + result);
    }

    @RepeatedTest(20)
    void reactionDelay_staysBelowDistributionMax()
    {
        // triangularDelay(80, 160, 400) — hard upper bound is 400ms
        long result = antiban.reactionDelay();
        assertTrue(result <= 400,
            "reactionDelay=" + result + " exceeded 400ms (distribution max)");
    }

    // ── mouseJitter ───────────────────────────────────────────────────────────

    @RepeatedTest(50)
    void mouseJitter_offsetWithinReasonableRange()
    {
        antiban.setJitterRadius(5);
        Point original = new Point(100, 100);
        Point jittered = antiban.mouseJitter(original);

        int dx = Math.abs(jittered.getX() - original.getX());
        int dy = Math.abs(jittered.getY() - original.getY());

        // Gaussian is clamped to ±2*radius, so offset never exceeds 2*radius
        assertTrue(dx <= 10 && dy <= 10,
            "Jitter offset (" + dx + ", " + dy + ") exceeded 2x radius (radius=5)");
    }

    @Test
    void mouseJitter_zeroRadius_returnsOriginalCoordinates()
    {
        antiban.setJitterRadius(0);
        Point p = new Point(200, 300);
        Point result = antiban.mouseJitter(p);
        assertEquals(p.getX(), result.getX());
        assertEquals(p.getY(), result.getY());
    }

    // ── mousePath ─────────────────────────────────────────────────────────────

    @Test
    void mousePath_returnsRequestedNumberOfSteps()
    {
        Point from = new Point(0, 0);
        Point to   = new Point(100, 100);
        Point[] path = antiban.mousePath(from, to, 10);
        assertEquals(10, path.length);
    }

    @Test
    void mousePath_allPointsNonNull()
    {
        Point from = new Point(0, 0);
        Point to   = new Point(200, 200);
        Point[] path = antiban.mousePath(from, to, 8);
        for (Point p : path)
        {
            assertNotNull(p, "Path should contain no null points");
        }
    }

    @Test
    void mousePath_firstAndLastApproachEndpoints()
    {
        Point from = new Point(0, 0);
        Point to   = new Point(200, 200);
        Point[] path = antiban.mousePath(from, to, 10);

        // First point: t=0 on Bezier → at exactly `from`
        assertEquals(from.getX(), path[0].getX());
        assertEquals(from.getY(), path[0].getY());

        // Last point: t=1 on Bezier → at exactly `to`
        assertEquals(to.getX(), path[9].getX());
        assertEquals(to.getY(), path[9].getY());
    }

    // ── break scheduling ──────────────────────────────────────────────────────

    @Test
    void shouldTakeBreak_falseImmediatelyAfterReset()
    {
        // reset() schedules next break at least 5 minutes in the future
        assertFalse(antiban.shouldTakeBreak(),
            "Should not trigger a break immediately after reset");
    }

    @Test
    void isBreakOver_falseImmediatelyAfterBreakStart()
    {
        antiban.setBreakDurationMinutes(10); // 10 minute break
        antiban.startBreak();
        assertFalse(antiban.isBreakOver(),
            "Break should not be over immediately after starting");
    }

    @Test
    void sessionElapsedMs_growsOverTime() throws InterruptedException
    {
        long t1 = antiban.sessionElapsedMs();
        Thread.sleep(50);
        long t2 = antiban.sessionElapsedMs();
        assertTrue(t2 > t1, "sessionElapsedMs should increase over time");
    }

    @Test
    void sessionElapsedMs_isNonNegativeAfterReset()
    {
        long elapsed = antiban.sessionElapsedMs();
        assertTrue(elapsed >= 0, "sessionElapsedMs should be >= 0 after reset");
    }
}
