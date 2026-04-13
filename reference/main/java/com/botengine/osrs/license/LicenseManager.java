package com.botengine.osrs.license;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages Axiom license key validation and tier access.
 *
 * <h3>Security model</h3>
 * Keys are validated offline using HMAC-SHA256 against {@code HMAC_SECRET}.
 * The creator bypass uses a separate hardcoded constant.  Both constants are
 * obfuscated by ProGuard in the distributed JAR, making them non-trivially
 * extractable without specialised deobfuscation tooling.
 *
 * <h3>Before first distribution — change these two values</h3>
 * <ul>
 *   <li>{@code CREATOR_KEY}  — your personal master key (any string you choose)</li>
 *   <li>{@code HMAC_SECRET}  — the signing secret used by your key-generation tool</li>
 * </ul>
 *
 * <h3>Key format</h3>
 * {@code userId:PREMIUM:expiryEpochDays:hmacSig16}
 * <ul>
 *   <li>{@code userId}          — any identifier for the customer (e.g. "user123")</li>
 *   <li>tier literal            — always "PREMIUM" for paid keys</li>
 *   <li>{@code expiryEpochDays} — days since Unix epoch; use {@code -1} for no expiry</li>
 *   <li>{@code hmacSig16}       — first 16 hex chars of HMAC-SHA256(payload, HMAC_SECRET)</li>
 * </ul>
 * Example: {@code user123:PREMIUM:20000:AB12CD34EF567890}
 *
 * <h3>Generating a customer key (run locally, never ship the generator)</h3>
 * <pre>{@code
 *   String key = LicenseManager.generateKey("user123", true, 365);
 *   System.out.println(key);
 * }</pre>
 * Pass {@code expiryDays = -1} for a never-expiring key.
 */
@Singleton
public class LicenseManager
{
    // ── Change before distributing ────────────────────────────────────────────

    /**
     * Your personal creator key.  Entering this grants CREATOR tier
     * (all access, never expires).  ProGuard obfuscates this in the JAR.
     */
    static final String CREATOR_KEY = "AXM-CR34TR-DJM-FULL-ACCESS-V1";

    /**
     * HMAC signing secret shared between LicenseManager and your key generator.
     * ProGuard obfuscates this in the JAR.
     */
    static final String HMAC_SECRET = "ax0m_s1gn1ng_s3cr3t_ch4ng3_b3f0r3_r3l3453";

    // ── Internals ─────────────────────────────────────────────────────────────

    private static final Path KEY_FILE = Paths.get(
        System.getProperty("user.home"), ".runelite", "axiom-license.key");

    private LicenseTier currentTier   = LicenseTier.FREE;
    private String      currentUserId = null;
    private long        expiryDays    = 0L;  // epoch days; Long.MAX_VALUE = never

    @Inject
    public LicenseManager()
    {
        loadFromDisk();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the currently active tier (FREE if no valid key has been entered). */
    public LicenseTier getTier() { return currentTier; }

    /** True if PREMIUM or CREATOR — allows P2P scripts. */
    public boolean isP2PUnlocked()
    {
        return currentTier == LicenseTier.PREMIUM || currentTier == LicenseTier.CREATOR;
    }

    /** The user identifier embedded in the current key (null for FREE). */
    public String getUserId() { return currentUserId; }

    /**
     * Attempts to activate a raw license key string.
     *
     * @param rawKey the key as entered by the user
     * @return {@code true} if the key is valid and was activated
     */
    public boolean activate(String rawKey)
    {
        if (rawKey == null || rawKey.isBlank()) return false;
        String key = rawKey.trim();

        // ── Creator bypass ────────────────────────────────────────────────────
        if (key.equals(CREATOR_KEY))
        {
            currentTier   = LicenseTier.CREATOR;
            currentUserId = "CREATOR";
            expiryDays    = Long.MAX_VALUE;
            saveToDisk(rawKey);
            return true;
        }

        // ── HMAC-validated customer key ───────────────────────────────────────
        // Format: userId:PREMIUM:expiryEpochDays:hmacSig16
        String[] parts = key.split(":");
        if (parts.length != 4) return false;

        try
        {
            String userId    = parts[0];
            String tierStr   = parts[1];
            long   expiry    = Long.parseLong(parts[2]);
            String givenSig  = parts[3].toUpperCase();

            if (!"PREMIUM".equals(tierStr)) return false;

            // Verify signature
            String payload  = userId + ":" + tierStr + ":" + expiry;
            String expected = computeHmac16(payload);
            if (!expected.equalsIgnoreCase(givenSig)) return false;

            // Check expiry (-1 = never)
            if (expiry != -1L)
            {
                long todayDays = System.currentTimeMillis() / 86_400_000L;
                if (todayDays > expiry) return false;
            }

            currentTier   = LicenseTier.PREMIUM;
            currentUserId = userId;
            expiryDays    = expiry;
            saveToDisk(rawKey);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** Human-readable tier label for display. */
    public String getStatusText()
    {
        switch (currentTier)
        {
            case CREATOR: return "CREATOR";
            case PREMIUM: return "PREMIUM";
            default:      return "FREE";
        }
    }

    /** Colour for the tier badge in the UI. */
    public Color getStatusColor()
    {
        switch (currentTier)
        {
            case CREATOR: return new Color(255, 200, 50);   // gold
            case PREMIUM: return new Color(80,  180, 255);  // sky blue
            default:      return new Color(120, 120, 120);  // grey
        }
    }

    // ── Static key generator (run locally to produce customer keys) ────────────

    /**
     * Generates a license key you can send to a customer.
     *
     * <pre>{@code
     *   // 30-day key for "user42"
     *   System.out.println(LicenseManager.generateKey("user42", true, 30));
     *
     *   // Never-expiring key
     *   System.out.println(LicenseManager.generateKey("user42", true, -1));
     * }</pre>
     *
     * @param userId     customer identifier embedded in the key
     * @param premium    must be true — only PREMIUM keys are generated here
     * @param daysFromNow key lifetime in days; pass {@code -1} for no expiry
     * @return the license key string
     */
    public static String generateKey(String userId, boolean premium, long daysFromNow)
    {
        if (!premium) throw new IllegalArgumentException("Only PREMIUM keys can be generated");
        String tier   = "PREMIUM";
        long   expiry = (daysFromNow == -1)
                        ? -1L
                        : (System.currentTimeMillis() / 86_400_000L) + daysFromNow;
        String payload = userId + ":" + tier + ":" + expiry;
        String sig     = computeHmac16Static(payload, HMAC_SECRET);
        return payload + ":" + sig;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String computeHmac16(String payload)
    {
        return computeHmac16Static(payload, HMAC_SECRET);
    }

    private static String computeHmac16Static(String payload, String secret)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            // First 8 bytes → 16 hex chars
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++)
            {
                sb.append(String.format("%02X", result[i]));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            return "0000000000000000";
        }
    }

    private void saveToDisk(String rawKey)
    {
        try
        {
            Files.createDirectories(KEY_FILE.getParent());
            Files.writeString(KEY_FILE, rawKey, StandardCharsets.UTF_8);
        }
        catch (IOException ignored) {}
    }

    private void loadFromDisk()
    {
        try
        {
            if (Files.exists(KEY_FILE))
            {
                activate(Files.readString(KEY_FILE, StandardCharsets.UTF_8).trim());
            }
        }
        catch (IOException ignored) {}
    }
}
