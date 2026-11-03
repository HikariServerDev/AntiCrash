//? if (>=1.19.1 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.19.1 && <=1.20.6) {
/^import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
^///?}


//? if (>=1.19.1 && <=1.21.11) {
/^import net.minecraft.block.BlockState;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.19.1 && <=1.21.11) {
/^import net.minecraft.block.entity.JukeboxBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.19.1 && <=1.20.6) {
/^import net.minecraft.nbt.NbtCompound;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
^///?}

//? if (>=1.19.1 && <=1.21.11) {
/^import net.minecraft.util.math.BlockPos;
^///?}

//? if (>=1.19.1 && <=1.21.11) {
/^import net.minecraft.world.World;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}


import org.spongepowered.asm.mixin.Mixin;
//? if (>=1.19.1 && <=1.20.6) {
/^import org.spongepowered.asm.mixin.Shadow;
^///?}

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* 1.19.1 saves TickCount/RecordStartTick/IsPlaying, but omits the periodic game-event phase. ^/


/^* 1.21: Vanilla saves ticks_since_song_started, including the JUKEBOX_PLAY modulo-20 phase. ^/
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxRuntimeMixin {

//? if (>=1.19.1 && <=1.20.6) {
/^@Shadow private int ticksThisSecond;
^///?}



//? if (>=1.19.1 && <=1.20.4) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveEventPhase(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.20.6) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveEventPhase(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21 && <=1.21.11) {
/^@Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(
            World world,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukebox,
            CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(
            Level world,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukebox,
            CallbackInfo ci)
^///?}
{

//? if (>=1.19.1 && <=1.20.6) {
/^if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
^///?}


//? if (>=1.19.1 && <=1.20.6) {
/^RuntimeExtensionNbt.write(
                nbt, extension -> extension.putInt("JukeboxEventPhase", ticksThisSecond));
    }
^///?}



//? if (>=1.19.1 && <=1.20.4) {
/^@Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadEventPhase(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.20.6) {
/^@Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadEventPhase(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci)
^///?}
//? if (>=1.19.1 && <=1.20.6) {
/^{
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
^///?}


//? if (>=1.19.1 && <=1.20.6) {
/^if (extension != null && extension.contains("JukeboxEventPhase", 3)) {
            ticksThisSecond = extension.getInt("JukeboxEventPhase");
        }
    }
^///?}



//? if (>=1.19.1 && <=1.19.3) {
/^@Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(
            World world,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukebox,
            CallbackInfo ci)
^///?}
//? if (>=1.19.4 && <=1.20.6) {
/^@Inject(
            method =
                    "tick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At("HEAD"))
    private void anticrash$markPlaybackTick(
            World world, BlockPos pos, BlockState state, CallbackInfo ci)
^///?}
//? if (>=1.19.1 && <=1.20.6) {
/^{
        if (!world.isClient)
^///?}
//? if (>=1.21 && <=1.21.8) {
/^if (!world.isClient && jukebox.getManager().getSong() != null)
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^if (!world.isClient() && jukebox.getManager().getSong() != null)
^///?}
//? if (>=1.19.1 && <=1.21.11) {
/^world.markDirty(pos);
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!world.isClientSide() && jukebox.getSongPlayer().getSong() != null)
            world.blockEntityChanged(pos);
^///?}

    }
}
*///?}
