package com.axiom.plugin;

import lombok.extern.slf4j.Slf4j;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Reads launch configuration passed from the Axiom Launcher via system properties
 * and environment variables.
 *
 * System properties set by the launcher:
 *   -Daxiom.script="Axiom Woodcutting"   → script to auto-start
 *   -Daxiom.world=302                    → world to hop to on start
 *   -Daxiom.account.id=<characterId>     → Jagex character ID for this instance
 *   -Daxiom.window.x=0                   → window X position
 *   -Daxiom.window.y=0                   → window Y position
 *   -Dhttps.proxyHost / -Dhttps.proxyPort → proxy routing (non-sensitive)
 *
 * Environment variables set by the launcher (kept out of process command line):
 *   AXIOM_PROXY_USER     → proxy authentication username
 *   AXIOM_PROXY_PASSWORD → proxy authentication password
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

        installProxyAuthenticator();
    }

    /**
     * Registers a java.net.Authenticator for proxy authentication if the launcher
     * passed credentials via AXIOM_PROXY_USER / AXIOM_PROXY_PASSWORD env vars.
     * Environment variables are not visible in the Windows Task Manager command-line
     * column, unlike -D system properties.
     */
    private static void installProxyAuthenticator()
    {
        String user = System.getenv("AXIOM_PROXY_USER");
        if (user == null || user.isEmpty()) return;

        String rawPass = System.getenv("AXIOM_PROXY_PASSWORD");
        char[] pass    = rawPass != null ? rawPass.toCharArray() : new char[0];

        Authenticator.setDefault(new Authenticator()
        {
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                if (getRequestorType() == RequestorType.PROXY)
                    return new PasswordAuthentication(user, pass);
                return null;
            }
        });

        log.info("LauncherBridge: proxy authenticator registered (user='{}')", redact(user));
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
