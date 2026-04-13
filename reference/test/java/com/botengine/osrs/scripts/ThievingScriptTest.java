package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.thieving.ThievingScript;
import com.botengine.osrs.scripts.thieving.ThievingSettings;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * State machine tests for ThievingScript (Stall and Pickpocket modes).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThievingScriptTest
{
    private static final int BAKERY_STALL = 11730;
    private static final int MAN_NPC_ID   = 1;
    private static final int FOOD_ID      = 1993;

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
    @Mock private GameObject stall;
    @Mock private NPC npc;

    private ThievingScript script;

    @BeforeEach
    void setUp()
    {
        script = new ThievingScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera,
            prayers, groundItems, worldHopper, grandExchange, antiban, time, log);
        when(players.getAnimation()).thenReturn(-1);
        when(players.isIdle()).thenReturn(false);
        when(players.shouldEat(anyInt())).thenReturn(false);
        when(inventory.isFull()).thenReturn(false);
    }

    // ── Stall mode ────────────────────────────────────────────────────────────

    @Test
    void stallMode_stallFound_stealsFromIt()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode   = "Stall";
        s.stallId = BAKERY_STALL;
        script.configure(null, s);
        script.onStart();

        when(gameObjects.nearest(BAKERY_STALL)).thenReturn(stall);

        script.onLoop(); // FIND_STALL → click stall → STEALING

        verify(interaction).click(eq(stall), eq("Steal-from"));
    }

    @Test
    void stallMode_stallNull_doesNothing()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode    = "Stall";
        s.stallId = BAKERY_STALL;
        script.configure(null, s);
        script.onStart();

        when(gameObjects.nearest(BAKERY_STALL)).thenReturn(null);

        script.onLoop();

        verify(interaction, never()).click(any(GameObject.class), anyString());
    }

    @Test
    void stallMode_inventoryFull_noBankMode_drops()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode        = "Stall";
        s.stallId     = BAKERY_STALL;
        s.bankWhenFull = false;
        script.configure(null, s);
        script.onStart();

        when(inventory.isFull()).thenReturn(true);
        when(client.getItemContainer(any())).thenReturn(null); // empty container

        script.onLoop(); // FIND_STALL → inventory full → DROPPING

        verify(gameObjects, never()).nearest(BAKERY_STALL);
    }

    // ── Pickpocket mode ───────────────────────────────────────────────────────

    @Test
    void pickpocketMode_targetFound_pickpockets()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode        = "Pickpocket";
        s.targetNpcId = MAN_NPC_ID;
        script.configure(null, s);
        script.onStart();

        when(npc.getId()).thenReturn(MAN_NPC_ID);
        when(npcs.nearest(any(Predicate.class))).thenReturn(npc);
        when(interaction.click(npc, "Pickpocket")).thenReturn(true);

        script.onLoop(); // FIND_TARGET → click npc → PICKPOCKETING

        verify(interaction).click(eq(npc), eq("Pickpocket"));
    }

    @Test
    void pickpocketMode_noTarget_doesNothing()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode        = "Pickpocket";
        s.targetNpcId = MAN_NPC_ID;
        script.configure(null, s);
        script.onStart();

        when(npcs.nearest(any(Predicate.class))).thenReturn(null);

        script.onLoop();

        verify(interaction, never()).click(any(NPC.class), anyString());
    }

    @Test
    void pickpocketMode_stunAnimation_waitsBeforeRetrying()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode        = "Pickpocket";
        s.targetNpcId = MAN_NPC_ID;
        script.configure(null, s);
        script.onStart();

        // Enter PICKPOCKETING then WAIT
        when(npc.getId()).thenReturn(MAN_NPC_ID);
        when(npcs.nearest(any(Predicate.class))).thenReturn(npc);
        when(interaction.click(npc, "Pickpocket")).thenReturn(true);
        script.onLoop(); // FIND_TARGET → click → PICKPOCKETING (ticksWaited=0)

        when(players.isIdle()).thenReturn(true);
        script.onLoop(); // PICKPOCKETING tick 1: ticksWaited=1, isIdle but <2 — stays
        script.onLoop(); // PICKPOCKETING tick 2: ticksWaited=2 → WAIT

        // Stun fires — should not immediately retry
        when(players.getAnimation()).thenReturn(424); // stun anim
        script.onLoop(); // WAIT: stunned tick 1 — no click

        verify(interaction, times(1)).click(any(NPC.class), anyString());
    }

    @Test
    void pickpocketMode_hungerTriggersEat()
    {
        ThievingSettings s = new ThievingSettings();
        s.mode            = "Pickpocket";
        s.targetNpcId     = MAN_NPC_ID;
        s.eatThresholdPct = 50;
        s.foodItemId      = FOOD_ID;
        script.configure(null, s);
        script.onStart();

        when(npc.getId()).thenReturn(MAN_NPC_ID);
        when(npcs.nearest(any(Predicate.class))).thenReturn(npc);
        when(interaction.click(npc, "Pickpocket")).thenReturn(true);
        script.onLoop(); // FIND_TARGET → click → PICKPOCKETING (ticksWaited=0)

        when(players.isIdle()).thenReturn(true);
        script.onLoop(); // PICKPOCKETING tick 1: ticksWaited=1, isIdle but <2 — stays
        script.onLoop(); // PICKPOCKETING tick 2: ticksWaited=2, isIdle and >=2 → WAIT

        // HP low — should eat
        when(players.shouldEat(50)).thenReturn(true);
        when(inventory.contains(FOOD_ID)).thenReturn(true);
        script.onLoop(); // WAIT: shouldEat → EAT
        script.onLoop(); // EAT → clicks food

        verify(interaction).clickInventoryItem(FOOD_ID, "Eat");
    }
}
