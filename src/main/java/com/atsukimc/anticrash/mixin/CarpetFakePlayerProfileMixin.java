//? if (>=1.20.2 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.integration.carpet.FakePlayerProfileScope;
import com.mojang.authlib.GameProfile;

//? if (>=1.21.9 && <=26.3) {
/^import net.minecraft.server.MinecraftServer;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.20.2 && <=1.21.8) {
/^import java.util.Optional;
^///?}
//? if (>=1.21.9 && <=26.3) {
/^import java.util.UUID;
^///?}

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
//? if (>=1.20.2 && <=1.21.8) {
/^import java.util.function.Consumer;
^///?}


/^*
 * Optional Carpet adapter. Verified against Carpet 1.4.120 / 1.4.128 / 1.4.140 / 1.4.147 / 1.4.161
 * / 1.4.177 sources. Only an AntiCrash-owned creation scope substitutes a completed saved profile.
 * Normal Carpet profile IO and its executor are untouched. Unknown factory shapes remain
 * fail-closed through the existing UUID/readiness checks; no reflection edits to player/cache maps
 * are performed.
 ^/


/^*
 * Optional Carpet adapter. Verified against Carpet 1.4.185 / 1.4.194 / v26.1 / v26.3 sources. Only
 * an AntiCrash-owned creation scope substitutes a completed saved profile. Normal Carpet profile IO
 * and its executor are untouched. Unknown factory shapes remain fail-closed through the existing
 * UUID/readiness checks; no reflection edits to player/cache maps are performed.
 ^/
@Pseudo
@Mixin(targets = "carpet.patches.EntityPlayerMPFake", remap = false)
public abstract class CarpetFakePlayerProfileMixin {

//? if (>=1.20.2 && <=1.21.8) {
/^@Inject(
            method = "fetchGameProfile",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void anticrash$savedProfile(
            String name,
            CallbackInfoReturnable<CompletableFuture<Optional<GameProfile>>> callback)
^///?}
//? if (>=1.21.9 && <=26.3) {
/^@Inject(
            method = "fetchGameProfile",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void anticrash$savedProfile(
            MinecraftServer server,
            UUID requestedId,
            CallbackInfoReturnable<CompletableFuture<GameProfile>> callback)
^///?}
{

//? if (>=1.20.2 && <=1.21.8) {
/^GameProfile saved = FakePlayerProfileScope.creationProfileForName(name);
^///?}
//? if (>=1.21.9 && <=26.3) {
/^GameProfile saved = FakePlayerProfileScope.creationProfile(server);
^///?}


//? if (>=1.20.2 && <=1.21.8) {
/^if (saved != null)
            callback.setReturnValue(CompletableFuture.completedFuture(Optional.of(saved)));
^///?}
//? if (>=1.21.9 && <=26.3) {
/^if (saved != null) callback.setReturnValue(CompletableFuture.completedFuture(saved));
^///?}

    }


//? if (>=1.20.2 && <=1.21.8) {
/^@Redirect(
            method = "createFake",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 0,
            remap = false)
    private static CompletableFuture<Optional<GameProfile>> anticrash$finishSavedProfile(
            CompletableFuture<Optional<GameProfile>> profile,
            BiConsumer<? super Optional<GameProfile>, ? super Throwable> action,
            Executor executor)
^///?}
//? if (>=1.21.9 && <=26.3) {
/^@Redirect(
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
            Executor executor)
^///?}
{
        return FakePlayerProfileScope.completeCreation(profile, action, executor);
    }


//? if (>=1.20.2 && <=1.21.8) {
/^@Redirect(
            method = "createFake",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 0,
            remap = false)
    private static CompletableFuture<Void> anticrash$acceptSavedProfile(
            CompletableFuture<Optional<GameProfile>> profile,
            Consumer<? super Optional<GameProfile>> action,
            Executor executor) {
        return FakePlayerProfileScope.acceptCreation(profile, action, executor);
    }
^///?}



//? if (>=1.20.2 && <=1.21.8) {
/^@Redirect(
            method = "createFake",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 0,
            remap = false)
    private static CompletableFuture<Void> anticrash$acceptSavedProfileImmediately(
            CompletableFuture<Optional<GameProfile>> profile,
            Consumer<? super Optional<GameProfile>> action) {
        return FakePlayerProfileScope.acceptCreation(profile, action);
    }
^///?}

}
*///?}
