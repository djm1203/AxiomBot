package com.axiom.plugin.impl.player;

import com.axiom.api.player.Equipment;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EquipmentImpl implements Equipment
{
    private final Client client;

    @Inject
    public EquipmentImpl(Client client) { this.client = client; }

    @Override
    public boolean isEquipped(int slot)
    {
        return getItemId(slot) != -1;
    }

    @Override
    public int getItemId(int slot)
    {
        ItemContainer equip = getContainer();
        if (equip == null) return -1;
        Item item = equip.getItem(slot);
        return item != null ? item.getId() : -1;
    }

    @Override
    public String getItemName(int slot)
    {
        int id = getItemId(slot);
        if (id == -1) return "";
        ItemComposition def = client.getItemDefinition(id);
        return (def != null && def.getName() != null) ? def.getName() : "";
    }

    @Override
    public boolean hasItemEquipped(int itemId)
    {
        ItemContainer equip = getContainer();
        if (equip == null) return false;
        for (Item item : equip.getItems())
        {
            if (item != null && item.getId() == itemId) return true;
        }
        return false;
    }

    @Override
    public boolean hasItemEquippedByName(String name)
    {
        ItemContainer equip = getContainer();
        if (equip == null) return false;
        for (Item item : equip.getItems())
        {
            if (item == null || item.getId() == -1) continue;
            ItemComposition def = client.getItemDefinition(item.getId());
            if (def != null && def.getName() != null
                && def.getName().toLowerCase().contains(name.toLowerCase()))
            {
                return true;
            }
        }
        return false;
    }

    private ItemContainer getContainer()
    {
        return client.getItemContainer(InventoryID.EQUIPMENT);
    }
}
