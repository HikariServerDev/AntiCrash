package com.atsukimc.anticrash.snapshot;

import java.nio.file.Path;

/** Metadata-only checkpoint candidate used before a full integrity verification is requested. */
public record CheckpointCandidate(Path path, CheckpointManifest manifest) {
    public String id() {
        return manifest == null ? path.getFileName().toString() : manifest.checkpointId;
    }

    public String timestamp() {
        return manifest == null ? null : manifest.utcTimestamp;
    }
}
