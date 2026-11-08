package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.integration.carpet.FakePlayerProfileScope;
import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact 1.18.2 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.18 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.18.1 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.19 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.19.1 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.19.2 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.19.3 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.19.4 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20.1 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20.2 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20.3 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20.4 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20.5 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.20.6 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.1 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.2 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.3 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.4 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.5 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.6 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.7 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.8 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.9 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.10 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 1.21.11 mapped cache API; ordinary authentication/cache lookups are unchanged. */
//? if (>=1.18 && <=1.21.11) {
/*//? if (>=1.18 && <=1.21.8) {
/^@Mixin(net.minecraft.server.players.GameProfileCache.class)
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^@Mixin(net.minecraft.server.players.CachedUserNameToIdResolver.class)
^///?}
public abstract class UserCacheMixin {

*///?}
    //? if (>=1.18 && <=1.21.8) {
    /*@Inject(
            method = "get(Ljava/lang/String;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true)
    private void anticrash$checkpointFakePlayerProfile(
            String name,
            CallbackInfoReturnable<java.util.Optional<com.mojang.authlib.GameProfile>> callback)
    *///?}
    //? if (>=1.21.9 && <=1.21.11) {
    /*@Inject(
            method = "get(Ljava/lang/String;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true)
    private void anticrash$checkpointFakePlayerProfile(
            String name,
            CallbackInfoReturnable<java.util.Optional<net.minecraft.server.players.NameAndId>>
                    callback)
    *///?}

/** Exact 26.1 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 26.2 mapped cache API; ordinary authentication/cache lookups are unchanged. */

/** Exact 26.3 mapped cache API; ordinary authentication/cache lookups are unchanged. */
//? if (>=26.1 && <=26.3) {
@Mixin(net.minecraft.server.players.CachedUserNameToIdResolver.class)
public abstract class UserCacheMixin {
    @Inject(
            method = "get(Ljava/lang/String;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true)
    private void anticrash$checkpointFakePlayerProfile(
            String name,
            CallbackInfoReturnable<java.util.Optional<net.minecraft.server.players.NameAndId>>
                    callback)
//?}
            {
        GameProfile profile = FakePlayerProfileScope.lookup(this, name);

        //? if (>=1.18 && <=1.21.8) {
        /*if (profile != null) callback.setReturnValue(java.util.Optional.of(profile));
        *///?}
        //? if (>=1.21.9 && <=26.3) {
        if (profile != null)
            callback.setReturnValue(
                    java.util.Optional.of(new net.minecraft.server.players.NameAndId(profile)));
        //?}

    }
}
