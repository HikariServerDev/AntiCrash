package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;
import net.minecraft.world.tick.TickManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickManager.class)
public abstract class NativeTickManagerBarrierMixin {
    /** The outer server loop continues during recovery; /tick step's counter must not drain. */
    @Inject(method = "step", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdStep(CallbackInfo ci) {
        var controller = AntiCrashMod.controller();
        if (controller != null && controller.isWorldFrozen()) ci.cancel();
    }
}
