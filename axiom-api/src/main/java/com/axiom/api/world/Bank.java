package com.axiom.api.world;

/**
 * Bank interaction API.
 * Implementation lives in axiom-plugin.
 */
public interface Bank
{
    /** Returns true if the bank interface is currently open. */
    boolean isOpen();

    /** Returns true if the player is close enough to a bank to open it. */
    boolean isNearBank();

    /**
     * Opens the nearest bank booth, chest, or counter.
     * Does nothing if already open. May need to walk closer first.
     */
    void openNearest();

    /** Deposits all items currently in the inventory. */
    void depositAll();

    /**
     * Deposits all inventory items whose IDs are NOT in the protected list.
     * Sends exactly one deposit action per call (the server processes one
     * action per tick). Scripts must loop until this returns false:
     *
     * <pre>
     *   if (bank.depositAllExcept(toolId)) { setTickDelay(1); return; }
     *   // inventory is clean — safe to close
     * </pre>
     *
     * @param protectedIds item IDs to keep (tools, rods, nets, axes, etc.)
     * @return true if a deposit action was sent (call again next tick),
     *         false when no unprotected items remain
     */
    boolean depositAllExcept(int... protectedIds);

    /**
     * Deposits all items with the given ID.
     * @param itemId the item to deposit
     */
    void deposit(int itemId);

    /**
     * Deposits a specific quantity of the given item.
     * @param itemId   the item to deposit
     * @param quantity how many to deposit (use Integer.MAX_VALUE for all)
     */
    void deposit(int itemId, int quantity);

    /**
     * Withdraws items from the bank.
     * @param itemId   the item to withdraw
     * @param quantity how many to withdraw (use Integer.MAX_VALUE for all)
     */
    void withdraw(int itemId, int quantity);

    /**
     * Withdraws all of the given item from the bank.
     */
    void withdrawAll(int itemId);

    /** Returns true if the bank contains at least one of the given item. */
    boolean contains(int itemId);

    /** Closes the bank interface. */
    void close();
}
