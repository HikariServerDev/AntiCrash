package com.atsukimc.anticrash.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DetachedGenerationTest {
    @Test
    void verifiedParentRemainsReusableAfterAnotherChildAddsHardlinks() throws Exception {
        createLiveWorld();
        Path parent = temp.resolve("parent/world");
        SnapshotResult first = firstGeneration(parent);
        Path child = temp.resolve("child/world");
        DetachedGeneration.capture(live, child, index(first), Map.of(), null)
                .materialize(parent, index(first), child, false, "PORTABLE_GENERATION");
        // Full content verification would still PASS, yet the recorded ctime is now different.
        Path next = temp.resolve("next/world");
        DetachedGeneration.capture(live, next, index(first), Map.of(), null)
                .materialize(parent, index(first), next, false, "PORTABLE_GENERATION");
        assertEquals("level-1", Files.readString(next.resolve("level.dat")));
    }

    @Test
    void retentionLinkRemovalDoesNotAuthenticateModifiedParentBytes() throws Exception {
        createLiveWorld();
        Path parent = temp.resolve("parent/world");
        SnapshotResult first = firstGeneration(parent);
        Path child = temp.resolve("child/world");
        SnapshotResult second = DetachedGeneration.capture(live, child, index(first), Map.of(), null)
                .materialize(parent, index(first), child, false, "PORTABLE_GENERATION");
        Files.delete(parent.resolve("level.dat"));
        Path corrupt = child.resolve("level.dat");
        FileTime mtime = Files.getLastModifiedTime(corrupt);
        Files.writeString(corrupt, "evil--1"); // same size, restored mtime, lower link count
        Files.setLastModifiedTime(corrupt, mtime);
        Path next = temp.resolve("next/world");
        DetachedGeneration detached = DetachedGeneration.capture(live, next, index(second), Map.of(), null);
        assertThrows(ParentCheckpointChangedException.class,
                () -> detached.materialize(child, index(second), next, false, "PORTABLE_GENERATION"));
    }
    @TempDir
    Path temp;

    private Path live;

    private void createLiveWorld() throws Exception {
        live = temp.resolve("live");
        Files.createDirectories(live.resolve("region"));
        Files.createDirectories(live.resolve("data"));
        Files.createDirectories(live.resolve("playerdata"));
        Files.writeString(live.resolve("level.dat"), "level-1");
        Files.writeString(live.resolve("region/r.0.0.mca"), "region-a-1");
        Files.writeString(live.resolve("region/r.1.0.mca"), "region-b-1");
        Files.writeString(live.resolve("data/raids.dat"), "raids-1");
        Files.writeString(live.resolve("playerdata/player.dat"), "never-captured");
    }

    private static Map<String, SnapshotFile> index(SnapshotResult result) {
        Map<String, SnapshotFile> files = new HashMap<>();
        for (SnapshotFile file : result.files) files.put(file.path, file);
        return files;
    }

    private SnapshotResult firstGeneration(Path destination) throws Exception {
        DetachedGeneration first = DetachedGeneration.capture(live, destination, Map.of(), Map.of(), null);
        assertEquals(4, first.detachedFiles());
        return first.materialize(null, Map.of(), destination, false, "PORTABLE_GENERATION");
    }

    /** Makes a later rewrite visible in mtime even on filesystems with coarse timestamps. */
    private static void rewrite(Path file, String content) throws Exception {
        FileTime before = Files.getLastModifiedTime(file);
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.fromMillis(before.toMillis() + 5_000L));
    }

    @Test
    void laterCaptureCopiesOnlyChangedFilesAndLinksTheRest() throws Exception {
        createLiveWorld();
        Path firstWorld = temp.resolve("cp-000001/world");
        SnapshotResult first = firstGeneration(firstWorld);
        assertEquals(4, first.changedFiles);
        assertFalse(Files.exists(firstWorld.resolve("playerdata/player.dat")));

        rewrite(live.resolve("region/r.0.0.mca"), "region-a-2");
        Path secondWorld = temp.resolve("cp-000002/world");
        DetachedGeneration second = DetachedGeneration.capture(live, secondWorld, index(first), Map.of(), null);
        assertEquals(1, second.detachedFiles());

        // Everything the generation needs was detached at the cut: later live writes stay out.
        rewrite(live.resolve("region/r.0.0.mca"), "after-the-cut");
        SnapshotResult result = second.materialize(firstWorld, index(first), secondWorld, false,
                "PORTABLE_GENERATION");
        assertEquals(1, result.changedFiles);
        assertEquals(3, result.reusedFiles);
        assertEquals("region-a-2", Files.readString(secondWorld.resolve("region/r.0.0.mca")));
        assertEquals("region-b-1", Files.readString(secondWorld.resolve("region/r.1.0.mca")));
        assertTrue(FileFacts.read(secondWorld.resolve("region/r.1.0.mca")).linkCount() >= 2);
        SnapshotFile changed = index(result).get("region/r.0.0.mca");
        assertFalse(changed.reusedFromParent);
        assertEquals(com.atsukimc.anticrash.util.Hashing.sha256("region-a-2".getBytes()), changed.sha256);
    }

    @Test
    void eitherTheTrackerOrTheMetadataIsEnoughToCopyAFile() throws Exception {
        createLiveWorld();
        Path firstWorld = temp.resolve("cp-000001/world");
        SnapshotResult first = firstGeneration(firstWorld);

        // The tracker saw a chunk write that left size/mtime/ctime looking unchanged.
        Set<Path> written = Set.of(live.resolve("region/r.0.0.mca"), live.resolve("region/c.3.4.mcc"));
        // A writer the tracker cannot see (another program) is still caught by the metadata.
        rewrite(live.resolve("region/r.1.0.mca"), "region-b-X");
        rewrite(live.resolve("data/raids.dat"), "raids-2");

        Path secondWorld = temp.resolve("cp-000002/world");
        DetachedGeneration second = DetachedGeneration.capture(live, secondWorld, index(first), Map.of(), written);
        assertTrue(second.tracked());
        assertEquals(4, second.statedFiles());
        assertEquals(3, second.detachedFiles());
        DetachedGeneration.Entry unchanged = second.entries().stream()
                .filter(entry -> entry.path().equals("level.dat")).findFirst().orElseThrow();
        assertEquals(DetachedGeneration.Kind.INHERITED, unchanged.kind());

        SnapshotResult result = second.materialize(firstWorld, index(first), secondWorld, false,
                "PORTABLE_GENERATION");
        assertEquals("region-b-X", Files.readString(secondWorld.resolve("region/r.1.0.mca")));
        assertEquals("raids-2", Files.readString(secondWorld.resolve("data/raids.dat")));
        assertEquals("region-a-1", Files.readString(secondWorld.resolve("region/r.0.0.mca")));
        // Copied because the tracker said so, then linked because the bytes proved equal.
        assertTrue(index(result).get("region/r.0.0.mca").reusedFromParent);
        assertFalse(Files.exists(secondWorld.resolve("region/c.3.4.mcc")));
        assertEquals(4, result.files.size());
    }

    @Test
    void rewrittenButIdenticalFileIsLinkedToTheParent() throws Exception {
        createLiveWorld();
        Path firstWorld = temp.resolve("cp-000001/world");
        SnapshotResult first = firstGeneration(firstWorld);
        rewrite(live.resolve("data/raids.dat"), "raids-1");

        Path secondWorld = temp.resolve("cp-000002/world");
        DetachedGeneration second = DetachedGeneration.capture(live, secondWorld, index(first), Map.of(), null);
        assertEquals(1, second.detachedFiles());
        SnapshotResult result = second.materialize(firstWorld, index(first), secondWorld, false,
                "PORTABLE_GENERATION");
        assertEquals(0, result.changedFiles);
        assertTrue(index(result).get("data/raids.dat").reusedFromParent);
    }

    @Test
    void baseGenerationCopiesInheritedFilesInsteadOfSharingInodes() throws Exception {
        createLiveWorld();
        Path firstWorld = temp.resolve("cp-000001/world");
        SnapshotResult first = firstGeneration(firstWorld);

        Path baseWorld = temp.resolve("cp-000002/world");
        DetachedGeneration base = DetachedGeneration.capture(live, baseWorld, index(first), Map.of(), null);
        assertEquals(0, base.detachedFiles());
        SnapshotResult result = base.materialize(firstWorld, index(first), baseWorld, true,
                "PORTABLE_GENERATION");
        assertEquals(4, result.changedFiles);
        assertEquals(0, result.reusedFiles);
        for (SnapshotFile file : result.files) {
            assertFalse(file.reusedFromParent);
            assertEquals(1L, FileFacts.read(baseWorld.resolve(file.path)).linkCount());
        }
    }

    @Test
    void modifiedParentFileIsReportedSoTheParentIsChosenAgain() throws Exception {
        createLiveWorld();
        Path firstWorld = temp.resolve("cp-000001/world");
        SnapshotResult first = firstGeneration(firstWorld);
        rewrite(firstWorld.resolve("region/r.1.0.mca"), "tampered!!");

        Path secondWorld = temp.resolve("cp-000002/world");
        DetachedGeneration second = DetachedGeneration.capture(live, secondWorld, index(first), Map.of(), null);
        assertThrows(ParentCheckpointChangedException.class, () -> second.materialize(firstWorld,
                index(first), secondWorld, false, "PORTABLE_GENERATION"));
    }

    @Test
    void sealRecordsTrackedDeletionsAsTombstones() throws Exception {
        createLiveWorld();
        Files.writeString(live.resolve("region/c.1.2.mcc"), "oversized");
        Path firstWorld = temp.resolve("cp-000001/world");
        DetachedGeneration initial = DetachedGeneration.capture(live, firstWorld, Map.of(), Map.of(), null);
        SnapshotResult first = initial.materialize(null, Map.of(), firstWorld, false, "PORTABLE_GENERATION");

        Files.delete(live.resolve("region/c.1.2.mcc"));
        Path secondWorld = temp.resolve("cp-000002/world");
        DetachedGeneration second = DetachedGeneration.capture(live, secondWorld, index(first), Map.of(),
                Set.of(live.resolve("region/c.1.2.mcc")));
        SnapshotResult result = second.materialize(firstWorld, index(first), secondWorld, false,
                "PORTABLE_GENERATION");
        GenerationSeal seal = new GenerationSeal(result, "cp-000001", 1L, 2L);
        seal.addTombstones(index(first));
        assertEquals(1, seal.tombstoneCount);
        assertFalse(Files.exists(secondWorld.resolve("region/c.1.2.mcc")));
    }

    @Test
    void regionStorageFilesAreRecognizedOnlyInsideStorageDirectories() {
        assertTrue(LiveFileTracker.isRegionStorageFile("region/r.0.0.mca"));
        assertTrue(LiveFileTracker.isRegionStorageFile("DIM-1/entities/r.-1.2.mca"));
        assertTrue(LiveFileTracker.isRegionStorageFile("dimensions/ns/sky/poi/c.10.-3.mcc"));
        assertFalse(LiveFileTracker.isRegionStorageFile("r.0.0.mca"));
        assertFalse(LiveFileTracker.isRegionStorageFile("data/r.0.0.mca"));
        assertFalse(LiveFileTracker.isRegionStorageFile("region/r.0.0.mca.bak"));
        assertFalse(LiveFileTracker.isRegionStorageFile("region/level.dat"));
    }
}
