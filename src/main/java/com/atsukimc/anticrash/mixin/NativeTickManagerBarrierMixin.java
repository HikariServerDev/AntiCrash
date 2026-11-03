//? if (>=1.20.3 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.world.TickRateManager;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.TickRateManager;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.20.3 && <=1.21.11) {
/^@Mixin(TickRateManager.class)
public abstract class NativeTickManagerBarrierMixin {
    /^¹* The outer server loop continues during recovery; /tick step's counter must not drain. ¹^/
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdStep(CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(TickRateManager.class)
public abstract class NativeTickManagerBarrierMixin {
    /^¹* The outer server loop continues during recovery; /tick step's counter must not drain. ¹^/
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdStep(CallbackInfo ci)
^///?}
{
        var controller = AntiCrashMod.controller();
        if (controller != null && controller.isWorldFrozen()) ci.cancel();
    }
}
*///?}
