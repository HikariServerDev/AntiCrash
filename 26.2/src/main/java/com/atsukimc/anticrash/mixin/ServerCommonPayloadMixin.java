package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.network.DeferredPayloads;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.2 moved payload handling to the common protocol handler; configuration has no player. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPayloadMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferPlayPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        Object self = this;
        var controller = AntiCrashMod.controller();
        if (!(self instanceof ServerGamePacketListenerImpl play) || controller == null || !controller.isRecoveryFrozen()) return;
        if (DeferredPayloads.immediate(String.valueOf(packet.payload().type().id()))) return;
        controller.deferCustomPayload(play, packet);
        ci.cancel();
    }
}
