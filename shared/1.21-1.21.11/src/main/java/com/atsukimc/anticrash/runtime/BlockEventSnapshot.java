package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import net.minecraft.block.Block;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockEventSnapshot {
    public int formatVersion = 1;
    public final List<Entry> entries = new ArrayList<>();

    public static BlockEventSnapshot capture(Iterable<ServerWorld> worlds) {
        BlockEventSnapshot snapshot = new BlockEventSnapshot();
        for (ServerWorld world : worlds) {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) world;
            for (BlockEvent event : accessor.anticrash$getSyncedBlockEventQueue()) {
                snapshot.entries.add(Entry.of(world, event, "SYNCED"));
            }
            for (BlockEvent event : accessor.anticrash$getBlockEventQueue()) {
                snapshot.entries.add(Entry.of(world, event, "REGULAR"));
            }
        }
        return snapshot;
    }

    public RestoreResult restore(Iterable<ServerWorld> worlds) {
        RestoreResult result = new RestoreResult();
        if (formatVersion != 1 || entries == null) { result.failed = 1; return result; }
        Map<String, ServerWorld> byDimension = new LinkedHashMap<>();
        for (ServerWorld world : worlds) byDimension.put(world.getRegistryKey().getValue().toString(), world);
        List<PreparedEntry> prepared = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                if (entry == null || !("SYNCED".equals(entry.queue) || "REGULAR".equals(entry.queue))) throw new IllegalArgumentException("invalid block event queue");
                ServerWorld world = byDimension.get(entry.dimension);
                if (world == null) throw new IllegalArgumentException("unknown block event dimension");
                Identifier blockId = Identifier.of(entry.block);
                if (!Registries.BLOCK.containsId(blockId)) throw new IllegalArgumentException("unknown block id");
                Block block = Registries.BLOCK.get(blockId);
                BlockEvent event = new BlockEvent(new BlockPos(entry.x, entry.y, entry.z), block, entry.type, entry.data);
                prepared.add(new PreparedEntry(world, entry.queue, event));
            } catch (Exception exception) {
                result.failed++;
            }
        }
        // Never clear live queues if even one checkpoint entry is invalid.  Validation is
        // all-or-nothing so RECOVERY_FAILED leaves the freshly loaded world untouched.
        if (result.failed > 0) return result;
        for (ServerWorld world : byDimension.values()) {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) world;
            accessor.anticrash$getSyncedBlockEventQueue().clear();
            accessor.anticrash$getBlockEventQueue().clear();
        }
        for (PreparedEntry entry : prepared) {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) entry.world;
            if ("SYNCED".equals(entry.queue)) accessor.anticrash$getSyncedBlockEventQueue().add(entry.event);
            else accessor.anticrash$getBlockEventQueue().add(entry.event);
            result.restored++;
        }
        return result;
    }

    private record PreparedEntry(ServerWorld world, String queue, BlockEvent event) { }

    public static final class Entry {
        public String queue;
        public String dimension;
        public int x, y, z;
        public String block;
        public int type, data;

        static Entry of(ServerWorld world, BlockEvent event, String queue) {
            Entry entry = new Entry();
            entry.queue = queue;
            entry.dimension = world.getRegistryKey().getValue().toString();
            entry.x = event.pos().getX(); entry.y = event.pos().getY(); entry.z = event.pos().getZ();
            entry.block = Registries.BLOCK.getId(event.block()).toString();
            entry.type = event.type(); entry.data = event.data();
            return entry;
        }
    }

    public static final class RestoreResult {
        public int restored;
        public int failed;
    }
}
