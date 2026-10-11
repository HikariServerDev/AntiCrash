package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21: Vanilla saves ticks_since_song_started, including the JUKEBOX_PLAY modulo-20 phase. */
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxRuntimeMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markPlaybackTick(Level world, BlockPos pos, BlockState state,
                                                   JukeboxBlockEntity jukebox, CallbackInfo ci) {
        if (!world.isClientSide() && jukebox.getSongPlayer().getSong() != null) world.blockEntityChanged(pos);
    }
}
