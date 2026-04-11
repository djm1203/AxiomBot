package com.axiom.api.util;

/**
 * Obstacle-aware pathfinding API.
 *
 * Implementations live in axiom-plugin where RuneLite APIs are available.
 * Scripts call these methods without knowing about the underlying implementation.
 *
 * Phase 1: basic BFS over RuneLite's collision map.
 * Phase 2: integrate ShortestPath plugin if available, fall back to BFS.
 *
 * Handles doors, gates, and staircases automatically.
 * Use this (not Movement.walkTo) whenever the path might cross an obstacle.
 */
public interface Pathfinder
{
    /**
     * Walks to the given world coordinates, handling obstacles automatically.
     * Clicks through doors, gates, and up/down staircases as needed.
     *
     * @param worldX  world X coordinate
     * @param worldY  world Y coordinate
     * @param plane   plane (0 = ground, 1 = first floor, etc.)
     */
    void walkTo(int worldX, int worldY, int plane);

    /**
     * Returns true if the given tile is reachable from the player's current position
     * using obstacle-aware pathfinding.
     */
    boolean isReachable(int worldX, int worldY, int plane);
}
