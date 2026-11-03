package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class PortableGenerationBackendTest {
    @TempDir Path temp;

    @Test
    void sealsPresentAndDeletedPathsAtCut() throws Exception {
        Path live = temp.resolve("live");
        Path first = temp.resolve("cp-000001/world");
        Path second = temp.resolve("cp-000002/world");
        Files.createDirectories(live.resolve("region"));
        Files.writeString(live.resolve("region/keep.mca"), "keep");
        Files.writeString(live.resolve("region/delete.mca"), "delete");

        PortableGenerationBackend backend = new PortableGenerationBackend();
        SnapshotResult initial = backend.snapshot(live, first, null, Map.of(), Map.of());
        Map<String, SnapshotFile> previous = new HashMap<>();
        for (SnapshotFile file : initial.files) previous.put(file.path, file);

        Files.delete(live.resolve("region/delete.mca"));
        Files.writeString(live.resolve("region/after-cut.mca"), "present-at-cut");
        GenerationSeal seal =
                backend.captureAtLogicalCut(
                        live, second, first, previous, Map.of(), "cp-000001", 100L, 200L);

        assertEquals("PORTABLE_GENERATION", seal.backend);
        assertEquals(2, seal.presentCount);
        assertEquals(1, seal.tombstoneCount);
        assertTrue(
                seal.fileSet.stream()
                        .anyMatch(entry ->
                                "region/delete.mca".equals(entry.path)
                                        && "TOMBSTONE".equals(entry.state)));
        assertTrue(Files.exists(second.resolve("region/keep.mca")));
        assertTrue(Files.exists(second.resolve("region/after-cut.mca")));
        assertFalse(Files.exists(second.resolve("region/delete.mca")));

        // A post-cut file must not enter the already sealed generation.
        Files.writeString(live.resolve("region/post-cut.mca"), "must-not-leak");
        assertFalse(Files.exists(second.resolve("region/post-cut.mca")));
    }

    @Test
    void sealJournalIsPersistedBeforeBackgroundWork() throws Exception {
        Path live = temp.resolve("live-journal");
        Path destination = temp.resolve("generation/world");
        Files.createDirectories(live);
        Files.writeString(live.resolve("level.dat"), "epoch");
        GenerationSeal seal =
                new PortableGenerationBackend()
                        .captureAtLogicalCut(
                                live, destination, null, Map.of(), Map.of(), null, 1L, 2L);
        seal.persist(destination.getParent());

        assertTrue(Files.isRegularFile(destination.getParent().resolve("generation-seal.json")));
        String journal = Files.readString(destination.getParent().resolve("generation-seal.json"));
        assertTrue(journal.contains("logicalCutServerTick"));
        assertTrue(journal.contains("level.dat"));
    }
}
