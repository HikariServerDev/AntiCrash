//? if (>=1.18 && <=1.19.2) {
package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.18.2) {
import net.minecraft.world.event.GameEvent;
//?}

//? if (>=1.18 && <=1.18.2) {
import net.minecraft.world.event.listener.SculkSensorListener;
//?}
//? if (>=1.19 && <=1.19.2) {
/*import net.minecraft.world.event.listener.VibrationListener;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.18.2) {
import java.util.Optional;

//?}

//? if (>=1.18 && <=1.18.2) {
@Mixin(SculkSensorListener.class)
public interface SculkSensorListenerAccessorMixin {
    @Accessor("event")
    Optional<GameEvent> anticrash$getEvent();

    //?}

    /** 1.19 Vanilla persists vibration/source UUIDs/distance/delay via the listener codec. */
    //? if (>=1.19 && <=1.19.2) {
    /*@Mixin(VibrationListener.class)
    public interface SculkSensorListenerAccessorMixin {
        @Accessor("vibration")
        VibrationListener.Vibration anticrash$getVibration();
*///?}

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("event")
    void anticrash$setEvent(Optional<GameEvent> event);

    //?}

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("distance")
    int anticrash$getDistance();

    //?}

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("distance")
    void anticrash$setDistance(int distance);

    //?}
    //? if (>=1.19 && <=1.19.2) {
    /*@Accessor("distance")
        float anticrash$getDistance();
*///?}

    @Accessor("delay")
    int anticrash$getDelay();

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("delay")
    void anticrash$setDelay(int delay);
    //?}

}
//?}
