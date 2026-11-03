package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.storage.SerializingRegionBasedStorage;
//?}

//? if (>=1.18 && <=1.20.4) {
import net.minecraft.world.storage.StorageIoWorker;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.SectionStorage;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * POI storage internals the checkpoint cut needs. PointOfInterestStorage#tick would save the same
 * sections, but it also advances the POI distance tracker, which the frozen cut must not do.
 */
//? if (>=1.18 && <=1.21.11) {
@Mixin(SerializingRegionBasedStorage.class)
public interface SerializingRegionBasedStorageAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.20.4) {
    @Accessor("worker")
    StorageIoWorker anticrash$getWorker();

    //?}
    //? if (>=1.20.5 && <=1.21.10) {
    /*@Accessor("storageAccess")
        net.minecraft.world.storage.ChunkPosKeyedStorage anticrash$getStorageAccess();
*///?}
    //? if 1.21.11 {
    /*@Accessor("storageAccess")
        net.minecraft.world.storage.VersionedChunkStorage anticrash$getStorageAccess();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(SectionStorage.class)
    public interface SerializingRegionBasedStorageAccessorMixin {
        @Accessor("simpleRegionStorage")
        net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorageAccess();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("unsavedElements")
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("dirtyChunks")
        LongLinkedOpenHashSet anticrash$getUnsavedElements();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Invoker("save")
    void anticrash$save(ChunkPos pos);
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Invoker("writeChunk")
        void anticrash$save(ChunkPos pos);
*///?}

}
