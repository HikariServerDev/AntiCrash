package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerMutationMixin {
    @Shadow public abstract PositionSource getListenerSource();
    @Inject(method = "handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z", at = @At("RETURN"))
    private void anticrash$dirtyListen(ServerLevel world, net.minecraft.core.Holder<GameEvent> event, GameEvent.Context emitter,
            Vec3 origin, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) getListenerSource().getPosition(world).ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
    }
    @Inject(method = "forceScheduleVibration", at = @At("TAIL"))
    private void anticrash$dirtyForcedListen(ServerLevel world, net.minecraft.core.Holder<GameEvent> event, GameEvent.Context emitter,
            Vec3 origin, CallbackInfo ci) {
        getListenerSource().getPosition(world).ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
    }
}
