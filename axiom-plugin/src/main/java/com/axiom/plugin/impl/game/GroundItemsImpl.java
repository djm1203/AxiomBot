package com.axiom.plugin.impl.game;

import com.axiom.api.game.GroundItems;
import com.axiom.api.game.SceneObject;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Singleton
public class GroundItemsImpl implements GroundItems
{
    private final Client client;

    @Inject
    public GroundItemsImpl(Client client) { this.client = client; }

    @Override
    public SceneObject nearest(int itemId)
    {
        return nearest(o -> o.getId() == itemId);
    }

    @Override
    public SceneObject nearestByName(String name)
    {
        return nearest(o -> o.getName().toLowerCase().contains(name.toLowerCase()));
    }

    @Override
    public SceneObject nearest(Predicate<SceneObject> filter)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return getAllWrapped().stream()
            .filter(filter)
            .min(Comparator.comparingInt(o ->
                Math.max(Math.abs(o.getWorldX() - playerPos.getX()),
                         Math.abs(o.getWorldY() - playerPos.getY()))))
            .orElse(null);
    }

    @Override
    public boolean exists(int itemId)
    {
        return nearest(itemId) != null;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private List<SceneObject> getAllWrapped()
    {
        List<SceneObject> result = new ArrayList<>();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();

        for (int x = 0; x < tiles[plane].length; x++)
        {
            for (int y = 0; y < tiles[plane][x].length; y++)
            {
                Tile tile = tiles[plane][x][y];
                if (tile == null) continue;

                ItemLayer layer = tile.getItemLayer();
                if (layer == null) continue;

                // ItemLayer is a linked list of TileItems
                net.runelite.api.Node node = layer.getBottom();
                while (node instanceof TileItem)
                {
                    TileItem item = (TileItem) node;
                    result.add(new GroundItemWrapper(client, item, tile.getWorldLocation()));
                    node = node.getNext();
                }
            }
        }
        return result;
    }
}
