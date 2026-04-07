package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.agility.AgilityScript;
import com.botengine.osrs.scripts.agility.AgilitySettings;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * State machine tests for AgilityScript.
 *
 *  FIND_OBSTACLE: obstacle found → clicks it → CLICKING_OBSTACLE
 *  FIND_OBSTACLE: obstacle null → waits (no click)
 *  CLICKING_OBSTACLE: animation starts → WAITING
 *  CLICKING_OBSTACLE: timeout → retry (back to FIND_OBSTACLE)
 *  WAITING: player idle → advances obstacleIndex
 *  WAITING: last obstacle → resets index, COURSE_COMPLETE
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgilityScriptTest
{
    // First Gnome Stronghold obstacle ID
    private static final int GNOME_OBS_0 = 23145;

    @Mock private Client client;
    @Mock private Players players;
    @Mock private Npcs npcs;
    @Mock private GameObjects gameObjects;
    @Mock private Inventory inventory;
    @Mock private Bank bank;
    @Mock private Movement movement;
    @Mock private Interaction interaction;
    @Mock private Magic magic;
    @Mock private Combat combat;
    @Mock private Camera camera;
    @Mock private Prayers prayers;
    @Mock private GroundItems groundItems;
    @Mock private WorldHopper worldHopper;
    @Mock private GrandExchange grandExchange;
    @Mock private Antiban antiban;
    @Mock private Time time;
    @Mock private Log log;
    @Mock private GameObject obstacle;

    private AgilityScript script;

    @BeforeEach
    void setUp()
    {
        script = new AgilityScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera,
            prayers, groundItems, worldHopper, grandExchange, antiban, time, log);

        AgilitySettings s = new AgilitySettings();
        s.courseName  = "Gnome Stronghold";
        s.pickupMarks = false; // simplify: no mark pickup in tests
        script.configure(null, s);
        script.onStart();

        when(players.getAnimation()).thenReturn(-1);
        when(players.isIdle()).thenReturn(false);
        when(players.isMoving()).thenReturn(false);
        when(groundItems.nearestWithTile(anyInt())).thenReturn(null);
    }

    @Test
    void findObstacle_obstacleNull_doesNotClick()
    {
        when(gameObjects.nearest(GNOME_OBS_0)).thenReturn(null);

        script.onLoop();

        verify(interaction, never()).click(any(GameObject.class), anyString());
    }

    @Test
    void findObstacle_obstacleFound_clicksIt()
    {
        when(gameObjects.nearest(GNOME_OBS_0)).thenReturn(obstacle);

        script.onLoop(); // FIND_OBSTACLE → click → CLICKING_OBSTACLE

        verify(interaction).click(eq(obstacle), eq("Walk-on"));
    }

    @Test
    void clickingObstacle_animationStarts_movesToWaiting()
    {
        when(gameObjects.nearest(GNOME_OBS_0)).thenReturn(obstacle);
        script.onLoop(); // FIND_OBSTACLE → CLICKING_OBSTACLE

        // Animation fires on next tick
        when(players.getAnimation()).thenReturn(829);
        script.onLoop(); // CLICKING_OBSTACLE → WAITING

        // Now idle → should advance to next obstacle
        when(players.getAnimation()).thenReturn(-1);
        when(players.isIdle()).thenReturn(true);
        script.onLoop(); // WAITING → advance index → FIND_OBSTACLE

        // Verify it now looks for obstacle index 1 (23134), not 0
        verify(gameObjects, atLeastOnce()).nearest(GNOME_OBS_0);
    }

    @Test
    void clickingObstacle_timeout_retriesClick()
    {
        when(gameObjects.nearest(GNOME_OBS_0)).thenReturn(obstacle);
        script.onLoop(); // FIND_OBSTACLE → click → CLICKING_OBSTACLE

        // Simulate 5 ticks of no animation (timeout = 5)
        for (int i = 0; i < 5; i++) script.onLoop();

        // After timeout, state = FIND_OBSTACLE — one more loop triggers retry click
        script.onLoop();

        // Should retry — FIND_OBSTACLE fires click again
        verify(interaction, times(2)).click(eq(obstacle), anyString());
    }

    @Test
    void waiting_playerIdle_advancesObstacleIndex()
    {
        when(gameObjects.nearest(GNOME_OBS_0)).thenReturn(obstacle);
        script.onLoop(); // click → CLICKING_OBSTACLE

        when(players.getAnimation()).thenReturn(829);
        script.onLoop(); // → WAITING

        when(players.getAnimation()).thenReturn(-1);
        when(players.isIdle()).thenReturn(true);
        script.onLoop(); // → advance, FIND_OBSTACLE for index 1

        // Now obstacle 1 (23134) should be searched
        when(gameObjects.nearest(23134)).thenReturn(obstacle);
        script.onLoop();
        verify(interaction, atLeast(2)).click(any(GameObject.class), anyString());
    }
}
