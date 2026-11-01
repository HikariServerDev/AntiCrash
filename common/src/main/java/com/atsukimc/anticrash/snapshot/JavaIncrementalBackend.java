package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

/**
 * Portable point-in-time file detachment used by the logical-cut barrier.
 *
 * <p>The portable generation path invokes this backend while the logical-cut barrier is held. The
 * destination must therefore be fully independent before the world resumes. A steady-state capture
 * changes a handful of files out of ten thousand, and the rest of the time is spent on per-file
 * stats and hard links - work that is latency bound rather than CPU bound and therefore spread over
 * a small pool of threads.
 */
public class JavaIncrementalBackend implements SnapshotBackend {
    /** Enough to hide storage latency without turning a spinning disk into random seeks. */
    private static final int DEFAULT_THREADS = 4;

    private final IntSupplier configuredThreads;

    public JavaIncrementalBackend() {
        this(() -> 0);
    }

    /**
     * @param configuredThreads worker count, or 0 to derive one from the available processors
     */
    public JavaIncrementalBackend(IntSupplier configuredThreads) {
        this.configuredThreads = configuredThreads;
    }

    @Override
    public String name() {
        return "JAVA_INCREMENTAL";
    }

    private int workerCount() {
        int configured = configuredThreads.getAsInt();
        if (configured > 0) return configured;
        return Math.max(1, Math.min(DEFAULT_THREADS, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    public boolean isSupported(Path liveWorld) {
        return Files.isDirectory(liveWorld, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(liveWorld);
    }

    @Override
    public SnapshotResult snapshot(
            Path liveWorld,
            Path destination,
            Path previousWorld,
            Map<String, SnapshotFile> previousFiles,
            Map<String, SnapshotFile> liveHashes)
            throws IOException {
        if (!isSupported(liveWorld))
            throw new IOException("Live world is not a regular directory: " + liveWorld);
        Files.createDirectories(destination);
        SnapshotResult result = new SnapshotResult(name());
        final List<Path> sources;
        try (Stream<Path> stream = Files.walk(liveWorld)) {
            sources = stream.toList();
        }
        // A world has thousands of files but only dozens of directories, and createDirectories
        // stats every level on each call. Remember the ones already made.
        Set<Path> createdDirectories = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<SnapshotFile> captured = new ConcurrentLinkedQueue<>();
        // Logical materialization intentionally uses the authenticated parent world as both the
        // immutable source and the reuse index. Creating a checkpoint-to-checkpoint hard link
        // changes that source inode's ctime/nlink, so the normal live-writer check must not treat
        // our own link operation as an external mutation in this one explicitly immutable case.
        boolean sourceIsPrevious =
                previousWorld != null
                        && liveWorld
                                .toAbsolutePath()
                                .normalize()
                                .equals(previousWorld.toAbsolutePath().normalize());
        AtomicLong totalBytes = new AtomicLong();
        AtomicInteger changedFiles = new AtomicInteger();
        AtomicInteger reusedFiles = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger cursor = new AtomicInteger();
        Runnable worker =
                () -> {
                    int index;
                    while (failure.get() == null
                            && (index = cursor.getAndIncrement()) < sources.size()) {
                        try {
                            captureOne(
                                    sources.get(index),
                                    liveWorld,
                                    destination,
                                    previousWorld,
                                    previousFiles,
                                    liveHashes,
                                    createdDirectories,
                                    captured,
                                    totalBytes,
                                    changedFiles,
                                    reusedFiles,
                                    sourceIsPrevious);
                        } catch (Throwable thrown) {
                            failure.compareAndSet(null, thrown);
                            return;
                        }
                    }
                };
        runInParallel(worker);
        Throwable thrown = failure.get();
        if (thrown instanceof IOException ioException) throw ioException;
        if (thrown instanceof RuntimeException runtimeException) throw runtimeException;
        if (thrown instanceof Error error) throw error;
        if (thrown != null) throw new IOException("Snapshot failed", thrown);
        result.files.addAll(captured);
        result.totalBytes = totalBytes.get();
        result.changedFiles = changedFiles.get();
        result.reusedFiles = reusedFiles.get();
        return result;
    }

    /** Runs the same worker on a small pool, or inline when a single thread is configured. */
    private void runInParallel(Runnable worker) throws IOException {
        int threads = workerCount();
        if (threads <= 1) {
            worker.run();
            return;
        }
        ExecutorService pool =
                Executors.newFixedThreadPool(
                        threads,
                        runnable -> {
                            Thread thread = new Thread(runnable, "AntiCrash-Snapshot");
                            thread.setDaemon(true);
                            return thread;
                        });
        try {
            List<Future<?>> running = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) running.add(pool.submit(worker));
            for (Future<?> future : running) {
                try {
                    future.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Snapshot was interrupted");
                } catch (ExecutionException execution) {
                    throw new IOException("Snapshot worker failed", execution.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void captureOne(
            Path source,
            Path liveWorld,
            Path destination,
            Path previousWorld,
            Map<String, SnapshotFile> previousFiles,
            Map<String, SnapshotFile> liveHashes,
            Set<Path> createdDirectories,
            ConcurrentLinkedQueue<SnapshotFile> captured,
            AtomicLong totalBytes,
            AtomicInteger changedFiles,
            AtomicInteger reusedFiles,
            boolean sourceIsPrevious)
            throws IOException {
        {
            {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Snapshot was interrupted");
                }
                // One stat covers directory/symlink/regular plus size, mtime, ctime and nlink.
                FileFacts sourceFacts = FileFacts.readOrNull(source);
                if (sourceFacts == null || sourceFacts.directory()) return;
                if (sourceFacts.symbolicLink()) {
                    throw new IOException(
                            "Symbolic links are not supported in a snapshot path: " + source);
                }
                if (!sourceFacts.regularFile()) return;
                String relative = liveWorld.relativize(source).toString().replace('\\', '/');
                if (SnapshotFilters.excluded(relative)) return;
                Path target = PathSafety.requireChild(destination, destination.resolve(relative));
                Path targetParent = target.getParent();
                // Claiming the path is not enough: another worker must not start writing into a
                // directory whose creation is still in flight, so the claim and the creation share
                // one critical section. Misses are rare - dozens of directories, thousands of
                // files - so the fast path stays lock free.
                if (!createdDirectories.contains(targetParent)) {
                    synchronized (createdDirectories) {
                        if (!createdDirectories.contains(targetParent)) {
                            Files.createDirectories(targetParent);
                            createdDirectories.add(targetParent);
                        }
                    }
                }
                long size = sourceFacts.size();
                long modifiedTime = sourceFacts.modifiedTime();
                long changeTime = sourceFacts.changeTime();
                SnapshotFile previous = previousFiles == null ? null : previousFiles.get(relative);
                Path previousFile = previousFile(previousWorld, relative);
                FileFacts previousFacts =
                        previousFile == null ? null : FileFacts.readOrNull(previousFile);
                boolean previousFileMatches =
                        previousFacts != null
                                && previousFacts.regularFile()
                                && !previousFacts.symbolicLink()
                                && previousFileMatches(previousFile, previousFacts, previous);
                boolean reused = sameMetadata(previous, sourceFacts) && previousFileMatches;
                String checksum;
                // Tracks whether the destination ended up as a hard link to the parent
                // checkpoint's already fsynced file rather than a freshly written copy.
                boolean linkedToParent = false;
                if (reused) {
                    linkedToParent = reusePrevious(previousFile, target);
                    // previousWorld is an immutable, already verified checkpoint. Re-hashing
                    // every unchanged region file here made a 6,000-file world spend ~14s in
                    // the frozen checkpoint state even though only a handful of files changed.
                    // The live file's size/mtime/ctime is checked below; on filesystems without
                    // ctime this branch is never selected and the safe source-hash path remains.
                    checksum = previous.sha256;
                    reusedFiles.incrementAndGet();
                } else {
                    // A pre-freeze scan may already have hashed this exact file revision. Reusing
                    // that digest is what keeps the first capture of a session from reading the
                    // whole world while the server is stopped.
                    String cached = cachedLiveDigest(liveHashes, relative, sourceFacts);
                    if (cached != null) {
                        checksum = cached;
                    } else {
                        checksum = Hashing.sha256(source);
                        ensureUnchanged(source, sourceFacts);
                    }
                    if (previous != null
                            && previous.sha256 != null
                            && previous.sha256.equals(checksum)
                            && previousFileMatches) {
                        // Old manifests have no ctime. One source hash safely upgrades them
                        // without copying and re-reading every unchanged live file.
                        linkedToParent = reusePrevious(previousFile, target);
                        if (previous.changeTime == 0L && !checksum.equals(Hashing.sha256(target))) {
                            // A legacy manifest cannot cheaply authenticate a same-size file
                            // because it has no ctime. Preserve the old safe migration behavior.
                            Files.deleteIfExists(target);
                            copyFresh(source, target);
                            linkedToParent = false;
                            if (!checksum.equals(Hashing.sha256(target))) {
                                throw new IOException(
                                        "Live/previous checkpoint content mismatch: " + source);
                            }
                            changedFiles.incrementAndGet();
                        } else {
                            // The source hash matches the authenticated previous digest, and the
                            // destination is either a hardlink to that file or a local copy of it.
                            reusedFiles.incrementAndGet();
                        }
                    } else {
                        copyFresh(source, target);
                        if (!checksum.equals(Hashing.sha256(target))) {
                            throw new IOException("Snapshot copy checksum mismatch: " + source);
                        }
                        changedFiles.incrementAndGet();
                    }
                }
                // Detect an external writer even though Minecraft mutations are stopped.
                if (!sourceIsPrevious) ensureUnchanged(source, sourceFacts);
                totalBytes.addAndGet(size);
                SnapshotFile snapshotFile =
                        new SnapshotFile(relative, size, modifiedTime, changeTime, checksum);
                snapshotFile.reusedFromParent = linkedToParent;
                FileFacts stored = FileFacts.read(target);
                snapshotFile.destinationMetadataRecorded = true;
                snapshotFile.storedModifiedTime = stored.modifiedTime();
                snapshotFile.storedChangeTime = stored.changeTime();
                snapshotFile.storedLinkCount = stored.linkCount();
                captured.add(snapshotFile);
            }
        }
    }

    /**
     * Digest recorded by the pre-freeze scan, or null when the live file no longer matches it. The
     * comparison is the same size/mtime/ctime identity the incremental fast path already trusts, so
     * a file touched after the scan falls back to a fresh hash.
     */
    static String cachedLiveDigest(
            Map<String, SnapshotFile> liveHashes, String relative, FileFacts source) {
        if (liveHashes == null || source.changeTime() == 0L) return null;
        SnapshotFile cached = liveHashes.get(relative);
        if (cached == null || cached.sha256 == null) return null;
        return cached.size == source.size()
                        && cached.modifiedTime == source.modifiedTime()
                        && cached.changeTime == source.changeTime()
                ? cached.sha256
                : null;
    }

    static boolean sameMetadata(SnapshotFile previous, FileFacts source) {
        // If the platform has no ctime, size/mtime alone is not trusted; hash instead.
        return previous != null
                && previous.sha256 != null
                && source.changeTime() != 0L
                && previous.changeTime != 0L
                && previous.size == source.size()
                && previous.modifiedTime == source.modifiedTime()
                && previous.changeTime == source.changeTime();
    }

    /**
     * How often a parent file had to be read and hashed because its metadata no longer matched its
     * record. Normally zero per generation; a generation that hashes every inherited file reads the
     * whole world, which is worth seeing in the checkpoint's performance line.
     */
    public static final java.util.concurrent.atomic.AtomicLong CONTENT_AUTHENTICATIONS =
            new java.util.concurrent.atomic.AtomicLong();

    static boolean previousFileMatches(Path previousFile, FileFacts facts, SnapshotFile previous)
            throws IOException {
        if (previous == null || previous.sha256 == null) return false;
        // A checkpoint created by an older AntiCrash build has no destination metadata. Keep
        // the old, safe behavior for direct callers and one-off migration paths: authenticate
        // that immutable file once instead of copying the whole world into the new checkpoint.
        if (!previous.destinationMetadataRecorded) {
            return previous.sha256.equals(Hashing.sha256(previousFile))
                    && FileFacts.read(previousFile).sameRevision(facts);
        }
        if (facts.size() != previous.size) {
            return false;
        }
        // ctime is unavailable on some platforms and is then stored as zero. On Linux it lets
        // us detect a changed previous checkpoint with only metadata I/O instead of hashing all
        // files. The destination ctime is recorded separately because a first checkpoint may be
        // a copy rather than a hardlink and therefore has a different ctime from the Live World.
        if (previous.storedChangeTime != 0L
                && facts.changeTime() == previous.storedChangeTime
                && facts.modifiedTime() == previous.storedModifiedTime) {
            return true;
        }
        // Link-count drift is neither corruption nor proof of immutable bytes. Both adding a
        // child and retention unlinking a sibling change Linux ctime. Authenticate the content
        // on ANY metadata drift (or a platform without ctime), not merely a lower link count.
        FileFacts before = facts;
        CONTENT_AUTHENTICATIONS.incrementAndGet();
        return previous.sha256.equals(Hashing.sha256(previousFile))
                && FileFacts.read(previousFile).sameRevision(before);
    }

    /** Pure path arithmetic; the caller stats the result once through {@link FileFacts}. */
    private static Path previousFile(Path previousWorld, String relative) throws IOException {
        if (previousWorld == null) return null;
        return PathSafety.requireChild(previousWorld, previousWorld.resolve(relative));
    }

    /**
     * @return true when a hard link was created, false when a fresh copy had to be written.
     */
    private boolean reusePrevious(Path previousFile, Path target) throws IOException {
        try {
            // This link is checkpoint-to-checkpoint only; live files are never linked.
            Files.createLink(target, previousFile);
            return true;
        } catch (UnsupportedOperationException | IOException linkFailure) {
            // Copy the already verified checkpoint file, never the mutable live file.
            copyFresh(previousFile, target);
            return false;
        }
    }

    private static void ensureUnchanged(Path source, FileFacts before) throws IOException {
        if (!FileFacts.read(source).sameRevision(before)) {
            throw new IOException("Live world file changed during snapshot: " + source);
        }
    }

    protected void copyFresh(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }
}
