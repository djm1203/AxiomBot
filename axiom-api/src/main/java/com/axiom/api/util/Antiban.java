package com.axiom.api.util;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * Human-like behavior simulation.
 *
 * Ported from the monolith Antiban.java — all logic preserved.
 * Uses java.awt.Point instead of net.runelite.api.Point so this
 * module stays RuneLite-free.
 *
 * Every action a script takes passes through Antiban in some form:
 *  - Delays between actions use gaussianDelay() instead of fixed values
 *  - Mouse click targets are jittered slightly via mouseJitter()
 *  - After a configurable session length, a break is automatically triggered
 *
 * Break scheduling:
 *   ScriptRunner calls shouldTakeBreak() each tick.
 *   When true, it transitions to BREAKING state and calls startBreak().
 *   Each tick in BREAKING state calls isBreakOver() until it returns true.
 */
@Slf4j
public class Antiban
{
    private static final Random random = new Random();

    // F-key VK codes for the OSRS skill/inventory tabs
    private static final int[] TAB_KEYS = {
        KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3,
        KeyEvent.VK_F5, KeyEvent.VK_F6  // skip F4 (inventory — we return to it)
    };

    private Robot robot;

    {
        try { robot = new Robot(); }
        catch (AWTException e) { log.warn("Antiban: Robot unavailable — camera/tab actions disabled"); }
    }

    /** Per-run random seed in range [0.85, 1.15] — varies timing slightly between sessions. */
    private final double accountSeed = 0.85 + Math.random() * 0.30;

    // ── Configuration (set by plugin config) ─────────────────────────────────

    /** Average minutes between breaks. Default: 45 minutes. */
    @Setter private int breakIntervalMinutes = 45;

    /** Average minutes per break. Default: 7 minutes. */
    @Setter private int breakDurationMinutes = 7;

    /** Maximum pixel jitter radius applied to click coordinates. Default: 3px. */
    @Setter private int jitterRadius = 3;

    // ── Internal state ────────────────────────────────────────────────────────

    private long sessionStartMs;
    private long nextBreakAtMs;
    private long breakStartMs;
    private long breakEndMs;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Resets session timer and schedules the first break.
     * Called by ScriptRunner when a script starts.
     */
    public void reset()
    {
        sessionStartMs = System.currentTimeMillis();
        scheduleNextBreak();
    }

    // ── Break scheduling ──────────────────────────────────────────────────────

    /** Returns true when it's time to take an antiban break. Called each game tick. */
    public boolean shouldTakeBreak()
    {
        return System.currentTimeMillis() >= nextBreakAtMs;
    }

    /**
     * Records the break start time and schedules the break end.
     * Called when transitioning to BREAKING state.
     */
    public void startBreak()
    {
        breakStartMs = System.currentTimeMillis();
        long variance = (long) (breakDurationMinutes * 60_000 * 0.3);
        long breakMs  = (long) (breakDurationMinutes * 60_000)
            + (long) (random.nextGaussian() * variance);
        breakMs    = Math.max(breakMs, 60_000); // minimum 1 minute
        breakEndMs = breakStartMs + breakMs;
    }

    /** Returns true when the current break has lasted long enough. */
    public boolean isBreakOver()
    {
        if (System.currentTimeMillis() >= breakEndMs)
        {
            scheduleNextBreak();
            return true;
        }
        return false;
    }

    private void scheduleNextBreak()
    {
        long variance   = (long) (breakIntervalMinutes * 60_000 * 0.2);
        long intervalMs = (long) (breakIntervalMinutes * 60_000)
            + (long) (random.nextGaussian() * variance);
        intervalMs    = Math.max(intervalMs, 5 * 60_000); // minimum 5 minutes
        nextBreakAtMs = System.currentTimeMillis() + intervalMs;
    }

    // ── Fatigue & timing ──────────────────────────────────────────────────────

    /**
     * Returns a delay multiplier based on elapsed session time.
     * Models human fatigue — delays gradually increase over a long session.
     *   0–30 min   → 1.00  (fresh)
     *   30–60 min  → 1.00–1.08
     *   60–120 min → 1.08–1.20
     *   120+ min   → 1.20–1.30 (tired, capped)
     */
    public double fatigueFactor()
    {
        long elapsedMin = sessionElapsedMs() / 60_000;
        if (elapsedMin <= 30)  return 1.0;
        if (elapsedMin <= 60)  return 1.0  + (elapsedMin - 30) / 30.0 * 0.08;
        if (elapsedMin <= 120) return 1.08 + (elapsedMin - 60) / 60.0 * 0.12;
        return 1.30;
    }

    /**
     * Gaussian delay — samples from a bell curve centred on meanMs.
     * Use for action-to-action delays that should feel human.
     *
     * @param meanMs   target delay in milliseconds
     * @param stdDevMs standard deviation
     * @return delay clamped to [meanMs * 0.4, meanMs * 2.5]
     */
    public long gaussianDelay(long meanMs, long stdDevMs)
    {
        long delay = (long) (meanMs * accountSeed) + (long) (random.nextGaussian() * stdDevMs);
        long min   = (long) (meanMs * 0.4);
        long max   = (long) (meanMs * 2.5);
        return Math.max(min, Math.min(max, delay));
    }

    /** Uniform random delay between min and max milliseconds. */
    public long randomDelay(long minMs, long maxMs)
    {
        if (minMs >= maxMs) return minMs;
        return minMs + (long) (random.nextDouble() * (maxMs - minMs));
    }

    /**
     * Reaction time delay using a triangular distribution.
     * Triangular distribution matches human reaction time data better than
     * Gaussian — clear minimum, most-likely response time, long tail.
     * Default range: 80ms min, 160ms mode, 400ms max (ABC2-spec inspired).
     * Fatigue and account seed shift the distribution each session.
     */
    public long reactionDelay()
    {
        double multiplier = accountSeed * fatigueFactor();
        return (long) (triangularDelay(80, 160, 400) * multiplier);
    }

    /**
     * Triangular distribution delay.
     *
     * @param min  minimum possible delay (ms)
     * @param mode most likely delay (ms) — peak of the triangle
     * @param max  maximum possible delay (ms)
     */
    public long triangularDelay(long min, long mode, long max)
    {
        if (min >= max) return min;
        double u = random.nextDouble();
        double f = (double) (mode - min) / (max - min);
        if (u < f)
            return min + (long) Math.sqrt(u * (max - min) * (mode - min));
        else
            return max - (long) Math.sqrt((1.0 - u) * (max - min) * (max - mode));
    }

    // ── Tick delays (for use inside state machines) ───────────────────────────

    /**
     * Returns a random number of game ticks to wait before the next action.
     * Models human reaction time as a tick count.
     *   60% → 0 ticks  (react immediately)
     *   30% → 1 tick   (slight hesitation)
     *   10% → 2 ticks  (noticeable pause)
     *
     * Use with BotScript.setTickDelay() after clicking a target.
     */
    public int reactionTicks()
    {
        double r = random.nextDouble() * accountSeed;
        if (r < 0.55) return 0;
        if (r < 0.88) return 1;
        return 2;
    }

    /**
     * Returns a random tick count in the range [min, max] inclusive.
     * Use for variable waits inside state machines.
     */
    public int randomIdleTicks(int min, int max)
    {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    /** Returns true with ~2% probability, simulating an occasional misclick. */
    public boolean shouldMisclick()
    {
        return random.nextDouble() < 0.02;
    }

    /** Returns the configured click jitter radius in pixels. */
    public int getJitterRadius() { return jitterRadius; }

    // ── Mouse helpers ─────────────────────────────────────────────────────────

    /**
     * Applies a small random offset to a canvas point, simulating imprecise
     * human clicking. Returns a new Point with the offset applied.
     * Uses java.awt.Point (not net.runelite.api.Point).
     */
    public Point mouseJitter(Point point)
    {
        int dx = clamp((int) (random.nextGaussian() * jitterRadius), -2 * jitterRadius, 2 * jitterRadius);
        int dy = clamp((int) (random.nextGaussian() * jitterRadius), -2 * jitterRadius, 2 * jitterRadius);
        return new Point(point.x + dx, point.y + dy);
    }

    /**
     * Generates a quadratic Bezier curve path between two points for realistic
     * mouse movement. Returns intermediate waypoints the cursor should travel through.
     * Uses java.awt.Point (not net.runelite.api.Point).
     *
     * @param from  starting canvas position
     * @param to    target canvas position
     * @param steps number of intermediate points (more = smoother)
     */
    public Point[] mousePath(Point from, Point to, int steps)
    {
        double midX = (from.x + to.x) / 2.0;
        double midY = (from.y + to.y) / 2.0;
        double cpX  = midX + random.nextGaussian() * 30;
        double cpY  = midY + random.nextGaussian() * 30;

        Point[] path = new Point[steps];
        for (int i = 0; i < steps; i++)
        {
            double t = (double) i / (steps - 1);
            // Quadratic Bezier: P(t) = (1-t)²·P0 + 2(1-t)t·CP + t²·P1
            double x = Math.pow(1 - t, 2) * from.x
                + 2 * (1 - t) * t * cpX
                + Math.pow(t, 2) * to.x;
            double y = Math.pow(1 - t, 2) * from.y
                + 2 * (1 - t) * t * cpY
                + Math.pow(t, 2) * to.y;
            path[i] = new Point((int) x, (int) y);
        }
        return path;
    }

    /**
     * Moves the system cursor from (fromX, fromY) to (toX, toY) along a
     * quadratic Bezier curve, with a small random jitter applied to the target.
     * Replaces a direct robot.mouseMove() call in RobotClick so all widget
     * interactions travel a natural-looking path.
     *
     * @param fromX  current cursor X in screen coordinates
     * @param fromY  current cursor Y in screen coordinates
     * @param toX    target X in screen coordinates (before jitter)
     * @param toY    target Y in screen coordinates (before jitter)
     */
    public void moveMouse(int fromX, int fromY, int toX, int toY)
    {
        if (robot == null) return;
        Point jittered = mouseJitter(new Point(toX, toY));
        Point[] path   = mousePath(new Point(fromX, fromY), jittered, 12);
        for (Point p : path)
        {
            robot.mouseMove(p.x, p.y);
        }
    }

    // ── Idle action triggers ──────────────────────────────────────────────────

    /**
     * Returns true with the given probability.
     * Use to gate optional idle actions.
     * Example: if (antiban.shouldIdleAction(0.04)) antiban.performCameraJitter();
     */
    public boolean shouldIdleAction(double chance)
    {
        return random.nextDouble() < chance;
    }

    /**
     * Taps the left or right arrow key briefly to simulate a small camera
     * rotation, as a real player would occasionally adjust their view.
     */
    public void performCameraJitter()
    {
        if (robot == null) return;
        int  key    = random.nextBoolean() ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        long holdMs = randomDelay(50, 200);
        try
        {
            robot.keyPress(key);
            Thread.sleep(holdMs);
            robot.keyRelease(key);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            robot.keyRelease(key);
        }
    }

    /**
     * Briefly switches to a random non-inventory tab (Skills, Quest, etc.)
     * then returns to the Inventory tab (F4), simulating a player glancing
     * at their stats between actions.
     */
    public void performTabGlance()
    {
        if (robot == null) return;
        int  tabKey   = TAB_KEYS[random.nextInt(TAB_KEYS.length)];
        long glanceMs = randomDelay(200, 600);
        try
        {
            robot.keyPress(tabKey);
            robot.keyRelease(tabKey);
            Thread.sleep(glanceMs);
            robot.keyPress(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_F4);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            robot.keyPress(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_F4);
        }
    }

    // ── Session info ──────────────────────────────────────────────────────────

    /** Returns how long the current session has been running in milliseconds. */
    public long sessionElapsedMs()
    {
        return System.currentTimeMillis() - sessionStartMs;
    }

    /** Returns the epoch-ms timestamp when the next break is scheduled. */
    public long getNextBreakAtMs() { return nextBreakAtMs; }

    /** Returns the epoch-ms timestamp when the current break ends (0 if not on break). */
    public long getBreakEndMs() { return breakEndMs; }

    /** Returns this session's account seed (range 0.85–1.15). */
    public double getAccountSeed() { return accountSeed; }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
