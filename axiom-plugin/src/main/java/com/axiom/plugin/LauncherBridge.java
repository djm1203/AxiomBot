package com.axiom.plugin;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads launch configuration passed from the Axiom Launcher via system properties.
 *
 * Phase 1: stub — reads system properties but takes no automatic action.
 * Phase 3: the launcher will spawn RuneLite with these args and this class
 * will auto-start the specified script.
 *
 * Properties set by the launcher:
 *   -Daxiom.script="Axiom Woodcutting"   → script to auto-start
 *   -Daxiom.world=302                    → world to hop to on start
 *   -Daxiom.account.id=<characterId>     → Jagex character ID for this instance
 *   -Daxiom.window.x=0                   → window X position
 *   -Daxiom.window.y=0                   → window Y position
 */
@Slf4j
public class LauncherBridge
{
    private final String scriptName;
    private final int    world;
    private final String accountId;
    private final int    windowX;
    private final int    windowY;

    public LauncherBridge()
    {
        scriptName = System.getProperty("axiom.script", "");
        world      = parseInt(System.getProperty("axiom.world", "0"), 0);
        accountId  = System.getProperty("axiom.account.id", "");
        windowX    = parseInt(System.getProperty("axiom.window.x", "0"), 0);
        windowY    = parseInt(System.getProperty("axiom.window.y", "0"), 0);

        if (!scriptName.isEmpty())
        {
            // accountId is a Jagex character ID — redact it in INFO logs to avoid
            // exposing it in shared log aggregators. Full value is at DEBUG.
            log.info("LauncherBridge: auto-start script='{}'  world={}  account='{}'",
                scriptName, world, redact(accountId));
            log.debug("LauncherBridge: full account id='{}'", accountId);
        }
    }

    /** Returns the script name to auto-start, or empty string if not set. */
    public String getScriptName() { return scriptName; }

    /** Returns the world to hop to on startup (0 = no hop). */
    public int getWorld() { return world; }

    /** Returns the Jagex character ID for this instance (empty = not set). */
    public String getAccountId() { return accountId; }

    /** Returns the target window X position (0 = default). */
    public int getWindowX() { return windowX; }

    /** Returns the target window Y position (0 = default). */
    public int getWindowY() { return windowY; }

    /** Returns true if launcher provided any configuration for this instance. */
    public boolean isLauncherManaged() { return !scriptName.isEmpty(); }

    private static int parseInt(String value, int fallback)
    {
        try   { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return fallback; }
    }

    /** Shows first 2 chars of {@code value} then "***"; fully masks values shorter than 4 chars. */
    private static String redact(String value)
    {
        if (value == null || value.length() < 4) return "***";
        return value.substring(0, 2) + "***";
    }
}
