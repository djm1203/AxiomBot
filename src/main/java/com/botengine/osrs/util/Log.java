package com.botengine.osrs.util;

import lombok.Setter;
import org.slf4j.LoggerFactory;

/**
 * Unified logging wrapper for the bot engine.
 *
 * Prefixes every message with [BotEngine] and optionally the active script name
 * so logs are easy to filter and identify in the RuneLite console.
 *
 * Example output:
 *   [BotEngine] [Woodcutting] Walking to oak trees at (3053, 3247)
 *   [BotEngine] [Woodcutting] Tree depleted — finding next target
 *   [BotEngine] Break started — resuming in 8 minutes
 *
 * Usage in a script:
 *   log.info("Starting woodcutting session");
 *   log.warn("No trees found within range");
 *   log.debug("Animation ID: " + animId);
 */
public class Log
{
    private static final org.slf4j.Logger logger =
        LoggerFactory.getLogger("BotEngine");

    /** Set by ScriptRunner when a new script starts. Cleared on stop. */
    @Setter
    private String scriptName = null;

    // ── Logging methods ───────────────────────────────────────────────────────

    public void info(String message)
    {
        logger.info(prefix(message));
    }

    public void warn(String message)
    {
        logger.warn(prefix(message));
    }

    public void debug(String message)
    {
        logger.debug(prefix(message));
    }

    public void error(String message)
    {
        logger.error(prefix(message));
    }

    public void error(String message, Throwable t)
    {
        logger.error(prefix(message), t);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String prefix(String message)
    {
        if (scriptName != null && !scriptName.isEmpty())
        {
            return "[BotEngine] [" + scriptName + "] " + message;
        }
        return "[BotEngine] " + message;
    }
}
