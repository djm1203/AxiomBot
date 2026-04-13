package com.botengine.osrs.api;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Helpers for finding GameObjects in the current scene.
 *
 * GameObjects are the interactive items placed in the world: trees, rocks,
 * fishing spots (when object-based), furnaces, anvils, bank booths, doors, etc.
 *
 * Unlike NPCs, GameObjects are stored in the scene tile array rather than
 * a flat list — so we iterate the scene tiles to collect them.
 * This is the same approach RuneLite's built-in plugins use.
 *
 * Object IDs can be found with RuneLite's Object Markers plugin or the OSRS Wiki.
 *
 * Note: A depleted tree and a full tree have different object IDs.
 * Example: Oak tree = 1751, Oak stump (depleted) = 1356.
 * Always filter by the "active" ID, not the depleted one.
 */
public class GameObjects
{
    private final Client client;

    @Inject
    public GameObjects(Client client)
    {
        this.client = client;
    }

    // ── Find by ID ────────────────────────────────────────────────────────────

    /**
     * Returns the nearest GameObject with the given ID, or null.
     */
    public GameObject nearest(int id)
    {
        return nearest(obj -> obj.getId() == id);
    }

    /**
     * Returns the nearest GameObject matching any of the given IDs.
     * Useful for objects that have multiple variant IDs (e.g. different tree types).
     */
    public GameObject nearest(int... ids)
    {
        return nearest(obj -> {
            for (int id : ids)
            {
                if (obj.getId() == id) return true;
            }
            return false;
        });
    }

    // ── Find by name ─────────────────────────────────────────────────────────

    /**
     * Returns the nearest GameObject whose name contains the given string (case-insensitive).
     * Name is read from the object's definition via the client.
     */
    public GameObject nearest(String name)
    {
        return nearest(obj -> {
            String objName = client.getObjectDefinition(obj.getId()).getName();
            return objName != null && objName.toLowerCase().contains(name.toLowerCase());
        });
    }

    // ── Find by predicate ─────────────────────────────────────────────────────

    /**
     * Returns the nearest GameObject passing the given filter, or null.
     * All other nearest() calls route through here.
     */
    public GameObject nearest(Predicate<GameObject> filter)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return getAll().stream()
            .filter(filter)
            .min(Comparator.comparingInt(obj ->
                obj.getWorldLocation().distanceTo(playerPos)))
            .orElse(null);
    }

    // ── Find all ──────────────────────────────────────────────────────────────

    /**
     * Returns all GameObjects with the given ID in the scene, nearest-first.
     */
    public List<GameObject> all(int id)
    {
        return all(obj -> obj.getId() == id);
    }

    /**
     * Returns all GameObjects passing the filter, sorted nearest-first.
     */
    public List<GameObject> all(Predicate<GameObject> filter)
    {
        if (client.getLocalPlayer() == null) return java.util.Collections.emptyList();
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return getAll().stream()
            .filter(filter)
            .sorted(Comparator.comparingInt(obj ->
                obj.getWorldLocation().distanceTo(playerPos)))
            .collect(Collectors.toList());
    }

    // ── Existence checks ──────────────────────────────────────────────────────

    /**
     * Returns true if any GameObject with the given ID exists in the scene.
     */
    public boolean exists(int id)
    {
        return nearest(id) != null;
    }

    /**
     * Returns the nearest GameObject with the given ID within maxDistance tiles.
     */
    public GameObject nearestWithin(int id, int maxDistance)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return nearest(obj -> obj.getId() == id
            && obj.getWorldLocation().distanceTo(playerPos) <= maxDistance);
    }

    // ── Diagnostic ────────────────────────────────────────────────────────────

    /**
     * Returns a compact string listing all TileObjects within {@code radius} tiles,
     * sorted by distance.  Useful for finding correct obstacle IDs/names in-game.
     * Example output: "id=23145 'Log balance' (2t), id=23134 'Obstacle net' (5t)"
     */
    public String dumpNearby(int radius)
    {
        if (client.getLocalPlayer() == null) return "(no player)";
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        StringBuilder sb = new StringBuilder();
        getAllTileObjects().stream()
            .filter(obj -> obj.getWorldLocation().distanceTo(playerPos) <= radius)
            .sorted(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerPos)))
            .forEach(obj -> {
                net.runelite.api.ObjectComposition def = client.getObjectDefinition(obj.getId());
                String name = (def != null && def.getName() != null) ? def.getName() : "?";
                int dist = obj.getWorldLocation().distanceTo(playerPos);
                if (sb.length() > 0) sb.append(", ");
                sb.append("id=").append(obj.getId())
                  .append(" '").append(name).append("'")
                  .append(" (").append(dist).append("t)");
            });
        return sb.length() > 0 ? sb.toString() : "(none within " + radius + " tiles)";
    }

    // ── TileObject-aware lookup (covers WallObject, DecorativeObject, GroundObject) ──

    /**
     * Returns the nearest TileObject (any type) matching the given ID.
     * Scans GameObject, WallObject, DecorativeObject, and GroundObject slots.
     * Use this for agility obstacles and other objects that may not be GameObjects.
     */
    public TileObject nearestTileObject(int id)
    {
        return nearestTileObject(obj -> obj.getId() == id);
    }

    /**
     * Returns the nearest TileObject whose definition name contains the given string
     * (case-insensitive). Scans all four tile object slots.
     */
    public TileObject nearestTileObject(String name)
    {
        return nearestTileObject(obj -> {
            net.runelite.api.ObjectComposition def = client.getObjectDefinition(obj.getId());
            return def != null && def.getName() != null
                && def.getName().toLowerCase().contains(name.toLowerCase());
        });
    }

    /**
     * Returns the nearest TileObject passing the filter, scanning all tile object types.
     */
    public TileObject nearestTileObject(Predicate<TileObject> filter)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return getAllTileObjects().stream()
            .filter(filter)
            .min(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerPos)))
            .orElse(null);
    }

    // ── Scene traversal ───────────────────────────────────────────────────────

    /**
     * Collects all GameObjects from the scene tile array.
     *
     * RuneLite stores objects in a 3D tile grid (x, y, plane).
     * The scene is 104x104 tiles. Plane 0 = ground floor.
     * Each tile can have multiple GameObjects (e.g. door + floor decoration).
     */
    private List<GameObject> getAll()
    {
        List<GameObject> result = new ArrayList<>();
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
                    if (obj != null) result.add(obj);
                }
            }
        }
        return result;
    }

    /**
     * Collects every TileObject from the scene — GameObject, WallObject,
     * DecorativeObject, and GroundObject.  Use this when you need to find
     * objects that are not stored in the GameObject slot (e.g. agility
     * obstacles, doors stored as WallObjects).
     */
    private List<TileObject> getAllTileObjects()
    {
        List<TileObject> result = new ArrayList<>();
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
                    if (obj != null) result.add(obj);
                }

                WallObject wall = tile.getWallObject();
                if (wall != null) result.add(wall);

                DecorativeObject deco = tile.getDecorativeObject();
                if (deco != null) result.add(deco);

                GroundObject ground = tile.getGroundObject();
                if (ground != null) result.add(ground);
            }
        }
        return result;
    }
}
