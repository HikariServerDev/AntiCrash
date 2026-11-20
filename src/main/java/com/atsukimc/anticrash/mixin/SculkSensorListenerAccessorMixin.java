//? if <=1.19.2 {
/*package com.atsukimc.anticrash.mixin;

//? if <=1.18.2 {
/^import java.util.Optional;
import net.minecraft.world.level.gameevent.GameEvent;
^///?}

import net.minecraft.world.level.gameevent.vibrations.VibrationListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/^*
 * From {@code >=1.19} Vanilla persists vibration/source UUIDs/distance/delay via the listener
 * codec.
 ^/
@Mixin(VibrationListener.class)
public interface SculkSensorListenerAccessorMixin {
    //? if <=1.18.2 {
    /^@Accessor("receivingEvent")
    Optional<GameEvent> anticrash$getEvent();

    @Accessor("receivingEvent")
    void anticrash$setEvent(Optional<GameEvent> event);

    @Accessor("receivingDistance")
    int anticrash$getDistance();

    @Accessor("receivingDistance")
    void anticrash$setDistance(int distance);

    @Accessor("travelTimeInTicks")
    void anticrash$setDelay(int delay);

    ^///?} else {
    @Accessor("receivingEvent")
    VibrationListener.ReceivingEvent anticrash$getVibration();

    @Accessor("receivingDistance")
    float anticrash$getDistance();

    //?}

    @Accessor("travelTimeInTicks")
    int anticrash$getDelay();
}
*///?}
