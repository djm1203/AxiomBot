package com.axiom.plugin.impl.game;

import com.axiom.api.game.SceneObject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;

/**
 * Wraps a TileItem (ground item) as a SceneObject.
 */
public class GroundItemWrapper implements SceneObject
{
    private final Client client;
    private final TileItem item;
    private final int id;
    private final String name;
    private final int worldX;
    private final int worldY;
    private final int plane;

    public GroundItemWrapper(Client client, TileItem item, WorldPoint location)
    {
        this.client = client;
        this.item   = item;
        this.id     = item.getId();
        this.worldX = location.getX();
        this.worldY = location.getY();
        this.plane  = location.getPlane();

        ItemComposition def = client.getItemDefinition(id);
        this.name = (def != null && def.getName() != null) ? def.getName() : "";
    }

    @Override public int    getId()     { return id; }
    @Override public String getName()   { return name; }
    @Override public int    getWorldX() { return worldX; }
    @Override public int    getWorldY() { return worldY; }
    @Override public int    getPlane()  { return plane; }
    @Override public String[] getActions() { return new String[]{"Take"}; }

    @Override
    public boolean hasAction(String action)
    {
        return "Take".equalsIgnoreCase(action);
    }

    @Override
    public void interact(String action)
    {
        // Ground item take — fires the GROUND_ITEM_FIRST_OPTION MenuAction
        client.menuAction(
            worldX, worldY,
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            id, -1,
            "Take", name
        );
    }
}
