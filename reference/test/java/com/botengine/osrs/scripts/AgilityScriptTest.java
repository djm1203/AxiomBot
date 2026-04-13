package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.agility.AgilityScript;
import com.botengine.osrs.scripts.agility.AgilitySettings;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
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
 * WAITING uses agility XP increase as the primary completion signal.
 * Position stability (4 ticks) is a fallback. Tests simulate XP drops.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgilityScriptTest
{
    private static final int GNOME_OBS_0 = 23145;

    // Two distinct positions to simulate movement vs stability
    private static final WorldPoint POS_A = new WorldPoint(2474, 3436, 0);
    private static final WorldPoint POS_B = new WorldPoint(2475, 3436, 0);

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
    @Mock private TileObject obstacle;

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
        s.pickupMarks = false;
        script.configure(null, s);
        script.onStart();

        // Default: no animation, stable at POS_A, no agility XP
        when(players.getAnimation()).thenReturn(-1);
        when(players.getLocation()).thenReturn(POS_A);
        when(groundItems.nearestWithTile(anyInt())).thenReturn(null);
        when(client.getSkillExperience(net.runelite.api.Skill.AGILITY)).thenReturn(0);
    }

    /** Simulates N ticks of the player being stationary at POS_A with no animation. */
    private void ticksIdle(int n)
    {
        when(players.getAnimation()).thenReturn(-1);
        when(players.getLocation()).thenReturn(POS_A);
        for (int i = 0; i < n; i++) script.onLoop();
    }

    @Test
    void findObstacle_obstacleNull_doesNotClick()
    {
        when(gameObjects.nearestTileObject(GNOME_OBS_0)).thenReturn(null);
        when(gameObjects.nearestTileObject(anyString())).thenReturn(null);

        script.onLoop();

        verify(interaction, never()).click(any(TileObject.class), anyString());
    }

    @Test
    void findObstacle_obstacleFound_clicksIt()
    {
        when(gameObjects.nearestTileObject(GNOME_OBS_0)).thenReturn(obstacle);

        script.onLoop(); // FIND_OBSTACLE → click → CLICKING_OBSTACLE

        verify(interaction).click(eq(obstacle), eq("Walk-on"));
    }

    @Test
    void clickingObstacle_animationStarts_movesToWaiting()
    {
        when(gameObjects.nearestTileObject(GNOME_OBS_0)).thenReturn(obstacle);
        script.onLoop(); // FIND_OBSTACLE → CLICKING_OBSTACLE

        // Animation fires
        when(players.getAnimation()).thenReturn(829);
        script.onLoop(); // CLICKING_OBSTACLE → WAITING

        // Player stops (stable at POS_A, no animation) for 2 ticks → advances
        ticksIdle(3);

        verify(gameObjects, atLeastOnce()).nearestTileObject(GNOME_OBS_0);
    }

    @Test
    void clickingObstacle_timeout_retriesClick()
    {
        when(gameObjects.nearestTileObject(GNOME_OBS_0)).thenReturn(obstacle);
        script.onLoop(); // FIND_OBSTACLE → click → CLICKING_OBSTACLE

        // 5 ticks with no animation and no movement → timeout
        for (int i = 0; i < 5; i++) script.onLoop();

        // Back to FIND_OBSTACLE — one more loop fires retry click
        script.onLoop();

        verify(interaction, times(2)).click(eq(obstacle), anyString());
    }

    @Test
    void waiting_xpDrop_advancesObstacleIndex()
    {
        when(gameObjects.nearestTileObject(GNOME_OBS_0)).thenReturn(obstacle);
        script.onLoop(); // click → CLICKING_OBSTACLE

        // Animation fires → WAITING
        when(players.getAnimation()).thenReturn(829);
        script.onLoop();

        // Player moves (position changes each tick) — no XP yet, should stay in WAITING
        when(players.getAnimation()).thenReturn(-1);
        when(players.getLocation()).thenReturn(POS_A);
        script.onLoop();
        when(players.getLocation()).thenReturn(POS_B);
        script.onLoop();
        when(players.getLocation()).thenReturn(POS_A);
        script.onLoop();

        // Verify still in WAITING (not yet clicked obstacle 1)
        verify(gameObjects, never()).nearestTileObject(23134);

        // XP fires — obstacle complete → advance to index 1
        when(client.getSkillExperience(net.runelite.api.Skill.AGILITY)).thenReturn(500);
        script.onLoop();

        // Now in FIND_OBSTACLE for obstacle 1 (23134)
        when(gameObjects.nearestTileObject(23134)).thenReturn(obstacle);
        when(gameObjects.nearestTileObject(anyString())).thenReturn(null);
        script.onLoop();
        verify(interaction, atLeast(2)).click(any(TileObject.class), anyString());
    }
}
