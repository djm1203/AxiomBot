package com.botengine.osrs.util;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Random;

/**
 * Human-like behavior simulation.
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
 *
 * All timing values have randomness built in so patterns are never identical
 * across sessions.
 */
@Slf4j
@Singleton
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

    // ── Configuration (set by BotEngineConfig) ────────────────────────────────

    /** Average minutes between breaks. Default: 45 minutes. */
    @Setter
    private int breakIntervalMinutes = 45;

    /** Average minutes per break. Default: 7 minutes. */
    @Setter
    private int breakDurationMinutes = 7;

    /** Maximum pixel jitter radius applied to click coordinates. Default: 3px. */
    @Setter
    private int jitterRadius = 3;

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

    /**
     * Returns true when it's time to take an antiban break.
     * Called by ScriptRunner once per game tick.
     */
    public boolean shouldTakeBreak()
    {
        return System.currentTimeMillis() >= nextBreakAtMs;
    }

    /**
     * Records the break start time and schedules the break end.
     * Called by ScriptRunner when transitioning to BREAKING state.
     */
    public void startBreak()
    {
        breakStartMs = System.currentTimeMillis();
        // Break duration: configured average ± 30% variance
        long variance = (long) (breakDurationMinutes * 60_000 * 0.3);
        long breakMs = (long) (breakDurationMinutes * 60_000)
            + (long) (random.nextGaussian() * variance);
        breakMs = Math.max(breakMs, 60_000); // minimum 1 minute
        breakEndMs = breakStartMs + breakMs;
    }

    /**
     * Returns true when the current break has lasted long enough.
     * Called by ScriptRunner each tick while in BREAKING state.
     */
    public boolean isBreakOver()
    {
        if (System.currentTimeMillis() >= breakEndMs)
        {
            scheduleNextBreak(); // schedule the next break immediately so the cycle continues
            return true;
        }
        return false;
    }

    /**
     * Schedules the next break based on configured interval ± 20% variance.
     */
    private void scheduleNextBreak()
    {
        long variance = (long) (breakIntervalMinutes * 60_000 * 0.2);
        long intervalMs = (long) (breakIntervalMinutes * 60_000)
            + (long) (random.nextGaussian() * variance);
        intervalMs = Math.max(intervalMs, 5 * 60_000); // minimum 5 minutes
        nextBreakAtMs = System.currentTimeMillis() + intervalMs;
    }

    // ── Delay utilities ───────────────────────────────────────────────────────

    /**
     * Returns a delay multiplier based on elapsed session time.
     * Models human fatigue: delays gradually increase over a long session.
     *   0–30 min  → 1.00  (fresh)
     *   30–60 min → 1.00–1.08
     *   60–120 min → 1.08–1.20
     *   120+ min  → 1.20–1.30 (tired, capped)
     */
    public double fatigueFactor()
    {
        long elapsedMin = sessionElapsedMs() / 60_000;
        if (elapsedMin <= 30)  return 1.0;
        if (elapsedMin <= 60)  return 1.0  + (elapsedMin - 30)  / 30.0 * 0.08;
        if (elapsedMin <= 120) return 1.08 + (elapsedMin - 60)  / 60.0 * 0.12;
        return 1.30;
    }

    /**
     * Returns a delay in milliseconds sampled from a Gaussian distribution.
     * Useful for action-to-action delays that should feel human.
     *
     * @param meanMs   target delay in milliseconds (e.g. 650 for ~1 tick)
     * @param stdDevMs standard deviation (e.g. 80 for natural variance)
     * @return delay clamped to [meanMs * 0.4, meanMs * 2.5] to avoid extremes
     *
     * Example:
     *   time.sleep(antiban.gaussianDelay(650, 80)); // ~1 tick with variance
     */
    public long gaussianDelay(long meanMs, long stdDevMs)
    {
        long delay = (long) (meanMs * accountSeed) + (long) (random.nextGaussian() * stdDevMs);
        long min = (long) (meanMs * 0.4);
        long max = (long) (meanMs * 2.5);
        return Math.max(min, Math.min(max, delay));
    }

    /**
     * Returns a uniformly random delay between min and max milliseconds.
     * Use this when you want a flat range rather than a bell curve.
     */
    public long randomDelay(long minMs, long maxMs)
    {
        if (minMs >= maxMs) return minMs;
        return minMs + (long) (random.nextDouble() * (maxMs - minMs));
    }

    /**
     * Returns a reaction time delay using a triangular distribution.
     *
     * Triangular distribution matches human reaction time data better than
     * Gaussian — it has a clear minimum (can't react instantly), a most-likely
     * response time, and a long tail for slow responses.
     *
     * Default range: 80ms min, 160ms mode, 400ms max (ABC2-spec inspired).
     * Fatigue and account seed shift the distribution slightly each session.
     */
    public long reactionDelay()
    {
        double multiplier = accountSeed * fatigueFactor();
        return (long) (triangularDelay(80, 160, 400) * multiplier);
    }

    /**
     * Triangular distribution delay — more ABC2-accurate than Gaussian.
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

    /** Returns this session's account seed (range 0.85–1.15). */
    public double getAccountSeed() { return accountSeed; }

    /**
     * Returns a random number of game ticks to wait before the next action.
     * Models human reaction time as a tick count rather than milliseconds.
     *   60% → 0 ticks  (react immediately on same tick)
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
     * Use for variable waits inside state machines (e.g. wait 1–3 ticks before re-mining).
     */
    public int randomIdleTicks(int min, int max)
    {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Returns true with ~2% probability, simulating an occasional misclick.
     * The caller is responsible for re-clicking the correct target.
     */
    public boolean shouldMisclick()
    {
        return random.nextDouble() < 0.02;
    }

    /** Returns the configured click jitter radius in pixels. */
    public int getJitterRadius() { return jitterRadius; }

    // ── Mouse jitter ─────────────────────────────────────────────────────────

    /**
     * Applies a small random offset to a canvas point, simulating imprecise
     * human clicking. The offset is within jitterRadius pixels of the original.
     *
     * @param point  original click target (canvas coordinates)
     * @return new point with random offset applied
     */
    public Point mouseJitter(Point point)
    {
        int dx = clamp((int) (random.nextGaussian() * jitterRadius), -2 * jitterRadius, 2 * jitterRadius);
        int dy = clamp((int) (random.nextGaussian() * jitterRadius), -2 * jitterRadius, 2 * jitterRadius);
        return new Point(point.getX() + dx, point.getY() + dy);
    }

    /**
     * Generates a Bezier curve path between two points for realistic mouse movement.
     *
     * Returns an array of intermediate points the mouse should travel through.
     * The control point is randomly offset from the midpoint to create a curve.
     *
     * @param from  starting canvas position
     * @param to    target canvas position
     * @param steps number of intermediate points (more = smoother, slower)
     * @return array of points forming the movement path
     */
    public Point[] mousePath(Point from, Point to, int steps)
    {
        // Random control point offset from midpoint (creates the curve)
        double midX = (from.getX() + to.getX()) / 2.0;
        double midY = (from.getY() + to.getY()) / 2.0;
        double cpX = midX + random.nextGaussian() * 30;
        double cpY = midY + random.nextGaussian() * 30;

        Point[] path = new Point[steps];
        for (int i = 0; i < steps; i++)
        {
            double t = (double) i / (steps - 1);
            // Quadratic Bezier: P(t) = (1-t)²·P0 + 2(1-t)t·CP + t²·P1
            double x = Math.pow(1 - t, 2) * from.getX()
                + 2 * (1 - t) * t * cpX
                + Math.pow(t, 2) * to.getX();
            double y = Math.pow(1 - t, 2) * from.getY()
                + 2 * (1 - t) * t * cpY
                + Math.pow(t, 2) * to.getY();
            path[i] = new Point((int) x, (int) y);
        }
        return path;
    }

    // ── Session info ──────────────────────────────────────────────────────────

    /** Returns how long the current session has been running in milliseconds. */
    public long sessionElapsedMs()
    {
        return System.currentTimeMillis() - sessionStartMs;
    }

    /** Returns the epoch-ms timestamp when the next break is scheduled. */
    public long getNextBreakAtMs()
    {
        return nextBreakAtMs;
    }

    /** Returns the epoch-ms timestamp when the current break ends (0 if not on break). */
    public long getBreakEndMs()
    {
        return breakEndMs;
    }

    // ── Idle action triggers ──────────────────────────────────────────────────

    /**
     * Returns true with the given probability. Use to gate optional idle actions.
     * Example: if (antiban.shouldIdleAction(0.05)) antiban.performCameraJitter();
     *
     * @param chance probability in range [0.0, 1.0]
     */
    public boolean shouldIdleAction(double chance)
    {
        return random.nextDouble() < chance;
    }

    /**
     * Taps the left or right arrow key briefly to simulate a small camera
     * rotation, as a real player would occasionally adjust their view.
     *
     * Duration is randomized between 50–200ms to produce varied rotation amounts.
     * Does nothing if Robot is unavailable.
     */
    public void performCameraJitter()
    {
        if (robot == null) return;
        int key = random.nextBoolean() ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        long holdMs = randomDelay(50, 200);
        try
        {
            robot.keyPress(key);
            Thread.sleep(holdMs);
            robot.keyRelease(key);
            log.debug("Antiban: camera jitter (key={} hold={}ms)", key == KeyEvent.VK_LEFT ? "LEFT" : "RIGHT", holdMs);
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
     * at their stats or quest log between actions.
     *
     * Does nothing if Robot is unavailable.
     */
    public void performTabGlance()
    {
        if (robot == null) return;
        int tabKey = TAB_KEYS[random.nextInt(TAB_KEYS.length)];
        long glanceMs = randomDelay(200, 600);
        try
        {
            robot.keyPress(tabKey);
            robot.keyRelease(tabKey);
            Thread.sleep(glanceMs);
            robot.keyPress(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_F4);
            log.debug("Antiban: tab glance (tab=F{} duration={}ms)", tabKey - KeyEvent.VK_F1 + 1, glanceMs);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            robot.keyPress(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_F4);
        }
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
