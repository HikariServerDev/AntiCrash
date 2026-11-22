//? if >=1.19.1 {
package com.atsukimc.anticrash.mixin;

//? if <=1.20.6 {
/*import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.runtime.VersionNbt;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Shadow;
*///?}

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.19.1 saves TickCount/RecordStartTick/IsPlaying, but omits the periodic game-event phase.
 *
 * <p>1.21: Vanilla saves ticks_since_song_started, including the JUKEBOX_PLAY modulo-20 phase.
 */
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxRuntimeMixin {
    //? if <=1.20.6 {
    /*@Shadow private int ticksSinceLastEvent;

    //? if <=1.20.4 {
    /^@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveEventPhase(CompoundTag nbt, CallbackInfo ci) {
    ^///?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveEventPhase(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
    //?}
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;

        RuntimeExtensionNbt.write(
                nbt, extension -> extension.putInt("JukeboxEventPhase", ticksSinceLastEvent));
    }

    //? if <=1.20.4 {
    /^@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadEventPhase(CompoundTag nbt, CallbackInfo ci) {
    ^///?} else {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadEventPhase(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
    //?}
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);

        if (extension != null && VersionNbt.contains(extension, "JukeboxEventPhase", 3)) {
            ticksSinceLastEvent = VersionNbt.getInt(extension, "JukeboxEventPhase");
        }
    }

    //? if <=1.19.3 {
    /^@Inject(method = "playRecordTick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(
            Level world,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukebox,
            CallbackInfo ci) {
    ^///?} else {
    @Inject(
            method =
                    "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"))
    private void anticrash$markPlaybackTick(
            Level world, BlockPos pos, BlockState state, CallbackInfo ci) {
    //?}
        if (!world.isClientSide()) world.blockEntityChanged(pos);
    }
    *///?} else {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(
            Level world,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukebox,
            CallbackInfo ci) {
        if (!world.isClientSide() && jukebox.getSongPlayer().getSong() != null)
            world.blockEntityChanged(pos);
    }
    //?}
}
//?}
