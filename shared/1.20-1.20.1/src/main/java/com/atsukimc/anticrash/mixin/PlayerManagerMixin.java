package com.atsukimc.anticrash.mixin;

import com.mojang.authlib.GameProfile;
import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.i18n.MessageCatalog;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "checkCanJoin", at = @At("RETURN"), cancellable = true)
    private void anticrash$loginGate(SocketAddress address, GameProfile profile,
                                     CallbackInfoReturnable<Text> callbackInfo) {
        // Preserve Vanilla ban, whitelist, IP-ban and capacity messages.  Returning a reason from
        // checkCanJoin lets ServerLoginNetworkHandler perform its normal one-shot disconnect;
        // cancelling onPlayerConnect after player creation caused handleDisconnection() twice.
        if (callbackInfo.getReturnValue() != null) return;
        AntiCrashController controller = AntiCrashMod.controller();
        // Reached only after ServerLoginNetworkHandlerMixin's retry window has expired.
        if (controller != null && controller.isLoginBlocked()) {
            callbackInfo.setReturnValue(Text.literal(MessageCatalog.text(
                    controller.config().language, "recovery.login_blocked")));
        }
    }

    @Inject(method = "onPlayerConnect", at = @At("RETURN"))
    private void anticrash$playerConnected(ClientConnection connection, ServerPlayerEntity player, CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onPlayerConnected(player);
    }
}
