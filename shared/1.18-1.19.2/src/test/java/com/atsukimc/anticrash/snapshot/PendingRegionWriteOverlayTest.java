package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PendingRegionWriteOverlayTest {
    @TempDir Path temp;

    @AfterEach
    void clearSealed() {
        SealedNbt.clear();
    }

    private static NbtCompound chunk(String marker) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("marker", marker);
        nbt.putLongArray("data", new long[] {1L, 2L, 3L});
        return nbt;
    }

    private static void write(Path region, ChunkPos pos, NbtCompound nbt) throws IOException {
        Files.createDirectories(region.getParent());
        RegionFile file = new RegionFile(region, region.getParent(), false);
        try (DataOutputStream output = file.getChunkOutputStream(pos)) {
            NbtIo.write(nbt, output);
        } finally {
            file.close();
        }
    }

    private static NbtCompound read(Path region, ChunkPos pos) throws IOException {
        RegionFile file = new RegionFile(region, region.getParent(), false);
        try (DataInputStream input = file.getChunkInputStream(pos)) {
            return input == null ? null : NbtIo.read(input);
        } finally {
            file.close();
        }
    }

    private static Map<String, SnapshotFile> index(SnapshotResult result) {
        Map<String, SnapshotFile> files = new HashMap<>();
        for (SnapshotFile file : result.files) files.put(file.path, file);
        return files;
    }

    @Test
    void queuedWritesReachTheCopyButNeverTheLiveFile() throws Exception {
        Path live = temp.resolve("live");
        Path region = live.resolve("region/r.0.0.mca");
        ChunkPos kept = new ChunkPos(0, 0);
        ChunkPos deleted = new ChunkPos(1, 0);
        ChunkPos queued = new ChunkPos(2, 0);
        write(region, kept, chunk("kept"));
        write(region, deleted, chunk("deleted"));
        Files.writeString(live.resolve("level.dat"), "level");

        Path firstWorld = temp.resolve("cp-000001/world");
        DetachedGeneration first =
                DetachedGeneration.capture(live, firstWorld, Map.of(), Map.of(), null);
        SnapshotResult parent =
                first.materialize(null, Map.of(), firstWorld, false, "PORTABLE_GENERATION");
        byte[] liveBefore = Files.readAllBytes(region);

        Path directory = live.resolve("region").toAbsolutePath();
        NbtCompound queuedChunk = chunk("queued");
        List<PendingRegionWrite> pending =
                List.of(
                        new PendingRegionWrite(directory, queued, queuedChunk),
                        new PendingRegionWrite(directory, deleted, null),
                        new PendingRegionWrite(
                                directory, new ChunkPos(40, 0), chunk("new-region")));
        Path secondWorld = temp.resolve("cp-000002/world");
        // The live region file is unchanged since the parent: only the queued writes force its
        // copy.
        DetachedGeneration second =
                DetachedGeneration.capture(
                        live, secondWorld, index(parent), Map.of(), null, pending);
        assertEquals(1, second.detachedFiles());
        assertEquals(3, second.pendingWriteCount());
        assertTrue(SealedNbt.isSealed(queuedChunk));

        SnapshotResult result =
                second.materialize(
                        firstWorld, index(parent), secondWorld, false, "PORTABLE_GENERATION");

        Path copied = secondWorld.resolve("region/r.0.0.mca");
        assertEquals("kept", read(copied, kept).getString("marker"));
        assertEquals("queued", read(copied, queued).getString("marker"));
        assertNull(read(copied, deleted));
        assertEquals(
                "new-region",
                read(secondWorld.resolve("region/r.1.0.mca"), new ChunkPos(40, 0))
                        .getString("marker"));
        assertTrue(java.util.Arrays.equals(liveBefore, Files.readAllBytes(region)));
        assertFalse(Files.exists(live.resolve("region/r.1.0.mca")));

        Map<String, SnapshotFile> files = index(result);
        assertEquals(3, files.size());
        SnapshotFile copiedFile = files.get("region/r.0.0.mca");
        assertNotNull(copiedFile);
        assertFalse(copiedFile.reusedFromParent);
        assertEquals(Files.size(copied), copiedFile.size);
        // No trustworthy live metadata exists for a file the cut rewrote; the next cut hashes it.
        assertEquals(0L, copiedFile.changeTime);
        assertNotNull(files.get("region/r.1.0.mca"));
        assertFalse(SealedNbt.isSealed(queuedChunk));
    }

    @Test
    void chunkDataModifiedWhileQueuedFailsTheCheckpointInsteadOfBeingSaved() throws Exception {
        Path live = temp.resolve("live");
        Files.createDirectories(live.resolve("region"));
        Files.writeString(live.resolve("level.dat"), "level");
        NbtCompound touched = chunk("touched");
        touched.putString("__context", "partially updated");
        List<PendingRegionWrite> pending =
                List.of(
                        new PendingRegionWrite(
                                live.resolve("region").toAbsolutePath(),
                                new ChunkPos(0, 0),
                                touched));
        Path world = temp.resolve("cp-000001/world");
        DetachedGeneration generation =
                DetachedGeneration.capture(live, world, Map.of(), Map.of(), null, pending);
        assertThrows(
                IOException.class,
                () -> generation.materialize(null, Map.of(), world, false, "PORTABLE_GENERATION"));
        assertFalse(SealedNbt.isSealed(touched));
    }

    @Test
    void discardedGenerationReleasesItsQueuedData() throws Exception {
        Path live = temp.resolve("live");
        Files.createDirectories(live.resolve("region"));
        NbtCompound queued = chunk("queued");
        DetachedGeneration generation =
                DetachedGeneration.capture(
                        live,
                        temp.resolve("cp/world"),
                        Map.of(),
                        Map.of(),
                        null,
                        List.of(
                                new PendingRegionWrite(
                                        live.resolve("region").toAbsolutePath(),
                                        new ChunkPos(0, 0),
                                        queued)));
        assertTrue(SealedNbt.isSealed(queued));
        generation.discard();
        assertFalse(SealedNbt.isSealed(queued));
        assertEquals(0, generation.pendingWriteCount());
    }
}
