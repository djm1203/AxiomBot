package com.axiom.api.util;

import com.axiom.api.player.Inventory;

import java.util.Arrays;

/**
 * Small reusable inventory snapshot helper for scripts that need to detect
 * item consumption or production without relying only on animation state.
 */
public final class InventoryDeltaTracker
{
    private int[] trackedItemIds = new int[0];
    private int[] baselineCounts = new int[0];

    public void capture(Inventory inventory, int... itemIds)
    {
        trackedItemIds = Arrays.copyOf(itemIds, itemIds.length);
        baselineCounts = new int[itemIds.length];
        for (int i = 0; i < itemIds.length; i++)
        {
            baselineCounts[i] = inventory.count(itemIds[i]);
        }
    }

    public boolean hasAnyIncrease(Inventory inventory)
    {
        for (int i = 0; i < trackedItemIds.length; i++)
        {
            if (inventory.count(trackedItemIds[i]) > baselineCounts[i])
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyDecrease(Inventory inventory)
    {
        for (int i = 0; i < trackedItemIds.length; i++)
        {
            if (inventory.count(trackedItemIds[i]) < baselineCounts[i])
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyChange(Inventory inventory)
    {
        return hasAnyIncrease(inventory) || hasAnyDecrease(inventory);
    }

    public void reset()
    {
        trackedItemIds = new int[0];
        baselineCounts = new int[0];
    }
}
