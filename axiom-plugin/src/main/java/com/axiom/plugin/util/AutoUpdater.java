package com.axiom.plugin.util;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

/**
 * Lightweight update checker for the Axiom plugin.
 *
 * On startup, AxiomPlugin calls {@link #checkAsync()} to kick off a background
 * check against the GitHub Releases API. The check runs on a daemon thread so
 * it never blocks plugin load.
 *
 * Results are emitted via SLF4J:
 *   INFO  — update available (prints current and latest version)
 *   DEBUG — already up to date
 *   WARN  — network error, malformed response, or missing configuration
 *
 * Version is read from {@code /version.properties} on the classpath, which is
 * populated at build time via Maven resource filtering:
 *   {@code version=${project.version}}
 *   {@code github.repo=owner/repo}
 *
 * No JSON library is required — the tag_name field is extracted with simple
 * string operations on the raw API response body.
 *
 * This class is a static utility and must not be instantiated.
 */
@Slf4j
public final class AutoUpdater
{
    private static final String VERSION_RESOURCE = "/version.properties";
    private static final String GITHUB_API_URL   =
        "https://api.github.com/repos/%s/releases/latest";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private AutoUpdater() { /* static utility */ }

    /**
     * Fires an asynchronous update check on a daemon thread.
     * Returns immediately; the check result is logged when complete.
     * Safe to call from the Swing EDT (startUp()).
     */
    public static void checkAsync()
    {
        Thread t = new Thread(AutoUpdater::runCheck, "axiom-update-check");
        t.setDaemon(true);
        t.start();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void runCheck()
    {
        try
        {
            Properties props = loadVersionProperties();
            if (props == null) return;

            String current = props.getProperty("version", "").trim();
            String repo    = props.getProperty("github.repo", "").trim();

            if (current.isEmpty() || current.startsWith("$"))
            {
                // Maven filtering was not applied (e.g., running from IDE without a build)
                log.debug("AutoUpdater: version.properties not filtered — skipping check");
                return;
            }

            if (repo.isEmpty() || repo.equals("OWNER/REPO"))
            {
                log.warn("AutoUpdater: github.repo not configured in version.properties");
                return;
            }

            String latest = fetchLatestTag(repo);
            if (latest == null) return; // error already logged

            String normCurrent = normalizeTag(current);
            String normLatest  = normalizeTag(latest);

            if (!normCurrent.equals(normLatest))
            {
                log.info("Axiom update available: {} → {} — https://github.com/{}/releases/latest",
                    current, latest, repo);
            }
            else
            {
                log.debug("Axiom is up to date ({})", current);
            }
        }
        catch (Exception e)
        {
            log.warn("AutoUpdater: unexpected error during update check: {}", e.getMessage());
        }
    }

    private static Properties loadVersionProperties()
    {
        try (InputStream in = AutoUpdater.class.getResourceAsStream(VERSION_RESOURCE))
        {
            if (in == null)
            {
                log.warn("AutoUpdater: {} not found on classpath", VERSION_RESOURCE);
                return null;
            }
            Properties props = new Properties();
            props.load(in);
            return props;
        }
        catch (Exception e)
        {
            log.warn("AutoUpdater: could not load {}: {}", VERSION_RESOURCE, e.getMessage());
            return null;
        }
    }

    /**
     * Calls the GitHub Releases API and extracts the tag_name field from the
     * JSON response without a JSON library dependency.
     *
     * @return tag string (e.g. "v1.2.0") or null on failure
     */
    private static String fetchLatestTag(String repo)
    {
        String url = String.format(GITHUB_API_URL, repo);
        try
        {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "axiom-plugin-updater")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                log.warn("AutoUpdater: GitHub API returned HTTP {} for {}",
                    response.statusCode(), url);
                return null;
            }

            return extractTagName(response.body());
        }
        catch (Exception e)
        {
            log.warn("AutoUpdater: network error checking {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the value of the {@code "tag_name"} field from a GitHub API
     * JSON response using substring operations, avoiding a JSON parser dependency.
     *
     * Handles the format: {@code "tag_name": "v1.2.3"}
     *
     * @return the extracted tag value, or null if not found
     */
    static String extractTagName(String body)
    {
        if (body == null || body.isEmpty()) return null;

        final String key = "\"tag_name\"";
        int keyIdx = body.indexOf(key);
        if (keyIdx < 0) return null;

        int colonIdx = body.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;

        int openQuote = body.indexOf('"', colonIdx + 1);
        if (openQuote < 0) return null;

        int closeQuote = body.indexOf('"', openQuote + 1);
        if (closeQuote < 0) return null;

        String tag = body.substring(openQuote + 1, closeQuote).trim();
        return tag.isEmpty() ? null : tag;
    }

    /**
     * Strips a leading 'v' from a version tag for comparison, so
     * "v1.0-SNAPSHOT" and "1.0-SNAPSHOT" are treated as equal.
     */
    private static String normalizeTag(String tag)
    {
        return tag.startsWith("v") || tag.startsWith("V")
            ? tag.substring(1)
            : tag;
    }
}
