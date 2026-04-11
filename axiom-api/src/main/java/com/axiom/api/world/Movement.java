package com.axiom.api.world;

/**
 * Basic player movement API (no obstacle handling).
 * Implementation lives in axiom-plugin.
 *
 * Use Pathfinder.walkTo() when the path might cross doors, gates, or staircases.
 * Use Movement.walkTo() only for short open-area walks where no obstacles exist.
 */
public interface Movement
{
    /**
     * Clicks the minimap or ground to walk toward the given world coordinates.
     * Does not handle obstacles — use Pathfinder for paths with doors/stairs.
     */
    void walkTo(int worldX, int worldY);

    /** Returns true if the player is currently moving. */
    boolean isMoving();

    /** Enables or disables run energy consumption. */
    void setRunning(boolean enabled);

    /** Returns the player's current run energy (0–100). */
    int getRunEnergy();

    /** Logs the player out gracefully. */
    void logout();
}
