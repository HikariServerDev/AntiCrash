package com.atsukimc.anticrash.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.Vibrations;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20 separates ListenerData/Ticker/Callback; countdown and pending selector must be dirty. */
@Mixin(Vibrations.Ticker.class)
public interface VibrationTickerMutationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$dirtyVibrationTick(
            World world,
            Vibrations.ListenerData data,
            Vibrations.Callback callback,
            CallbackInfo ci) {
        if (!world.isClient())
            callback.getPositionSource()
                    .getPos(world)
                    .ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
    }
}
