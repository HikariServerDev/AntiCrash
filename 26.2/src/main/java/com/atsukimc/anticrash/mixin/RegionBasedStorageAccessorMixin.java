package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

@Mixin(RegionFileStorage.class)
public interface RegionBasedStorageAccessorMixin {
    @Accessor("folder")
    Path anticrash$getDirectory();
}
