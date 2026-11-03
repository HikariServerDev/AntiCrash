package com.atsukimc.anticrash.snapshot.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.atsukimc.anticrash.player.NbtCodec;
import com.atsukimc.anticrash.snapshot.JavaIncrementalBackend;
import com.atsukimc.anticrash.snapshot.SnapshotFile;
import com.atsukimc.anticrash.snapshot.SnapshotResult;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.CompoundTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.ListTag;
*///?}

import net.minecraft.nbt.NbtIo;
//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.ListTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.ChunkPos;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.chunk.storage.RegionFile;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.RegionFile;
*///?}

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class LogicalDeltaMaterializerTest {
    @TempDir Path temp;

    @Test
    void appliesChangedRegionAndEntityTombstoneWithCopyOnWrite() throws Exception {
        Path live = temp.resolve("live");
        writeRegion(live, "region", new ChunkPos(0, 0), marker("before"));

        UUID removed = UUID.fromString("00000000-0000-0000-0000-000000000001");

        //? if (>=1.18 && <=1.21.11) {
        CompoundTag entities = new CompoundTag();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag entities = new CompoundTag();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        ListTag list = new ListTag();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*ListTag list = new ListTag();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        CompoundTag entity = new CompoundTag();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag entity = new CompoundTag();
*///?}

        //? if (>=1.18 && <=1.21.4) {
        entity.putUUID("UUID", removed);
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*entity.store("UUID", net.minecraft.core.UUIDUtil.CODEC, removed);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*entity.store("UUID", net.minecraft.core.UUIDUtil.CODEC, removed);
*///?}

        list.add(entity);
        entities.put("Entities", list);
        writeRegion(live, "entities", new ChunkPos(0, 0), entities);

        Path parentWorld = temp.resolve("parent");
        SnapshotResult parentSnapshot =
                new JavaIncrementalBackend(() -> 1).snapshot(live, parentWorld, null, Map.of());
        Map<String, SnapshotFile> parentFiles = new HashMap<>();
        for (SnapshotFile file : parentSnapshot.files) parentFiles.put(file.path, file);

        LogicalDelta delta = new LogicalDelta();
        delta.exact = true;
        LogicalChunkDelta changed = new LogicalChunkDelta();
        changed.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        changed.packedPosition = new ChunkPos(0, 0).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*changed.packedPosition = new ChunkPos(0, 0).pack();
*///?}

        changed.payloadBase64 = NbtCodec.encode(marker("after"));
        changed.sha256 = "unused-in-test";
        delta.addChunk(changed);

        //? if (>=1.18 && <=1.21.11) {
        delta.addTombstone(
                new LogicalTombstone(
                        "ENTITY_REMOVED",
                        "minecraft:overworld",
                        removed.toString(),
                        1L,
                        "test",
                        new ChunkPos(0, 0).toLong()));
        //?}
        //? if (>=26.1 && <=26.3) {
        /*delta.addTombstone(
                        new LogicalTombstone(
                                "ENTITY_REMOVED",
                                "minecraft:overworld",
                                removed.toString(),
                                1L,
                                "test",
                                new ChunkPos(0, 0).pack()));
*///?}

        Path destination = temp.resolve("materialized");
        LogicalDeltaMaterializer.materialize(parentWorld, destination, parentFiles, delta);

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                "before",
                readRegion(parentWorld, "region", new ChunkPos(0, 0)).getString("marker"));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        "before",
                        readRegion(parentWorld, "region", new ChunkPos(0, 0))
                                .getString("marker")
                                .orElseThrow());
*///?}

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                "after", readRegion(destination, "region", new ChunkPos(0, 0)).getString("marker"));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        "after",
                        readRegion(destination, "region", new ChunkPos(0, 0))
                                .getString("marker")
                                .orElseThrow());
*///?}

        assertNotSame(
                parentWorld.resolve("region/r.0.0.mca").toRealPath(),
                destination.resolve("region/r.0.0.mca").toRealPath());

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                0,
                readRegion(destination, "entities", new ChunkPos(0, 0))
                        .getList("Entities", 10)
                        .size());
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        0,
                        readRegion(destination, "entities", new ChunkPos(0, 0))
                                .getListOrEmpty("Entities")
                                .size());
*///?}

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                1,
                readRegion(parentWorld, "entities", new ChunkPos(0, 0))
                        .getList("Entities", 10)
                        .size());
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        1,
                        readRegion(parentWorld, "entities", new ChunkPos(0, 0))
                                .getListOrEmpty("Entities")
                                .size());
*///?}

    }

    @Test
    void emptyEntityChunkDeletesParentDataAndTombstoneNeverEditsAReplacedChunk() throws Exception {
        Path live = temp.resolve("live2");
        UUID returned = UUID.fromString("00000000-0000-0000-0000-000000000002");
        writeRegion(live, "entities", new ChunkPos(0, 0), entityChunk(UUID.randomUUID()));
        writeRegion(live, "entities", new ChunkPos(1, 0), entityChunk(returned));

        Path parentWorld = temp.resolve("parent2");
        SnapshotResult parentSnapshot =
                new JavaIncrementalBackend(() -> 1).snapshot(live, parentWorld, null, Map.of());
        Map<String, SnapshotFile> parentFiles = new HashMap<>();
        for (SnapshotFile file : parentSnapshot.files) parentFiles.put(file.path, file);

        LogicalDelta delta = new LogicalDelta();
        delta.exact = true;
        LogicalEntityChunkDelta emptied = new LogicalEntityChunkDelta();
        emptied.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        emptied.packedPosition = new ChunkPos(0, 0).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*emptied.packedPosition = new ChunkPos(0, 0).pack();
*///?}

        emptied.payloadBase64 = NbtCodec.encode(entityChunk());
        delta.addEntityChunk(emptied);
        // The entity left chunk (1,0) and came back before the cut: the replacement list is
        // authoritative and the stale move tombstone must not delete it again.
        LogicalEntityChunkDelta rewritten = new LogicalEntityChunkDelta();
        rewritten.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        rewritten.packedPosition = new ChunkPos(1, 0).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*rewritten.packedPosition = new ChunkPos(1, 0).pack();
*///?}

        rewritten.payloadBase64 = NbtCodec.encode(entityChunk(returned));
        delta.addEntityChunk(rewritten);

        //? if (>=1.18 && <=1.21.11) {
        delta.addTombstone(
                new LogicalTombstone(
                        "ENTITY_MOVED_FROM",
                        "minecraft:overworld",
                        returned.toString(),
                        1L,
                        "test",
                        new ChunkPos(1, 0).toLong()));
        //?}
        //? if (>=26.1 && <=26.3) {
        /*delta.addTombstone(
                        new LogicalTombstone(
                                "ENTITY_MOVED_FROM",
                                "minecraft:overworld",
                                returned.toString(),
                                1L,
                                "test",
                                new ChunkPos(1, 0).pack()));
*///?}

        Path destination = temp.resolve("materialized2");
        LogicalDeltaMaterializer.materialize(parentWorld, destination, parentFiles, delta);

        Path directory = destination.resolve("entities");

        //? if (>=1.18 && <=1.20.4) {
        try (RegionFile region = new RegionFile(directory.resolve("r.0.0.mca"), directory, false))
        //?}
        //? if (>=1.20.5 && <=26.3) {
        /*try (RegionFile region =
                        new RegionFile(
                                com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                                directory.resolve("r.0.0.mca"),
                                directory,
                                false))
*///?}
        {
            assertEquals(false, region.hasChunk(new ChunkPos(0, 0)));
        }

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                1,
                readRegion(destination, "entities", new ChunkPos(1, 0))
                        .getList("Entities", 10)
                        .size());
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        1,
                        readRegion(destination, "entities", new ChunkPos(1, 0))
                                .getListOrEmpty("Entities")
                                .size());
*///?}

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                1,
                readRegion(parentWorld, "entities", new ChunkPos(0, 0))
                        .getList("Entities", 10)
                        .size());
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        1,
                        readRegion(parentWorld, "entities", new ChunkPos(0, 0))
                                .getListOrEmpty("Entities")
                                .size());
*///?}

    }

    //? if (>=1.18 && <=1.21.11) {
    private static CompoundTag entityChunk(UUID... uuids)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static CompoundTag entityChunk(UUID... uuids)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        CompoundTag root = new CompoundTag();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag root = new CompoundTag();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        ListTag list = new ListTag();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*ListTag list = new ListTag();
*///?}

        for (UUID uuid : uuids) {

            //? if (>=1.18 && <=1.21.11) {
            CompoundTag entity = new CompoundTag();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*CompoundTag entity = new CompoundTag();
*///?}

            entity.putString("id", "minecraft:armor_stand");

            //? if (>=1.18 && <=1.21.4) {
            entity.putUUID("UUID", uuid);
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*entity.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*entity.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
*///?}

            list.add(entity);
        }
        root.put("Entities", list);
        return root;
    }

    //? if (>=1.18 && <=1.21.11) {
    private static CompoundTag marker(String value)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static CompoundTag marker(String value)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        CompoundTag root = new CompoundTag();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag root = new CompoundTag();
*///?}

        root.putString("marker", value);
        return root;
    }

    //? if (>=1.18 && <=1.21.11) {
    private static void writeRegion(
            Path world, String directoryName, ChunkPos pos, CompoundTag root) throws Exception
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static void writeRegion(
                            Path world, String directoryName, ChunkPos pos, CompoundTag root) throws Exception
*///?}
            {
        Path directory = world.resolve(directoryName);
        Files.createDirectories(directory);

        //? if (>=1.18 && <=1.21.11) {
        Path file = directory.resolve("r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca");
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Path file = directory.resolve("r." + (pos.x() >> 5) + "." + (pos.z() >> 5) + ".mca");
*///?}

        //? if (>=1.18 && <=1.20.4) {
        try (RegionFile region = new RegionFile(file, directory, false))
        //?}
        //? if (>=1.20.5 && <=26.3) {
        /*try (RegionFile region =
                        new RegionFile(
                                com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                                file,
                                directory,
                                false))
*///?}
        {

            //? if (>=1.18 && <=1.21.11) {
            try (DataOutputStream output = region.getChunkDataOutputStream(pos))
            //?}
            //? if (>=26.1 && <=26.3) {
            /*try (DataOutputStream output = region.getChunkDataOutputStream(pos))
*///?}
            {

                //? if (>=1.18 && <=1.21.11) {
                //? if (>=1.18 && <=1.20.1) {
                NbtIo.write(root, output);
                //?}
                //? if 1.20.2 {
                /*NbtIo.writeUnnamedTag(root, output);
                *///?}
                //? if (>=1.20.3 && <=1.21.11) {
                /*NbtIo.writeUnnamedTagWithFallback(root, output);
                *///?}
                //?}
                //? if (>=26.1 && <=26.3) {
                /*NbtIo.writeUnnamedTagWithFallback(root, output);
*///?}

            }

            //? if (>=1.18 && <=1.21.11) {
            region.flush();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*region.flush();
*///?}

        }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static CompoundTag readRegion(Path world, String directoryName, ChunkPos pos)
            throws Exception
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static CompoundTag readRegion(Path world, String directoryName, ChunkPos pos)
                            throws Exception
*///?}
            {
        Path directory = world.resolve(directoryName);

        //? if (>=1.18 && <=1.21.11) {
        Path file = directory.resolve("r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca");
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Path file = directory.resolve("r." + (pos.x() >> 5) + "." + (pos.z() >> 5) + ".mca");
*///?}

        //? if (>=1.18 && <=1.20.4) {
        try (RegionFile region = new RegionFile(file, directory, false);
                var input = region.getChunkDataInputStream(pos))
        //?}
        //? if (>=1.20.5 && <=1.21.11) {
        /*try (RegionFile region =
                                new RegionFile(
                                        com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                                        file,
                                        directory,
                                        false);
                        var input = region.getChunkDataInputStream(pos))
*///?}
        //? if (>=26.1 && <=26.3) {
        /*try (RegionFile region =
                                new RegionFile(
                                        com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                                        file,
                                        directory,
                                        false);
                        var input = region.getChunkDataInputStream(pos))
*///?}
        {

            //? if (>=1.18 && <=1.20.1) {
            return NbtIo.read(input);
            //?}
            //? if (>=1.20.2 && <=1.20.3) {
            /*return NbtIo.read(
                                input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
*///?}
            //? if (>=1.20.4 && <=1.21.11) {
            /*return NbtIo.read(input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
*///?}
            //? if (>=26.1 && <=26.3) {
            /*return NbtIo.read(input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
*///?}

        }
    }
}
