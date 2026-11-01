package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.i18n.MessageCatalog;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void anticrash$loginGate(
            SocketAddress address,
            net.minecraft.server.players.NameAndId profile,
            CallbackInfoReturnable<Component> callbackInfo) {
        // Preserve Vanilla ban, whitelist, IP-ban and capacity messages.  Returning a reason from
        // checkCanJoin lets ServerLoginNetworkHandler perform its normal one-shot disconnect;
        // cancelling onPlayerConnect after player creation caused handleDisconnection() twice.
        if (callbackInfo.getReturnValue() != null) return;
        AntiCrashController controller = AntiCrashMod.controller();
        // Reached only after ServerLoginNetworkHandlerMixin's retry window has expired.
        if (controller != null && controller.isLoginBlocked()) {
            callbackInfo.setReturnValue(
                    Component.literal(
                            MessageCatalog.text(
                                    controller.config().language, "recovery.login_blocked")));
        }
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void anticrash$playerConnected(
            Connection connection,
            ServerPlayer player,
            net.minecraft.server.network.CommonListenerCookie clientData,
            CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onPlayerConnected(player);
    }
}
