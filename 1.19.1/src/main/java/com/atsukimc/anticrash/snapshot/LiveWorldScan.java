package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Pre-hashes live world files while the server is still running.
 *
 * <p>The incremental backend reuses a parent checkpoint file only after proving that the live file
 * still has the same content. It can do that from metadata alone (size/mtime/ctime), but the very
 * first capture of a session usually cannot: the previous checkpoint was written by another run, so
 * its recorded metadata no longer matches and every unchanged file has to be read and hashed. That
 * read happened inside the checkpoint freeze and stopped a live server for 26 seconds on a ~10 GB
 * world.</p>
 *
 * <p>This scan performs exactly that work ahead of time, off the frozen window. A file is cached
 * only when its metadata is unchanged across its own hash, so a file written during the scan is
 * simply left out and hashed later by the barrier-protected backend. The cache is therefore an
 * optimisation and never a source of truth on its own: {@link JavaIncrementalBackend} still
 * requires the live metadata to match the cached entry exactly before trusting it.</p>
 */
public final class LiveWorldScan {
    private LiveWorldScan() {
    }

    public static Map<String, SnapshotFile> hashUnmatchedFiles(Path liveWorld,
                                                               Map<String, SnapshotFile> previousFiles)
            throws IOException {
        if (liveWorld == null || !Files.isDirectory(liveWorld, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(liveWorld)) {
            return Map.of();
        }
        Map<String, SnapshotFile> hashed = new HashMap<>();
        try (Stream<Path> stream = Files.walk(liveWorld)) {
            for (Path source : stream.toList()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Live world scan was interrupted");
                }
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) continue;
                if (PathSafety.isSymlinkOrIrregular(source)) continue;
                String relative = liveWorld.relativize(source).toString().replace('\\', '/');
                if (SnapshotFilters.excluded(relative)) continue;

                final BasicFileAttributes before;
                try {
                    before = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (IOException ignored) {
                    continue; // A file that disappeared mid-scan is simply not cached.
                }
                long changeTime = changeTime(source);
                // Without ctime the backend never trusts metadata, so caching a hash is pointless.
                if (changeTime == 0L) continue;
                // The backend's existing fast path already covers this file for free.
                if (matchesPrevious(previousFiles.get(relative), before, changeTime)) continue;

                final String digest;
                try {
                    digest = Hashing.sha256(source);
                } catch (InterruptedIOException interrupted) {
                    throw interrupted;
                } catch (IOException ignored) {
                    continue;
                }
                if (!stillUnchanged(source, before, changeTime)) continue;
                hashed.put(relative, new SnapshotFile(relative, before.size(),
                        before.lastModifiedTime().toMillis(), changeTime, digest));
            }
        }
        return Map.copyOf(hashed);
    }

    private static boolean matchesPrevious(SnapshotFile previous, BasicFileAttributes attributes,
                                           long changeTime) {
        return previous != null && previous.sha256 != null && previous.changeTime != 0L
                && previous.size == attributes.size()
                && previous.modifiedTime == attributes.lastModifiedTime().toMillis()
                && previous.changeTime == changeTime;
    }

    private static boolean stillUnchanged(Path source, BasicFileAttributes before, long beforeChangeTime) {
        try {
            BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            return after.size() == before.size()
                    && after.lastModifiedTime().toMillis() == before.lastModifiedTime().toMillis()
                    && changeTime(source) == beforeChangeTime;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static long changeTime(Path path) {
        try {
            Object value = Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS);
            return value instanceof FileTime time ? time.to(TimeUnit.NANOSECONDS) : 0L;
        } catch (UnsupportedOperationException | IOException ignored) {
            return 0L;
        }
    }
}
