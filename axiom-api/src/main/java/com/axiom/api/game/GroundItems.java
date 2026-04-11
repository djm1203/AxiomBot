package com.axiom.api.game;

import java.util.function.Predicate;

/**
 * Ground item queries.
 * Implementation lives in axiom-plugin.
 */
public interface GroundItems
{
    /** Nearest ground item with the given item ID, or null. */
    SceneObject nearest(int itemId);

    /** Nearest ground item whose name contains the string (case-insensitive). */
    SceneObject nearestByName(String name);

    /** Nearest ground item matching the predicate. */
    SceneObject nearest(Predicate<SceneObject> filter);

    /** Returns true if any ground item with the given ID is visible. */
    boolean exists(int itemId);
}
