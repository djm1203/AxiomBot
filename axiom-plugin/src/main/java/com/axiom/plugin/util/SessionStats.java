package com.axiom.plugin.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tracks per-session XP gains, level-ups, and derived stats (XP/hr).
 *
 * Lifecycle:
 *   reset()   — called when a script starts; snapshots baseline XP/levels
 *   onTick()  — called each game tick while RUNNING; samples XP, queues level-ups
 *   stop()    — called when script stops; marks session inactive
 *   pollLevelUps() — drains and returns pending level-up messages (caller logs them)
 *
 * Design notes:
 * - Uses client.getRealSkillLevel() directly — no custom XP table needed.
 * - Skips Skill.OVERALL to avoid contaminating top-skill calculations.
 * - XP/hr requires at least 10 seconds of session time to avoid wild spikes.
 * - Thread-safe at read level: all writes happen on the game tick thread;
 *   getters are safe to call from the overlay render thread.
 */
@Slf4j
@Singleton
public class SessionStats
{
    private static final int    MIN_ELAPSED_MS_FOR_RATE = 10_000;
    private static final Skill[] ALL_SKILLS             = Skill.values();

    private final Client client;

    // ── Baseline snapshot (set at reset()) ───────────────────────────────────
    private final int[] startXp    = new int[ALL_SKILLS.length];
    private final int[] startLevel = new int[ALL_SKILLS.length];

    // ── Rolling state (updated each tick) ────────────────────────────────────
    private final int[] currentXp    = new int[ALL_SKILLS.length];
    private final int[] lastLevel    = new int[ALL_SKILLS.length];

    // ── Aggregate stats ───────────────────────────────────────────────────────
    private volatile Skill topSkill        = null;
    private volatile int   topXpGained     = 0;
    private volatile int   topLevelsGained = 0;

    // ── Timing ────────────────────────────────────────────────────────────────
    private volatile long    sessionStartMs = 0;
    private volatile boolean active         = false;

    // ── Level-up event queue ──────────────────────────────────────────────────
    private final Deque<String> levelUpQueue = new ArrayDeque<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    @Inject
    public SessionStats(Client client)
    {
        this.client = client;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Snapshots current XP and levels as the baseline for this session.
     * Must be called from the game tick thread (e.g., just before script.onStart()).
     */
    public void reset()
    {
        sessionStartMs = System.currentTimeMillis();
        topSkill        = null;
        topXpGained     = 0;
        topLevelsGained = 0;
        levelUpQueue.clear();

        for (int i = 0; i < ALL_SKILLS.length; i++)
        {
            Skill s = ALL_SKILLS[i];
            if (s == Skill.OVERALL) continue;

            int xp    = client.getSkillExperience(s);
            int level = client.getRealSkillLevel(s);

            startXp[i]    = xp;
            currentXp[i]  = xp;
            startLevel[i] = level;
            lastLevel[i]  = level;
        }

        active = true;
        log.debug("SessionStats reset — baseline captured");
    }

    /** Called when the script stops. Freezes stats in their final state. */
    public void stop()
    {
        active = false;
        log.debug("SessionStats stopped — session elapsed {}ms", getElapsedMs());
    }

    /**
     * Samples current XP, detects level-ups, and updates aggregate stats.
     * Should be called once per game tick while the script is RUNNING.
     */
    public void onTick()
    {
        if (!active) return;

        Skill  newTopSkill        = topSkill;
        int    newTopXpGained     = topXpGained;
        int    newTopLevelsGained = topLevelsGained;

        for (int i = 0; i < ALL_SKILLS.length; i++)
        {
            Skill s = ALL_SKILLS[i];
            if (s == Skill.OVERALL) continue;

            int xp    = client.getSkillExperience(s);
            int level = client.getRealSkillLevel(s);

            currentXp[i] = xp;

            // Level-up detection
            if (level > lastLevel[i])
            {
                int gained = level - lastLevel[i];
                log.debug("Level-up detected: {} {} → {}", s.getName(), lastLevel[i], level);
                for (int l = lastLevel[i] + 1; l <= level; l++)
                {
                    levelUpQueue.add(String.format("Level up! %s is now level %d.", s.getName(), l));
                }
                lastLevel[i] = level;
            }

            // Top-skill tracking (by XP gained this session)
            int xpGained = xp - startXp[i];
            if (xpGained > newTopXpGained)
            {
                newTopXpGained = xpGained;
                newTopSkill    = s;
            }

            // Top-levels tracking
            int levelsGained = level - startLevel[i];
            if (levelsGained > newTopLevelsGained)
            {
                newTopLevelsGained = levelsGained;
            }
        }

        topSkill        = newTopSkill;
        topXpGained     = newTopXpGained;
        topLevelsGained = newTopLevelsGained;
    }

    /**
     * Drains all pending level-up messages. Safe to call from any thread —
     * caller (ScriptRunner) logs them via botLog on the tick thread.
     */
    public List<String> pollLevelUps()
    {
        if (levelUpQueue.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(levelUpQueue);
        levelUpQueue.clear();
        return result;
    }

    // ── Getters (safe to call from overlay render thread) ─────────────────────

    /** The skill with the most XP gained this session, or null if no XP gained yet. */
    public Skill getTopSkill() { return topSkill; }

    /** Total XP gained in the top skill this session. */
    public int getTopXpGained() { return topXpGained; }

    /** Highest number of levels gained in a single skill this session. */
    public int getTopLevelsGained() { return topLevelsGained; }

    /** Total XP gained across ALL skills this session. */
    public int getTotalXpGained()
    {
        int total = 0;
        for (int i = 0; i < ALL_SKILLS.length; i++)
        {
            if (ALL_SKILLS[i] == Skill.OVERALL) continue;
            total += Math.max(0, currentXp[i] - startXp[i]);
        }
        return total;
    }

    /** Total levels gained across ALL skills this session. */
    public int getTotalLevelsGained()
    {
        int total = 0;
        for (int i = 0; i < ALL_SKILLS.length; i++)
        {
            if (ALL_SKILLS[i] == Skill.OVERALL) continue;
            total += Math.max(0, lastLevel[i] - startLevel[i]);
        }
        return total;
    }

    /** Whether a session is active (between reset() and stop()). */
    public boolean isActive() { return active; }

    /** Milliseconds elapsed since session start, or 0 if not active. */
    public long getElapsedMs()
    {
        return active ? System.currentTimeMillis() - sessionStartMs : 0;
    }

    /**
     * XP gained per hour for the top skill.
     * Returns 0 if fewer than 10 seconds have elapsed (avoids erratic early readings).
     */
    public int getXpPerHour()
    {
        long elapsed = getElapsedMs();
        if (elapsed < MIN_ELAPSED_MS_FOR_RATE || topXpGained <= 0) return 0;
        return (int) (topXpGained * 3_600_000L / elapsed);
    }

    /**
     * XP gained per hour across all skills combined.
     * Returns 0 if fewer than 10 seconds have elapsed.
     */
    public int getTotalXpPerHour()
    {
        long elapsed = getElapsedMs();
        int total = getTotalXpGained();
        if (elapsed < MIN_ELAPSED_MS_FOR_RATE || total <= 0) return 0;
        return (int) (total * 3_600_000L / elapsed);
    }
}
