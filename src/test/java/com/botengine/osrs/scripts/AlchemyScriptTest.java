package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.alchemy.AlchemyScript;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import net.runelite.api.Client;
import net.runelite.api.Item;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlchemyScript.
 *
 * Item arrays are pre-built before any when() chain to avoid Mockito's
 * UnfinishedStubbingException (caused by stubbing inside thenReturn args).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlchemyScriptTest
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
    private AlchemyScript script;

    @BeforeEach
    void setUp()
    {
        script = new AlchemyScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera, antiban, time, log);
        script.onStart();
    }

    // ── Rune check ────────────────────────────────────────────────────────────

    @Test
    void onLoop_missingRunes_doesNotAlch()
    {
        when(magic.canAlch()).thenReturn(false);
        List<Item> items = makeItems(4151); // abyssal whip — alchable but no runes
        when(inventory.getItems()).thenReturn(items);

        script.onLoop(); // CHECK_RUNES — canAlch false, stays here
        script.onLoop();

        verify(magic, never()).alch(anyInt(), anyInt());
    }

    // ── Normal alch ───────────────────────────────────────────────────────────

    @Test
    void onLoop_alchableItem_callsMagicAlch()
    {
        when(magic.canAlch()).thenReturn(true);
        when(inventory.getSlot(4151)).thenReturn(0);
        List<Item> items = makeItems(4151, 561); // whip + nature rune
        when(inventory.getItems()).thenReturn(items);

        script.onLoop(); // CHECK_RUNES → ALCHING
        script.onLoop(); // alch fires

        verify(magic).alch(eq(4151), eq(0));
    }

    @Test
    void onLoop_onlyRunes_doesNotAlch()
    {
        when(magic.canAlch()).thenReturn(true);
        List<Item> items = makeItems(561, 554); // nature rune + fire rune only
        when(inventory.getItems()).thenReturn(items);

        script.onLoop(); // CHECK_RUNES — only runes found, findAlchItem returns -1
        script.onLoop();

        verify(magic, never()).alch(anyInt(), anyInt());
    }

    // ── Cooldown ──────────────────────────────────────────────────────────────

    @Test
    void onLoop_alchTwiceImmediately_onlyAlchsOnce()
    {
        when(magic.canAlch()).thenReturn(true);
        when(inventory.getSlot(4151)).thenReturn(0);
        List<Item> items = makeItems(4151, 561);
        when(inventory.getItems()).thenReturn(items);

        script.onLoop(); // CHECK_RUNES → ALCHING
        script.onLoop(); // first alch
        script.onLoop(); // too soon — blocked by 1800ms cooldown

        verify(magic, times(1)).alch(anyInt(), anyInt());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Pre-build Item list — call this BEFORE any when() chain. */
    private List<Item> makeItems(int... ids)
    {
        Item[] items = new Item[ids.length];
        for (int i = 0; i < ids.length; i++)
        {
            items[i] = mock(Item.class);
            when(items[i].getId()).thenReturn(ids[i]);
        }
        return Arrays.asList(items);
    }
}
