package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.integration.carpet.FakePlayerProfileScope;
import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Exact mapped cache API, verified for {@code >=1.18 <=26.3}: {@code GameProfileCache} for
 * {@code <=1.21.8} and {@code CachedUserNameToIdResolver} for {@code >=1.21.9}. Ordinary
 * authentication/cache lookups are unchanged.
 */
//? if <=1.21.8 {
/*@Mixin(net.minecraft.server.players.GameProfileCache.class)
public abstract class UserCacheMixin {
    @Inject(
            method = "get(Ljava/lang/String;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true)
    private void anticrash$checkpointFakePlayerProfile(
            String name,
            CallbackInfoReturnable<java.util.Optional<com.mojang.authlib.GameProfile>> callback) {
*///?} else {
@Mixin(net.minecraft.server.players.CachedUserNameToIdResolver.class)
public abstract class UserCacheMixin {
    @Inject(
            method = "get(Ljava/lang/String;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true)
    private void anticrash$checkpointFakePlayerProfile(
            String name,
            CallbackInfoReturnable<java.util.Optional<net.minecraft.server.players.NameAndId>>
                    callback) {
//?}
        GameProfile profile = FakePlayerProfileScope.lookup(this, name);

        //? if <=1.21.8 {
        /*if (profile != null) callback.setReturnValue(java.util.Optional.of(profile));
        *///?} else {
        if (profile != null)
            callback.setReturnValue(
                    java.util.Optional.of(new net.minecraft.server.players.NameAndId(profile)));
        //?}

    }
}
