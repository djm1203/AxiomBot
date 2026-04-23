package com.axiom.plugin.impl.game;

import com.axiom.api.game.Messages;

import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

@Singleton
public class MessagesImpl implements Messages
{
    private static final int MAX_BUFFER_SIZE = 100;
    private static final int MAX_MESSAGE_AGE_TICKS = 100;

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    @Override
    public synchronized boolean containsRecent(String text, int maxAgeTicks)
    {
        return findContaining(text, maxAgeTicks, false) != null;
    }

    @Override
    public synchronized String pollRecent(String text, int maxAgeTicks)
    {
        Entry match = findContaining(text, maxAgeTicks, true);
        return match != null ? match.text : null;
    }

    @Override
    public synchronized boolean containsRecentRegex(String regex, int maxAgeTicks)
    {
        return findRegex(regex, maxAgeTicks, false) != null;
    }

    @Override
    public synchronized String pollRecentRegex(String regex, int maxAgeTicks)
    {
        Entry match = findRegex(regex, maxAgeTicks, true);
        return match != null ? match.text : null;
    }

    @Override
    public synchronized void clear()
    {
        entries.clear();
    }

    public synchronized void record(String text, int tick)
    {
        if (text == null || text.isBlank())
        {
            return;
        }

        prune(tick);
        entries.addLast(new Entry(text, normalize(text), tick));
        while (entries.size() > MAX_BUFFER_SIZE)
        {
            entries.removeFirst();
        }
    }

    private Entry findContaining(String text, int maxAgeTicks, boolean remove)
    {
        String needle = normalize(text);
        int newestTick = newestTick();
        if (needle.isEmpty() || newestTick < 0)
        {
            return null;
        }

        Iterator<Entry> it = entries.iterator();
        while (it.hasNext())
        {
            Entry entry = it.next();
            if (isExpired(entry, newestTick, maxAgeTicks))
            {
                continue;
            }
            if (entry.normalized.contains(needle))
            {
                if (remove) it.remove();
                return entry;
            }
        }
        return null;
    }

    private Entry findRegex(String regex, int maxAgeTicks, boolean remove)
    {
        int newestTick = newestTick();
        if (regex == null || regex.isBlank() || newestTick < 0)
        {
            return null;
        }

        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext())
        {
            Entry entry = it.next();
            if (isExpired(entry, newestTick, maxAgeTicks))
            {
                continue;
            }
            if (pattern.matcher(entry.text).find())
            {
                if (remove) it.remove();
                return entry;
            }
        }
        return null;
    }

    private void prune(int currentTick)
    {
        while (!entries.isEmpty() && isExpired(entries.peekFirst(), currentTick, MAX_MESSAGE_AGE_TICKS))
        {
            entries.removeFirst();
        }
    }

    private int newestTick()
    {
        return entries.isEmpty() ? -1 : entries.peekLast().tick;
    }

    private static boolean isExpired(Entry entry, int currentTick, int maxAgeTicks)
    {
        int ageLimit = Math.max(0, maxAgeTicks);
        return currentTick - entry.tick > ageLimit;
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private static final class Entry
    {
        private final String text;
        private final String normalized;
        private final int    tick;

        private Entry(String text, String normalized, int tick)
        {
            this.text       = text;
            this.normalized = normalized;
            this.tick       = tick;
        }
    }
}
