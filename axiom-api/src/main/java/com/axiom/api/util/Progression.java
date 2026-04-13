package com.axiom.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps skill level thresholds to named methods (e.g., tree types, fish spots).
 *
 * A progression string encodes a series of level → method pairs:
 *   {@code "1:Normal,30:Willow,45:Maple,60:Yew,75:Magic"}
 *
 * At any given level, {@link #getMethodForLevel(int)} returns the method
 * corresponding to the highest threshold that is ≤ the current level.
 * If the level is below all thresholds, the first entry's method is returned.
 * If the progression is empty, the provided fallback is returned.
 *
 * Use the static factory {@link #parse(String)} to create an instance.
 * Use {@link #parse(String, String)} when a sensible fallback is available
 * in case the string is blank or entirely malformed.
 *
 * Thread-safe for reads after construction (immutable entries list).
 */
public final class Progression
{
    /** Default woodcutting progression for auto-mode. */
    public static final String DEFAULT_WOODCUTTING = "1:Oak,30:Willow,45:Maple,60:Yew,75:Magic";

    private final List<Entry> entries;
    private final String      fallback;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Parses a progression string with no fallback (returns null when no match).
     *
     * @param progression comma-separated {@code "level:method"} pairs
     * @return a Progression instance; never null
     */
    public static Progression parse(String progression)
    {
        return parse(progression, null);
    }

    /**
     * Parses a progression string with an explicit fallback value.
     * The fallback is returned when the progression is empty or the level is
     * below every defined threshold.
     *
     * @param progression comma-separated {@code "level:method"} pairs
     * @param fallback    value to return when no entry matches
     * @return a Progression instance; never null
     */
    public static Progression parse(String progression, String fallback)
    {
        return new Progression(progression, fallback);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    private Progression(String raw, String fallback)
    {
        this.fallback = fallback;
        this.entries  = parseEntries(raw);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns the method name appropriate for the given skill level.
     *
     * Algorithm: scan entries in ascending threshold order and return the
     * last one whose level is ≤ the given level. If none qualifies,
     * return the first entry's method (handles level below first threshold).
     * If empty, return the configured fallback (may be null).
     *
     * @param level current skill level (1–99)
     * @return method name, or fallback/null if empty
     */
    public String getMethodForLevel(int level)
    {
        if (entries.isEmpty()) return fallback;

        String best = entries.get(0).method; // default: first entry
        for (Entry e : entries)
        {
            if (e.level <= level) best = e.method;
        }
        return best;
    }

    /** Returns true if no valid entries were parsed. */
    public boolean isEmpty()
    {
        return entries.isEmpty();
    }

    /** Returns an unmodifiable view of the parsed entries, sorted by level ascending. */
    public List<Entry> getEntries()
    {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public String toString()
    {
        return "Progression" + entries;
    }

    // ── Entry ─────────────────────────────────────────────────────────────────

    /** A single threshold → method pair. */
    public static final class Entry
    {
        public final int    level;
        public final String method;

        private Entry(int level, String method)
        {
            this.level  = level;
            this.method = method;
        }

        @Override
        public String toString()
        {
            return level + ":" + method;
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private static List<Entry> parseEntries(String raw)
    {
        List<Entry> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;

        for (String token : raw.split(","))
        {
            token = token.trim();
            if (token.isEmpty()) continue;

            int colonIdx = token.indexOf(':');
            if (colonIdx <= 0 || colonIdx == token.length() - 1)
            {
                // Malformed: no colon, colon at start, or colon at end
                continue;
            }

            String levelStr = token.substring(0, colonIdx).trim();
            String method   = token.substring(colonIdx + 1).trim();
            if (method.isEmpty()) continue;

            try
            {
                int level = Integer.parseInt(levelStr);
                if (level < 1 || level > 99) continue; // out of OSRS range
                result.add(new Entry(level, method));
            }
            catch (NumberFormatException ignored)
            {
                // Non-integer level — skip
            }
        }

        // Sort ascending by level so getMethodForLevel() scan works correctly
        result.sort((a, b) -> Integer.compare(a.level, b.level));
        return result;
    }
}
