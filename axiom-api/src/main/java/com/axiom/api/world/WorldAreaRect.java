package com.axiom.api.world;

/**
 * Simple rectangular world area on a single plane.
 */
public final class WorldAreaRect
{
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int plane;

    public WorldAreaRect(int minX, int minY, int maxX, int maxY, int plane)
    {
        this.minX  = Math.min(minX, maxX);
        this.minY  = Math.min(minY, maxY);
        this.maxX  = Math.max(minX, maxX);
        this.maxY  = Math.max(minY, maxY);
        this.plane = plane;
    }

    public static WorldAreaRect centeredOn(WorldTile center, int radius)
    {
        return new WorldAreaRect(
            center.getWorldX() - radius,
            center.getWorldY() - radius,
            center.getWorldX() + radius,
            center.getWorldY() + radius,
            center.getPlane()
        );
    }

    public boolean contains(WorldTile tile)
    {
        return tile != null
            && tile.getPlane() == plane
            && tile.getWorldX() >= minX
            && tile.getWorldX() <= maxX
            && tile.getWorldY() >= minY
            && tile.getWorldY() <= maxY;
    }

    public int getPlane() { return plane; }
}
