package com.axiom.launcher.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM credential encryption for at-rest secrets (bank PINs, proxy passwords).
 *
 * <h3>Key storage</h3>
 * A 256-bit master key is generated on first launch and stored in a file:
 * <ul>
 *   <li>Windows: {@code %APPDATA%\axiom\master.key}</li>
 *   <li>Fallback: {@code ~/.axiom/master.key}</li>
 * </ul>
 * The file contains the raw 32-byte key. On POSIX systems owner-only permissions
 * (600) are set. On Windows the file sits in a user-private AppData folder.
 *
 * <p>TODO Phase 5: migrate to Windows DPAPI / macOS Keychain for hardware-backed
 * key protection.
 *
 * <h3>Ciphertext format</h3>
 * {@code ENC:<base64(12-byte-IV || AES-GCM-ciphertext)>}
 * A fresh random 96-bit IV is generated per {@link #encrypt} call.
 * The GCM auth tag is 128 bits (Java default).
 */
public final class CryptoManager
{
    private static final Logger log = LoggerFactory.getLogger(CryptoManager.class);

    private static final String ENC_PREFIX    = "ENC:";
    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final int    IV_BYTES       = 12;   // 96-bit IV
    private static final int    TAG_BITS       = 128;  // 128-bit auth tag
    private static final int    KEY_BITS       = 256;

    private static volatile SecretKey masterKey;

    private CryptoManager() {}

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Loads (or generates) the master key from disk.
     * Must be called once before any {@link #encrypt}/{@link #decrypt} calls.
     *
     * @throws Exception if the key file cannot be read or written
     */
    public static synchronized void init() throws Exception
    {
        if (masterKey != null) return;  // already initialised
        masterKey = loadOrGenerateKey();
        log.info("CryptoManager: master key loaded from {}", keyFile().getAbsolutePath());
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Encrypts {@code plaintext} and returns {@code "ENC:<base64>"}.
     * Returns null/empty unchanged; idempotent on already-encrypted values.
     *
     * @throws IllegalStateException if {@link #init()} has not been called
     */
    public static String encrypt(String plaintext)
    {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        if (isEncrypted(plaintext)) return plaintext;   // already encrypted
        ensureInitialised();

        try
        {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine: IV || ciphertext+tag
            byte[] combined = new byte[IV_BYTES + cipherBytes.length];
            System.arraycopy(iv,         0, combined, 0,        IV_BYTES);
            System.arraycopy(cipherBytes, 0, combined, IV_BYTES, cipherBytes.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt}.
     * Returns the original value unchanged if it is null, empty, or not prefixed
     * with {@code "ENC:"} (supports legacy plaintext rows during migration).
     *
     * @throws IllegalStateException if {@link #init()} has not been called
     */
    public static String decrypt(String ciphertext)
    {
        if (ciphertext == null || ciphertext.isEmpty()) return ciphertext;
        if (!isEncrypted(ciphertext)) return ciphertext;  // legacy plaintext — pass through
        ensureInitialised();

        try
        {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(ENC_PREFIX.length()));
            byte[] iv       = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] cipherBytes = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Decryption failed — key mismatch or corrupted data", e);
        }
    }

    /** Returns true if {@code value} was produced by {@link #encrypt} (starts with "ENC:"). */
    public static boolean isEncrypted(String value)
    {
        return value != null && value.startsWith(ENC_PREFIX);
    }

    // ── Key management ─────────────────────────────────────────────────────────

    private static SecretKey loadOrGenerateKey() throws Exception
    {
        File keyFile = keyFile();

        if (keyFile.isFile())
        {
            byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
            if (keyBytes.length != KEY_BITS / 8)
            {
                throw new IllegalStateException(
                    "master.key has unexpected length " + keyBytes.length +
                    "; expected " + (KEY_BITS / 8) + " bytes. Delete it to regenerate.");
            }
            log.debug("CryptoManager: loaded existing master key ({} bytes)", keyBytes.length);
            return new SecretKeySpec(keyBytes, "AES");
        }

        // First launch — generate a new key
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(KEY_BITS, new SecureRandom());
        SecretKey key = kg.generateKey();

        keyFile.getParentFile().mkdirs();
        Files.write(keyFile.toPath(), key.getEncoded(),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        // Restrict permissions on POSIX file systems (Linux / macOS)
        setPosixOwnerOnly(keyFile);

        log.info("CryptoManager: generated new master key at {}", keyFile.getAbsolutePath());
        return key;
    }

    private static File keyFile()
    {
        // Prefer %APPDATA%\axiom\master.key on Windows for better isolation
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.trim().isEmpty())
        {
            return new File(appdata + "/axiom/master.key");
        }
        // Fallback: ~/.axiom/master.key (Linux, macOS, or missing APPDATA)
        return new File(System.getProperty("user.home") + "/.axiom/master.key");
    }

    private static void setPosixOwnerOnly(File file)
    {
        try
        {
            java.nio.file.attribute.PosixFileAttributeView view =
                Files.getFileAttributeView(file.toPath(),
                    java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null)
            {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                    new java.util.HashSet<>();
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                view.setPermissions(perms);
            }
        }
        catch (Exception ignored)
        {
            // Windows: POSIX permissions not supported; AppData is user-private by default.
        }
    }

    private static void ensureInitialised()
    {
        if (masterKey == null)
            throw new IllegalStateException(
                "CryptoManager.init() has not been called. " +
                "Call it from AxiomLauncher.start() before using the repositories.");
    }
}
