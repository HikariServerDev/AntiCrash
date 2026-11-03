package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

class JavaIncrementalBackendTest {
    @TempDir Path temp;

    @Test
    void reusesUnchangedFilesOnlyBetweenCheckpoints() throws Exception {
        Path live = temp.resolve("live");
        Path first = temp.resolve("cp-000001/world");
        Path second = temp.resolve("cp-000002/world");
        Files.createDirectories(live.resolve("region"));
        Files.createDirectories(live.resolve("playerdata"));
        Files.writeString(live.resolve("region/unchanged.mca"), "same");
        Files.writeString(live.resolve("region/changed.mca"), "before");
        Files.writeString(live.resolve("playerdata/should-not-copy.dat"), "excluded");
        Files.writeString(live.resolve("session.lock"), "excluded");

        JavaIncrementalBackend backend = new JavaIncrementalBackend();
        SnapshotResult firstResult = backend.snapshot(live, first, null, MapSupport.empty());
        HashMap<String, SnapshotFile> index = new HashMap<>();
        for (SnapshotFile file : firstResult.files) index.put(file.path, file);

        Files.writeString(live.resolve("region/changed.mca"), "after");
        SnapshotResult secondResult = backend.snapshot(live, second, first, index);

        assertEquals(1, secondResult.reusedFiles);
        assertEquals(1, secondResult.changedFiles);
        assertTrue(
                Files.isSameFile(
                        first.resolve("region/unchanged.mca"),
                        second.resolve("region/unchanged.mca")));
        assertFalse(
                Files.isSameFile(
                        first.resolve("region/changed.mca"), second.resolve("region/changed.mca")));
        assertFalse(Files.exists(second.resolve("playerdata/should-not-copy.dat")));
        assertFalse(Files.exists(second.resolve("session.lock")));
    }

    @Test
    void rejectsSymlinkedWorldFiles() throws Exception {
        Path live = temp.resolve("symlink-live");
        Files.createDirectories(live);
        Path outside = temp.resolve("outside");
        Files.writeString(outside, "do not follow");
        try {
            Files.createSymbolicLink(live.resolve("link"), outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        JavaIncrementalBackend backend = new JavaIncrementalBackend();
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () ->
                        backend.snapshot(
                                live, temp.resolve("destination"), null, MapSupport.empty()));
    }

    @Test
    void oldManifestWithoutChangeTimeUsesOneSafeHashMigration() throws Exception {
        Path live = temp.resolve("old-manifest-live");
        Path first = temp.resolve("old-manifest-first");
        Path second = temp.resolve("old-manifest-second");
        Files.createDirectories(live);
        Files.writeString(live.resolve("level.dat"), "same");
        JavaIncrementalBackend backend = new JavaIncrementalBackend();
        SnapshotResult initial = backend.snapshot(live, first, null, MapSupport.empty());
        SnapshotFile oldManifestEntry = initial.files.get(0);
        oldManifestEntry.changeTime = 0L;

        SnapshotResult next =
                backend.snapshot(
                        live,
                        second,
                        first,
                        java.util.Map.of(oldManifestEntry.path, oldManifestEntry));

        assertEquals(1, next.reusedFiles);
        assertEquals(0, next.changedFiles);
        assertTrue(Files.isSameFile(first.resolve("level.dat"), second.resolve("level.dat")));
    }

    @Test
    void corruptedPreviousCheckpointIsNeverReusedWithoutVerification() throws Exception {
        Path live = temp.resolve("live-corruption");
        Path first = temp.resolve("first-corruption");
        Path second = temp.resolve("second-corruption");
        Files.createDirectories(live);
        Files.writeString(live.resolve("level.dat"), "safe-live-state");
        JavaIncrementalBackend backend = new JavaIncrementalBackend();
        SnapshotResult initial = backend.snapshot(live, first, null, MapSupport.empty());
        SnapshotFile metadata = initial.files.get(0);
        Files.writeString(first.resolve("level.dat"), "corrupted-old");

        SnapshotResult next =
                backend.snapshot(live, second, first, java.util.Map.of(metadata.path, metadata));

        assertEquals(0, next.reusedFiles);
        assertEquals(1, next.changedFiles);
        assertEquals("safe-live-state", Files.readString(second.resolve("level.dat")));
        assertFalse(Files.isSameFile(first.resolve("level.dat"), second.resolve("level.dat")));
    }

    @Test
    void retentionHardlinkRemovalDoesNotDisableFastReuse() throws Exception {
        Path live = temp.resolve("retention-live");
        Path first = temp.resolve("retention-first");
        Path second = temp.resolve("retention-second");
        Path third = temp.resolve("retention-third");
        Files.createDirectories(live);
        Files.writeString(live.resolve("level.dat"), "same");

        JavaIncrementalBackend backend = new JavaIncrementalBackend();
        SnapshotResult initial = backend.snapshot(live, first, null, MapSupport.empty());
        SnapshotResult retained =
                backend.snapshot(
                        live,
                        second,
                        first,
                        java.util.Map.of(initial.files.get(0).path, initial.files.get(0)));

        // This models retention deleting the old checkpoint after the new one was linked.
        Files.delete(first.resolve("level.dat"));
        Files.delete(first);

        SnapshotResult next =
                backend.snapshot(
                        live,
                        third,
                        second,
                        java.util.Map.of(retained.files.get(0).path, retained.files.get(0)));

        assertEquals(1, next.reusedFiles);
        assertEquals(0, next.changedFiles);
        assertTrue(Files.isSameFile(second.resolve("level.dat"), third.resolve("level.dat")));
    }

    private static final class MapSupport {
        private static java.util.Map<String, SnapshotFile> empty() {
            return java.util.Map.of();
        }
    }
}
