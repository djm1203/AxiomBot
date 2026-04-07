package com.botengine.osrs.util;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Properties;

/**
 * Sentry crash reporting integration for Axiom.
 *
 * Sentry captures unhandled exceptions and sends them to your Sentry dashboard
 * automatically. Free tier: 5,000 error events/month — plenty for early users.
 *
 * Setup:
 *   1. Create a free account at https://sentry.io
 *   2. Create a new project → Java
 *   3. Copy your DSN (looks like https://abc123@o123.ingest.sentry.io/456)
 *   4. For local dev: set env var AXIOM_SENTRY_DSN=<your-dsn>
 *   5. For CI release builds: add SENTRY_DSN as a GitHub Actions secret
 *
 * The DSN is injected at build time via the `release` Maven profile.
 * When DSN is empty (local dev builds), Sentry is silently disabled.
 *
 * Note: No personal data is ever sent — PII collection is explicitly disabled.
 */
@Slf4j
public final class AxiomSentry
{
    private static boolean initialized = false;

    private AxiomSentry() {}

    /**
     * Initializes Sentry from environment variable or build-time resource.
     * Safe to call multiple times — only initializes once.
     *
     * Call this early in BotEnginePlugin.startUp().
     */
    public static void init()
    {
        if (initialized) return;
        initialized = true;

        String dsn = resolveDsn();
        if (dsn.isEmpty())
        {
            log.debug("Sentry: DSN not configured — crash reporting disabled");
            return;
        }

        try
        {
            // Reflective init so the Sentry dep is optional at compile time.
            // If sentry.jar is not on the classpath, this silently does nothing.
            Class<?> sentryClass = Class.forName("io.sentry.Sentry");
            java.lang.reflect.Method initMethod = sentryClass.getMethod("init",
                java.util.function.Consumer.class);

            final String finalDsn = dsn;
            final String version  = AutoUpdater.currentVersion();

            initMethod.invoke(null, (java.util.function.Consumer<Object>) options ->
            {
                try
                {
                    options.getClass().getMethod("setDsn", String.class)
                        .invoke(options, finalDsn);
                    options.getClass().getMethod("setRelease", String.class)
                        .invoke(options, "axiom@" + version);
                    options.getClass().getMethod("setEnvironment", String.class)
                        .invoke(options, "production");
                    options.getClass().getMethod("setSendDefaultPii", boolean.class)
                        .invoke(options, false);  // never send PII
                }
                catch (Exception e)
                {
                    log.debug("Sentry options config failed: {}", e.getMessage());
                }
            });

            log.info("Sentry crash reporting enabled (release: axiom@{})", version);
        }
        catch (ClassNotFoundException e)
        {
            log.debug("Sentry: SDK not on classpath — crash reporting disabled");
        }
        catch (Exception e)
        {
            log.warn("Sentry init failed: {}", e.getMessage());
        }
    }

    /**
     * Manually captures an exception — use in catch blocks for non-fatal errors
     * you want to track without crashing.
     *
     * Example:
     *   try { ... }
     *   catch (Exception e) { AxiomSentry.capture(e); }
     */
    public static void capture(Throwable t)
    {
        if (!initialized) return;
        try
        {
            Class<?> sentryClass = Class.forName("io.sentry.Sentry");
            sentryClass.getMethod("captureException", Throwable.class).invoke(null, t);
        }
        catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolution order:
     *   1. Environment variable AXIOM_SENTRY_DSN (injected by CI or local .env)
     *   2. System property axiom.sentry.dsn (e.g. -Daxiom.sentry.dsn=... at launch)
     *   3. sentry.properties resource bundled in JAR (set at release build time)
     */
    private static String resolveDsn()
    {
        // 1. Environment variable (CI injects this)
        String env = System.getenv("AXIOM_SENTRY_DSN");
        if (env != null && !env.isBlank()) return env.trim();

        // 2. System property
        String prop = System.getProperty("axiom.sentry.dsn", "");
        if (!prop.isBlank()) return prop.trim();

        // 3. Bundled resource
        try (InputStream in = AxiomSentry.class.getResourceAsStream("/sentry.properties"))
        {
            if (in == null) return "";
            Properties p = new Properties();
            p.load(in);
            String dsn = p.getProperty("dsn", "");
            // Ignore unfilled placeholder
            return dsn.startsWith("YOUR_") || dsn.isBlank() ? "" : dsn;
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
