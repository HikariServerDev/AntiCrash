package com.atsukimc.anticrash.snapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface SnapshotBackend {
    String name();

    boolean isSupported(Path liveWorld);

    /** Convenience form without a pre-computed live hash cache. */
    default SnapshotResult snapshot(Path liveWorld, Path destination, Path previousWorld,
                                    Map<String, SnapshotFile> previousFiles) throws IOException {
        return snapshot(liveWorld, destination, previousWorld, previousFiles, Map.of());
    }

    /**
     * @param liveHashes live-file digests captured by {@link LiveWorldScan} before the freeze, used
     *                   only while the live metadata still matches the cached entry exactly
     */
    SnapshotResult snapshot(Path liveWorld, Path destination, Path previousWorld,
                            Map<String, SnapshotFile> previousFiles,
                            Map<String, SnapshotFile> liveHashes) throws IOException;
}
