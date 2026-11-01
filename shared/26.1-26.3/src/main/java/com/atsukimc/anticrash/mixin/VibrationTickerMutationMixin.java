package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20 separates ListenerData/Ticker/Callback; countdown and pending selector must be dirty. */
@Mixin(VibrationSystem.Ticker.class)
public interface VibrationTickerMutationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$dirtyVibrationTick(
            Level world,
            VibrationSystem.Data data,
            VibrationSystem.User callback,
            CallbackInfo ci) {
        if (!world.isClientSide())
            callback.getPositionSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
    }
}
