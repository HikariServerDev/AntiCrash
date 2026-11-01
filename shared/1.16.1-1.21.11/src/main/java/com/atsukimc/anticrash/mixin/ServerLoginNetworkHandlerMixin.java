package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;

import net.minecraft.server.network.ServerLoginNetworkHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a login briefly instead of rejecting it while AntiCrash is refusing connections.
 *
 * <p>A checkpoint barrier lasts well under a second, but a player who finished the handshake inside
 * that window was disconnected outright. Vanilla calls {@code acceptPlayer} only from {@code tick}
 * while the handler is in {@code READY_TO_ACCEPT}, and does not change the state there, so skipping
 * the tick simply retries the accept on the next one. {@code loginTicks} is incremented at the end
 * of that same method, so Vanilla's slow-login timeout is paused for exactly the held period too.
 * The handler ticks from {@code MinecraftServer.tick}, which keeps running while the world is
 * frozen, so the retry happens every tick of the freeze.
 *
 * <p>Only AntiCrash's own refusal is retried. Ban, whitelist, capacity and every other Vanilla
 * reason comes from {@code PlayerManager.checkCanJoin}, which a held login never reaches; once the
 * hold ends, or whenever AntiCrash is not refusing at all, Vanilla decides exactly as before.
 */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin {
    @Unique private long anticrash$holdDeadlineNanos;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdLoginWhileFrozen(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null) return;
        anticrash$holdDeadlineNanos =
                controller.loginHoldDeadlineNanos(anticrash$holdDeadlineNanos);
        if (anticrash$holdDeadlineNanos != 0L
                && System.nanoTime() - anticrash$holdDeadlineNanos < 0) {
            callbackInfo.cancel();
        }
    }
}
