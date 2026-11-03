package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.RedstoneTorchBlockAccessorMixin;
import com.atsukimc.anticrash.mixin.RedstoneTorchBurnoutEntryAccessorMixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.RedstoneTorchBlock;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.ServerLevel;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ServerLevel;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.BlockGetter;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.BlockGetter;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.RedstoneTorchBlock;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Serializable representation of RedstoneTorchBlock's per-world burnout history. */
public final class RedstoneTorchBurnoutSnapshot {
    public int formatVersion = 1;
    public String dimension;
    public final List<Entry> entries = new ArrayList<>();

    //? if (>=1.18 && <=1.21.11) {
    /*public static RedstoneTorchBurnoutSnapshot capture(ServerLevel world)
                *///?} else if (>=26.1 && <=26.3) {
    public static RedstoneTorchBurnoutSnapshot capture(ServerLevel world)
                //?}
            {
        RedstoneTorchBurnoutSnapshot snapshot = new RedstoneTorchBurnoutSnapshot();

        //? if (>=1.18 && <=1.21.11) {
        /*//? if (>=1.18 && <=1.21.10) {
        /^snapshot.dimension = world.dimension().location().toString();
        ^///?} else if 1.21.11 {
        /^snapshot.dimension = world.dimension().identifier().toString();
        ^///?}
        *///?} else if (>=26.1 && <=26.3) {
        snapshot.dimension = world.dimension().identifier().toString();
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> map =
                RedstoneTorchBlockAccessorMixin.anticrash$getBurnoutMap();
        *///?} else if (>=26.1 && <=26.3) {
        Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> map =
                RedstoneTorchBlockAccessorMixin.anticrash$getBurnoutMap();
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*List<RedstoneTorchBlock.Toggle> live = map.get(world);
        *///?} else if (>=26.1 && <=26.3) {
        List<RedstoneTorchBlock.Toggle> live = map.get(world);
        //?}

        if (live != null) {

            //? if (>=1.18 && <=1.21.11) {
            /*for (RedstoneTorchBlock.Toggle entry : live)
            *///?} else if (>=26.1 && <=26.3) {
            for (RedstoneTorchBlock.Toggle entry : live)
            //?}
            {
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

    //? if (>=1.18 && <=1.21.11) {
    /*public void restore(ServerLevel world)
                *///?} else if (>=26.1 && <=26.3) {
    public void restore(ServerLevel world)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> map =
                RedstoneTorchBlockAccessorMixin.anticrash$getBurnoutMap();
        *///?} else if (>=26.1 && <=26.3) {
        Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> map =
                RedstoneTorchBlockAccessorMixin.anticrash$getBurnoutMap();
        //?}

        if (entries.isEmpty()) {
            map.remove(world);
            return;
        }

        //? if (>=1.18 && <=1.21.11) {
        /*List<RedstoneTorchBlock.Toggle> restored = new ArrayList<>(entries.size());
        *///?} else if (>=26.1 && <=26.3) {
        List<RedstoneTorchBlock.Toggle> restored = new ArrayList<>(entries.size());
        //?}

        for (Entry entry : entries) {

            //? if (>=1.18 && <=1.21.11) {
            /*restored.add(
                    new RedstoneTorchBlock.Toggle(
                            new BlockPos(entry.x, entry.y, entry.z), entry.time));
            *///?} else if (>=26.1 && <=26.3) {
            restored.add(
                    new RedstoneTorchBlock.Toggle(
                            new BlockPos(entry.x, entry.y, entry.z), entry.time));
            //?}

        }
        // Only the new ServerLevel key is written; old World instances are never retained.
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
