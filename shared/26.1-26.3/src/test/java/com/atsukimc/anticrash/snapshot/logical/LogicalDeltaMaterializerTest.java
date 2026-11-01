package com.atsukimc.anticrash.snapshot.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.atsukimc.anticrash.player.NbtCodec;
import com.atsukimc.anticrash.snapshot.JavaIncrementalBackend;
import com.atsukimc.anticrash.snapshot.SnapshotFile;
import com.atsukimc.anticrash.snapshot.SnapshotResult;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

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
        CompoundTag entities = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entity = new CompoundTag();
        entity.store("UUID", net.minecraft.core.UUIDUtil.CODEC, removed);
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
        changed.packedPosition = new ChunkPos(0, 0).pack();
        changed.payloadBase64 = NbtCodec.encode(marker("after"));
        changed.sha256 = "unused-in-test";
        delta.addChunk(changed);
        delta.addTombstone(
                new LogicalTombstone(
                        "ENTITY_REMOVED",
                        "minecraft:overworld",
                        removed.toString(),
                        1L,
                        "test",
                        new ChunkPos(0, 0).pack()));

        Path destination = temp.resolve("materialized");
        LogicalDeltaMaterializer.materialize(parentWorld, destination, parentFiles, delta);

        assertEquals(
                "before",
                readRegion(parentWorld, "region", new ChunkPos(0, 0))
                        .getString("marker")
                        .orElseThrow());
        assertEquals(
                "after",
                readRegion(destination, "region", new ChunkPos(0, 0))
                        .getString("marker")
                        .orElseThrow());
        assertNotSame(
                parentWorld.resolve("region/r.0.0.mca").toRealPath(),
                destination.resolve("region/r.0.0.mca").toRealPath());
        assertEquals(
                0,
                readRegion(destination, "entities", new ChunkPos(0, 0))
                        .getListOrEmpty("Entities")
                        .size());
        assertEquals(
                1,
                readRegion(parentWorld, "entities", new ChunkPos(0, 0))
                        .getListOrEmpty("Entities")
                        .size());
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
        emptied.packedPosition = new ChunkPos(0, 0).pack();
        emptied.payloadBase64 = NbtCodec.encode(entityChunk());
        delta.addEntityChunk(emptied);
        // The entity left chunk (1,0) and came back before the cut: the replacement list is
        // authoritative and the stale move tombstone must not delete it again.
        LogicalEntityChunkDelta rewritten = new LogicalEntityChunkDelta();
        rewritten.dimension = "minecraft:overworld";
        rewritten.packedPosition = new ChunkPos(1, 0).pack();
        rewritten.payloadBase64 = NbtCodec.encode(entityChunk(returned));
        delta.addEntityChunk(rewritten);
        delta.addTombstone(
                new LogicalTombstone(
                        "ENTITY_MOVED_FROM",
                        "minecraft:overworld",
                        returned.toString(),
                        1L,
                        "test",
                        new ChunkPos(1, 0).pack()));

        Path destination = temp.resolve("materialized2");
        LogicalDeltaMaterializer.materialize(parentWorld, destination, parentFiles, delta);

        Path directory = destination.resolve("entities");
        try (RegionFile region =
                new RegionFile(
                        com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                        directory.resolve("r.0.0.mca"),
                        directory,
                        false)) {
            assertEquals(false, region.hasChunk(new ChunkPos(0, 0)));
        }
        assertEquals(
                1,
                readRegion(destination, "entities", new ChunkPos(1, 0))
                        .getListOrEmpty("Entities")
                        .size());
        assertEquals(
                1,
                readRegion(parentWorld, "entities", new ChunkPos(0, 0))
                        .getListOrEmpty("Entities")
                        .size());
    }

    private static CompoundTag entityChunk(UUID... uuids) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag entity = new CompoundTag();
            entity.putString("id", "minecraft:armor_stand");
            entity.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
            list.add(entity);
        }
        root.put("Entities", list);
        return root;
    }

    private static CompoundTag marker(String value) {
        CompoundTag root = new CompoundTag();
        root.putString("marker", value);
        return root;
    }

    private static void writeRegion(
            Path world, String directoryName, ChunkPos pos, CompoundTag root) throws Exception {
        Path directory = world.resolve(directoryName);
        Files.createDirectories(directory);
        Path file = directory.resolve("r." + (pos.x() >> 5) + "." + (pos.z() >> 5) + ".mca");
        try (RegionFile region =
                new RegionFile(
                        com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                        file,
                        directory,
                        false)) {
            try (DataOutputStream output = region.getChunkDataOutputStream(pos)) {
                NbtIo.writeUnnamedTagWithFallback(root, output);
            }
            region.flush();
        }
    }

    private static CompoundTag readRegion(Path world, String directoryName, ChunkPos pos)
            throws Exception {
        Path directory = world.resolve(directoryName);
        Path file = directory.resolve("r." + (pos.x() >> 5) + "." + (pos.z() >> 5) + ".mca");
        try (RegionFile region =
                        new RegionFile(
                                com.atsukimc.anticrash.snapshot.RegionStorageAdapter.KEY,
                                file,
                                directory,
                                false);
                var input = region.getChunkDataInputStream(pos)) {
            return NbtIo.read(input, net.minecraft.nbt.NbtAccounter.create(16 * 1024 * 1024));
        }
    }
}
