package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.network.DeferredPayloads;

import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.2 moved payload handling to the common protocol handler; configuration has no player. */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonPayloadMixin {
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferPlayPayload(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        Object self = this;
        var controller = AntiCrashMod.controller();
        if (!(self instanceof ServerPlayNetworkHandler play)
                || controller == null
                || !controller.isRecoveryFrozen()) return;
        if (DeferredPayloads.immediate(String.valueOf(packet.payload().id()))) return;
        controller.deferCustomPayload(play, packet);
        ci.cancel();
    }
}
