package com.botengine.osrs.scripts.agility;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agility script — runs laps on a configured rooftop or ground course.
 *
 * Each course is defined as an ordered array of obstacle GameObject IDs
 * and corresponding action strings. The script clicks each obstacle in
 * sequence, waits for the player to become idle, then advances to the next.
 *
 * Mark of Grace (item ID 11849) is picked up automatically when within
 * 5 tiles if the option is enabled.
 *
 * Note: obstacle IDs are from the OSRS Wiki and should be verified in-game
 * with the Object Markers plugin if a course fails to progress.
 */
public class AgilityScript extends BotScript
{
    private static final int MARK_OF_GRACE_ID = 11849;
    private static final int CLICK_TIMEOUT    = 5;  // ticks before retry

    // ── Course data ───────────────────────────────────────────────────────────
    //
    // IDs should be verified in-game with RuneLite's Object Markers plugin.
    // If an ID is wrong the script falls back to name-based lookup and logs
    // the real ID so you can update the array below.

    private static final Map<String, int[]>      COURSE_OBSTACLES = new LinkedHashMap<>();
    private static final Map<String, String[]>   COURSE_ACTIONS   = new LinkedHashMap<>();
    private static final Map<String, String[]>   COURSE_NAMES     = new LinkedHashMap<>();

    static
    {
        // ── Gnome Stronghold ──────────────────────────────────────────────────
        // IDs verified in-game via diagnostic dump (2026-04-07):
        //   1: 23145 Log balance ✓
        //   2: 23134 Obstacle net ✓
        //   3: 23138 Obstacle pipe ✓ (2t when at obstacle 3 position)
        //   4-6: inside treehouse (Tree branch up, Balancing rope, Tree branch down)
        //   7: 23139 Obstacle pipe ✓ (Squeeze-through after tree descent)
        //   8: 23135 Obstacle net ✓ (final net — course complete)
        COURSE_OBSTACLES.put("Gnome Stronghold",
            new int[]{ 23145, 23134, 23138, 23559, 23560, 23557, 23139, 23135 });
        COURSE_ACTIONS.put("Gnome Stronghold",
            new String[]{ "Walk-on", "Climb-over", "Squeeze-through", "Climb-up",
                          "Walk-on", "Climb-down", "Squeeze-through", "Climb-over" });
        COURSE_NAMES.put("Gnome Stronghold",
            new String[]{ "Log balance", "Obstacle net", "Obstacle pipe",
                          "Tree branch", "Balancing rope", "Tree branch",
                          "Obstacle pipe", "Obstacle net" });

        // ── Draynor rooftop ───────────────────────────────────────────────────
        COURSE_OBSTACLES.put("Draynor",
            new int[]{ 11404, 11405, 11406, 11407, 11408, 11409 });
        COURSE_ACTIONS.put("Draynor",
            new String[]{ "Climb", "Walk-on", "Cross", "Climb", "Jump", "Jump" });
        COURSE_NAMES.put("Draynor",
            new String[]{ "Rough wall", "Tightrope", "Tightrope",
                          "Narrow wall", "Wall", "Gap" });

        // ── Al Kharid rooftop ─────────────────────────────────────────────────
        COURSE_OBSTACLES.put("Al Kharid",
            new int[]{ 11378, 11380, 11381, 11382, 11383, 11384, 11385, 11386 });
        COURSE_ACTIONS.put("Al Kharid",
            new String[]{ "Climb", "Cross", "Climb", "Climb", "Cross",
                          "Climb", "Cross", "Jump" });
        COURSE_NAMES.put("Al Kharid",
            new String[]{ "Rough wall", "Tightrope", "Cable", "Tree",
                          "Tightrope", "Roof top", "Tightrope", "Gap" });

        // ── Varrock rooftop ───────────────────────────────────────────────────
        COURSE_OBSTACLES.put("Varrock",
            new int[]{ 14412, 14413, 14414, 14832, 14415, 14416, 14417, 14418 });
        COURSE_ACTIONS.put("Varrock",
            new String[]{ "Climb", "Leap", "Hurdle", "Jump-up", "Leap", "Leap", "Climb", "Jump" });
        COURSE_NAMES.put("Varrock",
            new String[]{ "Rough wall", "Clothes line", "Gap", "Wall",
                          "Gap", "Gap", "Ledge", "Edge" });

        // ── Canifis rooftop ───────────────────────────────────────────────────
        COURSE_OBSTACLES.put("Canifis",
            new int[]{ 14856, 14857, 14858, 14859, 14860, 14861, 14862 });
        COURSE_ACTIONS.put("Canifis",
            new String[]{ "Climb", "Jump", "Jump", "Jump", "Vault", "Jump", "Jump" });
        COURSE_NAMES.put("Canifis",
            new String[]{ "Tall tree", "Gap", "Gap", "Gap",
                          "Polevault", "Gap", "Gap" });
    }

    // ── State machine ─────────────────────────────────────────────────────────

    private enum State { FIND_OBSTACLE, CLICKING_OBSTACLE, WAITING, PICK_MARK, COURSE_COMPLETE }

    private State      state                = State.FIND_OBSTACLE;
    private String     courseName           = "Gnome Stronghold";
    private boolean    pickupMarks          = true;
    private int[]      obstacleIds;
    private String[]   obstacleActions;
    private String[]   obstacleNames;
    private int        currentObstacleIndex = 0;
    private int        ticksWaited          = 0;
    private WorldPoint lastPosition         = null;
    private int        stablePosTicks       = 0;
    private int        lastAgilityXp        = 0;
    private WorldPoint clickedFromPosition  = null;

    @Inject
    public AgilityScript() {}

    @Override
    public String getName() { return "Agility"; }

    @Override
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings)
    {
        if (scriptSettings instanceof AgilitySettings)
        {
            AgilitySettings s = (AgilitySettings) scriptSettings;
            courseName  = s.courseName;
            pickupMarks = s.pickupMarks;
        }
    }

    @Override
    public ScriptConfigDialog<?> createConfigDialog(JComponent parent)
    {
        return new AgilityConfigDialog(parent);
    }

    @Override
    public void onStart()
    {
        obstacleIds     = COURSE_OBSTACLES.getOrDefault(courseName,
                              COURSE_OBSTACLES.get("Gnome Stronghold"));
        obstacleActions = COURSE_ACTIONS.getOrDefault(courseName,
                              COURSE_ACTIONS.get("Gnome Stronghold"));
        obstacleNames   = COURSE_NAMES.getOrDefault(courseName,
                              COURSE_NAMES.get("Gnome Stronghold"));
        currentObstacleIndex = 0;
        stablePosTicks       = 0;
        lastPosition         = null;
        lastAgilityXp        = client.getSkillExperience(Skill.AGILITY);
        clickedFromPosition  = null;
        state = State.FIND_OBSTACLE;
        log.info("Agility started — course='{}' pickupMarks={}", courseName, pickupMarks);
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case FIND_OBSTACLE:    findObstacle();    break;
            case CLICKING_OBSTACLE: clickingObstacle(); break;
            case WAITING:          waiting();         break;
            case PICK_MARK:        pickMark();        break;
            case COURSE_COMPLETE:  courseComplete();  break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Agility stopped after {} obstacles", currentObstacleIndex);
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void findObstacle()
    {
        // Check for Mark of Grace first
        if (pickupMarks)
        {
            GroundItems.TileItemOnTile mark = groundItems.nearestWithTile(MARK_OF_GRACE_ID);
            if (mark != null && players.distanceTo(mark.tile.getWorldLocation()) <= 5)
            {
                state = State.PICK_MARK;
                return;
            }
        }

        int        obstacleId   = obstacleIds[currentObstacleIndex];
        String     obstacleName = obstacleNames[currentObstacleIndex];
        String     action       = obstacleActions[currentObstacleIndex];

        // Search ALL tile object types (GameObject, WallObject, DecorativeObject, GroundObject)
        TileObject obstacle = gameObjects.nearestTileObject(obstacleId);

        if (obstacle == null)
        {
            // ID not found — fall back to name lookup across all tile object types
            obstacle = gameObjects.nearestTileObject(obstacleName);
            if (obstacle == null)
            {
                log.warn("Obstacle [{}/{}] not found: expected id={} name='{}'. Nearby objects: {}",
                    currentObstacleIndex + 1, obstacleIds.length, obstacleId, obstacleName,
                    gameObjects.dumpNearby(15));
                return;
            }
            // Found by name — log the real ID so we can update the hardcoded array
            log.warn("Obstacle [{}/{}] id={} wrong — found '{}' by name, real ID={}. Update COURSE_OBSTACLES.",
                currentObstacleIndex + 1, obstacleIds.length,
                obstacleId, obstacleName, obstacle.getId());
            obstacleIds[currentObstacleIndex] = obstacle.getId(); // patch for this session
        }

        log.info("Clicking obstacle [{}/{}] id={} name='{}' action='{}'",
            currentObstacleIndex + 1, obstacleIds.length,
            obstacle.getId(), obstacleName, action);
        clickedFromPosition = players.getLocation();
        interaction.click(obstacle, action);
        antiban.reactionDelay();
        ticksWaited = 0;
        state = State.CLICKING_OBSTACLE;
    }

    private void clickingObstacle()
    {
        ticksWaited++;
        if (players.getAnimation() != -1)
        {
            // Animation started — obstacle was accepted
            ticksWaited = 0;
            state = State.WAITING;
            return;
        }
        if (clickedFromPosition != null && !players.getLocation().equals(clickedFromPosition))
        {
            // Player moved away from the click position — walking to or on the obstacle
            ticksWaited = 0;
            state = State.WAITING;
            return;
        }
        if (ticksWaited >= CLICK_TIMEOUT)
        {
            // Click not registered — likely wrong obstacle ID or wrong action string.
            // Check RuneLite logs for the obstacle ID and verify with Object Markers plugin.
            log.warn("Obstacle click timeout ({}t) — obstacle [{}/{}] id={} may have wrong ID or action",
                CLICK_TIMEOUT, currentObstacleIndex + 1, obstacleIds.length,
                obstacleIds[currentObstacleIndex]);
            ticksWaited = 0;
            state = State.FIND_OBSTACLE;
        }
    }

    private void waiting()
    {
        // Primary completion signal: agility XP increase (server-authoritative, fires exactly
        // once per obstacle regardless of obstacle type, animation, or equipment worn).
        int xp = client.getSkillExperience(Skill.AGILITY);
        if (xp > lastAgilityXp)
        {
            lastAgilityXp  = xp;
            ticksWaited    = 0;
            stablePosTicks = 0;
            lastPosition   = null;
            advanceObstacle();
            return;
        }

        // Fallback: position stability for up to 20 ticks (catches obstacles that award
        // no XP, or in case the XP event fires on a different tick than expected).
        WorldPoint pos = players.getLocation();
        if (pos.equals(lastPosition))
        {
            stablePosTicks++;
        }
        else
        {
            stablePosTicks = 0;
            lastPosition   = pos;
        }

        if (stablePosTicks >= 4 && players.getAnimation() == -1)
        {
            // Stable 4 ticks, no animation, no XP — assume complete (e.g. Mark of Grace pickup)
            stablePosTicks = 0;
            lastPosition   = null;
            advanceObstacle();
            return;
        }

        // Hard timeout — prevents getting stuck if XP never fires
        ticksWaited++;
        if (ticksWaited > 20)
        {
            log.warn("WAITING timeout (20t) on obstacle [{}/{}] — no XP received, retrying click",
                currentObstacleIndex + 1, obstacleIds.length);
            ticksWaited    = 0;
            stablePosTicks = 0;
            lastPosition   = null;
            state = State.FIND_OBSTACLE;
        }
    }

    private void advanceObstacle()
    {
        currentObstacleIndex++;
        if (currentObstacleIndex >= obstacleIds.length)
        {
            currentObstacleIndex = 0;
            state = State.COURSE_COMPLETE;
        }
        else
        {
            antiban.gaussianDelay(200, 60);
            state = State.FIND_OBSTACLE;
        }
    }

    private void pickMark()
    {
        GroundItems.TileItemOnTile mark = groundItems.nearestWithTile(MARK_OF_GRACE_ID);
        if (mark != null)
        {
            interaction.click(mark.item, mark.tile);
            antiban.reactionDelay();
        }
        state = State.FIND_OBSTACLE;
    }

    private void courseComplete()
    {
        log.info("Lap complete on {}", courseName);
        antiban.gaussianDelay(300, 80);
        state = State.FIND_OBSTACLE;
    }
}
