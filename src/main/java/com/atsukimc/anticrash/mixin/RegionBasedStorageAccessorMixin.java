package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.chunk.storage.RegionFileStorage;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(RegionFileStorage.class)
public interface RegionBasedStorageAccessorMixin {
    @Accessor("folder")
    Path anticrash$getDirectory();
    *///?} else if (>=26.1 && <=26.3) {
@Mixin(RegionFileStorage.class)
public interface RegionBasedStorageAccessorMixin {
    @Accessor("folder")
    Path anticrash$getDirectory();
    //?}

}
