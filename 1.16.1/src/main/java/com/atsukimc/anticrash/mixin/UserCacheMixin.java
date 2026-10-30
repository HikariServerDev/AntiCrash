package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.integration.carpet.FakePlayerProfileScope;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact 1.16.1 mapped cache API; ordinary authentication/cache lookups are unchanged. */
@Mixin(net.minecraft.util.UserCache.class)
public abstract class UserCacheMixin {
    @Inject(method = "findByName(Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;", at = @At("HEAD"), cancellable = true)
    private void anticrash$checkpointFakePlayerProfile(String name,
            CallbackInfoReturnable<GameProfile> callback) {
        GameProfile profile = FakePlayerProfileScope.lookup(this, name);
        if (profile != null) callback.setReturnValue(profile);
    }
}
