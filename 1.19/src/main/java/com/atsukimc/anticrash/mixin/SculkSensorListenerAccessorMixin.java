package com.atsukimc.anticrash.mixin;

import net.minecraft.world.event.listener.VibrationListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.19 Vanilla persists vibration/source UUIDs/distance/delay via the listener codec. */
@Mixin(VibrationListener.class)
public interface SculkSensorListenerAccessorMixin {
    @Accessor("vibration")
    VibrationListener.Vibration anticrash$getVibration();

    @Accessor("distance")
    float anticrash$getDistance();

    @Accessor("delay")
    int anticrash$getDelay();

}
