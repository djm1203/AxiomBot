package com.botengine.osrs.util;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

/**
 * Checks GitHub Releases for a newer version of Axiom on startup.
 *
 * Uses Java 11 HttpClient — no extra dependencies.
 * Runs on a background thread so it never blocks the RuneLite startup.
 *
 * Configuration:
 *   Set {@code github.repo=OWNER/REPO} in {@code version.properties} to point
 *   at the GitHub repository that hosts releases.
 *
 * Usage in BotEnginePlugin.startUp():
 *   AutoUpdater.checkAsync(newVersion ->
 *       log.info("New Axiom version available: " + newVersion));
 */
@Slf4j
public final class AutoUpdater
{
    private static final String VERSION_RESOURCE = "/version.properties";
    private static final String GITHUB_API       = "https://api.github.com/repos/%s/releases/latest";
    private static final Duration TIMEOUT        = Duration.ofSeconds(5);

    private AutoUpdater() {}

    /**
     * Reads the current version bundled in the JAR.
     * Returns "unknown" if the resource is missing (e.g. running from IDE without filtering).
     */
    public static String currentVersion()
    {
        try (InputStream in = AutoUpdater.class.getResourceAsStream(VERSION_RESOURCE))
        {
            if (in == null) return "unknown";
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "unknown");
        }
        catch (Exception e)
        {
            return "unknown";
        }
    }

    /**
     * Fetches the latest GitHub release tag for the configured repo.
     * Returns empty if the check fails or the network is unavailable.
     */
    public static Optional<String> latestRelease()
    {
        String repo = githubRepo();
        if (repo.equals("OWNER/REPO"))
        {
            log.debug("AutoUpdater: github.repo not configured — skipping update check");
            return Optional.empty();
        }

        try
        {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(GITHUB_API, repo)))
                .header("Accept", "application/vnd.github+json")
                .timeout(TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) return Optional.empty();

            // Simple extraction — avoids a JSON dep.
            // GitHub response contains: "tag_name":"v1.2.3"
            String body = response.body();
            int idx = body.indexOf("\"tag_name\"");
            if (idx == -1) return Optional.empty();

            int start = body.indexOf('"', idx + 10) + 1;
            int end   = body.indexOf('"', start);
            if (start <= 0 || end <= start) return Optional.empty();

            return Optional.of(body.substring(start, end));
        }
        catch (Exception e)
        {
            log.debug("AutoUpdater: update check failed — {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Runs the update check on a daemon thread.
     * Calls {@code onUpdateAvailable} on the Swing EDT if a newer version is found.
     *
     * @param onUpdateAvailable called with the new version string when an update exists
     */
    public static void checkAsync(java.util.function.Consumer<String> onUpdateAvailable)
    {
        Thread t = new Thread(() ->
        {
            String current = currentVersion();
            if ("unknown".equals(current)) return;

            latestRelease().ifPresent(latest ->
            {
                if (!latest.equalsIgnoreCase("v" + current) && !latest.equals(current))
                {
                    log.info("AutoUpdater: new version available — {} (current: {})", latest, current);
                    javax.swing.SwingUtilities.invokeLater(() -> onUpdateAvailable.accept(latest));
                }
            });
        }, "axiom-update-check");
        t.setDaemon(true);
        t.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String githubRepo()
    {
        try (InputStream in = AutoUpdater.class.getResourceAsStream(VERSION_RESOURCE))
        {
            if (in == null) return "OWNER/REPO";
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("github.repo", "OWNER/REPO");
        }
        catch (Exception e)
        {
            return "OWNER/REPO";
        }
    }
}
