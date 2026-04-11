package com.axiom.plugin.impl.game;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.SceneObject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class GameObjectsImpl implements GameObjects
{
    private final Client client;

    @Inject
    public GameObjectsImpl(Client client) { this.client = client; }

    @Override
    public SceneObject nearest(int id)
    {
        return nearest(o -> o.getId() == id);
    }

    @Override
    public SceneObject nearest(int... ids)
    {
        return nearest(o -> {
            for (int id : ids) if (o.getId() == id) return true;
            return false;
        });
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
    public List<SceneObject> all(Predicate<SceneObject> filter)
    {
        if (client.getLocalPlayer() == null) return List.of();
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return getAllWrapped().stream()
            .filter(filter)
            .sorted(Comparator.comparingInt(o ->
                Math.max(Math.abs(o.getWorldX() - playerPos.getX()),
                         Math.abs(o.getWorldY() - playerPos.getY()))))
            .collect(Collectors.toList());
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

                for (GameObject obj : tile.getGameObjects())
                {
                    if (obj != null) result.add(new SceneObjectWrapper(client, obj));
                }

                WallObject wall = tile.getWallObject();
                if (wall != null) result.add(new SceneObjectWrapper(client, wall));

                DecorativeObject deco = tile.getDecorativeObject();
                if (deco != null) result.add(new SceneObjectWrapper(client, deco));

                GroundObject ground = tile.getGroundObject();
                if (ground != null) result.add(new SceneObjectWrapper(client, ground));
            }
        }
        return result;
    }
}
