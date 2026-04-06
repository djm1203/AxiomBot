package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import java.util.Arrays;

/**
 * High-level API for interacting with the OSRS bank interface.
 *
 * The bank widget uses group ID 12. Key children:
 *   Child  0 — root container (used for isOpen() check)
 *   Child  2 — close button (X in top-right corner)
 *   Child 42 — "Deposit inventory" button
 *
 * Opening the bank requires clicking a banker NPC or a bank booth/chest
 * GameObject with the "Bank" option. Both overloads delegate to
 * {@link Interaction} so the caller does not need to know menu action IDs.
 *
 * All client/widget calls must be made on the client thread. Scripts are
 * responsible for ensuring they are not called during loading screens.
 */
@Slf4j
public class Bank
{
    /** Widget group ID for the bank interface. */
    private static final int BANK_GROUP_ID = 12;

    /** Child index of the close button within group 12. */
    private static final int CLOSE_CHILD_ID = 2;

    /** Child index of the "Deposit inventory" button within group 12. */
    private static final int DEPOSIT_ALL_CHILD_ID = 42;

    /** Bank items container child within group 12. */
    private static final int ITEMS_CHILD_ID = 13;

    private final Client client;
    private final Interaction interaction;
    private final GameObjects gameObjects;
    private final Npcs npcs;

    @Inject
    public Bank(Client client, Interaction interaction, GameObjects gameObjects, Npcs npcs)
    {
        this.client = client;
        this.interaction = interaction;
        this.gameObjects = gameObjects;
        this.npcs = npcs;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the bank interface is currently open and visible.
     *
     * <p>Checks that widget (group 12, child 0) exists and is not hidden.
     * Returns {@code false} during loading, on the login screen, or when the
     * bank has not yet been opened this session.
     *
     * @return {@code true} if the bank is open
     */
    public boolean isOpen()
    {
        Widget root = client.getWidget(BANK_GROUP_ID, 0);
        return root != null && !root.isHidden();
    }

    // ── Opening ───────────────────────────────────────────────────────────────

    /**
     * Opens the bank by clicking the "Bank" option on a bank booth or chest
     * {@link GameObject} (e.g. a Grand Exchange booth, Lumbridge bank chest).
     *
     * <p>The caller should confirm {@link #isOpen()} after a short delay, as
     * the server round-trip takes at least one game tick (~600 ms).
     *
     * @param banker the bank booth or chest object to interact with
     */
    public void open(GameObject banker)
    {
        log.debug("Opening bank via GameObject id={}", banker.getId());
        interaction.click(banker, "Bank");
    }

    /**
     * Opens the bank by clicking the "Bank" option on a banker {@link NPC}
     * (e.g. the standard bank tellers found in most cities).
     *
     * <p>The caller should confirm {@link #isOpen()} after a short delay.
     *
     * @param bankerNpc the banker NPC to interact with
     */
    public void open(NPC bankerNpc)
    {
        log.debug("Opening bank via NPC id={} name={}", bankerNpc.getId(), bankerNpc.getName());
        interaction.click(bankerNpc, "Bank");
    }

    // ── Depositing ────────────────────────────────────────────────────────────

    /**
     * Clicks the "Deposit inventory" button to deposit all items at once.
     *
     * <p>Requires the bank interface to already be open ({@link #isOpen()}).
     * Does nothing if the widget cannot be found (e.g. bank not open).
     */
    public void depositAll()
    {
        Widget depositBtn = client.getWidget(BANK_GROUP_ID, DEPOSIT_ALL_CHILD_ID);
        if (depositBtn == null || depositBtn.isHidden())
        {
            log.warn("depositAll: deposit-all widget not found or hidden");
            return;
        }
        log.debug("Clicking deposit-all button");
        interaction.click(depositBtn);
    }

    /**
     * Deposits all held copies of a specific item by invoking the
     * "Deposit-All" menu action directly on the inventory items container
     * within the bank interface.
     *
     * <p>Uses {@link MenuAction#CC_OP} against
     * {@link WidgetInfo#BANK_INVENTORY_ITEMS_CONTAINER} — the in-bank
     * inventory panel shown on the right-hand side of the bank window.
     *
     * <p>Requires the bank interface to be open. If the item is not in the
     * inventory the invocation is a no-op server-side.
     *
     * @param itemId the item ID to deposit (e.g. 995 for coins)
     */
    public void depositItem(int itemId)
    {
        log.debug("Depositing all of item id={}", itemId);
        client.menuAction(
            -1, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
            MenuAction.CC_OP,
            itemId, -1,
            "Deposit-All", ""
        );
    }

    // ── Withdrawing ───────────────────────────────────────────────────────────

    /**
     * Withdraws the specified quantity of an item from the bank.
     * Traverses bank item widgets (group 12, child 13) to find the item slot,
     * then fires the appropriate CC_OP action.
     *
     * Quantity mapping:
     *   1   → op 1
     *   5   → op 2
     *   10  → op 3
     *   All → op 7
     *   X   → not supported (requires dialog interaction)
     *
     * @param itemId   the item ID to withdraw
     * @param quantity the number of items (1, 5, 10, or Integer.MAX_VALUE for All)
     */
    public void withdraw(int itemId, int quantity)
    {
        Widget itemsContainer = client.getWidget(BANK_GROUP_ID, ITEMS_CHILD_ID);
        if (itemsContainer == null)
        {
            log.warn("withdraw: bank items container not found");
            return;
        }

        Widget[] children = itemsContainer.getDynamicChildren();
        if (children == null) return;

        for (int slot = 0; slot < children.length; slot++)
        {
            Widget child = children[slot];
            if (child == null || child.getItemId() != itemId) continue;

            int op = quantityToOp(quantity);
            log.debug("Withdrawing item id={} qty={} slot={} op={}", itemId, quantity, slot, op);
            client.menuAction(
                slot, itemsContainer.getId(),
                MenuAction.CC_OP,
                op, itemId,
                "", ""
            );
            return;
        }
        log.warn("withdraw: item id={} not found in bank", itemId);
    }

    /**
     * Withdraws all copies of an item from the bank.
     */
    public void withdrawAll(int itemId)
    {
        withdraw(itemId, Integer.MAX_VALUE);
    }

    // ── Bank finding ─────────────────────────────────────────────────────────

    /**
     * Returns the nearest bank booth or chest GameObject in the scene, or null.
     * Detects by checking if the object's definition includes a "Bank" action.
     * Works at any bank without needing hardcoded IDs.
     */
    public GameObject findNearestBankObject()
    {
        return gameObjects.nearest(obj -> {
            ObjectComposition def = client.getObjectDefinition(obj.getId());
            if (def == null) return false;
            String[] actions = def.getActions();
            if (actions == null) return false;
            for (String action : actions)
            {
                if ("Bank".equals(action)) return true;
            }
            return false;
        });
    }

    /**
     * Returns the nearest Banker NPC in the scene, or null.
     */
    public NPC findNearestBanker()
    {
        return npcs.nearest(npc -> {
            String name = npc.getName();
            return name != null && (name.equals("Banker") || name.contains("Banker"));
        });
    }

    /**
     * Returns true if a bank object or banker NPC is within 10 tiles.
     */
    public boolean isNearBank()
    {
        GameObject obj = findNearestBankObject();
        if (obj != null && obj.getWorldLocation().distanceTo(
            client.getLocalPlayer().getWorldLocation()) <= 10) return true;

        NPC banker = findNearestBanker();
        return banker != null && banker.getWorldLocation().distanceTo(
            client.getLocalPlayer().getWorldLocation()) <= 10;
    }

    /**
     * Opens the nearest bank (booth, chest, or banker NPC) if one is nearby.
     * Returns true if an open attempt was made, false if nothing nearby.
     */
    public boolean openNearest()
    {
        GameObject obj = findNearestBankObject();
        if (obj != null)
        {
            open(obj);
            return true;
        }
        NPC banker = findNearestBanker();
        if (banker != null)
        {
            open(banker);
            return true;
        }
        return false;
    }

    // ── Bank item queries ────────────────────────────────────────────────────

    /**
     * Returns true if the bank contains the given item (bank must be open).
     */
    public boolean contains(int itemId)
    {
        Widget itemsContainer = client.getWidget(BANK_GROUP_ID, ITEMS_CHILD_ID);
        if (itemsContainer == null) return false;
        Widget[] children = itemsContainer.getDynamicChildren();
        if (children == null) return false;
        for (Widget child : children)
        {
            if (child != null && child.getItemId() == itemId) return true;
        }
        return false;
    }

    // ── Closing ───────────────────────────────────────────────────────────────

    /**
     * Closes the bank interface by clicking the close button (group 12, child 2).
     *
     * <p>Does nothing if the bank is already closed or the widget is not found.
     * Pressing Escape is an equivalent action the player can do manually; this
     * method clicks the on-screen button to keep interaction consistent.
     */
    public void close()
    {
        Widget closeBtn = client.getWidget(BANK_GROUP_ID, CLOSE_CHILD_ID);
        if (closeBtn == null || closeBtn.isHidden())
        {
            log.warn("close: bank close button not found or hidden");
            return;
        }
        log.debug("Closing bank via close button");
        interaction.click(closeBtn);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static int quantityToOp(int quantity)
    {
        if (quantity == 1)               return 1;
        if (quantity == 5)               return 2;
        if (quantity == 10)              return 3;
        return 7; // "Withdraw-All"
    }
}
