package com.atsukimc.anticrash.mixin;

import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.listener.SculkSensorListener;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(SculkSensorListener.class)
public abstract class SculkSensorListenerMixin {
    @Shadow @Final protected PositionSource positionSource;
    @Shadow protected Optional<GameEvent> event;

    /**
     * The pending vibration counts down without the chunk being told its data changed, so the chunk
     * would not be saved and the countdown would be lost. Marked before the countdown step, so the
     * tick that delivers (and clears) the vibration is saved as well.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void anticrash$markPendingVibrationDirty(World world, CallbackInfo callbackInfo) {
        if (world.isClient || event.isEmpty()) return;
        positionSource.getPos(world).ifPresent(world::markDirty);
    }

    /**
     * A vibration heard after the sensor already ticked this tick must reach a cut at this tick's
     * end.
     */
    @Inject(
            method =
                    "listen(Lnet/minecraft/world/World;Lnet/minecraft/world/event/GameEvent;"
                        + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("TAIL"))
    private void anticrash$markHeardVibrationDirty(
            World world,
            GameEvent heard,
            net.minecraft.util.math.BlockPos pos,
            net.minecraft.util.math.BlockPos sourcePos,
            CallbackInfo callbackInfo) {
        if (!world.isClient) world.markDirty(pos);
    }
}
