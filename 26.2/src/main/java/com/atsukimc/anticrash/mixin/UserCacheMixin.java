package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.integration.carpet.FakePlayerProfileScope;
import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact 26.2 mapped cache API; ordinary authentication/cache lookups are unchanged. */
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
        GameProfile profile = FakePlayerProfileScope.lookup(this, name);
        if (profile != null)
            callback.setReturnValue(
                    java.util.Optional.of(new net.minecraft.server.players.NameAndId(profile)));
    }
}
