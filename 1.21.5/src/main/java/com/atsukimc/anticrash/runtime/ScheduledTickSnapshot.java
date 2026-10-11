package com.atsukimc.anticrash.runtime;

import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.world.tick.TickPriority;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.ChunkTickScheduler;
import net.minecraft.world.tick.OrderedTick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact runtime representation of one chunk-local scheduled tick.
 *
 * <p>Vanilla 1.18.2 chunk NBT stores the relative delay and priority, but not
 * {@link OrderedTick#subTickOrder()}. Reconstructing a queue from that NBT can therefore change
 * the order of observer/redstone updates sharing the same trigger tick. Runtime Capsule v3 keeps
 * all ordering fields required to recreate the active queue exactly.</p>
 */
public final class ScheduledTickSnapshot {
    public static final String BLOCK = "BLOCK";
    public static final String FLUID = "FLUID";

    private static final Comparator<ScheduledTickSnapshot> STABLE_ORDER = Comparator
            .comparingLong((ScheduledTickSnapshot tick) -> tick.triggerTick)
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

    public static List<ScheduledTickSnapshot> capture(String dimension, WorldChunk chunk) {
        List<ScheduledTickSnapshot> result = new ArrayList<>();
        captureQueue(result, dimension, BLOCK, blockScheduler(chunk), Registries.BLOCK);
        captureQueue(result, dimension, FLUID, fluidScheduler(chunk), Registries.FLUID);
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
        return TickPriority.byIndex(priority);
    }

    @SuppressWarnings("unchecked")
    static ChunkTickScheduler<Block> blockScheduler(WorldChunk chunk) {
        return (ChunkTickScheduler<Block>) chunk.getBlockTickScheduler();
    }

    @SuppressWarnings("unchecked")
    static ChunkTickScheduler<Fluid> fluidScheduler(WorldChunk chunk) {
        return (ChunkTickScheduler<Fluid>) chunk.getFluidTickScheduler();
    }

    private static <T> void captureQueue(List<ScheduledTickSnapshot> output, String dimension,
                                         String kind, ChunkTickScheduler<T> scheduler,
                                         Registry<T> registry) {
        scheduler.getQueueAsStream().forEach(ordered -> {
            Identifier identifier = registry.getId(ordered.type());
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
            snapshot.priority = ordered.priority().getIndex();
            snapshot.subTickOrder = ordered.subTickOrder();
            output.add(snapshot);
        });
    }
}
