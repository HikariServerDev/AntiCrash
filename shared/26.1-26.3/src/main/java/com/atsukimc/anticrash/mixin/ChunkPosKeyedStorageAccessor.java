package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleRegionStorage.class)
public interface ChunkPosKeyedStorageAccessor {
    @Accessor("worker") IOWorker anticrash$getWorker();
}
