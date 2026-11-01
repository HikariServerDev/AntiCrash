package com.atsukimc.anticrash.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** The IO actor internals a checkpoint cut seals; see {@link com.atsukimc.anticrash.checkpoint.IoSeal}. */
@Mixin(StorageIoWorker.class)
public interface StorageIoWorkerAccessorMixin {
    /** Queued writes not yet on disk. Only the worker's own actor thread may touch this map. */
    @Accessor("results")
    java.util.SequencedMap<ChunkPos, Object> anticrash$getResults();

    @Accessor("storage")
    RegionBasedStorage anticrash$getStorage();

    /** Runs a task on the worker's actor at foreground priority, after every task queued before it. */
    @Invoker("writeRemainingResults")
    void anticrash$writeRemainingResults();

    @Invoker("run")
    <T> CompletableFuture<T> anticrash$run(Supplier<T> task);
}
