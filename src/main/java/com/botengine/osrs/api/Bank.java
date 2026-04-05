package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;

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

    private final Client client;
    private final Interaction interaction;

    @Inject
    public Bank(Client client, Interaction interaction)
    {
        this.client = client;
        this.interaction = interaction;
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
     *
     * <p>TODO: Full implementation requires traversing the bank item widgets
     * (group 12, items container child) to find the slot that contains
     * {@code itemId}, then invoking the appropriate withdraw-N menu action on
     * that slot. The correct {@link MenuAction} variant depends on whether the
     * bank is in "withdraw as note" mode and which quantity option is selected
     * (1, 5, 10, X, All). This is left for a future implementation.
     *
     * @param itemId   the item ID to withdraw
     * @param quantity the number of items to withdraw (1, 5, 10, or a custom amount)
     */
    public void withdraw(int itemId, int quantity)
    {
        // TODO: Implement full widget traversal.
        //   1. Locate the bank items container widget (group 12, items child).
        //   2. Iterate its dynamic children to find the slot whose item ID
        //      matches itemId.
        //   3. Determine the MenuAction based on quantity:
        //        1  -> CC_OP (op 1)
        //        5  -> CC_OP (op 2)
        //        10 -> CC_OP (op 3)
        //        X  -> CC_OP_LOW_PRIORITY with an amount dialog
        //        All -> CC_OP (op 5 or 7 depending on note mode)
        //   4. Call client.invokeMenuAction with the resolved slot index and
        //      the widget's packed ID.
        log.warn("withdraw(itemId={}, quantity={}) is not yet implemented", itemId, quantity);
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
}
