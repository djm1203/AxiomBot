package com.botengine.osrs.api;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Helpers for finding and filtering NPCs in the current scene.
 *
 * RuneLite provides client.getNpcs() which returns all NPCs currently loaded
 * in the scene (roughly 104x104 tiles around the player). These helpers wrap
 * common filtering patterns scripts use repeatedly.
 *
 * NPC IDs can be looked up on the OSRS Wiki or found with RuneLite's
 * NPC Indicators plugin (right-click an NPC → tag → see ID in tooltip).
 *
 * Important: NPCs outside the loaded scene are not returned here.
 * If your target has wandered far away, it may not be in the list.
 */
public class Npcs
{
    private final Client client;

    @Inject
    public Npcs(Client client)
    {
        this.client = client;
    }

    // ── Find by ID ────────────────────────────────────────────────────────────

    /**
     * Returns the nearest NPC with the given ID, or null if none found.
     * Distance is measured in tiles from the player's current position.
     */
    public NPC nearest(int id)
    {
        return nearest(npc -> npc.getId() == id);
    }

    /**
     * Returns the nearest NPC matching any of the given IDs.
     * Useful when an NPC has multiple IDs (e.g. fishing spots vary by location).
     */
    public NPC nearest(int... ids)
    {
        return nearest(npc -> {
            for (int id : ids)
            {
                if (npc.getId() == id) return true;
            }
            return false;
        });
    }

    // ── Find by name ─────────────────────────────────────────────────────────

    /**
     * Returns the nearest NPC whose name contains the given string (case-insensitive).
     * Example: nearest("Chicken") matches "Chicken" and "Undead Chicken".
     */
    public NPC nearest(String name)
    {
        return nearest(npc -> npc.getName() != null
            && npc.getName().toLowerCase().contains(name.toLowerCase()));
    }

    // ── Find by predicate ─────────────────────────────────────────────────────

    /**
     * Returns the nearest NPC passing the given filter, or null.
     * This is the base method — all other nearest() calls go through here.
     */
    public NPC nearest(Predicate<NPC> filter)
    {
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return client.getNpcs().stream()
            .filter(npc -> npc != null && !npc.isDead())
            .filter(filter)
            .min(Comparator.comparingInt(npc ->
                npc.getWorldLocation().distanceTo(playerPos)))
            .orElse(null);
    }

    // ── Find all ──────────────────────────────────────────────────────────────

    /**
     * Returns all alive NPCs with the given ID, sorted nearest-first.
     */
    public List<NPC> all(int id)
    {
        return all(npc -> npc.getId() == id);
    }

    /**
     * Returns all alive NPCs passing the filter, sorted nearest-first.
     */
    public List<NPC> all(Predicate<NPC> filter)
    {
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return client.getNpcs().stream()
            .filter(npc -> npc != null && !npc.isDead())
            .filter(filter)
            .sorted(Comparator.comparingInt(npc ->
                npc.getWorldLocation().distanceTo(playerPos)))
            .collect(Collectors.toList());
    }

    // ── Distance checks ───────────────────────────────────────────────────────

    /**
     * Returns the nearest NPC with the given ID within maxDistance tiles, or null.
     */
    public NPC nearestWithin(int id, int maxDistance)
    {
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return nearest(npc -> npc.getId() == id
            && npc.getWorldLocation().distanceTo(playerPos) <= maxDistance);
    }

    /**
     * Returns true if any NPC with the given ID exists within the scene.
     */
    public boolean exists(int id)
    {
        return nearest(id) != null;
    }

    /**
     * Returns true if any NPC with the given name exists within the scene.
     */
    public boolean exists(String name)
    {
        return nearest(name) != null;
    }
}
