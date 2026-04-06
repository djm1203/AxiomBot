package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Helpers for finding items on the ground in the current scene.
 *
 * Ground items are stored per-tile in the scene's tile array, not in a flat list.
 * This class scans the tiles around the player to collect {@link TileItem} instances.
 *
 * Note: {@link TileItem} only exposes {@code getId()} and {@code getQuantity()}.
 * To get the tile a TileItem is on (needed for {@link Interaction#click(TileItem, Tile)}),
 * use {@link #nearestWithTile(int...)} which returns a paired result.
 *
 * Item IDs visible on ground are the same as inventory item IDs.
 * Noted items (id+1 of the base item) are also supported.
 */
public class GroundItems
{
    /** Default search radius in tiles for nearest() calls. */
    private static final int DEFAULT_RADIUS = 15;

    private final Client client;

    @Inject
    public GroundItems(Client client)
    {
        this.client = client;
    }

    // ── Find by ID ────────────────────────────────────────────────────────────

    /**
     * Returns the nearest ground item matching any of the given IDs within the
     * default search radius, or null. The item's tile is discarded — use
     * {@link #nearestWithTile(int...)} when you need to interact with it.
     */
    public TileItem nearest(int... itemIds)
    {
        TileItemOnTile result = nearestWithTile(itemIds);
        return result != null ? result.item : null;
    }

    /**
     * Returns the nearest ground item passing the filter, paired with its tile.
     * Returns null if no matching item found within the default radius.
     */
    public TileItemOnTile nearestWithTile(int... itemIds)
    {
        return nearestWithTile(item -> {
            for (int id : itemIds)
            {
                if (item.getId() == id) return true;
            }
            return false;
        });
    }

    // ── Find by predicate ─────────────────────────────────────────────────────

    /**
     * Returns the nearest matching ground item and its tile, or null.
     */
    public TileItemOnTile nearestWithTile(Predicate<TileItem> filter)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();

        return allWithTile(DEFAULT_RADIUS, filter).stream()
            .min(Comparator.comparingInt(r ->
                r.tile.getWorldLocation().distanceTo(playerPos)))
            .orElse(null);
    }

    // ── Find all ──────────────────────────────────────────────────────────────

    /**
     * Returns all ground items with the given IDs within the given tile radius,
     * sorted nearest-first.
     */
    public List<TileItemOnTile> allWithTile(int radius, int... itemIds)
    {
        return allWithTile(radius, item -> {
            for (int id : itemIds)
            {
                if (item.getId() == id) return true;
            }
            return false;
        });
    }

    /**
     * Returns all ground items passing the filter within the given tile radius,
     * sorted nearest-first.
     */
    public List<TileItemOnTile> allWithTile(int radius, Predicate<TileItem> filter)
    {
        if (client.getLocalPlayer() == null) return List.of();
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();

        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        int sceneX = client.getLocalPlayer().getLocalLocation().getSceneX();
        int sceneY = client.getLocalPlayer().getLocalLocation().getSceneY();

        List<TileItemOnTile> results = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                int tx = sceneX + dx;
                int ty = sceneY + dy;
                if (tx < 0 || ty < 0 || tx >= 104 || ty >= 104) continue;

                Tile tile = tiles[plane][tx][ty];
                if (tile == null) continue;

                List<TileItem> items = tile.getGroundItems();
                if (items == null) continue;

                for (TileItem item : items)
                {
                    if (item != null && filter.test(item))
                    {
                        results.add(new TileItemOnTile(item, tile));
                    }
                }
            }
        }

        results.sort(Comparator.comparingInt(r -> r.tile.getWorldLocation().distanceTo(playerPos)));
        return results;
    }

    // ── Existence checks ──────────────────────────────────────────────────────

    /**
     * Returns true if any ground item with the given ID exists within the default radius.
     */
    public boolean exists(int itemId)
    {
        return nearest(itemId) != null;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Pairs a TileItem with the tile it was found on.
     * Both are needed to call {@link Interaction#click(TileItem, Tile)}.
     */
    public static class TileItemOnTile
    {
        public final TileItem item;
        public final Tile tile;

        public TileItemOnTile(TileItem item, Tile tile)
        {
            this.item = item;
            this.tile = tile;
        }
    }
}
