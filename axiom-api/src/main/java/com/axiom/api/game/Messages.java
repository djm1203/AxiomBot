package com.axiom.api.game;

/**
 * Recent client message feed for scripts.
 *
 * This gives scripts access to chat/game messages without coupling them to
 * RuneLite events directly. Messages are stored as a short rolling buffer and
 * can be matched by substring or regex.
 */
public interface Messages
{
    /**
     * Returns true if a recent message contains the given text.
     *
     * @param text case-insensitive substring to match
     * @param maxAgeTicks maximum message age in game ticks
     */
    boolean containsRecent(String text, int maxAgeTicks);

    /**
     * Returns the first recent message containing the given text and removes it
     * from the buffer. Returns null if none match.
     */
    String pollRecent(String text, int maxAgeTicks);

    /**
     * Returns true if a recent message matches the given regex.
     *
     * @param regex case-insensitive regex pattern
     * @param maxAgeTicks maximum message age in game ticks
     */
    boolean containsRecentRegex(String regex, int maxAgeTicks);

    /**
     * Returns the first recent message matching the given regex and removes it
     * from the buffer. Returns null if none match.
     */
    String pollRecentRegex(String regex, int maxAgeTicks);

    /** Clears the rolling message buffer. */
    void clear();
}
