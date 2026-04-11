package com.axiom.plugin.impl;

import com.axiom.api.util.Pathfinder;
import com.axiom.plugin.impl.world.MovementImpl;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Phase 1 pathfinder: simple straight-line walk.
 *
 * A full obstacle-aware pathfinder (door opening, stair climbing) is a
 * significant undertaking. For Phase 1 the implementation delegates to
 * MovementImpl.walkTo() — scripts that need real pathfinding should keep
 * their routes in open areas where no obstacles exist.
 *
 * A production implementation would integrate ShortestPathPlugin or a
 * custom A* over the collision flags in the scene.
 */
@Slf4j
@Singleton
public class PathfinderImpl implements Pathfinder
{
    private final Client      client;
    private final MovementImpl movement;

    @Inject
    public PathfinderImpl(Client client, MovementImpl movement)
    {
        this.client   = client;
        this.movement = movement;
    }

    @Override
    public void walkTo(int worldX, int worldY, int plane)
    {
        if (!isReachable(worldX, worldY, plane))
        {
            log.warn("PathfinderImpl: ({},{},{}) is not reachable — skipping", worldX, worldY, plane);
            return;
        }
        movement.walkTo(worldX, worldY);
    }

    @Override
    public boolean isReachable(int worldX, int worldY, int plane)
    {
        // Phase 1: consider any tile on the same plane within 32 tiles reachable.
        // Phase 2: check collision flags in the scene.
        if (client.getPlane() != plane) return false;

        WorldPoint playerPos = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getWorldLocation()
            : null;
        if (playerPos == null) return false;

        int dist = Math.max(Math.abs(playerPos.getX() - worldX),
                            Math.abs(playerPos.getY() - worldY));
        return dist <= 32;
    }
}
