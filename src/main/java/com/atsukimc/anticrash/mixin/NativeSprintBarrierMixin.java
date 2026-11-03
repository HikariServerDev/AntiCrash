//? if (>=1.20.3 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.server.ServerTickManager;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.ServerTickRateManager;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.20.3 && <=1.21.11) {
/^@Mixin(ServerTickManager.class)
public abstract class NativeSprintBarrierMixin {
    @Inject(method = "sprint", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdSprint(CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(ServerTickRateManager.class)
public abstract class NativeSprintBarrierMixin {
    @Inject(method = "checkShouldSprintThisTick", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdSprint(CallbackInfoReturnable<Boolean> ci)
^///?}
{
        var controller = AntiCrashMod.controller();
        if (controller != null && controller.isWorldFrozen()) ci.setReturnValue(false);
    }
}
*///?}
