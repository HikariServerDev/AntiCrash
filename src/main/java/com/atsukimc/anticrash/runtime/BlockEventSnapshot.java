package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;

//? if (>=1.18 && <=1.19.2) {
/*import net.minecraft.core.Registry;
*///?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
//? if (>=1.18 && <=1.21.10) {
/*import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.resources.Identifier;
//?}

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockEventSnapshot {
    public int formatVersion = 1;
    public final List<Entry> entries = new ArrayList<>();

    public static BlockEventSnapshot capture(Iterable<ServerLevel> worlds) {
        BlockEventSnapshot snapshot = new BlockEventSnapshot();

        for (ServerLevel world : worlds) {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) world;

            for (BlockEventData event : accessor.anticrash$getSyncedBlockEventQueue()) {
                snapshot.entries.add(Entry.of(world, event, "SYNCED"));
            }

            for (BlockEventData event : accessor.anticrash$getBlockEventQueue()) {
                snapshot.entries.add(Entry.of(world, event, "REGULAR"));
            }
        }
        return snapshot;
    }

    public RestoreResult restore(Iterable<ServerLevel> worlds) {
        RestoreResult result = new RestoreResult();
        if (formatVersion != 1 || entries == null) {
            result.failed = 1;
            return result;
        }

        Map<String, ServerLevel> byDimension = new LinkedHashMap<>();

        //? if (>=1.18 && <=1.21.11) {
        /*for (ServerLevel world : worlds)
            //? if (>=1.18 && <=1.21.10) {
            /^byDimension.put(world.dimension().location().toString(), world);
            ^///?}
            //? if 1.21.11 {
            /^byDimension.put(world.dimension().identifier().toString(), world);
            ^///?}
        *///?}
        //? if (>=26.1 && <=26.3) {
        for (ServerLevel world : worlds)
            byDimension.put(world.dimension().identifier().toString(), world);
        //?}

        List<PreparedEntry> prepared = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                if (entry == null
                        || !("SYNCED".equals(entry.queue) || "REGULAR".equals(entry.queue)))
                    throw new IllegalArgumentException("invalid block event queue");

                ServerLevel world = byDimension.get(entry.dimension);

                if (world == null)
                    throw new IllegalArgumentException("unknown block event dimension");

                //? if (>=1.18 && <=1.20.6) {
                /*ResourceLocation blockId = new ResourceLocation(entry.block);
                *///?}
                //? if (>=1.21 && <=1.21.10) {
                /*ResourceLocation blockId = ResourceLocation.parse(entry.block);
                *///?}
                //? if (>=1.21.11 && <=26.3) {
                Identifier blockId = Identifier.parse(entry.block);
                //?}

                //? if (>=1.18 && <=1.19.2) {
                /*if (!Registry.BLOCK.containsKey(blockId))

                *///?}
                //? if (>=1.19.3 && <=26.3) {
                if (!BuiltInRegistries.BLOCK.containsKey(blockId))
                //?}
                    throw new IllegalArgumentException("unknown block id");

                //? if (>=1.18 && <=1.19.2) {
                /*Block block = Registry.BLOCK.get(blockId);
                *///?}
                //? if (>=1.19.3 && <=1.21.1) {
                /*Block block = BuiltInRegistries.BLOCK.get(blockId);
                *///?}
                //? if (>=1.21.2 && <=26.3) {
                Block block = BuiltInRegistries.BLOCK.getValue(blockId);
                //?}

                BlockEventData event =
                        new BlockEventData(
                                new BlockPos(entry.x, entry.y, entry.z),
                                block,
                                entry.type,
                                entry.data);

                prepared.add(new PreparedEntry(world, entry.queue, event));
            } catch (Exception exception) {
                result.failed++;
            }
        }
        // Never clear live queues if even one checkpoint entry is invalid.  Validation is
        // all-or-nothing so RECOVERY_FAILED leaves the freshly loaded world untouched.
        if (result.failed > 0) return result;

        for (ServerLevel world : byDimension.values()) {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) world;
            accessor.anticrash$getSyncedBlockEventQueue().clear();
            accessor.anticrash$getBlockEventQueue().clear();
        }
        for (PreparedEntry entry : prepared) {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) entry.world;
            if ("SYNCED".equals(entry.queue))
                accessor.anticrash$getSyncedBlockEventQueue().add(entry.event);
            else accessor.anticrash$getBlockEventQueue().add(entry.event);
            result.restored++;
        }
        return result;
    }

    private record PreparedEntry(ServerLevel world, String queue, BlockEventData event) {}

    public static final class Entry {
        public String queue;
        public String dimension;
        public int x, y, z;
        public String block;
        public int type, data;

        static Entry of(ServerLevel world, BlockEventData event, String queue) {
            Entry entry = new Entry();
            entry.queue = queue;

            //? if (>=1.18 && <=1.21.11) {
            /*//? if (>=1.18 && <=1.21.10) {
            /^entry.dimension = world.dimension().location().toString();
            ^///?}
            //? if 1.21.11 {
            /^entry.dimension = world.dimension().identifier().toString();
            ^///?}
            *///?}
            //? if (>=26.1 && <=26.3) {
            entry.dimension = world.dimension().identifier().toString();
            //?}

            entry.x = event.pos().getX();
            entry.y = event.pos().getY();
            entry.z = event.pos().getZ();

            //? if (>=1.18 && <=1.19.2) {
            /*entry.block = Registry.BLOCK.getKey(event.block()).toString();
            *///?}
            //? if (>=1.19.3 && <=26.3) {
            entry.block = BuiltInRegistries.BLOCK.getKey(event.block()).toString();
            //?}

            entry.type = event.paramA();

            entry.data = event.paramB();

            return entry;
        }
    }

    public static final class RestoreResult {
        public int restored;
        public int failed;
    }
}
