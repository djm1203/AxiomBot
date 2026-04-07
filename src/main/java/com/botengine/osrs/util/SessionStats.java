package com.botengine.osrs.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tracks XP gain and level-ups for the active script session.
 *
 * Call reset(client) when a script starts to snapshot starting XP.
 * Call sample(client) each overlay render to get up-to-date rates.
 *
 * Auto-detects which skill is gaining the most XP and reports that
 * as the "primary" skill — no per-script configuration needed.
 */
@Slf4j
@Singleton
public class SessionStats
{
    private static final Skill[] ALL_SKILLS = Skill.values();

    private long   sessionStartMs   = 0;
    private int[]  startXp          = new int[ALL_SKILLS.length];
    private int[]  currentXp        = new int[ALL_SKILLS.length];
    private Skill  topSkill         = null;
    private int    topXpGained      = 0;
    private int    topLevelsGained  = 0;
    private boolean active          = false;

    @Inject
    public SessionStats() {}

    /** Called by ScriptRunner when a script starts. Snapshots current XP. */
    public void reset(Client client)
    {
        sessionStartMs  = System.currentTimeMillis();
        topSkill        = null;
        topXpGained     = 0;
        topLevelsGained = 0;
        active          = true;

        for (int i = 0; i < ALL_SKILLS.length; i++)
        {
            try
            {
                int xp = client.getSkillExperience(ALL_SKILLS[i]);
                startXp[i]   = xp;
                currentXp[i] = xp;
            }
            catch (Exception e)
            {
                startXp[i] = currentXp[i] = 0;
            }
        }
    }

    /** Called by ScriptRunner when a script stops. */
    public void stop()
    {
        active = false;
    }

    /**
     * Samples current XP from the client and recomputes top skill.
     * Call this once per overlay render frame (or once per tick).
     */
    public void sample(Client client)
    {
        if (!active) return;

        int bestGain = 0;
        Skill bestSkill = null;

        for (int i = 0; i < ALL_SKILLS.length; i++)
        {
            try
            {
                currentXp[i] = client.getSkillExperience(ALL_SKILLS[i]);
                int gained = currentXp[i] - startXp[i];
                if (gained > bestGain)
                {
                    bestGain  = gained;
                    bestSkill = ALL_SKILLS[i];
                }
            }
            catch (Exception ignored) {}
        }

        if (bestSkill != null)
        {
            topSkill        = bestSkill;
            topXpGained     = bestGain;
            int idx         = bestSkill.ordinal();
            int startLevel  = xpToLevel(startXp[idx]);
            int curLevel    = xpToLevel(currentXp[idx]);
            topLevelsGained = Math.max(0, curLevel - startLevel);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The skill that has gained the most XP this session. Null if no XP gained yet. */
    public Skill getTopSkill() { return topSkill; }

    /** Total XP gained in the top skill this session. */
    public int getTopXpGained() { return topXpGained; }

    /** Levels gained in the top skill this session. */
    public int getTopLevelsGained() { return topLevelsGained; }

    /**
     * XP per hour for the top skill. Returns 0 if session is too short to compute.
     */
    public int getXpPerHour()
    {
        if (!active || topSkill == null || topXpGained == 0) return 0;
        long elapsedMs = System.currentTimeMillis() - sessionStartMs;
        if (elapsedMs < 10_000) return 0; // need at least 10s of data
        return (int) (topXpGained * 3_600_000L / elapsedMs);
    }

    public boolean isActive() { return active; }

    // ── XP → Level lookup ─────────────────────────────────────────────────────

    private static final int[] XP_TABLE = {
              0,    83,   174,   276,   388,   512,   650,   801,   969,  1154,
           1358,  1584,  1833,  2107,  2411,  2746,  3115,  3523,  3973,  4470,
           5018,  5624,  6291,  7028,  7842,  8740,  9730, 10824, 12031, 13363,
          14833, 16456, 18247, 20224, 22406, 24815, 27473, 30408, 33648, 37224,
          41171, 45529, 50339, 55649, 61512, 67983, 75127, 83014, 91721,101333,
         111945,123660,136594,150872,166636,184040,203254,224466,247886,273742,
         302288,333804,368599,407015,449428,496254,547953,605032,668051,737627,
         814445,899257,992895,1096278,1210421,1336443,1475581,1629200,1798808,
        1986068,2192818,2421087,2673114,2951373,3258594,3597792,3972294,4385776,
        4842295,5346332,5902831,6517253,7195629,7944614,8771558,9684577,10692629,
        11805606,13034431
    };

    private static int xpToLevel(int xp)
    {
        int level = 1;
        for (int i = 0; i < XP_TABLE.length; i++)
        {
            if (xp >= XP_TABLE[i]) level = i + 1;
            else break;
        }
        return Math.min(level, 99);
    }
}
