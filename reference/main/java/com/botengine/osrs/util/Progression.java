package com.botengine.osrs.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a level-based progression string and resolves the current target.
 *
 * Format: "threshold:value,threshold:value,..."
 * Example: "1:Oak,30:Willow,45:Maple,60:Yew"
 *
 * Returns the value whose threshold is the highest one the player has reached.
 * If the string is empty or invalid, returns the provided fallback value.
 */
public class Progression
{
    private final List<Entry> entries = new ArrayList<>();
    private final String fallback;

    /**
     * @param progressionString comma-separated "level:value" pairs
     * @param fallback          value to use when string is blank or unparseable
     */
    public Progression(String progressionString, String fallback)
    {
        this.fallback = fallback;
        parse(progressionString);
    }

    /**
     * Returns the target value for the given player level.
     * Picks the entry with the highest threshold that is <= level.
     */
    public String resolve(int level)
    {
        String best = fallback;
        int bestThreshold = -1;
        for (Entry e : entries)
        {
            if (e.level <= level && e.level > bestThreshold)
            {
                best = e.value;
                bestThreshold = e.level;
            }
        }
        return best;
    }

    /** Returns true if no entries were successfully parsed. */
    public boolean isEmpty() { return entries.isEmpty(); }

    private void parse(String raw)
    {
        if (raw == null || raw.trim().isEmpty()) return;
        for (String part : raw.split(","))
        {
            part = part.trim();
            int colon = part.indexOf(':');
            if (colon < 1) continue;
            try
            {
                int lvl = Integer.parseInt(part.substring(0, colon).trim());
                String val = part.substring(colon + 1).trim();
                if (!val.isEmpty()) entries.add(new Entry(lvl, val));
            }
            catch (NumberFormatException ignored) {}
        }
    }

    private static class Entry
    {
        final int level;
        final String value;
        Entry(int level, String value) { this.level = level; this.value = value; }
    }
}
