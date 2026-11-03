//? if (>=1.18 && <=1.19.2) {
package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.18.2) {
import net.minecraft.world.level.gameevent.GameEvent;
//?}

//? if (>=1.18 && <=1.18.2) {
import net.minecraft.world.level.gameevent.vibrations.VibrationListener;
//?}
//? if (>=1.19 && <=1.19.2) {
/*import net.minecraft.world.level.gameevent.vibrations.VibrationListener;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.18.2) {
import java.util.Optional;

//?}

//? if (>=1.18 && <=1.18.2) {
@Mixin(VibrationListener.class)
public interface SculkSensorListenerAccessorMixin {
    @Accessor("receivingEvent")
    Optional<GameEvent> anticrash$getEvent();

    //?}

    /** 1.19 Vanilla persists vibration/source UUIDs/distance/delay via the listener codec. */
    //? if (>=1.19 && <=1.19.2) {
    /*@Mixin(VibrationListener.class)
    public interface SculkSensorListenerAccessorMixin {
        @Accessor("receivingEvent")
        VibrationListener.ReceivingEvent anticrash$getVibration();
*///?}

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("receivingEvent")
    void anticrash$setEvent(Optional<GameEvent> event);

    //?}

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("receivingDistance")
    int anticrash$getDistance();

    //?}

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("receivingDistance")
    void anticrash$setDistance(int distance);

    //?}
    //? if (>=1.19 && <=1.19.2) {
    /*@Accessor("receivingDistance")
        float anticrash$getDistance();
*///?}

    @Accessor("travelTimeInTicks")
    int anticrash$getDelay();

    //? if (>=1.18 && <=1.18.2) {
    @Accessor("travelTimeInTicks")
    void anticrash$setDelay(int delay);
    //?}

}
//?}
