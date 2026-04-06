package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.scripts.woodcutting.WoodcuttingScript;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

/**
 * Unit tests for WoodcuttingScript state machine.
 *
 * Tests each state transition by controlling what the mocked api/ layer
 * returns, then calling onLoop() and verifying the correct action is taken.
 *
 *   FIND_TREE:  finds tree → clicks it → moves to CHOPPING
 *   FIND_TREE:  inventory full → moves to DROPPING
 *   CHOPPING:   player idle → moves back to FIND_TREE
 *   DROPPING:   has logs → drops them → moves to FIND_TREE when empty
 */
@ExtendWith(MockitoExtension.class)
class WoodcuttingScriptTest
{
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
    @Mock private Antiban antiban;
    @Mock private Time time;
    @Mock private Log log;
    @Mock private GameObject tree;

    private WoodcuttingScript script;

    @BeforeEach
    void setUp()
    {
        script = new WoodcuttingScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera, antiban, time, log);
        script.onStart();
    }

    // ── FIND_TREE state ───────────────────────────────────────────────────────

    @Test
    void findTree_inventoryFull_switchesToDropping()
    {
        when(inventory.isFull()).thenReturn(true);

        script.onLoop(); // should not click anything, just transition

        verify(interaction, never()).click(any(GameObject.class), anyString());
    }

    @Test
    void findTree_noTreeNearby_doesNothing()
    {
        when(inventory.isFull()).thenReturn(false);
        when(gameObjects.nearest(any(int[].class))).thenReturn(null);

        script.onLoop();

        verify(interaction, never()).click(any(GameObject.class), anyString());
    }

    @Test
    void findTree_treeFound_clicksChopDown()
    {
        when(inventory.isFull()).thenReturn(false);
        when(gameObjects.nearest(any(int[].class))).thenReturn(tree);
        when(tree.getId()).thenReturn(1276);

        script.onLoop();

        verify(interaction).click(eq(tree), eq("Chop down"));
    }

    // ── CHOPPING state (second loop call) ────────────────────────────────────

    @Test
    void chopping_inventoryFull_switchesToDropping()
    {
        // First loop: find and click tree → enter CHOPPING
        when(inventory.isFull()).thenReturn(false);
        when(gameObjects.nearest(any(int[].class))).thenReturn(tree);
        when(tree.getId()).thenReturn(1276);
        script.onLoop();

        // Second loop: inventory now full
        when(inventory.isFull()).thenReturn(true);
        script.onLoop();

        // Third loop: should now be in DROPPING state — drops logs, not clicking trees
        when(inventory.contains(anyInt())).thenReturn(false);
        when(inventory.isEmpty()).thenReturn(true);
        script.onLoop();

        verify(interaction, times(1)).click(eq(tree), eq("Chop down")); // only once
    }

    @Test
    void chopping_playerIdle_returnsToFindTree()
    {
        // Enter CHOPPING state
        when(inventory.isFull()).thenReturn(false);
        when(gameObjects.nearest(any(int[].class))).thenReturn(tree);
        when(tree.getId()).thenReturn(1276);
        script.onLoop();

        // Player becomes idle (tree depleted)
        when(inventory.isFull()).thenReturn(false);
        when(players.isIdle()).thenReturn(true);
        script.onLoop();

        // Third loop: should search for new tree
        when(gameObjects.nearest(any(int[].class))).thenReturn(tree);
        script.onLoop();

        // clicked twice: once initially, once after re-finding tree
        verify(interaction, times(2)).click(eq(tree), eq("Chop down"));
    }

    // ── Antiban hooks called ──────────────────────────────────────────────────

    @Test
    void findTree_afterClick_callsReactionDelay()
    {
        when(inventory.isFull()).thenReturn(false);
        when(gameObjects.nearest(any(int[].class))).thenReturn(tree);
        when(tree.getId()).thenReturn(1276);

        script.onLoop();

        verify(antiban).reactionDelay();
    }
}
