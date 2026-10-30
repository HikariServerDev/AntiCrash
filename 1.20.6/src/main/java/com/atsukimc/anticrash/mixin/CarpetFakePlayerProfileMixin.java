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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Optional Carpet adapter. Verified against Carpet 1.4.120 / 1.4.128 / 1.4.140 / 1.4.147 / 1.4.161 / 1.4.177 sources.
 * Only an AntiCrash-owned creation scope substitutes a completed saved profile. Normal Carpet
 * profile IO and its executor are untouched. Unknown factory shapes remain fail-closed through
 * the existing UUID/readiness checks; no reflection edits to player/cache maps are performed.
 */
@Pseudo
@Mixin(targets = "carpet.patches.EntityPlayerMPFake", remap = false)
public abstract class CarpetFakePlayerProfileMixin {
    @Inject(method = "fetchGameProfile", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void anticrash$savedProfile(String name,
            CallbackInfoReturnable<CompletableFuture<Optional<GameProfile>>> callback) {
        GameProfile saved = FakePlayerProfileScope.creationProfileForName(name);
        if (saved != null) callback.setReturnValue(CompletableFuture.completedFuture(Optional.of(saved)));
    }

    @Redirect(method = "createFake", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), require = 0, remap = false)
    private static CompletableFuture<Optional<GameProfile>> anticrash$finishSavedProfile(
            CompletableFuture<Optional<GameProfile>> profile,
            BiConsumer<? super Optional<GameProfile>, ? super Throwable> action, Executor executor) {
        return FakePlayerProfileScope.completeCreation(profile, action, executor);
    }

    @Redirect(method = "createFake", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), require = 0, remap = false)
    private static CompletableFuture<Void> anticrash$acceptSavedProfile(
            CompletableFuture<Optional<GameProfile>> profile,
            Consumer<? super Optional<GameProfile>> action, Executor executor) {
        return FakePlayerProfileScope.acceptCreation(profile, action, executor);
    }

    @Redirect(method = "createFake", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"), require = 0, remap = false)
    private static CompletableFuture<Void> anticrash$acceptSavedProfileImmediately(
            CompletableFuture<Optional<GameProfile>> profile, Consumer<? super Optional<GameProfile>> action) {
        return FakePlayerProfileScope.acceptCreation(profile, action);
    }
}
