package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.RedstoneTorchBlockAccessorMixin;
import com.atsukimc.anticrash.mixin.RedstoneTorchBurnoutEntryAccessorMixin;

import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Serializable representation of RedstoneTorchBlock's per-world burnout history. */
public final class RedstoneTorchBurnoutSnapshot {
    public int formatVersion = 1;
    public String dimension;
    public final List<Entry> entries = new ArrayList<>();

    public static RedstoneTorchBurnoutSnapshot capture(ServerWorld world) {
        RedstoneTorchBurnoutSnapshot snapshot = new RedstoneTorchBurnoutSnapshot();
        snapshot.dimension = world.getRegistryKey().getValue().toString();
        Map<BlockView, List<RedstoneTorchBlock.BurnoutEntry>> map =
                RedstoneTorchBlockAccessorMixin.anticrash$getBurnoutMap();
        List<RedstoneTorchBlock.BurnoutEntry> live = map.get(world);
        if (live != null) {
            for (RedstoneTorchBlock.BurnoutEntry entry : live) {
                if (entry == null) continue;
                RedstoneTorchBurnoutEntryAccessorMixin entryAccessor =
                        (RedstoneTorchBurnoutEntryAccessorMixin) entry;
                if (entryAccessor.anticrash$getPos() == null) continue;
                Entry copy = new Entry();
                copy.x = entryAccessor.anticrash$getPos().getX();
                copy.y = entryAccessor.anticrash$getPos().getY();
                copy.z = entryAccessor.anticrash$getPos().getZ();
                copy.time = entryAccessor.anticrash$getTime();
                snapshot.entries.add(copy);
            }
        }
        return snapshot;
    }

    public void restore(ServerWorld world) {
        Map<BlockView, List<RedstoneTorchBlock.BurnoutEntry>> map =
                RedstoneTorchBlockAccessorMixin.anticrash$getBurnoutMap();
        if (entries.isEmpty()) {
            map.remove(world);
            return;
        }
        List<RedstoneTorchBlock.BurnoutEntry> restored = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            restored.add(
                    new RedstoneTorchBlock.BurnoutEntry(
                            new BlockPos(entry.x, entry.y, entry.z), entry.time));
        }
        // Only the new ServerWorld key is written; old World instances are never retained.
        map.put(world, restored);
    }

    public List<String> canonicalLines() {
        List<String> result = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            result.add(
                    dimension
                            + "|"
                            + index
                            + "|"
                            + entry.x
                            + ','
                            + entry.y
                            + ','
                            + entry.z
                            + "|"
                            + entry.time);
        }
        return result;
    }

    public static final class Entry {
        public int x;
        public int y;
        public int z;
        public long time;
    }
}
