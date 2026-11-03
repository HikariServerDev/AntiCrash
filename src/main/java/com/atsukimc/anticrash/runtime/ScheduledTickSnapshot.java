package com.atsukimc.anticrash.runtime;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.Block;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.fluid.Fluid;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.Registry;
*///?}

//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.registry.Registries;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}

//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.registry.Registry;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.resources.Identifier;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.Identifier;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.Block;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.LevelChunk;
*///?}

//? if (>=1.18 && <=1.19.2) {
import net.minecraft.util.registry.Registry;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.material.Fluid;
*///?}

//? if (>=1.18 && <=1.19.2) {
import net.minecraft.world.TickPriority;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.ticks.LevelChunkTicks;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.chunk.WorldChunk;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.ticks.ScheduledTick;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.tick.ChunkTickScheduler;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.tick.OrderedTick;
//?}

//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.world.tick.TickPriority;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.ticks.TickPriority;
*///?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact runtime representation of one chunk-local scheduled tick.
 *
 * <p>Vanilla 1.18.2 chunk NBT stores the relative delay and priority, but not {@link
 * OrderedTick#subTickOrder()}. Reconstructing a queue from that NBT can therefore change the order
 * of observer/redstone updates sharing the same trigger tick. Runtime Capsule v3 keeps all ordering
 * fields required to recreate the active queue exactly.
 */

/**
 * Exact runtime representation of one chunk-local scheduled tick.
 *
 * <p>Vanilla 1.18.2 chunk NBT stores the relative delay and priority, but not {@link
 * ScheduledTick#subTickOrder()}. Reconstructing a queue from that NBT can therefore change the
 * order of observer/redstone updates sharing the same trigger tick. Runtime Capsule v3 keeps all
 * ordering fields required to recreate the active queue exactly.
 */
public final class ScheduledTickSnapshot {
    public static final String BLOCK = "BLOCK";
    public static final String FLUID = "FLUID";

    private static final Comparator<ScheduledTickSnapshot> STABLE_ORDER =
            Comparator.comparingLong((ScheduledTickSnapshot tick) -> tick.triggerTick)
                    .thenComparingInt(tick -> tick.priority)
                    .thenComparingLong(tick -> tick.subTickOrder)
                    .thenComparing(tick -> tick.kind)
                    .thenComparingInt(tick -> tick.x)
                    .thenComparingInt(tick -> tick.y)
                    .thenComparingInt(tick -> tick.z)
                    .thenComparing(tick -> tick.type);

    public String dimension;
    public String kind;
    public String type;
    public int x;
    public int y;
    public int z;
    public long triggerTick;
    public int priority;
    public long subTickOrder;

    //? if (>=1.18 && <=1.21.11) {
    public static List<ScheduledTickSnapshot> capture(String dimension, WorldChunk chunk)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static List<ScheduledTickSnapshot> capture(String dimension, LevelChunk chunk)
*///?}
            {
        List<ScheduledTickSnapshot> result = new ArrayList<>();

        //? if (>=1.18 && <=1.19.2) {
        captureQueue(result, dimension, BLOCK, blockScheduler(chunk), Registry.BLOCK);
        //?}
        //? if (>=1.19.3 && <=1.21.11) {
        /*captureQueue(result, dimension, BLOCK, blockScheduler(chunk), Registries.BLOCK);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*captureQueue(result, dimension, BLOCK, blockScheduler(chunk), BuiltInRegistries.BLOCK);
*///?}

        //? if (>=1.18 && <=1.19.2) {
        captureQueue(result, dimension, FLUID, fluidScheduler(chunk), Registry.FLUID);
        //?}
        //? if (>=1.19.3 && <=1.21.11) {
        /*captureQueue(result, dimension, FLUID, fluidScheduler(chunk), Registries.FLUID);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*captureQueue(result, dimension, FLUID, fluidScheduler(chunk), BuiltInRegistries.FLUID);
*///?}

        result.sort(STABLE_ORDER);
        return result;
    }

    public BlockPos position() {
        return new BlockPos(x, y, z);
    }

    public Identifier typeIdentifier() {
        return Identifier.tryParse(type);
    }

    public TickPriority tickPriority() {

        //? if (>=1.18 && <=1.21.11) {
        return TickPriority.byIndex(priority);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*return TickPriority.byValue(priority);
*///?}

    }

    //? if (>=1.18 && <=1.21.11) {
    @SuppressWarnings("unchecked")
    static ChunkTickScheduler<Block> blockScheduler(WorldChunk chunk)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@SuppressWarnings("unchecked")
                    static LevelChunkTicks<Block> blockScheduler(LevelChunk chunk)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        return (ChunkTickScheduler<Block>) chunk.getBlockTickScheduler();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*return (LevelChunkTicks<Block>) chunk.getBlockTicks();
*///?}

    }

    //? if (>=1.18 && <=1.21.11) {
    @SuppressWarnings("unchecked")
    static ChunkTickScheduler<Fluid> fluidScheduler(WorldChunk chunk)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@SuppressWarnings("unchecked")
                    static LevelChunkTicks<Fluid> fluidScheduler(LevelChunk chunk)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        return (ChunkTickScheduler<Fluid>) chunk.getFluidTickScheduler();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*return (LevelChunkTicks<Fluid>) chunk.getFluidTicks();
*///?}

    }

    //? if (>=1.18 && <=1.21.11) {
    private static <T> void captureQueue(
            List<ScheduledTickSnapshot> output,
            String dimension,
            String kind,
            ChunkTickScheduler<T> scheduler,
            Registry<T> registry)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static <T> void captureQueue(
                            List<ScheduledTickSnapshot> output,
                            String dimension,
                            String kind,
                            LevelChunkTicks<T> scheduler,
                            Registry<T> registry)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        scheduler
                .getQueueAsStream()
                .forEach(ordered ->
                //?}
                //? if (>=26.1 && <=26.3) {
                /*scheduler
                                .getAll()
                                .forEach(ordered ->
*///?}
                {

                    //? if (>=1.18 && <=1.21.11) {
                    Identifier identifier = registry.getId(ordered.type());
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*Identifier identifier = registry.getKey(ordered.type());
*///?}

                    if (identifier == null) {
                        throw new IllegalStateException("Scheduled tick type is not registered");
                    }
                    ScheduledTickSnapshot snapshot = new ScheduledTickSnapshot();
                    snapshot.dimension = dimension;
                    snapshot.kind = kind;
                    snapshot.type = identifier.toString();
                    snapshot.x = ordered.pos().getX();
                    snapshot.y = ordered.pos().getY();
                    snapshot.z = ordered.pos().getZ();
                    snapshot.triggerTick = ordered.triggerTick();

                    //? if (>=1.18 && <=1.21.11) {
                    snapshot.priority = ordered.priority().getIndex();
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*snapshot.priority = ordered.priority().getValue();
*///?}

                    snapshot.subTickOrder = ordered.subTickOrder();
                    output.add(snapshot);
                });
    }
}
