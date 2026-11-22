//? if >=1.19 {
package com.atsukimc.anticrash.runtime;

//? if <=1.19.4 {
/*import net.minecraft.world.level.gameevent.vibrations.VibrationListener;
*///?} else {
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
//?}
//? if >=26.1 {
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
//?}

import com.mojang.serialization.JsonOps;

import net.minecraft.world.level.block.entity.*;

/** Version-native codecs include every persisted candidate, source UUID and spread cursor. */
public final class SculkRuntimeFingerprint {
    private SculkRuntimeFingerprint() {}

    public static String capture(BlockEntity entity) {
        //? if <=1.19.4 {
        /*VibrationListener listener;

        if (entity instanceof SculkSensorBlockEntity sensor) listener = sensor.getListener();
        else if (entity instanceof SculkShriekerBlockEntity shrieker)
            listener = shrieker.getListener();
        else if (entity instanceof SculkCatalystBlockEntity catalyst) {
        *///?} else {
        if (entity instanceof SculkCatalystBlockEntity catalyst) {
        //?}
            var view = VersionStorage.writer(entity);

            //? if <=1.19.4 {
            /*catalyst.getSculkSpreader().save(view);
            *///?} else {
            catalyst.getListener().getSculkSpreader().save(view);
            //?}

            return "catalyst|" + VersionStorage.result(view);

        //? if <=1.19.4 {
        /*} else return null;

        return "listener|"
                + VibrationListener.codec((VibrationListener.VibrationListenerConfig) entity)
                        .encodeStart(JsonOps.INSTANCE, listener)
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
        *///?} else {
        } else if (!(entity instanceof VibrationSystem)) return null;

        return "listener|"
                + VibrationSystem.Data.CODEC
                        .encodeStart(
                                JsonOps.INSTANCE, ((VibrationSystem) entity).getVibrationData())
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
        //?}

    }
}
//?}
