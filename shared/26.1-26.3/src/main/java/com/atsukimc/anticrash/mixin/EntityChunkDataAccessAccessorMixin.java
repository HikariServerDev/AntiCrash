package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.chunk.storage.EntityStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityStorage.class)
public interface EntityChunkDataAccessAccessorMixin {
    @Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorage();


    @Accessor("entityDeserializerQueue")
    net.minecraft.util.thread.ConsecutiveExecutor anticrash$getTaskExecutor();
}
