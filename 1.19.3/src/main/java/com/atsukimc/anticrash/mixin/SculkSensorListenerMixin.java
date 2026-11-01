package com.atsukimc.anticrash.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.listener.VibrationListener;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VibrationListener.class)
public abstract class SculkSensorListenerMixin {
    @Shadow @Final protected PositionSource positionSource;

    /**
     * The pending vibration counts down without the chunk being told its data changed, so the chunk
     * would not be saved and the countdown would be lost. Marked before the countdown step, so the
     * tick that delivers (and clears) the vibration is saved as well.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void anticrash$markPendingVibrationDirty(World world, CallbackInfo callbackInfo) {
        // The selector may hold a same-tick candidate while the current vibration is still null.
        if (world.isClient) return;
        positionSource.getPos(world).ifPresent(pos -> world.markDirty(new BlockPos(pos)));
    }

    /**
     * A vibration heard after the sensor already ticked this tick must reach a cut at this tick's
     * end.
     */
    @Inject(
            method =
                    "listen(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/event/GameEvent;Lnet/minecraft/world/event/GameEvent$Emitter;Lnet/minecraft/util/math/Vec3d;)Z",
            at = @At("RETURN"))
    private void anticrash$markHeardVibrationDirty(
            ServerWorld world,
            GameEvent event,
            GameEvent.Emitter emitter,
            net.minecraft.util.math.Vec3d origin,
            CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) {
            positionSource.getPos(world).ifPresent(pos -> world.markDirty(new BlockPos(pos)));
        }
    }
}
