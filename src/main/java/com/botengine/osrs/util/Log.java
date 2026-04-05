package com.botengine.osrs.util;

import lombok.Setter;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * Unified logging wrapper for the bot engine.
 *
 * Prefixes every message with [BotEngine] and optionally the active script name
 * so logs are easy to filter and identify in the RuneLite console.
 *
 * Supports SLF4J-style parameterized messages:
 *   log.info("Walking to {} at ({}, {})", "oaks", x, y);
 *
 * Example output:
 *   [BotEngine] [Woodcutting] Walking to oak trees at (3053, 3247)
 *   [BotEngine] [Woodcutting] Tree depleted — finding next target
 *   [BotEngine] Break started — resuming in 8 minutes
 */
public class Log
{
    private static final org.slf4j.Logger logger =
        LoggerFactory.getLogger("BotEngine");

    /** Set by ScriptRunner when a new script starts. Cleared on stop. */
    @Setter
    private String scriptName = null;

    // ── Logging methods ───────────────────────────────────────────────────────

    public void info(String message, Object... args)
    {
        logger.info(prefix(format(message, args)));
    }

    public void warn(String message, Object... args)
    {
        logger.warn(prefix(format(message, args)));
    }

    public void debug(String message, Object... args)
    {
        logger.debug(prefix(format(message, args)));
    }

    public void error(String message, Object... args)
    {
        logger.error(prefix(format(message, args)));
    }

    public void error(String message, Throwable t)
    {
        logger.error(prefix(message), t);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String format(String message, Object... args)
    {
        if (args == null || args.length == 0) return message;
        return MessageFormatter.arrayFormat(message, args).getMessage();
    }

    private String prefix(String message)
    {
        if (scriptName != null && !scriptName.isEmpty())
        {
            return "[BotEngine] [" + scriptName + "] " + message;
        }
        return "[BotEngine] " + message;
    }
}
