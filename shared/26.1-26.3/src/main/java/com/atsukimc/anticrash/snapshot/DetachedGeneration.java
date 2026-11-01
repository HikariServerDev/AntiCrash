package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

/**
 * A point-in-time copy of the live world split at the world freeze.
 *
 * <p>{@link #capture} runs at the checkpoint cut. It only decides, per file, whether the parent
 * checkpoint already holds the cut's bytes ({@link Kind#INHERITED}) and copies the files that
 * changed into the temporary generation ({@link Kind#DETACHED}). After it returns, nothing in the
 * generation depends on the mutable live world any more, so the server may resume.</p>
 *
 * <p>{@link #materialize} runs on the checkpoint worker. It hashes the detached copies and
 * hard-links (or, for a BASE, copies) the inherited files from the immutable parent checkpoint.
 * Hashing every changed file twice, linking ten thousand unchanged files and the periodic full
 * copy of the world used to happen while the server was frozen and cost seconds per checkpoint.</p>
 */
public final class DetachedGeneration {
    public enum Kind { INHERITED, DETACHED }

    /**
     * One world file at the cut. The metadata is that of the live file at the cut (or, for a file
     * the tracker proves unwritten, the parent's record of it); {@code sha256} is known only for an
     * inherited file.
     */
    public record Entry(String path, Kind kind, long size, long modifiedTime, long changeTime,
                        String sha256) {
    }

    private static final int DEFAULT_THREADS = 4;

    private volatile List<Entry> entries;
    private final boolean tracked;
    private final int statedFiles;
    private final long detachedBytes;
    /** Writes still queued at the cut; written into the detached copies before materializing. */
    private List<QueuedWrite> pendingWrites;

    /** A queued write with its region and external chunk file relative to the world. */
    private record QueuedWrite(String regionPath, String externalPath, PendingRegionWrite write) {
    }

    private DetachedGeneration(List<Entry> entries, boolean tracked, int statedFiles, long detachedBytes,
                               List<QueuedWrite> pendingWrites) {
        this.entries = List.copyOf(entries);
        this.tracked = tracked;
        this.statedFiles = statedFiles;
        this.detachedBytes = detachedBytes;
        this.pendingWrites = pendingWrites == null ? List.of() : List.copyOf(pendingWrites);
    }

    public List<Entry> entries() {
        return entries;
    }

    /** True when the region file tracker was consulted in addition to the metadata comparison. */
    public boolean tracked() {
        return tracked;
    }

    public int statedFiles() {
        return statedFiles;
    }

    public int detachedFiles() {
        int count = 0;
        for (Entry entry : entries) if (entry.kind == Kind.DETACHED) count++;
        return count;
    }

    public long detachedBytes() {
        return detachedBytes;
    }

    public int pendingWriteCount() {
        return pendingWrites.size();
    }

    public static DetachedGeneration capture(Path liveWorld, Path destination,
                                             Map<String, SnapshotFile> previousFiles,
                                             Map<String, SnapshotFile> liveHashes,
                                             Set<Path> writtenSinceParent) throws IOException {
        return capture(liveWorld, destination, previousFiles, liveHashes, writtenSinceParent, List.of());
    }

    /**
     * Captures the live world at the cut. The caller guarantees that nothing writes the live world
     * meanwhile: the world is frozen and every storage IO queue is either drained or sealed.
     *
     * <p>Every file is examined. A file is inherited from the parent only when both witnesses
     * agree that it did not change since the parent's cut: its size, mtime and ctime equal the
     * parent's record (or a pre-freeze digest proves the bytes equal), and no chunk, entity or POI
     * write reached it according to {@link LiveFileTracker}. Either witness alone is enough to
     * copy it, so a writer one of them cannot see still ends up in the checkpoint.</p>
     *
     * @param previousFiles       the parent's world files, or empty for a first BASE
     * @param liveHashes          digests from the pre-freeze live scan, possibly empty
     * @param writtenSinceParent  region files written since the parent's cut, or null when the
     *                            tracker cannot vouch for that interval
     * @param pendingWrites       writes queued but not yet on disk at the cut; their region files
     *                            are always detached so the writes can be applied to the copies
     */
    public static DetachedGeneration capture(Path liveWorld, Path destination,
                                             Map<String, SnapshotFile> previousFiles,
                                             Map<String, SnapshotFile> liveHashes,
                                             Set<Path> writtenSinceParent,
                                             List<PendingRegionWrite> pendingWrites) throws IOException {
        Map<String, SnapshotFile> parent = previousFiles == null ? Map.of() : previousFiles;
        Path root = liveWorld.toAbsolutePath().normalize();
        boolean tracked = writtenSinceParent != null;
        Files.createDirectories(destination);

        Set<String> writtenRelative = new HashSet<>();
        if (tracked) {
            for (Path written : writtenSinceParent) {
                Path normalized = written.toAbsolutePath().normalize();
                if (normalized.startsWith(root) && !normalized.equals(root)) {
                    writtenRelative.add(relative(root, normalized));
                }
            }
        }
        Set<String> pendingRegions = new HashSet<>();
        List<PendingRegionWrite> pending = pendingWrites == null ? List.of() : pendingWrites;
        List<QueuedWrite> queued = new ArrayList<>(pending.size());
        for (PendingRegionWrite write : pending) {
            Path region = write.regionFile().toAbsolutePath().normalize();
            Path external = write.externalChunkFile().toAbsolutePath().normalize();
            if (!region.startsWith(root) || region.equals(root)) {
                throw new IOException("Queued chunk write is outside the world: " + region);
            }
            String regionPath = relative(root, region);
            if (SnapshotFilters.excluded(regionPath)) continue;
            pendingRegions.add(regionPath);
            queued.add(new QueuedWrite(regionPath, relative(root, external), write));
        }
        List<Candidate> candidates = scan(root);
        List<Entry> entries = new ArrayList<>();
        List<Candidate> changed = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (writtenRelative.contains(candidate.relative) || pendingRegions.contains(candidate.relative)) {
                changed.add(candidate);
                continue;
            }
            SnapshotFile previous = parent.get(candidate.relative);
            FileFacts facts = candidate.facts;
            if (JavaIncrementalBackend.sameMetadata(previous, facts)) {
                entries.add(new Entry(candidate.relative, Kind.INHERITED, facts.size(),
                        facts.modifiedTime(), facts.changeTime(), previous.sha256));
                continue;
            }
            String cached = JavaIncrementalBackend.cachedLiveDigest(liveHashes, candidate.relative, facts);
            if (cached != null && previous != null && cached.equals(previous.sha256)) {
                // Touched but byte-identical (for example a region header rewritten on close).
                entries.add(new Entry(candidate.relative, Kind.INHERITED, facts.size(),
                        facts.modifiedTime(), facts.changeTime(), previous.sha256));
                continue;
            }
            changed.add(candidate);
        }

        Set<Path> createdDirectories = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<Entry> detached = new ConcurrentLinkedQueue<>();
        AtomicLong bytes = new AtomicLong();
        parallel(changed, DEFAULT_THREADS, candidate -> {
            Path target = PathSafety.requireChild(destination, destination.resolve(candidate.relative));
            createParent(target, createdDirectories);
            Files.copy(candidate.absolute, target, StandardCopyOption.COPY_ATTRIBUTES);
            long size = candidate.facts.size();
            if (LiveFileTracker.isRegionStorageFile(candidate.relative)) {
                // Chunk loading keeps reading while the world is frozen, and a read that evicts a
                // cached RegionFile closes it: the header is rewritten with the same bytes, and a
                // region file a read just created gains its empty header. Neither changes a chunk,
                // and no chunk write can happen after the drain, so the copy is taken as is.
                size = FileFacts.read(target).size();
            } else if (!FileFacts.read(candidate.absolute).sameRevision(candidate.facts)) {
                // Nothing in Minecraft writes these during the freeze; this is an external writer.
                throw new IOException("Live world file changed during snapshot: " + candidate.absolute);
            }
            bytes.addAndGet(size);
            detached.add(new Entry(candidate.relative, Kind.DETACHED, size,
                    candidate.facts.modifiedTime(), candidate.facts.changeTime(), null));
        });
        entries.addAll(detached);
        entries.sort(Comparator.comparing(Entry::path));
        SealedNbt.seal(pending);
        return new DetachedGeneration(entries, tracked, candidates.size(), bytes.get(), queued);
    }

    /**
     * Writes the queued writes of the cut into the detached copies, exactly as the IO worker writes
     * them into the live files, and corrects the entries of every file they touched. Runs once, on
     * the checkpoint worker, before anything is hashed.
     */
    private synchronized void applyPendingWrites(Path destination) throws IOException {
        List<QueuedWrite> pending = pendingWrites;
        if (pending.isEmpty()) return;
        try {
            Map<String, Entry> byPath = new java.util.TreeMap<>();
            for (Entry entry : entries) byPath.put(entry.path, entry);
            Map<String, List<QueuedWrite>> byRegion = new java.util.LinkedHashMap<>();
            for (QueuedWrite write : pending) {
                byRegion.computeIfAbsent(write.regionPath, ignored -> new ArrayList<>()).add(write);
            }
            for (Map.Entry<String, List<QueuedWrite>> region : byRegion.entrySet()) {
                Path target = PathSafety.requireChild(destination, destination.resolve(region.getKey()));
                Entry existing = byPath.get(region.getKey());
                if (existing != null && existing.kind != Kind.DETACHED) {
                    throw new IOException("Region file with queued writes was not detached: " + region.getKey());
                }
                CheckpointWriteGuard.requireDetached(target);
                Files.createDirectories(target.getParent());
                net.minecraft.world.level.chunk.storage.RegionFile file =
                        new net.minecraft.world.level.chunk.storage.RegionFile(com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY, target, target.getParent(), false);
                try {
                    for (QueuedWrite queued : region.getValue()) {
                        PendingRegionWrite write = queued.write;
                        if (write.nbt() == null) {
                            file.clear(write.pos());
                        } else {
                            if (write.nbt().contains("__context")) {
                                throw new IOException("Queued chunk data was being modified: " + write.pos());
                            }
                            try (java.io.DataOutputStream output = file.getChunkDataOutputStream(write.pos())) {
                                net.minecraft.nbt.NbtIo.writeUnnamedTagWithFallback(write.nbt(), output);
                            }
                        }
                    }
                } finally {
                    file.close();
                }
                // Zero times mean "no metadata": the next checkpoint hashes instead of trusting them.
                byPath.put(region.getKey(), new Entry(region.getKey(), Kind.DETACHED,
                        Files.size(target), 0L, 0L, null));
                for (QueuedWrite queued : region.getValue()) {
                    String external = queued.externalPath;
                    Path externalTarget = PathSafety.requireChild(destination, destination.resolve(external));
                    if (Files.exists(externalTarget)) {
                        byPath.put(external, new Entry(external, Kind.DETACHED,
                                Files.size(externalTarget), 0L, 0L, null));
                    } else {
                        byPath.remove(external);
                    }
                }
            }
            entries = List.copyOf(byPath.values());
            pendingWrites = List.of();
        } catch (java.util.ConcurrentModificationException modified) {
            throw new IOException("Queued chunk data was modified while being written", modified);
        } finally {
            SealedNbt.clear();
        }
    }

    /** Drops the queued writes of a generation that will never be materialized. */
    public void discard() {
        pendingWrites = List.of();
        SealedNbt.clear();
    }


    /**
     * Completes the generation from the immutable parent checkpoint. Never reads the live world.
     *
     * @param copyInherited true for a BASE generation: inherited files are copied rather than
     *                      hard-linked so the new generation shares no inode with its parent
     */
    public SnapshotResult materialize(Path previousWorld, Map<String, SnapshotFile> previousFiles,
                                      Path destination, boolean copyInherited, String backend)
            throws IOException {
        return materialize(previousWorld, previousFiles, destination, copyInherited, backend,
                DEFAULT_THREADS);
    }

    public SnapshotResult materialize(Path previousWorld, Map<String, SnapshotFile> previousFiles,
                                      Path destination, boolean copyInherited, String backend,
                                      int threads) throws IOException {
        CheckpointWriteGuard.requireMutable(destination);
        applyPendingWrites(destination);
        List<Entry> entries = this.entries;
        Map<String, SnapshotFile> parent = previousFiles == null ? Map.of() : previousFiles;
        Set<Path> createdDirectories = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<SnapshotFile> files = new ConcurrentLinkedQueue<>();
        AtomicInteger changedFiles = new AtomicInteger();
        AtomicInteger reusedFiles = new AtomicInteger();
        AtomicLong totalBytes = new AtomicLong();
        parallel(entries, Math.max(1, threads), entry -> {
            Path target = PathSafety.requireChild(destination, destination.resolve(entry.path));
            SnapshotFile previous = parent.get(entry.path);
            boolean linked = false;
            String sha256;
            if (entry.kind == Kind.INHERITED) {
                Path previousFile = verifiedParentFile(previousWorld, entry.path, previous);
                if (previousFile == null) {
                    throw new ParentCheckpointChangedException(
                            parentFailure(previousWorld, entry.path, previous));
                }
                createParent(target, createdDirectories);
                if (copyInherited) {
                    Files.copy(previousFile, target, StandardCopyOption.COPY_ATTRIBUTES);
                    changedFiles.incrementAndGet();
                } else {
                    linked = linkOrCopy(previousFile, target);
                    reusedFiles.incrementAndGet();
                }
                sha256 = entry.sha256;
            } else {
                sha256 = Hashing.sha256(target);
                Path previousFile = !copyInherited && previous != null && sha256.equals(previous.sha256)
                        ? verifiedParentFile(previousWorld, entry.path, previous) : null;
                if (previousFile != null) {
                    // Rewritten with the same bytes: share the parent's already durable inode.
                    Files.delete(target);
                    linked = linkOrCopy(previousFile, target);
                    reusedFiles.incrementAndGet();
                } else {
                    changedFiles.incrementAndGet();
                }
            }
            FileFacts stored = FileFacts.read(target);
            if (stored.size() != entry.size) {
                throw new IOException("Checkpoint copy size mismatch: " + entry.path);
            }
            SnapshotFile file = new SnapshotFile(entry.path, entry.size, entry.modifiedTime,
                    entry.changeTime, sha256);
            file.reusedFromParent = linked;
            file.destinationMetadataRecorded = true;
            file.storedModifiedTime = stored.modifiedTime();
            file.storedChangeTime = stored.changeTime();
            file.storedLinkCount = stored.linkCount();
            files.add(file);
            totalBytes.addAndGet(entry.size);
        });
        SnapshotResult result = new SnapshotResult(backend);
        List<SnapshotFile> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(file -> file.path));
        result.files.addAll(sorted);
        result.totalBytes = totalBytes.get();
        result.changedFiles = changedFiles.get();
        result.reusedFiles = reusedFiles.get();
        return result;
    }

    /** Returns the parent's file when it still holds exactly the recorded bytes, else null. */
    private static Path verifiedParentFile(Path previousWorld, String relative, SnapshotFile previous)
            throws IOException {
        if (previousWorld == null || previous == null || previous.sha256 == null) return null;
        Path previousFile = PathSafety.requireChild(previousWorld, previousWorld.resolve(relative));
        FileFacts facts = FileFacts.readOrNull(previousFile);
        if (facts == null || !facts.regularFile() || facts.symbolicLink()) return null;
        return JavaIncrementalBackend.previousFileMatches(previousFile, facts, previous) ? previousFile : null;
    }

    private static String parentFailure(Path root, String relative, SnapshotFile previous) throws IOException {
        FileFacts facts = root == null ? null : FileFacts.readOrNull(PathSafety.requireChild(root, root.resolve(relative)));
        String reason = previous == null ? "MISSING_MANIFEST_ENTRY" : facts == null ? "MISSING"
                : !facts.regularFile() || facts.symbolicLink() ? "UNSAFE_FILE"
                : facts.size() != previous.size ? "SIZE_MISMATCH" : "CHECKSUM_MISMATCH";
        return "Parent checkpoint file is missing or was modified: " + relative + "; reason=" + reason
                + "; expectedSize=" + (previous == null ? -1 : previous.size) + "; actual=" + facts;
    }

    /** @return true for a hard link to the parent's inode, false when a copy had to be made. */
    private static boolean linkOrCopy(Path previousFile, Path target) throws IOException {
        try {
            Files.createLink(target, previousFile);
            return true;
        } catch (UnsupportedOperationException | IOException linkFailure) {
            Files.deleteIfExists(target);
            Files.copy(previousFile, target, StandardCopyOption.COPY_ATTRIBUTES);
            return false;
        }
    }

    /** Stats every non-excluded world file, one directory level at a time on a small pool. */
    private static List<Candidate> scan(Path root) throws IOException {
        ConcurrentLinkedQueue<Candidate> files = new ConcurrentLinkedQueue<>();
        List<Path> level = List.of(root);
        while (!level.isEmpty()) {
            List<Path> children = new ArrayList<>();
            for (Path directory : level) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                    for (Path child : stream) {
                        if (!SnapshotFilters.excluded(relative(root, child))) children.add(child);
                    }
                }
            }
            ConcurrentLinkedQueue<Path> next = new ConcurrentLinkedQueue<>();
            parallel(children, DEFAULT_THREADS, child -> {
                FileFacts facts = FileFacts.readOrNull(child);
                if (facts == null) return;
                if (facts.symbolicLink()) {
                    throw new IOException("Symbolic links are not supported in a snapshot path: " + child);
                }
                if (facts.directory()) next.add(child);
                else if (facts.regularFile()) files.add(new Candidate(relative(root, child), child, facts));
            });
            level = new ArrayList<>(next);
        }
        List<Candidate> result = new ArrayList<>(files);
        result.sort(Comparator.comparing(Candidate::relative));
        return result;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void createParent(Path target, Set<Path> createdDirectories) throws IOException {
        Path parent = target.getParent();
        if (parent == null || createdDirectories.contains(parent)) return;
        synchronized (createdDirectories) {
            if (!createdDirectories.contains(parent)) {
                Files.createDirectories(parent);
                createdDirectories.add(parent);
            }
        }
    }

    private interface IoAction<T> {
        void run(T item) throws IOException;
    }

    /** Runs the action over a small pool; file work here is latency bound, not CPU bound. */
    private static <T> void parallel(List<T> items, int threads, IoAction<T> action) throws IOException {
        if (items.isEmpty()) return;
        AtomicInteger cursor = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable worker = () -> {
            int index;
            while (failure.get() == null && (index = cursor.getAndIncrement()) < items.size()) {
                if (Thread.currentThread().isInterrupted()) {
                    failure.compareAndSet(null, new InterruptedIOException("Snapshot was interrupted"));
                    return;
                }
                try {
                    action.run(items.get(index));
                } catch (Throwable thrown) {
                    failure.compareAndSet(null, thrown);
                    return;
                }
            }
        };
        int count = Math.max(1, Math.min(threads, Math.min(items.size(),
                Runtime.getRuntime().availableProcessors())));
        if (count == 1) {
            worker.run();
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(count, runnable -> {
                Thread thread = new Thread(runnable, "AntiCrash-Snapshot");
                thread.setDaemon(true);
                return thread;
            });
            try {
                List<Future<?>> running = new ArrayList<>(count);
                for (int i = 0; i < count; i++) running.add(pool.submit(worker));
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
        Throwable thrown = failure.get();
        if (thrown instanceof IOException ioException) throw ioException;
        if (thrown instanceof RuntimeException runtimeException) throw runtimeException;
        if (thrown instanceof Error error) throw error;
        if (thrown != null) throw new IOException("Snapshot failed", thrown);
    }

    private record Candidate(String relative, Path absolute, FileFacts facts) {
    }
}
