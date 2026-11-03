package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.nbt.CompoundTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.CompoundTag;
//?}

import net.minecraft.nbt.NbtIo;
//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.ChunkPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.ChunkPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.chunk.storage.RegionFile;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.chunk.storage.RegionFile;
//?}

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

    //? if (>=1.18 && <=1.21.11) {
    /*private static CompoundTag chunk(String marker)
                *///?} else if (>=26.1 && <=26.3) {
    private static CompoundTag chunk(String marker)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*CompoundTag nbt = new CompoundTag();
        *///?} else if (>=26.1 && <=26.3) {
        CompoundTag nbt = new CompoundTag();
        //?}

        nbt.putString("marker", marker);
        nbt.putLongArray("data", new long[] {1L, 2L, 3L});
        return nbt;
    }

    //? if (>=1.18 && <=1.21.11) {
    /*private static void write(Path region, ChunkPos pos, CompoundTag nbt) throws IOException
                *///?} else if (>=26.1 && <=26.3) {
    private static void write(Path region, ChunkPos pos, CompoundTag nbt) throws IOException
                //?}
            {
        Files.createDirectories(region.getParent());

        //? if (>=1.18 && <=1.20.4) {
        /*RegionFile file = new RegionFile(region, region.getParent(), false);
        *///?} else if (>=1.20.5 && <=26.3) {
        RegionFile file =
                new RegionFile(
                        com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                        region,
                        region.getParent(),
                        false);
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*try (DataOutputStream output = file.getChunkDataOutputStream(pos))
        *///?} else if (>=26.1 && <=26.3) {
        try (DataOutputStream output = file.getChunkDataOutputStream(pos))
        //?}
        {

            //? if (>=1.18 && <=1.21.11) {
            /*//? if (>=1.18 && <=1.20.1) {
            /^NbtIo.write(nbt, output);
            ^///?} else if 1.20.2 {
            /^NbtIo.writeUnnamedTag(nbt, output);
            ^///?} else if (>=1.20.3 && <=1.21.11) {
            /^NbtIo.writeUnnamedTagWithFallback(nbt, output);
            ^///?}
            *///?} else if (>=26.1 && <=26.3) {
            NbtIo.writeUnnamedTagWithFallback(nbt, output);
            //?}

        } finally {
            file.close();
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    /*private static CompoundTag read(Path region, ChunkPos pos) throws IOException
                *///?} else if (>=26.1 && <=26.3) {
    private static CompoundTag read(Path region, ChunkPos pos) throws IOException
                //?}
            {

        //? if (>=1.18 && <=1.20.4) {
        /*RegionFile file = new RegionFile(region, region.getParent(), false);
        *///?} else if (>=1.20.5 && <=26.3) {
        RegionFile file =
                new RegionFile(
                        com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                        region,
                        region.getParent(),
                        false);
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*try (DataInputStream input = file.getChunkDataInputStream(pos))
        *///?} else if (>=26.1 && <=26.3) {
        try (DataInputStream input = file.getChunkDataInputStream(pos))
        //?}
        {

            //? if (>=1.18 && <=1.20.1) {
            /*return input == null ? null : NbtIo.read(input);
            *///?} else if (>=1.20.2 && <=1.20.3) {
            /*return input == null
                                ? null
                                : NbtIo.read(
                                        input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
*///?} else if (>=1.20.4 && <=1.21.11) {
            /*return input == null
                                ? null
                                : NbtIo.read(
                                        input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
*///?} else if (>=26.1 && <=26.3) {
            return input == null
                    ? null
                    : NbtIo.read(input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
            //?}

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

        //? if (>=1.18 && <=1.21.11) {
        /*CompoundTag queuedChunk = chunk("queued");
        *///?} else if (>=26.1 && <=26.3) {
        CompoundTag queuedChunk = chunk("queued");
        //?}

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

        //? if (>=1.18 && <=1.21.4) {
        /*assertEquals("kept", read(copied, kept).getString("marker"));
        *///?} else if (>=1.21.5 && <=26.3) {
        assertEquals("kept", read(copied, kept).getString("marker").orElseThrow());
        //?}

        //? if (>=1.18 && <=1.21.4) {
        /*assertEquals("queued", read(copied, queued).getString("marker"));
        *///?} else if (>=1.21.5 && <=26.3) {
        assertEquals("queued", read(copied, queued).getString("marker").orElseThrow());
        //?}

        assertNull(read(copied, deleted));

        //? if (>=1.18 && <=1.21.4) {
        /*assertEquals(
                "new-region",
                read(secondWorld.resolve("region/r.1.0.mca"), new ChunkPos(40, 0))
                        .getString("marker"));
        *///?} else if (>=1.21.5 && <=26.3) {
        assertEquals(
                "new-region",
                read(secondWorld.resolve("region/r.1.0.mca"), new ChunkPos(40, 0))
                        .getString("marker")
                        .orElseThrow());
        //?}

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

        //? if (>=1.18 && <=1.21.11) {
        /*CompoundTag touched = chunk("touched");
        *///?} else if (>=26.1 && <=26.3) {
        CompoundTag touched = chunk("touched");
        //?}

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

        //? if (>=1.18 && <=1.21.11) {
        /*CompoundTag queued = chunk("queued");
        *///?} else if (>=26.1 && <=26.3) {
        CompoundTag queued = chunk("queued");
        //?}

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
