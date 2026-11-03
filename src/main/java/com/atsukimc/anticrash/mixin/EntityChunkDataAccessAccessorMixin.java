package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.util.thread.TaskExecutor;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.storage.EntityChunkDataAccess;
//?}

//? if (>=1.18 && <=1.20.4) {
import net.minecraft.world.storage.StorageIoWorker;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.EntityStorage;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
@Mixin(EntityChunkDataAccess.class)
public interface EntityChunkDataAccessAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.20.4) {
    @Accessor("dataLoadWorker")
    StorageIoWorker anticrash$getDataLoadWorker();

    //?}
    //? if (>=1.20.5 && <=1.21.10) {
    /*@Accessor("storage")
        net.minecraft.world.storage.ChunkPosKeyedStorage anticrash$getStorage();
*///?}
    //? if 1.21.11 {
    /*@Accessor("storage")
        net.minecraft.world.storage.VersionedChunkStorage anticrash$getStorage();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(EntityStorage.class)
    public interface EntityChunkDataAccessAccessorMixin {
        @Accessor("simpleRegionStorage")
        net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorage();
*///?}

    //? if (>=1.18 && <=1.21.1) {
    @Accessor("taskExecutor")
    TaskExecutor<Runnable> anticrash$getTaskExecutor();
    //?}
    //? if (>=1.21.2 && <=1.21.11) {
    /*@Accessor("taskExecutor")
        net.minecraft.util.thread.SimpleConsecutiveExecutor anticrash$getTaskExecutor();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("entityDeserializerQueue")
        net.minecraft.util.thread.ConsecutiveExecutor anticrash$getTaskExecutor();
*///?}

}
