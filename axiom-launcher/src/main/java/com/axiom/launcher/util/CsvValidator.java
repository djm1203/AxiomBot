package com.axiom.launcher.util;

import com.axiom.launcher.cli.CsvUtil;

import java.util.Map;
import java.util.Set;

/**
 * Validates rows from a CSV import before they reach the database.
 *
 * <p>Rules for accounts:
 * <ul>
 *   <li>{@code display_name}      — required, max 64 chars</li>
 *   <li>{@code jagex_character_id} — optional, max 64 chars; warns if not {@code chr_} prefix</li>
 *   <li>{@code proxy_name}        — optional; if non-blank must match a known proxy name</li>
 *   <li>{@code preferred_world}   — optional; if present must be 0 or 301–550</li>
 *   <li>{@code bank_pin}          — optional; if present must be exactly 4 digits</li>
 * </ul>
 *
 * <p>Rules for proxies:
 * <ul>
 *   <li>{@code name}     — required, max 64 chars</li>
 *   <li>{@code host}     — required, max 256 chars</li>
 *   <li>{@code port}     — required, integer 1–65535</li>
 *   <li>{@code username} — optional, max 64 chars</li>
 *   <li>{@code password} — optional, max 256 chars</li>
 * </ul>
 */
public final class CsvValidator
{
    private CsvValidator() {}

    // ── Result ─────────────────────────────────────────────────────────────────

    public static final class ValidationResult
    {
        public final boolean valid;
        /** Null when valid; human-readable reason when not valid. */
        public final String  error;
        /** Non-null warning that does not prevent import (e.g. missing chr_ prefix). */
        public final String  warning;

        private ValidationResult(boolean valid, String error, String warning)
        {
            this.valid   = valid;
            this.error   = error;
            this.warning = warning;
        }

        public static ValidationResult ok()
        {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult warn(String warning)
        {
            return new ValidationResult(true, null, warning);
        }

        public static ValidationResult fail(String error)
        {
            return new ValidationResult(false, error, null);
        }
    }

    // ── Account validation ─────────────────────────────────────────────────────

    /**
     * Validates a single CSV row for the accounts import.
     *
     * @param row            column map from {@link CsvUtil#read}
     * @param knownProxies   set of proxy names already in the database (case-insensitive)
     * @return               a {@link ValidationResult}
     */
    public static ValidationResult validateAccount(Map<String, String> row,
                                                    Set<String> knownProxies)
    {
        // display_name — required
        String name = CsvUtil.get(row, "display_name").trim();
        if (name.isEmpty())
            return ValidationResult.fail("display_name is required");
        if (name.length() > 64)
            return ValidationResult.fail("display_name exceeds 64 characters");

        // jagex_character_id — optional, max 64
        String jagexId = CsvUtil.get(row, "jagex_character_id").trim();
        if (!jagexId.isEmpty())
        {
            if (jagexId.length() > 64)
                return ValidationResult.fail("jagex_character_id exceeds 64 characters");
            if (!jagexId.startsWith("chr_"))
                return ValidationResult.warn("jagex_character_id '" + jagexId + "' does not start with 'chr_'");
        }

        // proxy_name — optional; if given must exist in DB
        String proxyName = CsvUtil.get(row, "proxy_name").trim();
        if (!proxyName.isEmpty())
        {
            if (proxyName.length() > 64)
                return ValidationResult.fail("proxy_name exceeds 64 characters");
            if (!knownProxies.contains(proxyName.toLowerCase()))
                return ValidationResult.fail("proxy '" + proxyName + "' not found — import proxies first");
        }

        // preferred_world — optional; 0 or 301-550
        String worldStr = CsvUtil.get(row, "preferred_world").trim();
        if (!worldStr.isEmpty())
        {
            try
            {
                int world = Integer.parseInt(worldStr);
                if (world != 0 && (world < 301 || world > 550))
                    return ValidationResult.fail("preferred_world " + world + " is not 0 or in range 301–550");
            }
            catch (NumberFormatException e)
            {
                // Non-numeric — default to 0, don't fail
            }
        }

        // bank_pin — optional; exactly 4 digits if present
        String pin = CsvUtil.get(row, "bank_pin").trim();
        if (!pin.isEmpty())
        {
            if (!pin.matches("\\d{4}"))
                return ValidationResult.fail("bank_pin must be exactly 4 digits (got '" + pin + "')");
        }

        return ValidationResult.ok();
    }

    // ── Proxy validation ───────────────────────────────────────────────────────

    /**
     * Validates a single CSV row for the proxies import.
     *
     * @param row   column map from {@link CsvUtil#read}
     * @return      a {@link ValidationResult}
     */
    public static ValidationResult validateProxy(Map<String, String> row)
    {
        // name — required
        String name = CsvUtil.get(row, "name").trim();
        if (name.isEmpty())
            return ValidationResult.fail("name is required");
        if (name.length() > 64)
            return ValidationResult.fail("name exceeds 64 characters");

        // host — required
        String host = CsvUtil.get(row, "host").trim();
        if (host.isEmpty())
            return ValidationResult.fail("host is required");
        if (host.length() > 256)
            return ValidationResult.fail("host exceeds 256 characters");

        // port — required, 1-65535
        String portStr = CsvUtil.get(row, "port").trim();
        if (!portStr.isEmpty())
        {
            try
            {
                int port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535)
                    return ValidationResult.fail("port " + port + " is not in range 1–65535");
            }
            catch (NumberFormatException e)
            {
                return ValidationResult.fail("port '" + portStr + "' is not a valid integer");
            }
        }

        // username — optional, max 64
        String user = CsvUtil.get(row, "username").trim();
        if (user.length() > 64)
            return ValidationResult.fail("username exceeds 64 characters");

        // password — optional, max 256
        String password = CsvUtil.get(row, "password").trim();
        if (password.length() > 256)
            return ValidationResult.fail("password exceeds 256 characters");

        return ValidationResult.ok();
    }
}
