package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.ServerPlayer;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.ServerPlayerGameMode;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ServerPlayer;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ServerPlayerGameMode;
//?}

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow @Final protected ServerPlayer player;

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow @Final protected ServerPlayer player;

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "destroyBlock", at = @At("RETURN"))
    private void anticrash$recordSuccessfulBreak(
            BlockPos position, CallbackInfoReturnable<Boolean> info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void anticrash$recordSuccessfulBreak(
            BlockPos position, CallbackInfoReturnable<Boolean> info)
                //?}
            {
        if (!info.getReturnValueZ()) return;
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null)

            //? if (>=1.18 && <=1.21.11) {
            /*controller.recordTransaction(
                    "BLOCK_BREAK", player, "world", "player", position, player.getMainHandItem());
        *///?} else if (>=26.1 && <=26.3) {
            controller.recordTransaction(
                    "BLOCK_BREAK", player, "world", "player", position, player.getMainHandItem());
        //?}

    }
}
