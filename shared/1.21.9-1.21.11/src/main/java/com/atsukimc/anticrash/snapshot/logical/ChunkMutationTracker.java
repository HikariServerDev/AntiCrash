package com.atsukimc.anticrash.snapshot.logical;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conservative, server-thread-owned mutation generations for semantic chunk candidates.
 *
 * <p>This is deliberately not a universal WAL. It records only the mutation boundary needed to
 * decide whether a previously detached chunk NBT candidate may be reused. A missing hook must
 * invalidate the candidate, not silently make it look unchanged.
 */
public final class ChunkMutationTracker {
    private static final ChunkMutationTracker GLOBAL = new ChunkMutationTracker();

    /**
     * Generations per dimension, keyed by packed chunk position. World#markDirty reaches this for
     * every block-entity change and hopper/furnace tick, so the hot path must not build strings.
     */
    private final Map<String, Long2LongOpenHashMap> generations = new HashMap<>();

    private final Map<RegistryKey<World>, String> dimensionNames = new IdentityHashMap<>();

    /**
     * WorldTickScheduler has no World reference in 1.18.2; bind its identity while worlds exist.
     */
    private final Map<Object, String> schedulerDimensions = new WeakHashMap<>();

    private final AtomicLong globalVersion = new AtomicLong();
    private boolean uncertain;

    public static ChunkMutationTracker global() {
        return GLOBAL;
    }

    /** Mixin entry point; it does no allocation when the tracker is not being inspected. */
    public static void record(World world, ChunkPos position, String reason) {
        if (!LogicalTracking.enabled() || world == null || position == null || world.isClient())
            return;
        GLOBAL.record(GLOBAL.dimensionName(world), position.toLong(), reason);
    }

    /** Registers a scheduler object without touching its private queue or ticket state. */
    public static void registerScheduler(Object scheduler, String dimension) {
        if (scheduler == null || dimension == null || dimension.isBlank()) return;
        synchronized (GLOBAL) {
            GLOBAL.schedulerDimensions.put(scheduler, dimension);
        }
    }

    /**
     * Records a scheduler mutation against the owning chunk. The mapped 1.18.2 WorldTickScheduler
     * does not retain a World reference, so an unregistered scheduler is treated as uncertain
     * rather than guessed into a dimension.
     */
    public static void recordScheduler(Object scheduler, ChunkPos position, String reason) {
        if (!LogicalTracking.enabled() || scheduler == null || position == null) return;
        String dimension;
        synchronized (GLOBAL) {
            dimension = GLOBAL.schedulerDimensions.get(scheduler);
        }
        if (dimension == null) {
            GLOBAL.markUncertain("unregistered_world_tick_scheduler:" + reason);
            return;
        }
        GLOBAL.record(dimension, position.toLong(), reason);
    }

    /** Returns the observed owner dimension for diagnostics without exposing scheduler state. */
    public static String schedulerDimension(Object scheduler) {
        if (scheduler == null) return null;
        synchronized (GLOBAL) {
            return GLOBAL.schedulerDimensions.get(scheduler);
        }
    }

    /** Marks a scheduler API that cannot be mapped to one exact Chunk as unsafe. */
    public static void markSchedulerUncertain(String reason) {
        if (!LogicalTracking.enabled()) return;
        GLOBAL.markUncertain(reason);
    }

    public synchronized long record(String dimension, long packedPosition, String reason) {
        long next =
                generations
                                .computeIfAbsent(
                                        dimension == null ? "unknown" : dimension,
                                        ignored -> new Long2LongOpenHashMap())
                                .addTo(packedPosition, 1L)
                        + 1L;
        globalVersion.incrementAndGet();
        return next;
    }

    private synchronized String dimensionName(World world) {
        // RegistryKey instances are interned, so identity lookup avoids Identifier#toString.
        return dimensionNames.computeIfAbsent(
                world.getRegistryKey(), key -> key.getValue().toString());
    }

    /** Marks a component as unsafe when a mutation path was observed but not classifiable. */
    public synchronized void markUncertain(String reason) {
        uncertain = true;
        globalVersion.incrementAndGet();
    }

    public synchronized long generation(String dimension, long packedPosition) {
        Long2LongOpenHashMap byChunk = generations.get(dimension == null ? "unknown" : dimension);
        return byChunk == null ? 0L : byChunk.get(packedPosition);
    }

    public long version() {
        return globalVersion.get();
    }

    public synchronized boolean isUncertain() {
        return uncertain;
    }

    public synchronized void clearUncertain() {
        uncertain = false;
    }

    public synchronized Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long2LongOpenHashMap> dimension : generations.entrySet()) {
            for (Long2LongMap.Entry entry : dimension.getValue().long2LongEntrySet()) {
                result.put(key(dimension.getKey(), entry.getLongKey()), entry.getLongValue());
            }
        }
        return Map.copyOf(result);
    }

    public synchronized void resetForTests() {
        generations.clear();
        dimensionNames.clear();
        schedulerDimensions.clear();
        uncertain = false;
        globalVersion.set(0L);
    }

    public static String key(String dimension, long packedPosition) {
        return (dimension == null ? "unknown" : dimension) + ":" + packedPosition;
    }
}
