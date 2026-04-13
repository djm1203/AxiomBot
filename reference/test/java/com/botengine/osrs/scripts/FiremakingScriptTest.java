package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.firemaking.FiremakingScript;
import com.botengine.osrs.scripts.firemaking.FiremakingSettings;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * State machine tests for FiremakingScript.
 *
 *  FIND_LOGS: no logs → BANKING (banking mode) or DONE (drop mode)
 *  FIND_LOGS: has logs + tinderbox → LIGHT_FIRE
 *  LIGHT_FIRE: calls useItemOnItem(tinderSlot, logSlot)
 *  WAIT_LIT: player idle, log consumed → BANKING or DONE
 *  WAIT_LIT: player idle, log remains → LIGHT_FIRE
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FiremakingScriptTest
{
    private static final int TINDERBOX = 590;
    private static final int NORMAL_LOG = 1511;

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

    private FiremakingScript script;

    @BeforeEach
    void setUp()
    {
        script = new FiremakingScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera,
            prayers, groundItems, worldHopper, grandExchange, antiban, time, log);
        script.onStart();
        when(players.getAnimation()).thenReturn(-1);
        when(players.isIdle()).thenReturn(false);
    }

    @Test
    void findLogs_noTinderbox_stopsScript()
    {
        when(inventory.contains(TINDERBOX)).thenReturn(false);

        script.onLoop(); // FIND_LOGS → DONE (no tinderbox)

        verify(interaction, never()).useItemOnItem(anyInt(), anyInt());
    }

    @Test
    void findLogs_noLogs_dropMode_stopsScript()
    {
        when(inventory.contains(TINDERBOX)).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(false);
        // bankingMode = false by default

        script.onLoop(); // FIND_LOGS → DONE

        verify(bank, never()).openNearest();
    }

    @Test
    void findLogs_noLogs_bankingMode_goesToBanking()
    {
        FiremakingSettings s = new FiremakingSettings();
        s.bankingMode = true;
        script.configure(null, s);

        when(inventory.contains(TINDERBOX)).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(false);
        when(bank.isOpen()).thenReturn(false);
        when(bank.isNearBank()).thenReturn(false);

        script.onLoop(); // FIND_LOGS → BANKING
        script.onLoop(); // BANKING: not near bank → enable run

        verify(movement).setRunning(true);
    }

    @Test
    void findLogs_hasLogsAndTinderbox_callsUseItemOnItem()
    {
        when(inventory.contains(TINDERBOX)).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(true);
        when(inventory.getSlot(TINDERBOX)).thenReturn(0);
        when(inventory.getSlot(NORMAL_LOG)).thenReturn(1);

        script.onLoop(); // FIND_LOGS → LIGHT_FIRE
        script.onLoop(); // LIGHT_FIRE → fires useItemOnItem, moves to WAIT_LIT

        verify(interaction).useItemOnItem(0, 1);
    }

    @Test
    void waitLit_playerIdleLogConsumed_dropMode_finishes()
    {
        // Enter LIGHT_FIRE state
        when(inventory.contains(TINDERBOX)).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(true);
        when(inventory.getSlot(TINDERBOX)).thenReturn(0);
        when(inventory.getSlot(NORMAL_LOG)).thenReturn(1);
        script.onLoop(); // FIND_LOGS → LIGHT_FIRE
        script.onLoop(); // LIGHT_FIRE → WAIT_LIT

        // Player becomes idle, log is gone
        when(players.isIdle()).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(false);

        script.onLoop(); // WAIT_LIT → DONE (no banking mode)

        verify(bank, never()).openNearest();
    }

    @Test
    void waitLit_playerIdleLogRemains_lightsNextFire()
    {
        when(inventory.contains(TINDERBOX)).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(true);
        when(inventory.getSlot(TINDERBOX)).thenReturn(0);
        when(inventory.getSlot(NORMAL_LOG)).thenReturn(1);
        script.onLoop(); // FIND_LOGS → LIGHT_FIRE
        script.onLoop(); // LIGHT_FIRE → WAIT_LIT

        // Player idle, logs still present (fire lit, pushed east, more logs remain)
        when(players.isIdle()).thenReturn(true);
        when(inventory.contains(NORMAL_LOG)).thenReturn(true);
        when(inventory.getSlot(NORMAL_LOG)).thenReturn(1);

        script.onLoop(); // WAIT_LIT → LIGHT_FIRE
        script.onLoop(); // LIGHT_FIRE → fires again

        verify(interaction, times(2)).useItemOnItem(0, 1);
    }
}
