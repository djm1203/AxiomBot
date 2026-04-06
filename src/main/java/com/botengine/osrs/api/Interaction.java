package com.botengine.osrs.api;

import com.botengine.osrs.util.Mouse;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

/**
 * Low-level dispatcher for all in-game interactions.
 *
 * Every interaction in OSRS is expressed as a menu-entry invocation:
 * a tuple of (p0, p1, action, id, itemId, option, target).
 * RuneLite exposes this through Client.menuAction().
 *
 * menuAction signature (RuneLite 1.12.x):
 *   client.menuAction(p0, p1, MenuAction action, int id, int itemId, String option, String target)
 *
 *   p0, p1  — scene coordinates for world interactions, or widget child index for UI
 *   action  — the type of interaction (WALK, GAME_OBJECT_FIRST_OPTION, NPC_FIRST_OPTION, etc.)
 *   id      — entity ID (object ID, NPC index, widget component ID, etc.)
 *   itemId  — item ID for inventory interactions, -1 otherwise
 *   option  — menu option text (e.g. "Chop down", "Bank")
 *   target  — entity name shown in menu (e.g. "<col=ffff00>Oak tree</col>")
 *
 * All methods must be called on the client thread (via ClientThread.invoke if needed).
 */
@Slf4j
public class Interaction
{
    private final Client client;
    private final Mouse mouse;

    @Inject
    public Interaction(Client client, Mouse mouse)
    {
        this.client = client;
        this.mouse = mouse;
    }

    // ── GameObjects ───────────────────────────────────────────────────────────

    /**
     * Interacts with a GameObject using the first menu option (left-click action).
     *
     * Scene coordinates from getSceneMinLocation() tell the client which tile
     * the object is on. The object's name is resolved from its definition.
     *
     * TODO: Support non-first options by mapping action string to
     *       GAME_OBJECT_SECOND/THIRD/FOURTH/FIFTH_OPTION.
     *
     * @param obj    the game object to interact with
     * @param action the option text (e.g. "Chop down", "Mine", "Open", "Bank")
     */
    public void click(GameObject obj, String action)
    {
        net.runelite.api.ObjectComposition def = client.getObjectDefinition(obj.getId());
        String name = (def != null && def.getName() != null) ? def.getName() : "";

        int sceneX = obj.getSceneMinLocation().getX();
        int sceneY = obj.getSceneMinLocation().getY();

        log.debug("click(GameObject) id={} name='{}' action='{}' scene=({},{})",
            obj.getId(), name, action, sceneX, sceneY);

        client.menuAction(
            sceneX, sceneY,
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            obj.getId(),
            -1,
            action,
            name
        );
    }

    // ── NPCs ──────────────────────────────────────────────────────────────────

    /**
     * Interacts with an NPC using the first menu option.
     *
     * For NPC interactions: p0 = -1 (unused), p1 = npc.getIndex(), id = npc.getIndex().
     * The NPC index (slot in the server NPC table) identifies the target.
     * Using 0,0 for p0/p1 causes the action to be silently rejected.
     *
     * @param npc    the NPC to interact with
     * @param action the option text (e.g. "Talk-to", "Bank", "Attack", "Lure")
     */
    /**
     * Clicks an NPC using the given action. Returns true if the click was fired,
     * false if the NPC was off-screen (caller should not transition state in that case).
     */
    public boolean click(NPC npc, String action)
    {
        log.debug("click(NPC) id={} index={} name='{}' action='{}'",
            npc.getId(), npc.getIndex(), npc.getName() != null ? npc.getName() : "", action);

        // Use a real Robot click. OSRS requires the cursor to be over the entity
        // and processes it as a normal left-click — no menuAction parameters needed.
        return mouse.click(npc);
    }

    // ── Ground items ──────────────────────────────────────────────────────────

    /**
     * Picks up a ground item from the given tile.
     *
     * TileItem does not expose a name or tile reference directly — the caller
     * must pass the tile the item was found on (from Tile.getGroundItems()).
     * The item name is resolved from the item manager if needed, or left blank.
     *
     * GROUND_ITEM_THIRD_OPTION corresponds to the "Take" action.
     *
     * @param item the ground item to pick up
     * @param tile the tile the item is on
     */
    public void click(TileItem item, Tile tile)
    {
        int sceneX = tile.getSceneLocation().getX();
        int sceneY = tile.getSceneLocation().getY();

        log.debug("click(TileItem) id={} qty={} scene=({},{})",
            item.getId(), item.getQuantity(), sceneX, sceneY);

        client.menuAction(
            sceneX, sceneY,
            MenuAction.GROUND_ITEM_THIRD_OPTION,
            item.getId(),
            -1,
            "Take",
            ""
        );
    }

    // ── Widgets ───────────────────────────────────────────────────────────────

    /**
     * Clicks a UI widget using its primary action (CC_OP with id=1).
     * Equivalent to a left-click on a button, inventory item, or interface element.
     *
     * @param widget the widget to click
     */
    public void click(Widget widget)
    {
        log.debug("click(Widget) id={}", widget.getId());

        client.menuAction(
            -1, widget.getId(),
            MenuAction.CC_OP,
            1,
            -1,
            "",
            ""
        );
    }

    /**
     * Clicks a widget by its packed widget ID (group << 16 | child).
     * Use this when you have the raw widget ID without a Widget reference.
     *
     * @param packedWidgetId the packed widget ID
     */
    public void clickWidget(int packedWidgetId)
    {
        log.debug("clickWidget packedId={}", packedWidgetId);

        client.menuAction(
            -1, packedWidgetId,
            MenuAction.CC_OP,
            1,
            -1,
            "",
            ""
        );
    }

    // ── Inventory items ───────────────────────────────────────────────────────

    /**
     * Clicks an inventory item by item ID (e.g. "Drop", "Eat", "Wield").
     * Finds the first slot containing the item and fires CC_OP.
     *
     * @param itemId the item to click
     * @param action the option text (e.g. "Drop", "Eat")
     */
    public void clickInventoryItem(int itemId, String action)
    {
        net.runelite.api.ItemContainer inv =
            client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null) return;

        net.runelite.api.Item[] items = inv.getItems();
        for (int slot = 0; slot < items.length; slot++)
        {
            if (items[slot] != null && items[slot].getId() == itemId)
            {
                log.debug("clickInventoryItem id={} slot={} action='{}'", itemId, slot, action);
                client.menuAction(
                    slot, net.runelite.api.widgets.WidgetInfo.INVENTORY.getId(),
                    MenuAction.CC_OP,
                    7,   // slot 7 = "Drop" in default inventory context menu
                    itemId,
                    action,
                    ""
                );
                return;
            }
        }
        log.warn("clickInventoryItem: item id={} not found in inventory", itemId);
    }

    /**
     * Uses one inventory item on another inventory item.
     * Fires ITEM_USE on slot1 then ITEM_USE_ON_WIDGET on slot2.
     * Used to open production dialogues (chisel+gem, knife+log, etc.)
     *
     * @param slot1 inventory slot of the tool/resource to use
     * @param slot2 inventory slot of the target item
     */
    public void useItemOnItem(int slot1, int slot2)
    {
        int widgetId = net.runelite.api.widgets.WidgetInfo.INVENTORY.getId();

        log.debug("useItemOnItem slot1={} slot2={}", slot1, slot2);

        // Step 1: activate the first item (ITEM_USE)
        net.runelite.api.ItemContainer inv =
            client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inv == null) return;
        int item1Id = inv.getItems()[slot1].getId();

        client.menuAction(
            slot1, widgetId,
            MenuAction.ITEM_USE,
            item1Id,
            -1,
            "Use",
            ""
        );

        // Step 2: use on second item (WIDGET_TARGET_ON_WIDGET)
        int item2Id = inv.getItems()[slot2].getId();
        client.menuAction(
            slot2, widgetId,
            MenuAction.WIDGET_TARGET_ON_WIDGET,
            item2Id,
            -1,
            "Use",
            ""
        );
    }

    // ── Use-item-on-object ────────────────────────────────────────────────────

    /**
     * Uses an inventory item on a GameObject (two-step interaction).
     *
     * Step 1 — select the item from inventory (ITEM_USE).
     * Step 2 — click the target object (WIDGET_TARGET_ON_GAME_OBJECT).
     *
     * Both steps are fired immediately; RuneLite queues them in order.
     * The inventory slot of the item is obtained from the Inventory API.
     *
     * @param itemId       the item ID in inventory to use
     * @param inventorySlot the slot index (0–27) of the item
     * @param target       the game object to use the item on
     */
    public void useItemOn(int itemId, int inventorySlot, GameObject target)
    {
        net.runelite.api.ObjectComposition targetDef = client.getObjectDefinition(target.getId());
        String targetName = (targetDef != null && targetDef.getName() != null) ? targetDef.getName() : "";

        int sceneX = target.getSceneMinLocation().getX();
        int sceneY = target.getSceneMinLocation().getY();

        log.debug("useItemOn itemId={} slot={} -> objectId={} scene=({},{})",
            itemId, inventorySlot, target.getId(), sceneX, sceneY);

        // Step 1: select item in inventory
        client.menuAction(
            inventorySlot, net.runelite.api.widgets.WidgetInfo.INVENTORY.getId(),
            MenuAction.ITEM_USE,
            itemId,
            -1,
            "Use",
            ""
        );

        // Step 2: use on object
        client.menuAction(
            sceneX, sceneY,
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
            target.getId(),
            -1,
            "Use",
            targetName
        );
    }
}
