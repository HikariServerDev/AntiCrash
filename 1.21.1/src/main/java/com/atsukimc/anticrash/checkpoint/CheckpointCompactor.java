package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.snapshot.CheckpointManifest;
import com.atsukimc.anticrash.snapshot.CheckpointStore;
import com.atsukimc.anticrash.snapshot.SnapshotManager;
import com.atsukimc.anticrash.snapshot.logical.LogicalDelta;
import com.atsukimc.anticrash.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Offline compaction of an already authenticated, fully materialized checkpoint view.
 *
 * <p>The current logical-delta materializer writes a complete copy-on-write world for every
 * committed delta.  Compaction therefore does not need to read the live server or replay mutable
 * Minecraft objects: it validates the newest view, copies its immutable payloads, and commits a
 * new root whose parent is null.  It is intentionally run by the checkpoint IO executor.</p>
 */
public final class CheckpointCompactor {
    private CheckpointCompactor() {
    }

    public static Path compact(CheckpointStore store, Path source) throws IOException {
        if (store == null || source == null) throw new IOException("Compaction source is missing");
        store.pin(source.getFileName().toString());
        try { return compactPinned(store, source); }
        finally { store.unpin(source.getFileName().toString()); }
    }

    private static Path compactPinned(CheckpointStore store, Path source) throws IOException {
        CheckpointManifest sourceManifest = store.verifyForIncrementalReuse(source);
        Path temporary = store.createTempDirectory();
        boolean committed = false;
        try {
            copyPayloadTree(source, temporary);
            Files.deleteIfExists(temporary.resolve("logical-delta.json"));
            CheckpointManifest manifest = AtomicFiles.readJson(source.resolve("manifest.json"),
                    CheckpointManifest.class, 16L * 1024 * 1024);
            manifest.checkpointId = temporary.getFileName().toString().replaceFirst("\\.tmp$", "");
            manifest.parentCheckpointId = null;
            // The compacted root is the same epoch as its source. Keep the source on the recovery
            // timeline so a damaged compacted copy can still fall back to the original.
            manifest.timelineParentCheckpointId = sourceManifest.checkpointId;
            // Every world file is now an independent copy, not a hard link to an already fsynced
            // parent file. Clearing these flags makes commit force the new files to disk.
            for (com.atsukimc.anticrash.snapshot.SnapshotFile file : manifest.worldFiles) {
                file.reusedFromParent = false;
                file.destinationMetadataRecorded = false;
                file.storedModifiedTime = 0L;
                file.storedChangeTime = 0L;
                file.storedLinkCount = 0L;
            }
            manifest.baseCheckpointId = null;
            manifest.generationType = "BASE";
            manifest.generationChainDepth = 0;
            manifest.generationSealFormatVersion = 0;
            manifest.logicalCutMonotonicNanos = 0L;
            manifest.logicalCutServerTick = 0L;
            manifest.generationPresentFiles = 0;
            manifest.generationAbsentFiles = 0;
            manifest.generationInheritedFiles = 0;
            manifest.generationSealedFiles = 0;
            manifest.generationTombstoneFiles = 0;
            manifest.fileSet.clear();
            manifest.checkpointEngine = "LOGICAL_DELTA";
            manifest.snapshotBackend = "LOGICAL_DELTA";
            manifest.logicalDeltaFormatVersion = LogicalDelta.CURRENT_FORMAT_VERSION;
            manifest.logicalDeltaEpoch = sourceManifest.serverTick;
            manifest.logicalDeltaChunkCount = 0;
            manifest.logicalDeltaEntityCount = 0;
            manifest.logicalDeltaEntityChunkCount = 0;
            manifest.logicalDeltaTombstoneCount = 0;
            manifest.logicalDeltaPayloadBytes = 0L;
            manifest.logicalDeltaFailureReason = null;
            manifest.logicalDeltaComponentStatus.clear();
            manifest.logicalDeltaComponentStatus.put("COMPACTION", "MATERIALIZED_AUTHENTICATED_VIEW");
            LogicalDelta compactedDelta = new LogicalDelta();
            compactedDelta.captureEpoch = sourceManifest.serverTick;
            compactedDelta.captureKind = "COMPACTION_BASE";
            compactedDelta.exact = true;
            compactedDelta.componentStatus.put("COMPACTION", "MATERIALIZED_AUTHENTICATED_VIEW");
            AtomicFiles.writeJson(temporary.resolve("logical-delta.json"), compactedDelta);
            manifest.utcTimestamp = Instant.now().toString();
            manifest.localTimestamp = ZonedDateTime.now(ZoneId.systemDefault()).toString();
            manifest.monotonicNanos = System.nanoTime();
            manifest.changedFiles = manifest.worldFiles == null ? 0 : manifest.worldFiles.size();
            manifest.reusedFiles = 0;
            manifest.clean = true;
            Path result = store.commit(temporary, manifest);
            committed = true;
            return result;
        } finally {
            if (!committed && Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                SnapshotManager.deleteTree(temporary);
            }
        }
    }

    private static void copyPayloadTree(Path source, Path destination) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path file : stream.sorted().toList()) {
                Path relative = source.relativize(file);
                if (relative.toString().isBlank() || relative.toString().equals("manifest.json")
                        || relative.toString().equals("READY")
                        || relative.toString().equals("checksums.sha256")
                        || relative.toString().equals("logical-delta.json")
                        || relative.toString().equals("generation-seal.json")) continue;
                Path target = destination.resolve(relative).normalize();
                if (!target.startsWith(destination.normalize())) {
                    throw new IOException("Unsafe compaction path: " + relative);
                }
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    if (Files.isSymbolicLink(file)
                            || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Unsafe compaction source: " + file);
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }
}
