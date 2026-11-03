package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.Block;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.BlockEventData;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.resources.Identifier;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.level.ServerLevel;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
//? if (>=1.18 && <=1.21.10) {
import net.minecraft.resources.ResourceLocation;
//?}
//? if 1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?}
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.BlockEventData;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.19.2) {
import net.minecraft.core.Registry;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.Block;
*///?}

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockEventSnapshot {
    public int formatVersion = 1;
    public final List<Entry> entries = new ArrayList<>();

    //? if (>=1.18 && <=1.21.11) {
    public static BlockEventSnapshot capture(Iterable<ServerLevel> worlds)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static BlockEventSnapshot capture(Iterable<ServerLevel> worlds)
*///?}
            {
        BlockEventSnapshot snapshot = new BlockEventSnapshot();

        //? if (>=1.18 && <=1.21.11) {
        for (ServerLevel world : worlds)
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (ServerLevel world : worlds)
*///?}
        {
            ServerWorldAccessorMixin accessor = (ServerWorldAccessorMixin) world;

            //? if (>=1.18 && <=1.21.11) {
            for (BlockEventData event : accessor.anticrash$getSyncedBlockEventQueue())
            //?}
            //? if (>=26.1 && <=26.3) {
            /*for (BlockEventData event : accessor.anticrash$getSyncedBlockEventQueue())
*///?}
            {
                snapshot.entries.add(Entry.of(world, event, "SYNCED"));
            }

            //? if (>=1.18 && <=1.21.11) {
            for (BlockEventData event : accessor.anticrash$getBlockEventQueue())
            //?}
            //? if (>=26.1 && <=26.3) {
            /*for (BlockEventData event : accessor.anticrash$getBlockEventQueue())
*///?}
            {
                snapshot.entries.add(Entry.of(world, event, "REGULAR"));
            }
        }
        return snapshot;
    }

    //? if (>=1.18 && <=1.21.11) {
    public RestoreResult restore(Iterable<ServerLevel> worlds)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public RestoreResult restore(Iterable<ServerLevel> worlds)
*///?}
            {
        RestoreResult result = new RestoreResult();
        if (formatVersion != 1 || entries == null) {
            result.failed = 1;
            return result;
        }

        //? if (>=1.18 && <=1.21.11) {
        Map<String, ServerLevel> byDimension = new LinkedHashMap<>();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Map<String, ServerLevel> byDimension = new LinkedHashMap<>();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        for (ServerLevel world : worlds)
            //? if (>=1.18 && <=1.21.10) {
            byDimension.put(world.dimension().location().toString(), world);
        //?}
        //? if 1.21.11 {
        /*byDimension.put(world.dimension().identifier().toString(), world);
            *///?}
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (ServerLevel world : worlds)
                    byDimension.put(world.dimension().identifier().toString(), world);
*///?}

        List<PreparedEntry> prepared = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                if (entry == null
                        || !("SYNCED".equals(entry.queue) || "REGULAR".equals(entry.queue)))
                    throw new IllegalArgumentException("invalid block event queue");

                //? if (>=1.18 && <=1.21.11) {
                ServerLevel world = byDimension.get(entry.dimension);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*ServerLevel world = byDimension.get(entry.dimension);
*///?}

                if (world == null)
                    throw new IllegalArgumentException("unknown block event dimension");

                //? if (>=1.18 && <=1.20.6) {
                ResourceLocation blockId = new ResourceLocation(entry.block);
                //?}
                //? if (>=1.21 && <=1.21.10) {
                /*ResourceLocation blockId = ResourceLocation.parse(entry.block);
                *///?}
                //? if 1.21.11 {
                /*Identifier blockId = Identifier.parse(entry.block);
                *///?}
                //? if (>=26.1 && <=26.3) {
                /*Identifier blockId = Identifier.parse(entry.block);
*///?}

                //? if (>=1.18 && <=1.19.2) {
                if (!Registry.BLOCK.containsKey(blockId))

                    //?}
                    //? if (>=1.19.3 && <=1.21.11) {
                    /*if (!BuiltInRegistries.BLOCK.containsKey(blockId))
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (!BuiltInRegistries.BLOCK.containsKey(blockId))
*///?}
                    throw new IllegalArgumentException("unknown block id");

                //? if (>=1.18 && <=1.19.2) {
                Block block = Registry.BLOCK.get(blockId);
                //?}
                //? if (>=1.19.3 && <=1.21.1) {
                /*Block block = BuiltInRegistries.BLOCK.get(blockId);
                *///?}
                //? if (>=1.21.2 && <=1.21.11) {
                /*Block block = BuiltInRegistries.BLOCK.getValue(blockId);
                *///?}
                //? if (>=26.1 && <=26.3) {
                /*Block block = BuiltInRegistries.BLOCK.getValue(blockId);
*///?}

                //? if (>=1.18 && <=1.21.11) {
                BlockEventData event =
                        new BlockEventData(
                                new BlockPos(entry.x, entry.y, entry.z),
                                block,
                                entry.type,
                                entry.data);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*BlockEventData event =
                                        new BlockEventData(
                                                new BlockPos(entry.x, entry.y, entry.z),
                                                block,
                                                entry.type,
                                                entry.data);
*///?}

                prepared.add(new PreparedEntry(world, entry.queue, event));
            } catch (Exception exception) {
                result.failed++;
            }
        }
        // Never clear live queues if even one checkpoint entry is invalid.  Validation is
        // all-or-nothing so RECOVERY_FAILED leaves the freshly loaded world untouched.
        if (result.failed > 0) return result;

        //? if (>=1.18 && <=1.21.11) {
        for (ServerLevel world : byDimension.values())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (ServerLevel world : byDimension.values())
*///?}
        {
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

    private record PreparedEntry(
            //? if (>=1.18 && <=1.21.11) {
            ServerLevel world
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*ServerLevel world
*///?}
                    ,
            String queue,
            //? if (>=1.18 && <=1.21.11) {
            BlockEventData event
            //?}
            //? if (>=26.1 && <=26.3) {
            /*BlockEventData event
*///?}
            ) {}

    public static final class Entry {
        public String queue;
        public String dimension;
        public int x, y, z;
        public String block;
        public int type, data;

        //? if (>=1.18 && <=1.21.11) {
        static Entry of(ServerLevel world, BlockEventData event, String queue)
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*static Entry of(ServerLevel world, BlockEventData event, String queue)
*///?}
                {
            Entry entry = new Entry();
            entry.queue = queue;

            //? if (>=1.18 && <=1.21.11) {
            //? if (>=1.18 && <=1.21.10) {
            entry.dimension = world.dimension().location().toString();
            //?}
            //? if 1.21.11 {
            /*entry.dimension = world.dimension().identifier().toString();
            *///?}
            //?}
            //? if (>=26.1 && <=26.3) {
            /*entry.dimension = world.dimension().identifier().toString();
*///?}

            entry.x = event.pos().getX();
            entry.y = event.pos().getY();
            entry.z = event.pos().getZ();

            //? if (>=1.18 && <=1.19.2) {
            entry.block = Registry.BLOCK.getKey(event.block()).toString();
            //?}
            //? if (>=1.19.3 && <=1.21.11) {
            /*entry.block = BuiltInRegistries.BLOCK.getKey(event.block()).toString();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*entry.block = BuiltInRegistries.BLOCK.getKey(event.block()).toString();
*///?}

            //? if (>=1.18 && <=1.21.11) {
            entry.type = event.paramA();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*entry.type = event.paramA();
*///?}

            //? if (>=1.18 && <=1.21.11) {
            entry.data = event.paramB();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*entry.data = event.paramB();
*///?}

            return entry;
        }
    }

    public static final class RestoreResult {
        public int restored;
        public int failed;
    }
}
