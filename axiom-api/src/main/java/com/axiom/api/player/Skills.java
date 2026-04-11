package com.axiom.api.player;

/**
 * Skill level and XP queries.
 * Implementation lives in axiom-plugin (wraps net.runelite.api.Skill).
 *
 * Skill names match RuneLite's Skill enum names exactly so the
 * implementation can do a simple valueOf() lookup.
 */
public interface Skills
{
    /**
     * Skill identifiers. These names match net.runelite.api.Skill enum constants
     * so axiom-plugin can convert them without a mapping table.
     */
    enum Skill
    {
        ATTACK, DEFENCE, STRENGTH, HITPOINTS, RANGED, PRAYER, MAGIC,
        COOKING, WOODCUTTING, FLETCHING, FISHING, FIREMAKING, CRAFTING,
        SMITHING, MINING, HERBLORE, AGILITY, THIEVING, SLAYER, FARMING,
        RUNECRAFT, HUNTER, CONSTRUCTION
    }

    /** Returns the player's current (boosted/drained) level in the given skill. */
    int getLevel(Skill skill);

    /** Returns the player's base (unboosted) level in the given skill. */
    int getBaseLevel(Skill skill);

    /** Returns the player's total XP in the given skill. */
    int getXp(Skill skill);

    /** Returns the XP remaining until the next level in the given skill. */
    int getXpToNextLevel(Skill skill);
}
