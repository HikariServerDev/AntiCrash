package com.atsukimc.anticrash.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Portable generation backend.
 *
 * <p>This backend is intentionally conservative: it uses ordinary directory operations and the
 * existing authenticated checkpoint-to-checkpoint reuse implementation. Capture is invoked only
 * inside the logical-cut barrier; finalization receives the already independent destination and
 * therefore cannot observe post-cut live-world writes. Btrfs/Reflink remain optional accelerators
 * for the legacy full-barrier path and are never required for correctness.
 */
public final class PortableGenerationBackend implements SnapshotBackend {
    public static final String NAME = "PORTABLE_GENERATION";
    private final JavaIncrementalBackend captureBackend;

    public PortableGenerationBackend(IntSupplier configuredThreads) {
        this.captureBackend = new JavaIncrementalBackend(configuredThreads);
    }

    public PortableGenerationBackend() {
        this(() -> 1);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isSupported(Path liveWorld) {
        return Files.isDirectory(liveWorld, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(liveWorld);
    }

    /** Direct callers retain the SnapshotBackend API; CheckpointManager uses the cut-aware API. */
    @Override
    public SnapshotResult snapshot(
            Path liveWorld,
            Path destination,
            Path previousWorld,
            Map<String, SnapshotFile> previousFiles,
            Map<String, SnapshotFile> liveHashes)
            throws IOException {
        if (!isSupported(liveWorld))
            throw new IOException("Live world is not a safe directory: " + liveWorld);
        SnapshotResult result =
                captureBackend.snapshot(
                        liveWorld,
                        destination,
                        previousWorld,
                        previousFiles == null ? Map.of() : previousFiles,
                        liveHashes == null ? Map.of() : liveHashes);
        result.backend = NAME;
        return result;
    }

    public GenerationSeal captureAtLogicalCut(
            Path liveWorld,
            Path destination,
            Path previousWorld,
            Map<String, SnapshotFile> previousFiles,
            Map<String, SnapshotFile> liveHashes,
            String parentCheckpointId,
            long serverTick,
            long monotonicNanos)
            throws IOException {
        if (!isSupported(liveWorld)) {
            throw new IOException("Live world is not a safe directory: " + liveWorld);
        }
        SnapshotResult result =
                snapshot(liveWorld, destination, previousWorld, previousFiles, liveHashes);
        GenerationSeal seal =
                new GenerationSeal(result, parentCheckpointId, serverTick, monotonicNanos);
        seal.addTombstones(previousFiles);
        return seal;
    }
}
