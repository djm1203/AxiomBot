package com.axiom.launcher.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

/**
 * OS-independent utility that restricts a file or directory to the current user only.
 *
 * <ul>
 *   <li>POSIX (Linux / macOS): sets permissions to {@code rwx------} (700) for directories
 *       and {@code rw-------} (600) for files via {@link PosixFileAttributeView}.</li>
 *   <li>Windows: replaces the ACL with a single ALLOW entry for the current user via
 *       {@link AclFileAttributeView}. This removes inherited entries for SYSTEM /
 *       Administrators — intentional for credential and key files.</li>
 * </ul>
 *
 * All failures are logged as WARN and silently swallowed so the caller's main path
 * is never blocked by a permission-setting failure.
 */
public final class FilePermissions
{
    private static final Logger log = LoggerFactory.getLogger(FilePermissions.class);

    private FilePermissions() {}

    /**
     * Restricts {@code file} (or directory) so only the current OS user can access it.
     * Safe to call on a file that does not yet exist — the call is a no-op in that case.
     */
    public static void setOwnerOnly(File file)
    {
        if (file == null || !file.exists()) return;
        Path path = file.toPath();

        // ── POSIX (Linux / macOS) ──────────────────────────────────────────────
        try
        {
            PosixFileAttributeView posix =
                Files.getFileAttributeView(path, PosixFileAttributeView.class);
            if (posix != null)
            {
                String perm = file.isDirectory() ? "rwx------" : "rw-------";
                posix.setPermissions(PosixFilePermissions.fromString(perm));
                log.debug("FilePermissions: set {} on {}", perm, path);
                return;  // POSIX succeeded — no need to try ACL
            }
        }
        catch (Exception e)
        {
            log.debug("FilePermissions: POSIX view unavailable on {}: {}", path, e.getMessage());
        }

        // ── ACL (Windows) ─────────────────────────────────────────────────────
        try
        {
            AclFileAttributeView aclView =
                Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) return;

            UserPrincipal owner = path.getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));

            AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                    AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA,
                    AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.READ_NAMED_ATTRS,
                    AclEntryPermission.WRITE_NAMED_ATTRS,
                    AclEntryPermission.EXECUTE,
                    AclEntryPermission.DELETE_CHILD,
                    AclEntryPermission.READ_ATTRIBUTES,
                    AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.DELETE,
                    AclEntryPermission.READ_ACL,
                    AclEntryPermission.WRITE_ACL,
                    AclEntryPermission.WRITE_OWNER,
                    AclEntryPermission.SYNCHRONIZE)
                .build();

            aclView.setAcl(List.of(entry));
            log.debug("FilePermissions: ACL set to owner-only on {}", path);
        }
        catch (Exception e)
        {
            log.warn("FilePermissions: could not set ACL on {}: {}", path, e.getMessage());
        }
    }
}
