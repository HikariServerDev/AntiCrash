package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.19.1 saves TickCount/RecordStartTick/IsPlaying, but omits the periodic game-event phase. */
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxRuntimeMixin {
    @Shadow private int ticksThisSecond;

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveEventPhase(NbtCompound nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt, extension -> extension.putInt("JukeboxEventPhase", ticksThisSecond));
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadEventPhase(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension != null && extension.contains("JukeboxEventPhase", 3)) {
            ticksThisSecond = extension.getInt("JukeboxEventPhase");
        }
    }

    @Inject(
            method =
                    "tick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V",
            at = @At("HEAD"))
    private void anticrash$markPlaybackTick(
            World world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!world.isClient) world.markDirty(pos);
    }
}
