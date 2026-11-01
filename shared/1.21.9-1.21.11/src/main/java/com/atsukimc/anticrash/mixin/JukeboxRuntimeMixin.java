package com.atsukimc.anticrash.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21: Vanilla saves ticks_since_song_started, including the JUKEBOX_PLAY modulo-20 phase. */
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxRuntimeMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(
            World world,
            BlockPos pos,
            BlockState state,
            JukeboxBlockEntity jukebox,
            CallbackInfo ci) {
        if (!world.isClient() && jukebox.getManager().getSong() != null) world.markDirty(pos);
    }
}
