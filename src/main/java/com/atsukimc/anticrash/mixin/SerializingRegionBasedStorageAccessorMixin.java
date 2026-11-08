package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;

//? if (>=1.18 && <=1.20.4) {
/*import net.minecraft.world.level.chunk.storage.IOWorker;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * POI storage internals the checkpoint cut needs. PoiManager#tick would save the same
 * sections, but it also advances the POI distance tracker, which the frozen cut must not do.
 */
//? if (>=1.18 && <=1.21.11) {
/*@Mixin(SectionStorage.class)
public interface SerializingRegionBasedStorageAccessorMixin {

*///?}
    //? if (>=1.18 && <=1.20.4) {
    /*@Accessor("worker")
    IOWorker anticrash$getWorker();

    *///?}
    //? if (>=1.20.5 && <=1.21.11) {
    /*@Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorageAccess();

    *///?}
//? if (>=26.1 && <=26.3) {
@Mixin(SectionStorage.class)
public interface SerializingRegionBasedStorageAccessorMixin {
    @Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorageAccess();

//?}

    //? if (>=1.18 && <=1.21.11) {
    /*//? if (>=1.18 && <=1.21.1) {
    /^@Accessor("dirty")
    ^///?}
    //? if (>=1.21.2 && <=1.21.11) {
    /^@Accessor("dirtyChunks")
    ^///?}
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    *///?}
    //? if (>=26.1 && <=26.3) {
    @Accessor("dirtyChunks")
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*//? if (>=1.18 && <=1.21.1) {
    /^@Invoker("writeColumn")
    ^///?}
    //? if (>=1.21.2 && <=1.21.11) {
    /^@Invoker("writeChunk")
    ^///?}
    void anticrash$save(ChunkPos pos);
    *///?}
    //? if (>=26.1 && <=26.3) {
    @Invoker("writeChunk")
    void anticrash$save(ChunkPos pos);
    //?}

}
