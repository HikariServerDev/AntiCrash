package com.atsukimc.anticrash.mixin;

import net.minecraft.util.thread.TaskExecutor;
import net.minecraft.world.storage.EntityChunkDataAccess;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityChunkDataAccess.class)
public interface EntityChunkDataAccessAccessorMixin {
    @Accessor("dataLoadWorker")
    StorageIoWorker anticrash$getDataLoadWorker();

    @Accessor("taskExecutor")
    TaskExecutor<Runnable> anticrash$getTaskExecutor();
}
