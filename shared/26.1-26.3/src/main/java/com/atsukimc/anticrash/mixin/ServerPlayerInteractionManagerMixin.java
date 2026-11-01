package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void anticrash$recordSuccessfulBreak(
            BlockPos position, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ()) return;
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null)
            controller.recordTransaction(
                    "BLOCK_BREAK", player, "world", "player", position, player.getMainHandItem());
    }
}
