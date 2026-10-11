package com.atsukimc.anticrash.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.Vibrations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Vibrations.VibrationListener.class)
public abstract class VibrationListenerMutationMixin {
    @Shadow public abstract PositionSource getPositionSource();
    @Inject(method = "listen(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/world/event/GameEvent$Emitter;Lnet/minecraft/util/math/Vec3d;)Z", at = @At("RETURN"))
    private void anticrash$dirtyListen(ServerWorld world, net.minecraft.registry.entry.RegistryEntry<GameEvent> event, GameEvent.Emitter emitter,
            Vec3d origin, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) getPositionSource().getPos(world).ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
    }
    @Inject(method = "forceListen", at = @At("TAIL"))
    private void anticrash$dirtyForcedListen(ServerWorld world, net.minecraft.registry.entry.RegistryEntry<GameEvent> event, GameEvent.Emitter emitter,
            Vec3d origin, CallbackInfo ci) {
        getPositionSource().getPos(world).ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
    }
}
