package com.atsukimc.anticrash.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Everything the incremental snapshot needs about one file, read with a single stat.
 *
 * <p>Each of {@code Files.isDirectory}, {@code isSymbolicLink}, {@code isRegularFile}, {@code
 * readAttributes} and every {@code getAttribute("unix:...")} performs its own stat, so the per-file
 * probe set cost six of them before a byte was copied. A world of ~10,000 files spent most of the
 * frozen snapshot window in that overhead even when almost nothing had changed. The {@code unix}
 * view returns all of it at once.
 */
public record FileFacts(
        boolean regularFile,
        boolean symbolicLink,
        boolean directory,
        long size,
        long modifiedTime,
        long changeTime,
        long linkCount) {
    private static final String UNIX_ATTRIBUTES =
            "unix:size,lastModifiedTime,ctime,nlink,isRegularFile,isSymbolicLink,isDirectory";

    /** Set once per JVM when the filesystem has no unix view; ctime/nlink are then unavailable. */
    private static volatile boolean unixViewUnavailable;

    /** Reads one file, or returns null when it no longer exists. */
    public static FileFacts readOrNull(Path path) throws IOException {
        try {
            return read(path);
        } catch (NoSuchFileException absent) {
            return null;
        }
    }

    public static FileFacts read(Path path) throws IOException {
        if (!unixViewUnavailable) {
            try {
                Map<String, Object> attributes =
                        Files.readAttributes(path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                return new FileFacts(
                        Boolean.TRUE.equals(attributes.get("isRegularFile")),
                        Boolean.TRUE.equals(attributes.get("isSymbolicLink")),
                        Boolean.TRUE.equals(attributes.get("isDirectory")),
                        attributes.get("size") instanceof Number size ? size.longValue() : 0L,
                        attributes.get("lastModifiedTime") instanceof FileTime time
                                ? time.toMillis()
                                : 0L,
                        attributes.get("ctime") instanceof FileTime time
                                ? time.to(TimeUnit.NANOSECONDS)
                                : 0L,
                        attributes.get("nlink") instanceof Number links ? links.longValue() : 0L);
            } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
                unixViewUnavailable = true;
            }
        }
        // Windows and any filesystem without the unix view. A zero ctime already means "do not
        // trust metadata alone" everywhere this record is used, so the conservative paths apply.
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileFacts(
                attributes.isRegularFile(),
                attributes.isSymbolicLink(),
                attributes.isDirectory(),
                attributes.size(),
                attributes.lastModifiedTime().toMillis(),
                0L,
                0L);
    }

    /** True when the file is the same revision as {@code other}, by size, mtime and ctime. */
    public boolean sameRevision(FileFacts other) {
        return other != null
                && size == other.size
                && modifiedTime == other.modifiedTime
                && changeTime == other.changeTime;
    }
}
