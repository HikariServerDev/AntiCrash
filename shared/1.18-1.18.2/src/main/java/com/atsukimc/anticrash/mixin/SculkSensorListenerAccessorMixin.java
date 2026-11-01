package com.atsukimc.anticrash.mixin;

import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.listener.SculkSensorListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(SculkSensorListener.class)
public interface SculkSensorListenerAccessorMixin {
    @Accessor("event")
    Optional<GameEvent> anticrash$getEvent();

    @Accessor("event")
    void anticrash$setEvent(Optional<GameEvent> event);

    @Accessor("distance")
    int anticrash$getDistance();

    @Accessor("distance")
    void anticrash$setDistance(int distance);

    @Accessor("delay")
    int anticrash$getDelay();

    @Accessor("delay")
    void anticrash$setDelay(int delay);
}
