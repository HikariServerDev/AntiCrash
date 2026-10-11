package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable hand-off from the server-thread logical cut to background finalization.
 *
 * <p>The portable generation implementation captures all present, changed or unprovable files
 * while the world is frozen.  Unchanged files are linked only from an already committed parent
 * checkpoint.  Consequently the background worker never opens the mutable live world.  The file
 * set also records deletions so a file created after the cut cannot leak into generation N.</p>
 */
public final class GenerationSeal {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public int formatVersion = CURRENT_FORMAT_VERSION;
    public String backend = "PORTABLE_GENERATION";
    public String generationType = "BASE";
    public String parentCheckpointId;
    public long logicalCutServerTick;
    public long logicalCutMonotonicNanos;
    public int presentCount;
    public int absentCount;
    public int inheritedCount;
    public int sealedCount;
    public int tombstoneCount;
    public long sealedBytes;
    public final List<GenerationFileEntry> fileSet = new ArrayList<>();

    /** The actual snapshot result lives only in memory and is deliberately not serialized. */
    private transient SnapshotResult snapshot;

    public GenerationSeal() {
    }

    public GenerationSeal(SnapshotResult snapshot, String parentCheckpointId,
                          long logicalCutServerTick, long logicalCutMonotonicNanos) {
        this.snapshot = snapshot;
        this.parentCheckpointId = parentCheckpointId;
        this.logicalCutServerTick = logicalCutServerTick;
        this.logicalCutMonotonicNanos = logicalCutMonotonicNanos;
        if (parentCheckpointId != null) {
            this.generationType = "DELTA";
        }
        if (snapshot != null && snapshot.files != null) {
            List<SnapshotFile> files = new ArrayList<>(snapshot.files);
            files.sort(Comparator.comparing(file -> file.path == null ? "" : file.path));
            for (SnapshotFile file : files) {
                GenerationFileEntry entry = GenerationFileEntry.fromSnapshot(file);
                if (entry == null || entry.path == null) continue;
                fileSet.add(entry);
                presentCount++;
                if (GenerationCaptureKind.INHERITED.name().equals(entry.captureKind)) {
                    inheritedCount++;
                } else {
                    sealedCount++;
                    sealedBytes = Math.addExact(sealedBytes, Math.max(0L, entry.size));
                }
            }
        }
    }

    /** Adds the parent paths absent at the cut as tombstones. */
    public void addTombstones(Map<String, SnapshotFile> previousFiles) {
        if (previousFiles == null || previousFiles.isEmpty()) return;
        Set<String> present = new HashSet<>();
        for (GenerationFileEntry entry : fileSet) if (entry != null && entry.path != null) present.add(entry.path);
        List<String> missing = new ArrayList<>();
        for (String path : previousFiles.keySet()) {
            if (path != null && !present.contains(path)) missing.add(path);
        }
        missing.sort(String::compareTo);
        for (String path : missing) {
            fileSet.add(new GenerationFileEntry(path, GenerationFileState.TOMBSTONE,
                    null, 0L, 0L, 0L, null));
            tombstoneCount++;
        }
        fileSet.sort(Comparator.comparing(entry -> entry.path == null ? "" : entry.path));
    }

    /** Records an optional path that was explicitly absent at the cut. */
    public void addAbsent(String path) {
        if (path == null || path.isBlank()) return;
        for (GenerationFileEntry entry : fileSet) {
            if (entry != null && path.equals(entry.path)) return;
        }
        fileSet.add(new GenerationFileEntry(path, GenerationFileState.ABSENT,
                null, 0L, 0L, 0L, null));
        absentCount++;
        fileSet.sort(Comparator.comparing(entry -> entry.path == null ? "" : entry.path));
    }

    public SnapshotResult snapshotResult() {
        return snapshot;
    }

    /**
     * Writes and fsyncs the minimum seal journal before the world can resume.
     * AtomicFiles gives the journal a 0600 temporary file and an atomic replacement.
     */
    public void persist(Path checkpointTemporary) throws IOException {
        if (checkpointTemporary == null || Files.isSymbolicLink(checkpointTemporary)
                || !Files.isDirectory(checkpointTemporary, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe generation temporary directory");
        }
        AtomicFiles.writeJson(checkpointTemporary.resolve("generation-seal.json"), this);
    }
}
