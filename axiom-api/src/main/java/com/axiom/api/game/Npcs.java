package com.axiom.api.game;

import java.util.List;
import java.util.function.Predicate;

/**
 * NPC queries.
 * Implementation lives in axiom-plugin (wraps net.runelite.api.Client NPC list).
 */
public interface Npcs
{
    /** Nearest NPC with the given ID, or null. */
    SceneObject nearest(int id);

    /** Nearest NPC whose ID matches any in the array. */
    SceneObject nearest(int... ids);

    /** Nearest NPC whose name contains the given string (case-insensitive). */
    SceneObject nearestByName(String name);

    /** Nearest NPC matching the predicate. */
    SceneObject nearest(Predicate<SceneObject> filter);

    /** All NPCs in range matching the predicate. */
    List<SceneObject> all(Predicate<SceneObject> filter);
}
