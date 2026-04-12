package com.axiom.api.game;

/**
 * Local player state queries.
 * Implementation lives in axiom-plugin (wraps net.runelite.api.Client).
 */
public interface Players
{
    /** Returns true if the local player is not moving and not animating. */
    boolean isIdle();

    /** Returns true if the local player is currently moving. */
    boolean isMoving();

    /** Returns true if the local player is currently animating (not idle animation). */
    boolean isAnimating();

    /** Returns true if the local player is in combat (has an interacting target). */
    boolean isInCombat();

    /** Returns the local player's current health percentage (0–100). */
    int getHealthPercent();

    /** Returns the world X coordinate of the local player. */
    int getWorldX();

    /** Returns the world Y coordinate of the local player. */
    int getWorldY();

    /** Returns the plane (0 = ground floor, 1 = first floor, etc.). */
    int getPlane();

    /** Returns the Chebyshev distance from the local player to the given world tile. */
    int distanceTo(int worldX, int worldY);

    /** Returns true if the local player is at the given world coordinates. */
    boolean isAt(int worldX, int worldY);

    /**
     * Returns the current animation ID of the local player, or -1 if idle.
     * Use this for precise animation detection (e.g. alchemy = 712, chop = 879).
     */
    int getAnimation();

    /**
     * Returns the current graphic (SpotAnim) ID on the local player, or -1 if none.
     * Used to detect stun during pickpocketing (graphic 245 = stun hitsplat).
     */
    int getGraphic();
}
