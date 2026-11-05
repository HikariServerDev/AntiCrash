package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.i18n.MessageCatalog;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;

//? if (>=1.18 && <=1.18.2) {
/*import net.minecraft.network.chat.TextComponent;
*///?}
//? if (>=1.18 && <=1.21.8) {
/*import com.mojang.authlib.GameProfile;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(PlayerList.class)
public abstract class PlayerManagerMixin {

    *///?}
//? if (>=1.18 && <=1.21.8) {
/*@Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void anticrash$loginGate(
            SocketAddress address,
            GameProfile profile,
            CallbackInfoReturnable<Component> callbackInfo)
                *///?}
//? if (>=1.21.9 && <=1.21.11) {
/*@Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
                    private void anticrash$loginGate(
                            SocketAddress address,
                            net.minecraft.server.players.NameAndId profile,
                            CallbackInfoReturnable<Component> callbackInfo)
*///?}
//? if (>=26.1 && <=26.3) {
@Mixin(PlayerList.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void anticrash$loginGate(
            SocketAddress address,
            net.minecraft.server.players.NameAndId profile,
            CallbackInfoReturnable<Component> callbackInfo)
                //?}
            {
        // Preserve Vanilla ban, whitelist, IP-ban and capacity messages.  Returning a reason from
        // checkCanJoin lets ServerLoginPacketListenerImpl perform its normal one-shot disconnect;
        // cancelling onPlayerConnect after player creation caused handleDisconnection() twice.
        if (callbackInfo.getReturnValue() != null) return;
        AntiCrashController controller = AntiCrashMod.controller();
        // Reached only after ServerLoginNetworkHandlerMixin's retry window has expired.
        if (controller != null && controller.isLoginBlocked()) {

            //? if (>=1.18 && <=1.18.2) {
            /*callbackInfo.setReturnValue(
                    new TextComponent(
                            MessageCatalog.text(
                                    controller.config().language, "recovery.login_blocked")));
            *///?}
            //? if (>=1.19 && <=26.3) {
            callbackInfo.setReturnValue(
                    Component.literal(
                            MessageCatalog.text(
                                    controller.config().language, "recovery.login_blocked")));
            //?}

        }
    }

    //? if (>=1.18 && <=1.20.1) {
    /*@Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void anticrash$playerConnected(
            Connection connection, ServerPlayer player, CallbackInfo callbackInfo)
                *///?}
    //? if (>=1.20.2 && <=26.3) {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void anticrash$playerConnected(
            Connection connection,
            ServerPlayer player,
            net.minecraft.server.network.CommonListenerCookie clientData,
            CallbackInfo callbackInfo)
                //?}
            {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onPlayerConnected(player);
    }
}
