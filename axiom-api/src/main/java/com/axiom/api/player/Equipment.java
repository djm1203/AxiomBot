package com.axiom.api.player;

/**
 * Equipment slot queries.
 * Implementation lives in axiom-plugin.
 */
public interface Equipment
{
    /**
     * Equipment slot indices matching RuneLite's EquipmentInventorySlot ordinals.
     * Use these constants instead of raw integers.
     */
    int SLOT_HEAD    = 0;
    int SLOT_CAPE    = 1;
    int SLOT_AMULET  = 2;
    int SLOT_WEAPON  = 3;
    int SLOT_BODY    = 4;
    int SLOT_SHIELD  = 5;
    int SLOT_LEGS    = 7;
    int SLOT_GLOVES  = 9;
    int SLOT_BOOTS   = 10;
    int SLOT_RING    = 12;
    int SLOT_AMMO    = 13;

    /** Returns true if the given equipment slot has an item equipped. */
    boolean isEquipped(int slot);

    /** Returns the item ID in the given slot, or -1 if empty. */
    int getItemId(int slot);

    /** Returns the item name in the given slot, or empty string if empty. */
    String getItemName(int slot);

    /** Returns true if any equipped item has the given ID. */
    boolean hasItemEquipped(int itemId);

    /** Returns true if any equipped item's name contains the given string. */
    boolean hasItemEquippedByName(String name);
}
