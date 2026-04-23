package com.axiom.api.game;

/**
 * RuneLite-free wrapper for a game object (NPC, scenery, or ground item).
 *
 * Scripts interact with the game exclusively through this type — no
 * direct references to net.runelite.api.GameObject or net.runelite.api.NPC.
 *
 * The implementation in axiom-plugin holds the underlying RuneLite object
 * and delegates calls to it.
 */
public interface SceneObject
{
    /** The object's RuneLite definition ID. */
    int getId();

    /** The object's display name, or empty string if unavailable. */
    String getName();

    /** World X coordinate of this object's tile. */
    int getWorldX();

    /** World Y coordinate of this object's tile. */
    int getWorldY();

    /** Plane (floor level) this object is on. */
    int getPlane();

    /** Returns true if the given action string is available on this object. */
    boolean hasAction(String action);

    /** Returns all available action strings on this object. */
    String[] getActions();

    /**
     * Fires the given action on this object (e.g. "Chop down", "Mine", "Attack").
     * Equivalent to right-clicking and selecting the action.
     */
    void interact(String action);

    /**
     * Uses the currently selected item or spell on this object.
     * Scripts should call an item-selection method first, then invoke this on
     * the next tick to perform item-on-object or spell-on-object interactions.
     */
    default void useSelected() {}

    /**
     * For NPCs: returns true if this NPC is currently engaged in combat with any actor.
     * Used by the combat script to skip NPCs already being fought by another player.
     * Always returns false for non-NPC objects (game objects, ground items).
     */
    default boolean isInCombat() { return false; }
}
