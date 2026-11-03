package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.1) {
/*import net.minecraft.util.thread.ProcessorMailbox;
*///?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.chunk.storage.EntityStorage;
*///?}

//? if (>=1.18 && <=1.20.4) {
/*import net.minecraft.world.level.chunk.storage.IOWorker;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.chunk.storage.EntityStorage;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(EntityStorage.class)
public interface EntityChunkDataAccessAccessorMixin {

    *///?}
//? if (>=1.18 && <=1.20.4) {
/*@Accessor("worker")
    IOWorker anticrash$getDataLoadWorker();

    *///?} else if (>=1.20.5 && <=1.21.10) {
/*@Accessor("simpleRegionStorage")
        net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorage();
*///?} else if 1.21.11 {
/*@Accessor("simpleRegionStorage")
        net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorage();
*///?} else if (>=26.1 && <=26.3) {
@Mixin(EntityStorage.class)
public interface EntityChunkDataAccessAccessorMixin {
    @Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorage();

    //?}

    //? if (>=1.18 && <=1.21.1) {
    /*@Accessor("entityDeserializerQueue")
    ProcessorMailbox<Runnable> anticrash$getTaskExecutor();
    *///?} else if (>=1.21.2 && <=1.21.11) {
    /*@Accessor("entityDeserializerQueue")
        net.minecraft.util.thread.ConsecutiveExecutor anticrash$getTaskExecutor();
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("entityDeserializerQueue")
    net.minecraft.util.thread.ConsecutiveExecutor anticrash$getTaskExecutor();
    //?}

}
