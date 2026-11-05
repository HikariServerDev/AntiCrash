package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.1) {
/*import com.mojang.datafixers.util.Either;
import java.util.Map;
*///?}

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The IO actor internals a checkpoint cut seals; see {@link
 * com.atsukimc.anticrash.checkpoint.IoSeal}.
 */
//? if (>=1.18 && <=1.21.11) {
/*@Mixin(IOWorker.class)
public interface StorageIoWorkerAccessorMixin {
    /^* Queued writes not yet on disk. Only the worker's own actor thread may touch this map. ^/

    *///?}
//? if (>=1.18 && <=1.21.1) {
/*@Accessor("pendingWrites")
    Map<ChunkPos, Object> anticrash$getResults();

    *///?}
//? if (>=1.21.2 && <=1.21.11) {
/*@Accessor("pendingWrites")
        java.util.SequencedMap<ChunkPos, Object> anticrash$getResults();
*///?}
//? if (>=26.1 && <=26.3) {
@Mixin(IOWorker.class)
public interface StorageIoWorkerAccessorMixin {
    /** Queued writes not yet on disk. Only the worker's own actor thread may touch this map. */
    @Accessor("pendingWrites")
    java.util.SequencedMap<ChunkPos, Object> anticrash$getResults();

    //?}

    @Accessor("storage")
    RegionFileStorage anticrash$getStorage();

    /**
     * Runs a task on the worker's actor at foreground priority, after every task queued before it.
     */

    @Invoker("tellStorePending")
    void anticrash$writeRemainingResults();

    //? if (>=1.18 && <=1.21.1) {
    /*@Invoker("submitTask")
    <T> CompletableFuture<T> anticrash$run(Supplier<Either<T, Exception>> task);
    *///?}
    //? if (>=1.21.2 && <=26.3) {
    @Invoker("submitTask")
    <T> CompletableFuture<T> anticrash$run(Supplier<T> task);
    //?}

}
