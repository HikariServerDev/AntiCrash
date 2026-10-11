package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.util.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointStoreTest {
    @Test
    void discoverySkipsLargeWorldArraysWithoutRejectingRealManifestSize() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig(); config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("large-checkpoints"), config,
                new AntiCrashLogger(temp.resolve("large-logs"), config.logging));
        Path directory = temp.resolve("large-checkpoints/cp-000001"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("READY"), "unverified");
        String payload = "{\"checkpointId\":\"cp-000001\",\"utcTimestamp\":\"2026-10-04T00:00:00Z\",\"worldFiles\":["
                + "{\"path\":\"region/r.0.0.mca\",\"sha256\":\"" + "0".repeat(64) + "\"},";
        Files.writeString(directory.resolve("manifest.json"), payload.substring(0, payload.indexOf("[" ) + 1)
                + (payload.substring(payload.indexOf("[") + 1)).repeat(4000)
                + "{}]}");
        assertTrue(Files.size(directory.resolve("manifest.json")) > 256 * 1024);
        CheckpointCandidate candidate = store.discoverLatestCandidate(null);
        assertEquals("cp-000001", candidate.id());
        assertTrue(candidate.manifest().worldFiles.isEmpty());
        assertEquals(CheckpointStore.CandidateState.DISCOVERED, store.candidateState(candidate.id()));
    }

    @Test
    void parentVerificationDeduplicatesAndQuarantinesActualMutationAcrossRestart() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig(); config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("cache-checkpoints"), config,
                new AntiCrashLogger(temp.resolve("cache-logs"), config.logging));
        Path cp = commit(store, "cp-000001", "level.dat", "immutable");
        CheckpointManifest first = store.verifyForIncrementalReuse(cp);
        assertTrue(first == store.verifyForIncrementalReuse(cp));
        assertEquals(CheckpointStore.CandidateState.VALID, store.candidateState("cp-000001"));
        Files.writeString(cp.resolve("world/level.dat"), "tampered!");
        assertThrows(IOException.class, () -> store.verifyForIncrementalReuse(cp));
        assertEquals(CheckpointStore.CandidateState.QUARANTINED, store.candidateState("cp-000001"));
        assertThrows(IOException.class, () -> store.verifyForIncrementalReuse(cp));
        CheckpointStore reopened = new CheckpointStore(temp.resolve("cache-checkpoints"), config,
                new AntiCrashLogger(temp.resolve("cache-logs"), config.logging));
        assertTrue(reopened.isQuarantined("cp-000001"));
        assertTrue(Files.exists(cp.resolve("READY"))); // no repair, no deletion of evidence
    }

    @Test
    void concurrentVerificationPublishesOnlyOneResultAndPinProtectsRetention() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig(); config.logging.console = false;
        config.checkpoint.retentionCount = 1;
        CheckpointStore store = new CheckpointStore(temp.resolve("concurrent-checkpoints"), config,
                new AntiCrashLogger(temp.resolve("concurrent-logs"), config.logging));
        Path cp = commit(store, "cp-000001", "level.dat", "immutable");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> store.verifyForIncrementalReuse(cp));
            var b = pool.submit(() -> store.verifyForIncrementalReuse(cp));
            assertTrue(a.get() == b.get());
        } finally { pool.shutdownNow(); }
        store.pin("cp-000001");
        commit(store, "cp-000002", "level.dat", "new-world");
        assertTrue(Files.exists(cp));
        store.unpin("cp-000001"); store.retain();
        assertFalse(Files.exists(cp));
    }
    @TempDir
    Path temp;

    @Test
    void commitCreatesAtomicReadyCheckpointAndDetectsCorruption() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);
        Path committed = commit(store, "cp-000001", "region/r.0.0.mca", "region");

        assertTrue(Files.exists(committed.resolve("READY")));
        assertFalse(Files.exists(temp.resolve("checkpoints/cp-000001.tmp")));
        assertEquals(1, store.listValid().size());
        store.verify(committed);

        Files.writeString(committed.resolve("world/region/r.0.0.mca"), "corrupted");
        assertThrows(java.io.IOException.class, () -> store.verify(committed));
        assertTrue(store.listValid().isEmpty());
    }

    @Test
    void incompleteDirectoriesAreNeverValid() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);
        Path incomplete = temp.resolve("checkpoints/cp-000777");
        Files.createDirectories(incomplete);
        Files.writeString(incomplete.resolve("manifest.json"), "{}");

        assertEquals(1, store.listComplete().size());
        assertTrue(store.listValid().isEmpty());
    }

    @Test
    void preWorldCandidateDiscoveryReadsOnlyBoundedMetadata() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        Path older = commit(store, "cp-000001", "level.dat", "older");
        Path newer = commit(store, "cp-000002", "level.dat", "newer");

        // Candidate discovery is deliberately not authentication.  A pre-world ASK prompt may
        // inspect metadata while the live World is still unopened, but Y/AUTO must run verify()
        // before this payload can be used for restore.
        Files.writeString(newer.resolve("world/level.dat"), "tampered");

        CheckpointCandidate preferred = store.discoverLatestCandidate(older.getFileName().toString());
        assertEquals("cp-000001", preferred.id());
        assertEquals("cp-000001", preferred.manifest().checkpointId);

        CheckpointCandidate newest = store.discoverLatestCandidate(null);
        assertEquals("cp-000002", newest.id());
        assertEquals("cp-000002", newest.manifest().checkpointId);
    }

    @Test
    void preWorldCandidateDiscoverySkipsMalformedControlMetadata() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        commit(store, "cp-000001", "level.dat", "valid");
        Path malformed = temp.resolve("checkpoints/cp-000002");
        Files.createDirectories(malformed);
        Files.writeString(malformed.resolve("READY"), "not-a-valid-seal");
        Files.writeString(malformed.resolve("manifest.json"), "{}");

        CheckpointCandidate candidate = store.discoverLatestCandidate(null);

        assertEquals("cp-000001", candidate.id());
    }

    @Test
    void retentionKeepsNewestConfiguredNumberAndNeverDeletesNewest() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 2;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);

        for (int i = 0; i < 3; i++) {
            commit(store, String.format("cp-%06d", i + 1), "file-" + i, "payload-" + i);
        }

        assertEquals(2, store.listValid().size());
        assertTrue(Files.isDirectory(temp.resolve("checkpoints/cp-000003")));
        assertFalse(Files.exists(temp.resolve("checkpoints/cp-000001")));
    }

    /**
     * Regression test: retention kept every ancestor of a retained generation. Since generations
     * chain without a periodic BASE, that kept all of them (1,400 on a server configured for 20)
     * and each commit re-read every manifest. A generation is a complete file set by itself.
     */
    @Test
    void retentionRemovesAncestorsOfTheNewestGeneration() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 1;
        config.checkpoint.retentionDays = 0;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        commit(store, "cp-000001", "level.dat", "root", null, 2);
        commit(store, "cp-000002", "level.dat", "delta-1", "cp-000001", 2);
        commit(store, "cp-000003", "level.dat", "delta-2", "cp-000002", 2);

        assertFalse(Files.exists(temp.resolve("checkpoints/cp-000001")));
        assertFalse(Files.exists(temp.resolve("checkpoints/cp-000002")));
        assertTrue(Files.isDirectory(temp.resolve("checkpoints/cp-000003")));
        store.verify(temp.resolve("checkpoints/cp-000003"));
    }

    @Test
    void aRetentionBacklogIsWorkedOffOverSeveralCommits() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 100;
        config.checkpoint.retentionDays = 0;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        String parent = null;
        for (int i = 1; i <= 14; i++) {
            String id = String.format("cp-%06d", i);
            commit(store, id, "level.dat", "payload-" + i, parent, 2);
            parent = id;
        }
        assertEquals(14, store.listComplete().size());

        config.checkpoint.retentionCount = 2;
        commit(store, "cp-000015", "level.dat", "payload-15", parent, 2);
        // 13 are over the limit; one pass removes at most 8 of them, oldest first.
        assertEquals(7, store.listComplete().size());
        assertFalse(Files.exists(temp.resolve("checkpoints/cp-000008")));
        assertTrue(Files.isDirectory(temp.resolve("checkpoints/cp-000009")));

        commit(store, "cp-000016", "level.dat", "payload-16", "cp-000015", 2);
        assertEquals(2, store.listComplete().size());
        assertTrue(Files.isDirectory(temp.resolve("checkpoints/cp-000015")));
        assertTrue(Files.isDirectory(temp.resolve("checkpoints/cp-000016")));
    }

    /**
     * Removing an old generation unlinks inodes the newest one shares and so changes their ctime.
     * The newest generation's record has to follow, or the next generation would hash every
     * unchanged file of the world again.
     */
    @Test
    void retentionKeepsTheNewestGenerationsFileRecordCurrent() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 1;
        config.checkpoint.retentionDays = 0;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        Path first = commit(store, "cp-000001", "region/r.0.0.mca", "unchanged", null, 2);

        Path temporary = store.createTempDirectory();
        Path linked = temporary.resolve("world/region/r.0.0.mca");
        Files.createDirectories(linked.getParent());
        Files.createLink(linked, first.resolve("world/region/r.0.0.mca"));
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");
        CheckpointManifest manifest = new CheckpointManifest();
        manifest.formatVersion = 2;
        manifest.checkpointId = "cp-000002";
        manifest.parentCheckpointId = "cp-000001";
        manifest.snapshotBackend = "JAVA_INCREMENTAL";
        manifest.utcTimestamp = Instant.now().toString();
        FileFacts atLink = FileFacts.read(linked);
        org.junit.jupiter.api.Assumptions.assumeTrue(atLink.changeTime() != 0L && atLink.linkCount() == 2L);
        SnapshotFile file = new SnapshotFile("region/r.0.0.mca", atLink.size(), atLink.modifiedTime(),
                Hashing.sha256(linked));
        file.reusedFromParent = true;
        file.destinationMetadataRecorded = true;
        file.storedModifiedTime = atLink.modifiedTime();
        file.storedChangeTime = atLink.changeTime();
        file.storedLinkCount = atLink.linkCount();
        manifest.totalBytes = atLink.size();
        manifest.worldFiles.add(file);

        Path second = store.commit(temporary, manifest);

        assertFalse(Files.exists(first));
        FileFacts now = FileFacts.read(second.resolve("world/region/r.0.0.mca"));
        assertEquals(1L, now.linkCount());
        assertEquals(now.changeTime(), file.storedChangeTime);
        assertEquals(1L, file.storedLinkCount);
        assertEquals("unchanged", Files.readString(second.resolve("world/region/r.0.0.mca")));
        // The next generation recognises the file from metadata alone, without reading it.
        long hashed = JavaIncrementalBackend.CONTENT_AUTHENTICATIONS.get();
        assertTrue(JavaIncrementalBackend.previousFileMatches(second.resolve("world/region/r.0.0.mca"), now, file));
        assertEquals(hashed, JavaIncrementalBackend.CONTENT_AUTHENTICATIONS.get());
    }

    /** A file that did not match its record before the pass is never blessed by it. */
    @Test
    void retentionDoesNotAcceptAFileThatHadAlreadyDrifted() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 1;
        config.checkpoint.retentionDays = 0;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        commit(store, "cp-000001", "level.dat", "one", null, 2);

        Path temporary = store.createTempDirectory();
        Path world = temporary.resolve("world/level.dat");
        Files.createDirectories(world.getParent());
        Files.writeString(world, "two");
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");
        CheckpointManifest manifest = new CheckpointManifest();
        manifest.formatVersion = 2;
        manifest.checkpointId = "cp-000002";
        manifest.parentCheckpointId = "cp-000001";
        manifest.snapshotBackend = "JAVA_INCREMENTAL";
        manifest.utcTimestamp = Instant.now().toString();
        FileFacts facts = FileFacts.read(world);
        SnapshotFile file = new SnapshotFile("level.dat", facts.size(), facts.modifiedTime(), Hashing.sha256(world));
        file.destinationMetadataRecorded = true;
        file.storedModifiedTime = facts.modifiedTime();
        file.storedChangeTime = facts.changeTime() - 1L;
        manifest.totalBytes = facts.size();
        manifest.worldFiles.add(file);

        store.commit(temporary, manifest);

        assertEquals(facts.changeTime() - 1L, file.storedChangeTime);
    }

    @Test
    void portableGenerationRequiresAnAuthenticatedSealJournal() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        Path temporary = store.createTempDirectory();
        Path worldFile = temporary.resolve("world/level.dat");
        Files.createDirectories(worldFile.getParent());
        Files.writeString(worldFile, "portable");
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");

        SnapshotFile snapshotFile = new SnapshotFile("level.dat", Files.size(worldFile),
                Files.getLastModifiedTime(worldFile).toMillis(), Hashing.sha256(worldFile));
        SnapshotResult snapshot = new SnapshotResult("PORTABLE_GENERATION");
        snapshot.files.add(snapshotFile);
        snapshot.totalBytes = snapshotFile.size;
        snapshot.changedFiles = 1;
        GenerationSeal seal = new GenerationSeal(snapshot, null, 12L, 34L);
        seal.persist(temporary);

        CheckpointManifest manifest = new CheckpointManifest();
        manifest.checkpointId = "cp-000001";
        manifest.snapshotBackend = "PORTABLE_GENERATION";
        manifest.utcTimestamp = Instant.now().toString();
        manifest.runtimeCapsuleVersion = 1;
        manifest.fidelity = "OPTIMIZED";
        manifest.chunkDomainHash = "0".repeat(64);
        manifest.carpetCaptureStatus = "NOT_APPLICABLE";
        manifest.generationSealFormatVersion = seal.formatVersion;
        manifest.logicalCutServerTick = seal.logicalCutServerTick;
        manifest.logicalCutMonotonicNanos = seal.logicalCutMonotonicNanos;
        manifest.generationPresentFiles = seal.presentCount;
        manifest.generationInheritedFiles = seal.inheritedCount;
        manifest.generationSealedFiles = seal.sealedCount;
        manifest.generationTombstoneFiles = seal.tombstoneCount;
        manifest.worldFiles.add(snapshotFile);
        manifest.fileSet.addAll(seal.fileSet);
        manifest.totalBytes = snapshotFile.size;
        manifest.changedFiles = 1;

        Path committed = store.commit(temporary, manifest);
        store.verify(committed);
        Files.delete(committed.resolve("generation-seal.json"));
        assertThrows(IOException.class, () -> store.verify(committed));
    }

    @Test
    void newestValidFallsBackWithoutRequiringEveryOlderCheckpoint() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);

        for (int i = 1; i <= 2; i++) {
            commit(store, String.format("cp-%06d", i), "level.dat", "world-" + i);
        }
        Files.writeString(temp.resolve("checkpoints/cp-000002/world/level.dat"), "corrupt");

        Path newestValid = store.newestValid();

        assertEquals("cp-000001", newestValid.getFileName().toString());
    }

    @Test
    void newestValidStopsBeforeInspectingOlderGenerations() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);

        for (int i = 1; i <= 2; i++) {
            commit(store, String.format("cp-%06d", i), "level.dat", "world-" + i);
        }
        Files.writeString(temp.resolve("checkpoints/cp-000001/world/level.dat"), "older-corrupt");

        Path newestValid = store.newestValid();

        assertEquals("cp-000002", newestValid.getFileName().toString());
        String log = Files.readString(temp.resolve("logs/anticrash.log"));
        assertFalse(log.contains("cp-000001"));
    }

    @Test
    void interruptedVerificationIsCancellationNotCheckpointCorruption() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);
        commit(store, "cp-000001", "level.dat", "world");

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, store::newestValid);
        } finally {
            // Do not leak the deliberate interrupt into the JUnit worker.
            Thread.interrupted();
        }
    }

    @Test
    void manifestCannotOmitOrInventWorldFiles() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);
        Path checkpoint = commit(store, "cp-000001", "level.dat", "world");

        CheckpointManifest manifest = store.readManifest(checkpoint);
        manifest.worldFiles.clear();
        Files.writeString(checkpoint.resolve("manifest.json"), com.atsukimc.anticrash.util.AtomicFiles.GSON.toJson(manifest));

        assertThrows(java.io.IOException.class, () -> store.verify(checkpoint));
    }

    @Test
    void recoveryPayloadVerificationAvoidsSecondWorldHashButRejectsRuntimeCorruption() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        Path checkpoint = commit(store, "cp-000001", "level.dat", "world");

        store.verify(checkpoint);
        // Pre-world restore already authenticated and copied this file. A same-size later change
        // must not trigger another complete-world hash pass during runtime setup.
        Files.writeString(checkpoint.resolve("world/level.dat"), "WORLD");
        store.verifyRecoveryPayloads(checkpoint);

        Files.writeString(checkpoint.resolve("players.json"), "[ ]");
        assertThrows(java.io.IOException.class,
                () -> store.verifyRecoveryPayloads(checkpoint));
    }

    @Test
    void acceptsCurrentRuntimeCapsuleVersionAndRejectsUnknownFutureVersion() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 10;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        Path current = commit(store, "cp-000001", "level.dat", "current",
                null, 2, RuntimeCapsule.CURRENT_FORMAT_VERSION);
        store.verify(current);
        store.verifyRecoveryPayloads(current);

        Path future = commit(store, "cp-000002", "level.dat", "future",
                "cp-000001", 2, RuntimeCapsule.CURRENT_FORMAT_VERSION + 1);
        IOException failure = assertThrows(IOException.class, () -> store.verify(future));
        assertTrue(failure.getMessage().contains("Runtime Capsule version"));
    }

    @Test
    void verifiedLegacyManifestHydratesIncrementalReuseMetadataInMemory() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
        Path checkpoint = commit(store, "cp-000001", "level.dat", "world");

        CheckpointManifest manifest = store.verifyForIncrementalReuse(checkpoint);

        assertTrue(manifest.worldFiles.get(0).destinationMetadataRecorded);
        assertTrue(manifest.worldFiles.get(0).storedModifiedTime >= 0L);
    }

    @Test
    void versionTwoTimelineNeverFallsForwardIntoAnAbandonedBranch() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 10;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        commit(store, "cp-000001", "level.dat", "root", null, 2);
        commit(store, "cp-000002", "level.dat", "old-branch-1", "cp-000001", 2);
        commit(store, "cp-000003", "level.dat", "old-branch-2", "cp-000002", 2);
        commit(store, "cp-000004", "level.dat", "new-branch", "cp-000001", 2);

        assertEquals(List.of("cp-000004", "cp-000001"),
                store.timelineCandidates("cp-000004").stream()
                        .map(path -> path.getFileName().toString()).toList());
    }

    @Test
    void timelineContinuesPastABaseGenerationThatHasNoFileParent() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 10;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        commit(store, "cp-000001", "level.dat", "first", null, 2);
        commit(store, "cp-000002", "level.dat", "delta", "cp-000001", "cp-000001", 4, 1);
        // A periodic BASE reuses no parent files but is still the next step on the timeline.
        commit(store, "cp-000003", "level.dat", "base", null, "cp-000002", 4, 1);

        assertEquals(List.of("cp-000003", "cp-000002", "cp-000001"),
                store.timelineCandidates("cp-000003").stream()
                        .map(path -> path.getFileName().toString()).toList());
    }

    @Test
    void legacyTimelineFallsBackOnlyToLowerCheckpointIds() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.checkpoint.retentionCount = 10;
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        commit(store, "cp-000001", "level.dat", "one", null, 1);
        commit(store, "cp-000002", "level.dat", "two", null, 1);
        commit(store, "cp-000003", "level.dat", "three", null, 1);

        assertEquals(List.of("cp-000002", "cp-000001"),
                store.timelineCandidates("cp-000002").stream()
                        .map(path -> path.getFileName().toString()).toList());
    }

    @Test
    void dottedFileNamesAreNotMistakenForParentTraversal() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        Path checkpoint = commit(store, "cp-000001", "datapacks/my..pack.zip", "pack");

        store.verify(checkpoint);
    }

    private static Path commit(CheckpointStore store, String id, String relativeWorldPath,
                               String payload) throws Exception {
        return commit(store, id, relativeWorldPath, payload, null, 2);
    }

    private static Path commit(CheckpointStore store, String id, String relativeWorldPath,
                               String payload, String parent, int formatVersion) throws Exception {
        return commit(store, id, relativeWorldPath, payload, parent, formatVersion, 1);
    }

    private static Path commit(CheckpointStore store, String id, String relativeWorldPath,
                               String payload, String parent, int formatVersion,
                               int runtimeCapsuleVersion) throws Exception {
        return commit(store, id, relativeWorldPath, payload, parent, null, formatVersion,
                runtimeCapsuleVersion);
    }

    private static Path commit(CheckpointStore store, String id, String relativeWorldPath,
                               String payload, String parent, String timelineParent,
                               int formatVersion, int runtimeCapsuleVersion) throws Exception {
        Path temporary = store.createTempDirectory();
        Path worldFile = temporary.resolve("world").resolve(relativeWorldPath);
        Files.createDirectories(worldFile.getParent());
        Files.writeString(worldFile, payload);
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");

        CheckpointManifest manifest = new CheckpointManifest();
        manifest.formatVersion = formatVersion;
        manifest.runtimeCapsuleVersion = runtimeCapsuleVersion;
        manifest.checkpointId = id;
        manifest.parentCheckpointId = parent;
        manifest.timelineParentCheckpointId = timelineParent;
        manifest.snapshotBackend = "JAVA_INCREMENTAL";
        manifest.utcTimestamp = Instant.now().toString();
        if (runtimeCapsuleVersion >= 6) {
            manifest.fidelity = "OPTIMIZED";
            manifest.chunkDomainHash = "0".repeat(64);
            manifest.carpetCaptureStatus = "NOT_APPLICABLE";
        }
        long size = Files.size(worldFile);
        manifest.totalBytes = size;
        manifest.changedFiles = 1;
        manifest.worldFiles.add(new SnapshotFile(
                relativeWorldPath.replace('\\', '/'),
                size,
                Files.getLastModifiedTime(worldFile).toMillis(),
                Hashing.sha256(worldFile)));
        return store.commit(temporary, manifest);
    }
}
