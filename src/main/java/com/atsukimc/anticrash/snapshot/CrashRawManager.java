package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CrashRawManager {
    private static final long MAX_CHECKSUM_FILE_BYTES = 64L * 1024L * 1024L;
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private final Path directory;
    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;

    public CrashRawManager(Path directory, AntiCrashConfig config, AntiCrashLogger logger)
            throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.config = config;
        this.logger = logger;
        if (Files.exists(this.directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(this.directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(this.directory))
                throw new IOException("Unsafe crash-raw directory: " + this.directory);
        } else Files.createDirectories(this.directory);
        discardAbandonedTemporaries();
    }

    /**
     * Removes {@code .tmp-<uuid>} directories left by a JVM that died while preserving a world.
     * {@link #retain()} deliberately skips them, so nothing else would ever reclaim that space.
     */
    private void discardAbandonedTemporaries() {
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith(".tmp-")
                        || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(path)) continue;
                SnapshotManager.deleteTree(path);
                logger.importantKey("crashraw.abandoned_temp_removed", name);
            }
        } catch (IOException exception) {
            logger.importantKey("crashraw.abandoned_temp_failed", exception.getMessage());
        }
    }

    public Path capture(Path world, String runId) throws IOException {
        if (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(world))
            throw new IOException("Live world directory is unavailable: " + world);
        if (runId == null || !runId.matches(UUID_PATTERN))
            throw new IOException("Invalid crash-raw run ID");
        Path target = directory.resolve(runId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            target = directory.resolve(runId + "-" + System.currentTimeMillis());
        Path temporary = directory.resolve(".tmp-" + UUID.randomUUID());
        try {
            java.util.Map<String, String> digests = new java.util.TreeMap<>();
            synchronized (this) {
                RawListing previous = newestListing(null);
                long linked =
                        copyDeduplicated(world, temporary.resolve("world"), previous, digests);
                if (previous != null)
                    logger.normalKey("crashraw.deduplicated", linked, previous.root.getFileName());
            }
            writeIntegrityMetadata(temporary, digests);
            forceTree(temporary);
            AtomicFiles.moveAtomically(temporary, target);
            AtomicFiles.forceDirectory(directory);
        } finally {
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS))
                SnapshotManager.deleteTree(temporary);
        }
        logger.normalKey("crashraw.saved", target);
        try {
            retain();
        } catch (IOException exception) {
            logger.importantKey("crashraw.retention_failed", exception.getMessage());
        }
        return target;
    }

    public synchronized void retain() throws IOException {
        try (var stream = Files.list(directory)) {
            var paths =
                    stream.filter(path ->
                                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                                            && !Files.isSymbolicLink(path))
                            .filter(path -> !path.getFileName().toString().startsWith(".tmp-"))
                            .sorted(Comparator.comparingLong(this::mtime).reversed())
                            .toList();
            for (int i = Math.max(1, config.snapshot.crashRawRetentionCount); i < paths.size(); i++)
                SnapshotManager.deleteTree(paths.get(i));
        }
    }

    /** Cheap presence check for a committed crash-raw copy; it does not re-hash the world. */
    public boolean contains(String id) {
        if (id == null || !id.matches(UUID_PATTERN + "(?:-[0-9]{1,19})?")) return false;
        try {
            Path path = PathSafety.requireChild(directory, directory.resolve(id));
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(path)
                    && Files.isRegularFile(path.resolve("READY"), LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            return false;
        }
    }

    public Path find(String id) throws IOException {
        if (id == null || !id.matches(UUID_PATTERN + "(?:-[0-9]{1,19})?")) return null;
        Path path = PathSafety.requireChild(directory, directory.resolve(id));
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            return null;
        verify(path);
        return path;
    }

    public synchronized void verify(Path raw) throws IOException {
        Path root = PathSafety.requireChild(directory, raw);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("Unsafe crash-raw directory");
        Path checksumFile = root.resolve("checksums.sha256");
        Path readyFile = root.resolve("READY");
        if (!Files.isRegularFile(checksumFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(readyFile, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Crash-raw is incomplete");
        if (Files.size(checksumFile) > MAX_CHECKSUM_FILE_BYTES || Files.size(readyFile) > 1024)
            throw new IOException("Crash-raw integrity metadata is too large");
        String ready = AtomicFiles.readText(readyFile, 1024L).trim();
        if (!ready.equals(Hashing.sha256(checksumFile)))
            throw new IOException("Crash-raw READY checksum mismatch");
        Set<String> listed = new HashSet<>();
        for (String line :
                AtomicFiles.readText(checksumFile, MAX_CHECKSUM_FILE_BYTES).split("\\R", -1)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf("  ");
            if (separator <= 0) throw new IOException("Malformed crash-raw checksum line");
            String digest = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String relative = line.substring(separator + 2).trim().replace('\\', '/');
            if (!digest.matches("[0-9a-f]{64}")
                    || !relative.startsWith("world/")
                    || !listed.add(relative))
                throw new IOException("Invalid crash-raw checksum entry");
            Path file = PathSafety.requireChild(root, root.resolve(relative));
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)
                    || !digest.equals(Hashing.sha256(file)))
                throw new IOException("Crash-raw checksum mismatch: " + relative);
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                PathSafety.rejectSymlink(path);
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!relative.equals("READY")
                        && !relative.equals("checksums.sha256")
                        && !listed.contains(relative))
                    throw new IOException("Unlisted crash-raw file: " + relative);
            }
        }
        if (!Files.isDirectory(root.resolve("world"), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root.resolve("world")))
            throw new IOException("Crash-raw world is missing");
    }

    private static void writeIntegrityMetadata(Path root, java.util.Map<String, String> digests)
            throws IOException {
        List<String> lines = new ArrayList<>();
        for (var entry : digests.entrySet()) lines.add(entry.getValue() + "  " + entry.getKey());
        Path checksums = root.resolve("checksums.sha256");
        AtomicFiles.writeText(
                checksums, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        AtomicFiles.writeText(
                root.resolve("READY"), Hashing.sha256(checksums) + System.lineSeparator());
    }

    private static void forceTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path path : stream.toList())
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) AtomicFiles.force(path);
        }
        AtomicFiles.forceDirectory(root);
    }

    /** The verified checksum listing of one committed crash-raw copy. */
    private record RawListing(Path root, java.util.Map<String, String> digests) {}

    /** Reads a committed copy's listing after checking it against READY; null if unusable. */
    private RawListing listing(Path root) {
        try {
            Path checksumFile = root.resolve("checksums.sha256");
            Path readyFile = root.resolve("READY");
            if (!Files.isRegularFile(checksumFile, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(readyFile, LinkOption.NOFOLLOW_LINKS)) return null;
            if (Files.size(checksumFile) > MAX_CHECKSUM_FILE_BYTES || Files.size(readyFile) > 1024)
                return null;
            if (!AtomicFiles.readText(readyFile, 1024L).trim().equals(Hashing.sha256(checksumFile)))
                return null;
            java.util.Map<String, String> digests = new java.util.HashMap<>();
            for (String line :
                    AtomicFiles.readText(checksumFile, MAX_CHECKSUM_FILE_BYTES).split("\\R", -1)) {
                if (line.isBlank()) continue;
                int separator = line.indexOf("  ");
                if (separator <= 0) return null;
                String digest = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String relative = line.substring(separator + 2).trim().replace('\\', '/');
                if (!digest.matches("[0-9a-f]{64}") || !relative.startsWith("world/")) return null;
                digests.put(relative, digest);
            }
            return new RawListing(root, digests);
        } catch (IOException exception) {
            return null;
        }
    }

    /** Committed copies, newest first. */
    private List<Path> committed() throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(path ->
                            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                                    && !Files.isSymbolicLink(path))
                    .filter(path -> !path.getFileName().toString().startsWith(".tmp-"))
                    .filter(path ->
                            Files.isRegularFile(path.resolve("READY"), LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparingLong(this::mtime).reversed())
                    .toList();
        }
    }

    private RawListing newestListing(Path exclude) throws IOException {
        for (Path root : committed()) {
            if (root.equals(exclude)) continue;
            RawListing listing = listing(root);
            if (listing != null) return listing;
        }
        return null;
    }

    /**
     * Copies the world into a new crash-raw copy. A file whose bytes equal the same file in the
     * newest earlier copy becomes a hard link to it: both copies are immutable and each keeps its
     * own checksum list, so they stay independently verifiable while the unchanged bulk of a 19 GB
     * world is stored once instead of once per recovery. Equality is proven by hashing both files,
     * never assumed from metadata.
     *
     * @return the number of files linked instead of copied
     */
    private static long copyDeduplicated(
            Path source,
            Path destination,
            RawListing previous,
            java.util.Map<String, String> digests)
            throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing copy source directory: " + source);
        PathSafety.rejectSymlink(source);
        Files.createDirectories(destination);
        long linked = 0;
        try (var stream = Files.walk(source)) {
            for (Path input : stream.sorted().toList()) {
                if (input.equals(source)) continue;
                PathSafety.rejectSymlink(input);
                Path relative = source.relativize(input);
                Path output = PathSafety.requireChild(destination, destination.resolve(relative));
                if (Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(output);
                    continue;
                }
                if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.createDirectories(output.getParent());
                String key = "world/" + relative.toString().replace('\\', '/');
                String previousDigest = previous == null ? null : previous.digests.get(key);
                if (previousDigest != null) {
                    Path previousFile =
                            PathSafety.requireChild(previous.root, previous.root.resolve(key));
                    if (Files.isRegularFile(previousFile, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(previousFile)
                            && Files.size(previousFile) == Files.size(input)) {
                        String liveDigest = Hashing.sha256(input);
                        if (liveDigest.equals(previousDigest)
                                && previousDigest.equals(Hashing.sha256(previousFile))) {
                            try {
                                Files.createLink(output, previousFile);
                                digests.put(key, previousDigest);
                                linked++;
                                continue;
                            } catch (UnsupportedOperationException | IOException linkFailure) {
                                Files.deleteIfExists(output);
                            }
                        }
                    }
                }
                Files.copy(
                        input,
                        output,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                digests.put(key, Hashing.sha256(output));
            }
        }
        return linked;
    }

    /**
     * Shares identical files between copies made before deduplication existed, oldest copies
     * pointing at the newest. The older file is replaced only when the newer file hashes to the
     * digest the older copy lists for it, so every copy still matches its own checksum list. Runs
     * on a low-priority background thread and reads at most {@code bytesPerSecond}.
     */
    public Thread compactExisting(long bytesPerSecond) {
        Thread thread =
                new Thread(
                        () -> {
                            long saved = 0;
                            long files = 0;
                            try {
                                List<Path> roots;
                                synchronized (this) {
                                    roots = committed();
                                }
                                if (roots.size() < 2) return;
                                RawListing canonical = listing(roots.get(0));
                                if (canonical == null) return;
                                java.util.Set<String> verified = new HashSet<>();
                                long windowStart = System.nanoTime();
                                long windowBytes = 0;
                                for (int i = 1; i < roots.size(); i++) {
                                    RawListing older = listing(roots.get(i));
                                    if (older == null) continue;
                                    for (var entry : older.digests.entrySet()) {
                                        String digest = canonical.digests.get(entry.getKey());
                                        if (digest == null || !digest.equals(entry.getValue()))
                                            continue;
                                        synchronized (this) {
                                            if (!Files.isDirectory(
                                                            older.root, LinkOption.NOFOLLOW_LINKS)
                                                    || !Files.isDirectory(
                                                            canonical.root,
                                                            LinkOption.NOFOLLOW_LINKS)) break;
                                            Path keep =
                                                    PathSafety.requireChild(
                                                            canonical.root,
                                                            canonical.root.resolve(entry.getKey()));
                                            Path replace =
                                                    PathSafety.requireChild(
                                                            older.root,
                                                            older.root.resolve(entry.getKey()));
                                            if (!Files.isRegularFile(
                                                            keep, LinkOption.NOFOLLOW_LINKS)
                                                    || !Files.isRegularFile(
                                                            replace, LinkOption.NOFOLLOW_LINKS))
                                                continue;
                                            if (Files.isSameFile(keep, replace)) continue;
                                            long size = Files.size(replace);
                                            if (!verified.contains(entry.getKey())) {
                                                windowBytes += Files.size(keep);
                                                if (!digest.equals(Hashing.sha256(keep))) continue;
                                                verified.add(entry.getKey());
                                            }
                                            Path temporary =
                                                    replace.resolveSibling(
                                                            replace.getFileName()
                                                                    + ".anticrash-link");
                                            Files.deleteIfExists(temporary);
                                            Files.createLink(temporary, keep);
                                            AtomicFiles.moveAtomically(temporary, replace);
                                            saved += size;
                                            files++;
                                        }
                                        long elapsedNanos = System.nanoTime() - windowStart;
                                        long minimumNanos =
                                                bytesPerSecond <= 0
                                                        ? 0
                                                        : windowBytes
                                                                * 1_000_000_000L
                                                                / bytesPerSecond;
                                        if (minimumNanos > elapsedNanos)
                                            Thread.sleep(
                                                    (minimumNanos - elapsedNanos) / 1_000_000L);
                                    }
                                    AtomicFiles.forceDirectory(older.root);
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            } catch (Exception exception) {
                                logger.importantKey(
                                        "crashraw.compaction_failed",
                                        String.valueOf(exception.getMessage()));
                            } finally {
                                if (files > 0)
                                    logger.normalKey(
                                            "crashraw.compacted", files, saved / (1024L * 1024L));
                            }
                        },
                        "AntiCrash-CrashRawCompaction");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return thread;
    }

    private long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
