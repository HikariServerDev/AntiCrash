package com.atsukimc.anticrash.mixin;

import net.minecraft.world.storage.RegionBasedStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(RegionBasedStorage.class)
public interface RegionBasedStorageAccessorMixin {
    @Accessor("directory")
    Path anticrash$getDirectory();
}
