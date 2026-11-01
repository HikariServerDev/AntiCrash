package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void anticrash$freezeWorld(
            BooleanSupplier shouldKeepTicking, CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null && controller.isWorldFrozen()) callbackInfo.cancel();
    }

    /**
     * The checkpoint cut flushed every entity before its Runtime Capsule; see {@link CutSave}.
     * Nothing has changed since, so the save inside the cut's MinecraftServer#save is skipped. A
     * wrap rather than a redirect, so another mod may hook the same call.
     */
    @WrapOperation(
            method = "save",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/world/ServerEntityManager;save()V"))
    private void anticrash$entitiesAlreadyFlushedAtCut(
            ServerEntityManager<?> manager, Operation<Void> original) {
        if (!CutSave.active()) original.call(manager);
    }
}
