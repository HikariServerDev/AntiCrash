package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.LiveFileTracker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/** Records every region file a chunk, entity or POI write touches; see {@link LiveFileTracker}. */
@Mixin(RegionFileStorage.class)
public abstract class RegionBasedStorageMixin {
    @Shadow @Final private Path folder;

    @Inject(method = "write", at = @At("HEAD"))
    private void anticrash$recordWrite(ChunkPos pos, CompoundTag nbt, CallbackInfo callbackInfo) {
        LiveFileTracker.global().recordRegionWrite(folder, pos);
    }
}
