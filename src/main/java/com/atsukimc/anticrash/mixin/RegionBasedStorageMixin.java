package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.LiveFileTracker;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.CompoundTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.RegionFileStorage;
*///?}

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/** Records every region file a chunk, entity or POI write touches; see {@link LiveFileTracker}. */
//? if (>=1.18 && <=1.21.11) {
@Mixin(RegionFileStorage.class)
public abstract class RegionBasedStorageMixin {
    @Shadow @Final private Path folder;

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(RegionFileStorage.class)
    public abstract class RegionBasedStorageMixin {
        @Shadow @Final private Path folder;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "write", at = @At("HEAD"))
    private void anticrash$recordWrite(ChunkPos pos, CompoundTag nbt, CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "write", at = @At("HEAD"))
                    private void anticrash$recordWrite(ChunkPos pos, CompoundTag nbt, CallbackInfo callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        LiveFileTracker.global().recordRegionWrite(folder, pos);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*LiveFileTracker.global().recordRegionWrite(folder, pos);
*///?}

    }
}
