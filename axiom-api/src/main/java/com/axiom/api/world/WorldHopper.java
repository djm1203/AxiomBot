package com.axiom.api.world;

/**
 * World hopping API.
 * Implementation lives in axiom-plugin.
 */
public interface WorldHopper
{
    /**
     * Hops to the given world number.
     * @param world the target world (e.g. 302, 393)
     */
    void hopTo(int world);

    /**
     * Hops to a random low-population members world.
     * Used for anti-competition logic when another player enters the area.
     */
    void hopToLowPop();

    /** Returns the current world number. */
    int getCurrentWorld();
}
