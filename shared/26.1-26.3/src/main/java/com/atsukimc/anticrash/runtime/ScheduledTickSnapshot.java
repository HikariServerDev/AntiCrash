package com.atsukimc.anticrash.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    public static List<ScheduledTickSnapshot> capture(String dimension, LevelChunk chunk) {
        List<ScheduledTickSnapshot> result = new ArrayList<>();
        captureQueue(result, dimension, BLOCK, blockScheduler(chunk), BuiltInRegistries.BLOCK);
        captureQueue(result, dimension, FLUID, fluidScheduler(chunk), BuiltInRegistries.FLUID);
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
        return TickPriority.byValue(priority);
    }

    @SuppressWarnings("unchecked")
    static LevelChunkTicks<Block> blockScheduler(LevelChunk chunk) {
        return (LevelChunkTicks<Block>) chunk.getBlockTicks();
    }

    @SuppressWarnings("unchecked")
    static LevelChunkTicks<Fluid> fluidScheduler(LevelChunk chunk) {
        return (LevelChunkTicks<Fluid>) chunk.getFluidTicks();
    }

    private static <T> void captureQueue(
            List<ScheduledTickSnapshot> output,
            String dimension,
            String kind,
            LevelChunkTicks<T> scheduler,
            Registry<T> registry) {
        scheduler
                .getAll()
                .forEach(ordered -> {
                    Identifier identifier = registry.getKey(ordered.type());
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
                    snapshot.priority = ordered.priority().getValue();
                    snapshot.subTickOrder = ordered.subTickOrder();
                    output.add(snapshot);
                });
    }
}
