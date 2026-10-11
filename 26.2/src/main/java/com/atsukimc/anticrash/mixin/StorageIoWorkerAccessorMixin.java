package com.atsukimc.anticrash.mixin;

import com.mojang.datafixers.util.Either;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

/** The IO actor internals a checkpoint cut seals; see {@link com.atsukimc.anticrash.checkpoint.IoSeal}. */
@Mixin(IOWorker.class)
public interface StorageIoWorkerAccessorMixin {
    /** Queued writes not yet on disk. Only the worker's own actor thread may touch this map. */
    @Accessor("pendingWrites")
    java.util.SequencedMap<ChunkPos, Object> anticrash$getResults();

    @Accessor("storage")
    RegionFileStorage anticrash$getStorage();

    /** Runs a task on the worker's actor at foreground priority, after every task queued before it. */
    @Invoker("tellStorePending")
    void anticrash$writeRemainingResults();

    @Invoker("submitTask")
    <T> CompletableFuture<T> anticrash$run(Supplier<T> task);
}
