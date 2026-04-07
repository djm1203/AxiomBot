package com.botengine.osrs.scripts.agility;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import net.runelite.api.GameObject;

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

    private static final Map<String, int[]> COURSE_OBSTACLES = new LinkedHashMap<>();
    private static final Map<String, String[]> COURSE_ACTIONS = new LinkedHashMap<>();

    static
    {
        COURSE_OBSTACLES.put("Gnome Stronghold",
            new int[]{ 23145, 23134, 23559, 23560, 23557, 23132, 23131 });
        COURSE_ACTIONS.put("Gnome Stronghold",
            new String[]{ "Walk-on", "Climb-over", "Climb-up", "Walk-on",
                          "Climb-down", "Climb-over", "Squeeze-through" });

        COURSE_OBSTACLES.put("Draynor",
            new int[]{ 11404, 11405, 11406, 11407, 11408, 11409 });
        COURSE_ACTIONS.put("Draynor",
            new String[]{ "Climb", "Climb", "Climb", "Climb", "Jump", "Jump" });

        COURSE_OBSTACLES.put("Al Kharid",
            new int[]{ 11378, 11380, 11381, 11382, 11383, 11384, 11385, 11386 });
        COURSE_ACTIONS.put("Al Kharid",
            new String[]{ "Climb", "Climb", "Climb", "Climb", "Climb", "Climb", "Climb", "Climb" });

        COURSE_OBSTACLES.put("Varrock",
            new int[]{ 14412, 14413, 14414, 14832, 14415, 14416, 14417, 14418 });
        COURSE_ACTIONS.put("Varrock",
            new String[]{ "Climb", "Climb", "Jump", "Climb", "Jump", "Jump", "Climb", "Jump" });

        COURSE_OBSTACLES.put("Canifis",
            new int[]{ 14856, 14857, 14858, 14859, 14860, 14861, 14862 });
        COURSE_ACTIONS.put("Canifis",
            new String[]{ "Climb", "Jump", "Jump", "Jump", "Vault", "Jump", "Jump" });
    }

    // ── State machine ─────────────────────────────────────────────────────────

    private enum State { FIND_OBSTACLE, CLICKING_OBSTACLE, WAITING, PICK_MARK, COURSE_COMPLETE }

    private State    state                = State.FIND_OBSTACLE;
    private String   courseName           = "Gnome Stronghold";
    private boolean  pickupMarks          = true;
    private int[]    obstacleIds;
    private String[] obstacleActions;
    private int      currentObstacleIndex = 0;
    private int      ticksWaited          = 0;

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
        currentObstacleIndex = 0;
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

        int obstacleId = obstacleIds[currentObstacleIndex];
        GameObject obstacle = gameObjects.nearest(obstacleId);
        if (obstacle == null)
        {
            // Not yet loaded in scene — wait.
            // If this keeps printing, the obstacle ID is wrong for this course.
            // Use RuneLite's Object Markers plugin to find the correct ID in-game.
            log.debug("Waiting for obstacle [{}/{}] id={} action='{}'",
                currentObstacleIndex + 1, obstacleIds.length,
                obstacleId, obstacleActions[currentObstacleIndex]);
            return;
        }

        String action = obstacleActions[currentObstacleIndex];
        log.info("Clicking obstacle [{}/{}] id={} action='{}'",
            currentObstacleIndex + 1, obstacleIds.length, obstacleId, action);
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
        if (players.isMoving())
        {
            // Walking to obstacle
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
        if (!players.isIdle() || players.isMoving())
        {
            return;
        }

        // Player is idle — obstacle complete, advance
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
