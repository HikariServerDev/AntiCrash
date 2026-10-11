package com.atsukimc.anticrash.mixin;

import net.minecraft.world.storage.VersionedChunkStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VersionedChunkStorage.class)
public interface ChunkPosKeyedStorageAccessor {
    @Accessor("worker") StorageIoWorker anticrash$getWorker();
}
