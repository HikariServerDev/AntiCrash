package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.integration.carpet.FakePlayerProfileScope;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Optional Carpet adapter. Verified against Carpet 1.4.185 / 1.4.194 / v26.1 / v26.3 sources. Only
 * an AntiCrash-owned creation scope substitutes a completed saved profile. Normal Carpet profile IO
 * and its executor are untouched. Unknown factory shapes remain fail-closed through the existing
 * UUID/readiness checks; no reflection edits to player/cache maps are performed.
 */
@Pseudo
@Mixin(targets = "carpet.patches.EntityPlayerMPFake", remap = false)
public abstract class CarpetFakePlayerProfileMixin {
    @Inject(
            method = "fetchGameProfile",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void anticrash$savedProfile(
            MinecraftServer server,
            UUID requestedId,
            CallbackInfoReturnable<CompletableFuture<GameProfile>> callback) {
        GameProfile saved = FakePlayerProfileScope.creationProfile(server);
        if (saved != null) callback.setReturnValue(CompletableFuture.completedFuture(saved));
    }

    @Redirect(
            method = "createFake",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 0,
            remap = false)
    private static CompletableFuture<GameProfile> anticrash$finishSavedProfile(
            CompletableFuture<GameProfile> profile,
            BiConsumer<? super GameProfile, ? super Throwable> action,
            Executor executor) {
        return FakePlayerProfileScope.completeCreation(profile, action, executor);
    }
}
