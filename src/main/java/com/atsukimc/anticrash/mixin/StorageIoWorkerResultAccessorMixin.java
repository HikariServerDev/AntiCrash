package com.atsukimc.anticrash.mixin;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore")
public interface StorageIoWorkerResultAccessorMixin {
    @Accessor("data")
    CompoundTag anticrash$getNbt();
}
