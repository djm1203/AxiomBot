package com.axiom.api.game;

import java.util.List;
import java.util.function.Predicate;

/**
 * Game object (scenery) queries.
 * Implementation lives in axiom-plugin (wraps net.runelite.api.Client scene).
 *
 * All methods return {@link SceneObject} — a RuneLite-free wrapper that
 * exposes id, name, world position, and interact().
 */
public interface GameObjects
{
    /**
     * Returns the nearest game object matching the given ID, or null if none found.
     * Searches within the standard scan radius (~10 tiles).
     */
    SceneObject nearest(int id);

    /**
     * Returns the nearest game object whose ID matches any in the given array.
     */
    SceneObject nearest(int... ids);

    /**
     * Returns the nearest game object matching the given name (case-insensitive),
     * or null if none found.
     */
    SceneObject nearestByName(String name);

    /**
     * Returns the nearest game object matching the given predicate, or null.
     * Use this for complex queries (e.g. has a specific action, specific name + id).
     */
    SceneObject nearest(Predicate<SceneObject> filter);

    /**
     * Returns all game objects in range matching the predicate.
     */
    List<SceneObject> all(Predicate<SceneObject> filter);
}
