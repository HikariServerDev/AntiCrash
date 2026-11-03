//? if (>=1.20.2 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.network.DeferredPayloads;

//? if (>=1.20.2 && <=1.21.11) {
/^import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
^///?}

//? if (>=1.20.2 && <=1.21.11) {
/^import net.minecraft.server.network.ServerCommonNetworkHandler;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.network.ServerCommonPacketListenerImpl;
^///?}

//? if (>=1.20.2 && <=1.21.11) {
/^import net.minecraft.server.network.ServerPlayNetworkHandler;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.network.ServerGamePacketListenerImpl;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* 1.20.2 moved payload handling to the common protocol handler; configuration has no player. ^/
//? if (>=1.20.2 && <=1.21.11) {
/^@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonPayloadMixin {
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferPlayPayload(CustomPayloadC2SPacket packet, CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPayloadMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferPlayPayload(
            ServerboundCustomPayloadPacket packet, CallbackInfo ci)
^///?}
{
        Object self = this;
        var controller = AntiCrashMod.controller();

//? if (>=1.20.2 && <=1.21.11) {
/^if (!(self instanceof ServerPlayNetworkHandler play)
                || controller == null
                || !controller.isRecoveryFrozen())
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!(self instanceof ServerGamePacketListenerImpl play)
                || controller == null
                || !controller.isRecoveryFrozen())
^///?}
return;

//? if (>=1.20.2 && <=1.20.4) {
/^if (DeferredPayloads.immediate(String.valueOf(packet.payload().id())))
^///?}
//? if (>=1.20.5 && <=1.21.11) {
/^if (DeferredPayloads.immediate(String.valueOf(packet.payload().getId().id())))
^///?}
//? if (>=26.1 && <=26.3) {
/^if (DeferredPayloads.immediate(String.valueOf(packet.payload().type().id())))
^///?}
return;
        controller.deferCustomPayload(play, packet);
        ci.cancel();
    }
}
*///?}
