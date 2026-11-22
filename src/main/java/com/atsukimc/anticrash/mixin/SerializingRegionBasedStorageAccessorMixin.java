package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;

//? if <=1.20.4 {
/*import net.minecraft.world.level.chunk.storage.IOWorker;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * POI storage internals the checkpoint cut needs. PoiManager#tick would save the same
 * sections, but it also advances the POI distance tracker, which the frozen cut must not do.
 */
@Mixin(SectionStorage.class)
public interface SerializingRegionBasedStorageAccessorMixin {
    //? if <=1.20.4 {
    /*@Accessor("worker")
    IOWorker anticrash$getWorker();

    *///?} else {
    @Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorageAccess();

    //?}

    //? if <=1.21.1 {
    /*@Accessor("dirty")
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    @Invoker("writeColumn")
    void anticrash$save(ChunkPos pos);
    *///?} else {
    @Accessor("dirtyChunks")
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    @Invoker("writeChunk")
    void anticrash$save(ChunkPos pos);
    //?}
}
