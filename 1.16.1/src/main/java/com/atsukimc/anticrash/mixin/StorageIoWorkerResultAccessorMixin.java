package com.atsukimc.anticrash.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.storage.StorageIoWorker$Result")
public interface StorageIoWorkerResultAccessorMixin {
    @Accessor("nbt")
    CompoundTag anticrash$getNbt();
}
