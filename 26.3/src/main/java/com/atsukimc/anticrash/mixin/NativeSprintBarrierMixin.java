package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;
import net.minecraft.server.ServerTickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerTickRateManager.class)
public abstract class NativeSprintBarrierMixin {
    @Inject(method = "checkShouldSprintThisTick", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdSprint(CallbackInfoReturnable<Boolean> ci) {
        var controller = AntiCrashMod.controller();
        if (controller != null && controller.isWorldFrozen()) ci.setReturnValue(false);
    }
}
