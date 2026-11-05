package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.1) {
/*import net.minecraft.util.thread.ProcessorMailbox;
*///?}

import net.minecraft.world.level.chunk.storage.EntityStorage;

//? if (>=1.18 && <=1.20.4) {
/*import net.minecraft.world.level.chunk.storage.IOWorker;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityStorage.class)
public interface EntityChunkDataAccessAccessorMixin {

    //? if (>=1.18 && <=1.20.4) {
    /*@Accessor("worker")
    IOWorker anticrash$getDataLoadWorker();

    *///?} else {
    @Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorage();

    //?}

    //? if (>=1.18 && <=1.21.1) {
    /*@Accessor("entityDeserializerQueue")
    ProcessorMailbox<Runnable> anticrash$getTaskExecutor();
    *///?} else {
    @Accessor("entityDeserializerQueue")
    net.minecraft.util.thread.ConsecutiveExecutor anticrash$getTaskExecutor();
    //?}
}
