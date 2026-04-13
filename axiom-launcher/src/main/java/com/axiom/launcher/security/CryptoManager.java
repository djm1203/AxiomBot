package com.axiom.launcher.security;

import com.sun.jna.platform.win32.Crypt32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinCrypt;
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
 *   <li>Windows ({@code %APPDATA%} set): {@code %APPDATA%\axiom\master.key}
 *       — raw key bytes are <b>encrypted with Windows DPAPI</b> before writing.
 *       The file will be ~200 bytes (not 32). Only the same Windows user account
 *       can decrypt the file; a copied file from another account will fail.</li>
 *   <li>Fallback (Linux / macOS): {@code ~/.axiom/master.key}
 *       — raw 32-byte key with POSIX 600 permissions.
 *       A one-time WARN is logged: "DPAPI unavailable — using file-based key storage".</li>
 * </ul>
 *
 * <h3>Ciphertext format</h3>
 * {@code ENC:<base64(12-byte-IV || AES-GCM-ciphertext)>}
 * A fresh random 96-bit IV is generated per {@link #encrypt} call.
 * The GCM auth tag is 128 bits (Java default).
 *
 * <h3>Legacy migration</h3>
 * If a plain 32-byte key file is found on Windows (written by a prior version),
 * it is automatically re-encrypted with DPAPI and the file is overwritten.
 */
public final class CryptoManager
{
    private static final Logger log = LoggerFactory.getLogger(CryptoManager.class);

    private static final String ENC_PREFIX = "ENC:";
    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_BYTES   = 12;   // 96-bit IV
    private static final int    TAG_BITS   = 128;  // 128-bit auth tag
    private static final int    KEY_BITS   = 256;

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
        if (isEncrypted(plaintext)) return plaintext;
        ensureInitialised();

        try
        {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

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
        if (!isEncrypted(ciphertext)) return ciphertext;
        ensureInitialised();

        try
        {
            byte[] combined    = Base64.getDecoder().decode(ciphertext.substring(ENC_PREFIX.length()));
            byte[] iv          = Arrays.copyOfRange(combined, 0, IV_BYTES);
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

    /** Returns true if {@code value} was produced by {@link #encrypt}. */
    public static boolean isEncrypted(String value)
    {
        return value != null && value.startsWith(ENC_PREFIX);
    }

    /**
     * Returns a redacted form of a value safe for log output.
     * Shows the first 2 characters followed by {@code "***"}.
     * Values shorter than 4 characters are fully masked.
     *
     * <p>Use this anywhere an ID or name is needed in a log line without
     * exposing the full value.
     */
    public static String redact(String value)
    {
        if (value == null || value.length() < 4) return "***";
        return value.substring(0, 2) + "***";
    }

    // ── Key management ─────────────────────────────────────────────────────────

    private static SecretKey loadOrGenerateKey() throws Exception
    {
        File    keyFile  = keyFile();
        boolean usesDpapi = isWindows();

        if (!usesDpapi)
        {
            log.warn("CryptoManager: DPAPI unavailable — using file-based key storage (non-Windows)");
        }

        if (keyFile.isFile())
        {
            byte[] fileBytes = Files.readAllBytes(keyFile.toPath());

            if (usesDpapi)
            {
                // Legacy migration: a plain 32-byte key was written by a previous version.
                // Re-encrypt with DPAPI so future reads are hardware-bound.
                if (fileBytes.length == KEY_BITS / 8)
                {
                    log.info("CryptoManager: migrating plain key to DPAPI protection");
                    byte[] encrypted = dpapiProtect(fileBytes);
                    Files.write(keyFile.toPath(), encrypted,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    FilePermissions.setOwnerOnly(keyFile);
                    return new SecretKeySpec(fileBytes, "AES");
                }

                // Normal path: DPAPI-encrypted blob
                byte[] rawKey = dpapiUnprotect(fileBytes);
                if (rawKey.length != KEY_BITS / 8)
                    throw new IllegalStateException(
                        "DPAPI-decrypted key has unexpected length " + rawKey.length +
                        "; delete " + keyFile.getAbsolutePath() + " to regenerate.");
                log.debug("CryptoManager: loaded DPAPI-protected key ({} bytes in file)", fileBytes.length);
                return new SecretKeySpec(rawKey, "AES");
            }
            else
            {
                // Non-Windows: plain file
                if (fileBytes.length != KEY_BITS / 8)
                    throw new IllegalStateException(
                        "master.key has unexpected length " + fileBytes.length +
                        "; expected " + (KEY_BITS / 8) + " bytes. Delete it to regenerate.");
                log.debug("CryptoManager: loaded plain key ({} bytes)", fileBytes.length);
                return new SecretKeySpec(fileBytes, "AES");
            }
        }

        // ── First launch: generate a new key ───────────────────────────────────
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(KEY_BITS, new SecureRandom());
        SecretKey key    = kg.generateKey();
        byte[]    rawKey = key.getEncoded();

        File parent = keyFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        FilePermissions.setOwnerOnly(parent);

        if (usesDpapi)
        {
            byte[] encrypted = dpapiProtect(rawKey);
            Files.write(keyFile.toPath(), encrypted,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            FilePermissions.setOwnerOnly(keyFile);
            log.info("CryptoManager: generated DPAPI-protected key at {}", keyFile.getAbsolutePath());
        }
        else
        {
            Files.write(keyFile.toPath(), rawKey,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            FilePermissions.setOwnerOnly(keyFile);
            log.info("CryptoManager: generated plain key at {}", keyFile.getAbsolutePath());
        }

        return key;
    }

    private static File keyFile()
    {
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.trim().isEmpty())
        {
            return new File(appdata + "/axiom/master.key");
        }
        return new File(System.getProperty("user.home") + "/.axiom/master.key");
    }

    private static boolean isWindows()
    {
        String appdata = System.getenv("APPDATA");
        return appdata != null && !appdata.trim().isEmpty();
    }

    // ── Windows DPAPI ─────────────────────────────────────────────────────────

    /**
     * Encrypts {@code raw} with Windows DPAPI (CryptProtectData).
     * The returned bytes are bound to the current Windows user account.
     */
    /**
     * Encrypts {@code raw} with Windows DPAPI (CryptProtectData).
     * Uses {@code DATA_BLOB(byte[])} constructor so cbData (DWORD) is set correctly.
     * The returned bytes are bound to the current Windows user account.
     */
    private static byte[] dpapiProtect(byte[] raw) throws Exception
    {
        // DATA_BLOB(byte[]) constructor allocates JNA Memory and sets cbData as DWORD
        WinCrypt.DATA_BLOB input  = new WinCrypt.DATA_BLOB(raw);
        WinCrypt.DATA_BLOB output = new WinCrypt.DATA_BLOB();

        boolean ok = Crypt32.INSTANCE.CryptProtectData(
            input, null, null, null, null, 0, output);

        if (!ok)
        {
            int err = Kernel32.INSTANCE.GetLastError();
            throw new RuntimeException("CryptProtectData failed (Win32 error: " + err + ")");
        }

        output.read();
        byte[] result = output.getData();   // getData() uses cbData.intValue() correctly
        Kernel32.INSTANCE.LocalFree(output.pbData);
        return result;
    }

    /**
     * Decrypts {@code encrypted} with Windows DPAPI (CryptUnprotectData).
     *
     * @throws RuntimeException if decryption fails — most likely because the key
     *                          was created under a different Windows user account.
     */
    private static byte[] dpapiUnprotect(byte[] encrypted) throws Exception
    {
        WinCrypt.DATA_BLOB input  = new WinCrypt.DATA_BLOB(encrypted);
        WinCrypt.DATA_BLOB output = new WinCrypt.DATA_BLOB();

        boolean ok = Crypt32.INSTANCE.CryptUnprotectData(
            input, null, null, null, null, 0, output);

        if (!ok)
        {
            throw new RuntimeException(
                "CryptUnprotectData failed — key is tied to a different Windows user account. " +
                "Delete " + keyFile().getAbsolutePath() + " to regenerate.");
        }

        output.read();
        byte[] result = output.getData();
        Kernel32.INSTANCE.LocalFree(output.pbData);
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void ensureInitialised()
    {
        if (masterKey == null)
            throw new IllegalStateException(
                "CryptoManager.init() has not been called. " +
                "Call it from AxiomLauncher.start() before using the repositories.");
    }
}
