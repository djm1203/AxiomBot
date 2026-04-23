package com.axiom.api.world;

/**
 * Immutable world tile coordinate.
 */
public final class WorldTile
{
    private final int worldX;
    private final int worldY;
    private final int plane;

    public WorldTile(int worldX, int worldY, int plane)
    {
        this.worldX = worldX;
        this.worldY = worldY;
        this.plane  = plane;
    }

    public int getWorldX() { return worldX; }

    public int getWorldY() { return worldY; }

    public int getPlane() { return plane; }

    public int chebyshevDistanceTo(WorldTile other)
    {
        if (other == null || plane != other.plane)
        {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(worldX - other.worldX), Math.abs(worldY - other.worldY));
    }
}
