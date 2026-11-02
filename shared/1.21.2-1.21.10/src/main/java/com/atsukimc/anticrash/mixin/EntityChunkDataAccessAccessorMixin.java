package com.atsukimc.anticrash.mixin;

import net.minecraft.world.storage.EntityChunkDataAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityChunkDataAccess.class)
public interface EntityChunkDataAccessAccessorMixin {
    @Accessor("storage")
    net.minecraft.world.storage.ChunkPosKeyedStorage anticrash$getStorage();

    @Accessor("taskExecutor")
    net.minecraft.util.thread.SimpleConsecutiveExecutor anticrash$getTaskExecutor();
}
