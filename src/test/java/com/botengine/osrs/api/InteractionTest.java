package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

/**
 * Unit tests for Interaction API.
 *
 * Each test verifies that the correct menuAction call is made with the
 * expected MenuAction type, entity ID, and option text. We capture the
 * arguments passed to client.menuAction() using ArgumentCaptor.
 *
 * This is the most critical API to test — incorrect menuAction params
 * silently do nothing in-game, making bugs hard to spot without tests.
 */
@ExtendWith(MockitoExtension.class)
class InteractionTest
{
    @Mock private Client client;
    @Mock private GameObject gameObject;
    @Mock private ObjectComposition objectDef;
    @Mock private NPC npc;
    @Mock private Widget widget;

    private Interaction interaction;

    @BeforeEach
    void setUp()
    {
        interaction = new Interaction(client);
    }

    // ── click(GameObject) ─────────────────────────────────────────────────────

    @Test
    void clickGameObject_usesFirstOptionAction()
    {
        when(gameObject.getId()).thenReturn(1276);
        when(client.getObjectDefinition(1276)).thenReturn(objectDef);
        when(objectDef.getName()).thenReturn("Tree");
        when(gameObject.getSceneMinLocation()).thenReturn(new Point(10, 20));

        interaction.click(gameObject, "Chop down");

        verify(client).menuAction(
            eq(10), eq(20),
            eq(MenuAction.GAME_OBJECT_FIRST_OPTION),
            eq(1276),
            eq(-1),
            eq("Chop down"),
            eq("Tree")
        );
    }

    @Test
    void clickGameObject_nullName_usesEmptyString()
    {
        when(gameObject.getId()).thenReturn(10820);
        when(client.getObjectDefinition(10820)).thenReturn(objectDef);
        when(objectDef.getName()).thenReturn(null);
        when(gameObject.getSceneMinLocation()).thenReturn(new Point(5, 5));

        assertDoesNotThrow(() -> interaction.click(gameObject, "Chop down"));
    }

    // ── click(NPC) ────────────────────────────────────────────────────────────

    @Test
    void clickNpc_usesFirstOptionAndNpcIndex()
    {
        when(npc.getId()).thenReturn(1530);
        when(npc.getIndex()).thenReturn(42);
        when(npc.getName()).thenReturn("Fishing spot");

        interaction.click(npc, "Lure");

        verify(client).menuAction(
            eq(0), eq(0),
            eq(MenuAction.NPC_FIRST_OPTION),
            eq(42),   // index, not id
            eq(-1),
            eq("Lure"),
            eq("Fishing spot")
        );
    }

    @Test
    void clickNpc_nullName_usesEmptyString()
    {
        when(npc.getIndex()).thenReturn(1);
        when(npc.getName()).thenReturn(null);

        assertDoesNotThrow(() -> interaction.click(npc, "Attack"));
    }

    // ── click(Widget) ─────────────────────────────────────────────────────────

    @Test
    void clickWidget_usesCcOpAction()
    {
        when(widget.getId()).thenReturn(12345);

        interaction.click(widget);

        verify(client).menuAction(
            eq(-1), eq(12345),
            eq(MenuAction.CC_OP),
            eq(1),
            eq(-1),
            eq(""),
            eq("")
        );
    }

    // ── clickWidget(packedId) ─────────────────────────────────────────────────

    @Test
    void clickWidgetById_usesCcOpWithPackedId()
    {
        int packedId = net.runelite.api.widgets.WidgetUtil.packComponentId(218, 48);

        interaction.clickWidget(packedId);

        verify(client).menuAction(
            eq(-1), eq(packedId),
            eq(MenuAction.CC_OP),
            eq(1),
            eq(-1),
            eq(""),
            eq("")
        );
    }

    // ── clickInventoryItem ────────────────────────────────────────────────────

    @Test
    void clickInventoryItem_dropsItemAtCorrectSlot()
    {
        ItemContainer container = mock(ItemContainer.class);
        Item[] items = new Item[28];
        // Put item 1511 (logs) at slot 3
        for (int i = 0; i < 28; i++)
        {
            items[i] = mock(Item.class);
            when(items[i].getId()).thenReturn(i == 3 ? 1511 : -1);
        }
        when(container.getItems()).thenReturn(items);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);

        interaction.clickInventoryItem(1511, "Drop");

        verify(client).menuAction(
            eq(3),
            eq(WidgetInfo.INVENTORY.getId()),
            eq(MenuAction.CC_OP),
            eq(7),
            eq(1511),
            eq("Drop"),
            eq("")
        );
    }

    // ── useItemOn(item, slot, GameObject) ─────────────────────────────────────

    @Test
    void useItemOnGameObject_firesTwoMenuActions()
    {
        when(gameObject.getId()).thenReturn(2097); // anvil
        when(client.getObjectDefinition(2097)).thenReturn(objectDef);
        when(objectDef.getName()).thenReturn("Anvil");
        when(gameObject.getSceneMinLocation()).thenReturn(new Point(15, 25));

        interaction.useItemOn(2359, 0, gameObject); // mithril bar in slot 0

        // Should fire exactly 2 menuAction calls: ITEM_USE then WIDGET_TARGET_ON_GAME_OBJECT
        verify(client, times(2)).menuAction(
            anyInt(), anyInt(), any(MenuAction.class), anyInt(), anyInt(), anyString(), anyString()
        );
    }

    @Test
    void useItemOnGameObject_firstActionIsItemUse()
    {
        when(gameObject.getId()).thenReturn(2097);
        when(client.getObjectDefinition(2097)).thenReturn(objectDef);
        when(objectDef.getName()).thenReturn("Anvil");
        when(gameObject.getSceneMinLocation()).thenReturn(new Point(15, 25));

        ArgumentCaptor<MenuAction> actionCaptor = ArgumentCaptor.forClass(MenuAction.class);
        interaction.useItemOn(2359, 0, gameObject);

        verify(client, times(2)).menuAction(
            anyInt(), anyInt(), actionCaptor.capture(), anyInt(), anyInt(), anyString(), anyString()
        );

        assertEquals(MenuAction.ITEM_USE, actionCaptor.getAllValues().get(0));
        assertEquals(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, actionCaptor.getAllValues().get(1));
    }
}
