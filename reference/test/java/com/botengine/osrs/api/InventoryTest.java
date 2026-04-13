package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

/**
 * Unit tests for Inventory API.
 *
 * RuneLite's Item class has final methods, so we cannot mock Item directly.
 * Instead we build a fake ItemContainer that returns a pre-constructed Item[]
 * where each Item is a mock whose getId() and getQuantity() are stubbed
 * BEFORE being used inside any when() chain (to avoid nested-stubbing issues).
 */
@ExtendWith(MockitoExtension.class)
class InventoryTest
{
    @Mock private Client client;
    @Mock private ItemContainer container;

    private Inventory inventory;

    @BeforeEach
    void setUp()
    {
        inventory = new Inventory(client);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
    }

    // ── Helper — MUST be called before any when() chain ──────────────────────

    /**
     * Builds a 28-slot Item array.
     * IMPORTANT: stubs are set up here; never call this inside thenReturn().
     */
    private Item[] makeItems(int... ids)
    {
        Item[] items = new Item[28];
        for (int i = 0; i < ids.length && i < 28; i++)
        {
            Item item = mock(Item.class);
            when(item.getId()).thenReturn(ids[i]);
            when(item.getQuantity()).thenReturn(1);
            items[i] = item;
        }
        return items;
    }

    // ── isFull / isEmpty ─────────────────────────────────────────────────────

    @Test
    void isFull_when28Items_returnsTrue()
    {
        int[] ids = new int[28];
        for (int i = 0; i < 28; i++) ids[i] = 100 + i;
        Item[] items = makeItems(ids);
        when(container.getItems()).thenReturn(items);
        assertTrue(inventory.isFull());
    }

    @Test
    void isFull_whenFewerThan28Items_returnsFalse()
    {
        Item[] items = makeItems(441, 441, 441);
        when(container.getItems()).thenReturn(items);
        assertFalse(inventory.isFull());
    }

    @Test
    void isEmpty_whenNoItems_returnsTrue()
    {
        when(container.getItems()).thenReturn(new Item[28]);
        assertTrue(inventory.isEmpty());
    }

    @Test
    void isEmpty_whenHasItem_returnsFalse()
    {
        Item[] items = makeItems(555);
        when(container.getItems()).thenReturn(items);
        assertFalse(inventory.isEmpty());
    }

    // ── contains ─────────────────────────────────────────────────────────────

    @Test
    void contains_itemPresent_returnsTrue()
    {
        Item[] items = makeItems(1755, 1623);
        when(container.getItems()).thenReturn(items);
        assertTrue(inventory.contains(1755));
        assertTrue(inventory.contains(1623));
    }

    @Test
    void contains_itemAbsent_returnsFalse()
    {
        Item[] items = makeItems(1755);
        when(container.getItems()).thenReturn(items);
        assertFalse(inventory.contains(9999));
    }

    // ── containsAll ──────────────────────────────────────────────────────────

    @Test
    void containsAll_allPresent_returnsTrue()
    {
        Item[] items = makeItems(946, 1511, 555);
        when(container.getItems()).thenReturn(items);
        assertTrue(inventory.containsAll(946, 1511));
    }

    @Test
    void containsAll_oneMissing_returnsFalse()
    {
        Item[] items = makeItems(946, 1511);
        when(container.getItems()).thenReturn(items);
        assertFalse(inventory.containsAll(946, 1511, 9999));
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test
    void count_multipleStacks_sumsTotalQuantity()
    {
        Item[] items = new Item[28];
        Item stack1 = mock(Item.class);
        when(stack1.getId()).thenReturn(555);
        when(stack1.getQuantity()).thenReturn(500);
        Item stack2 = mock(Item.class);
        when(stack2.getId()).thenReturn(555);
        when(stack2.getQuantity()).thenReturn(300);
        items[0] = stack1;
        items[1] = stack2;
        when(container.getItems()).thenReturn(items);

        assertEquals(800, inventory.count(555));
    }

    @Test
    void count_itemAbsent_returnsZero()
    {
        Item[] items = makeItems(556);
        when(container.getItems()).thenReturn(items);
        assertEquals(0, inventory.count(555));
    }

    // ── getFreeSlots / getUsedSlots ───────────────────────────────────────────

    @Test
    void getFreeSlots_threeItems_returns25Free()
    {
        Item[] items = makeItems(1, 2, 3);
        when(container.getItems()).thenReturn(items);
        assertEquals(25, inventory.getFreeSlots());
    }

    @Test
    void getUsedSlots_threeItems_returns3()
    {
        Item[] items = makeItems(1, 2, 3);
        when(container.getItems()).thenReturn(items);
        assertEquals(3, inventory.getUsedSlots());
    }

    @Test
    void freeAndUsed_alwaysSumTo28()
    {
        Item[] items = makeItems(10, 20, 30, 40, 50);
        when(container.getItems()).thenReturn(items);
        assertEquals(28, inventory.getFreeSlots() + inventory.getUsedSlots());
    }

    // ── getSlot ───────────────────────────────────────────────────────────────

    @Test
    void getSlot_itemPresent_returnsCorrectSlot()
    {
        Item[] items = makeItems(100, 200, 441);
        when(container.getItems()).thenReturn(items);
        assertEquals(2, inventory.getSlot(441));
    }

    @Test
    void getSlot_itemAbsent_returnsNegativeOne()
    {
        Item[] items = makeItems(100, 200);
        when(container.getItems()).thenReturn(items);
        assertEquals(-1, inventory.getSlot(999));
    }
}
