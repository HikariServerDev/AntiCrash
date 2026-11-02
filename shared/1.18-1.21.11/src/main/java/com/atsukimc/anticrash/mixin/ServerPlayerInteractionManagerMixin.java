package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void anticrash$recordSuccessfulBreak(
            BlockPos position, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ()) return;
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null)
            controller.recordTransaction(
                    "BLOCK_BREAK", player, "world", "player", position, player.getMainHandStack());
    }
}
