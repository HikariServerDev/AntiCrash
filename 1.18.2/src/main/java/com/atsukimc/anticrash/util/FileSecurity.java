package com.atsukimc.anticrash.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** Cross-platform best-effort protection for AntiCrash-owned files. */
public final class FileSecurity {
    private static final Set<PosixFilePermission> OWNER_FILE =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");

    private FileSecurity() {
    }

    public static void hardenCreatedFile(Path file) throws IOException {
        setOwnerOnlyIfSupported(file, OWNER_FILE);
    }

    public static void hardenCreatedDirectory(Path directory) throws IOException {
        setOwnerOnlyIfSupported(directory, OWNER_DIRECTORY);
    }

    /** Returns false on filesystems that do not expose POSIX permissions. */
    public static boolean isWritableByGroupOrOthers(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(
                    path, LinkOption.NOFOLLOW_LINKS);
            return permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE);
        } catch (UnsupportedOperationException exception) {
            return false;
        }
    }

    /** Detects possible disclosure as well as modification of private internal data. */
    public static boolean hasGroupOrOtherAccess(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(
                    path, LinkOption.NOFOLLOW_LINKS);
            return permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                    || permission.name().startsWith("OTHERS_"));
        } catch (UnsupportedOperationException exception) {
            return false;
        }
    }

    private static void setOwnerOnlyIfSupported(Path path,
                                                 Set<PosixFilePermission> permissions)
            throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some custom filesystems do not expose POSIX mode bits.
        }
    }
}
