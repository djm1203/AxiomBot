package com.axiom.api.player;

/**
 * Prayer state queries and actions.
 * Implementation lives in axiom-plugin.
 */
public interface Prayer
{
    /**
     * Common prayer identifiers. These match RuneLite's Prayer enum names
     * for easy lookup in the implementation.
     */
    enum PrayerType
    {
        // Protection
        PROTECT_FROM_MELEE, PROTECT_FROM_MISSILES, PROTECT_FROM_MAGIC,
        // Offensive
        PIETY, RIGOUR, AUGURY,
        ULTIMATE_STRENGTH, INCREDIBLE_REFLEXES, EAGLE_EYE, MYSTIC_MIGHT,
        // Basic
        THICK_SKIN, BURST_OF_STRENGTH, CLARITY_OF_THOUGHT,
        SHARP_EYE, MYSTIC_WILL, ROCK_SKIN, SUPERHUMAN_STRENGTH,
        IMPROVED_REFLEXES, RAPID_RESTORE, RAPID_HEAL, PROTECT_ITEM,
        HAWK_EYE, MYSTIC_LORE, STEEL_SKIN, CHIVALRY,
        PRESERVE, SMITE, RETRIBUTION, REDEMPTION
    }

    /** Returns true if the given prayer is currently active. */
    boolean isActive(PrayerType prayer);

    /** Activates the given prayer. No-op if already active. */
    void activate(PrayerType prayer);

    /** Deactivates the given prayer. No-op if not active. */
    void deactivate(PrayerType prayer);

    /** Deactivates all currently active prayers. */
    void deactivateAll();

    /** Returns the player's current prayer points. */
    int getPoints();

    /** Returns true if the player has at least the given number of prayer points. */
    boolean hasPoints(int minimum);
}
