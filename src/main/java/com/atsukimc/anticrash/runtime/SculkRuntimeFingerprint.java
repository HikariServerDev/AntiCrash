//? if (>=1.19 && <=26.3) {
package com.atsukimc.anticrash.runtime;

import com.mojang.serialization.JsonOps;

//? if (>=1.19 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.*;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.*;
//?}

//? if (>=1.19 && <=1.21.5) {
/*import net.minecraft.nbt.CompoundTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.BlockEntity;
//?}

//? if (>=1.19 && <=1.19.4) {
/*import net.minecraft.world.level.gameevent.vibrations.VibrationListener;
*///?}
//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

//?}

/** Version-native codecs include every persisted candidate, source UUID and spread cursor. */
public final class SculkRuntimeFingerprint {
    private SculkRuntimeFingerprint() {}

    public static String capture(BlockEntity entity) {

        //? if (>=1.19 && <=1.19.4) {
        /*VibrationListener listener;
*///?}

        //? if (>=1.19 && <=1.19.4) {
        /*if (entity instanceof SculkSensorBlockEntity sensor) listener = sensor.getListener();
        else if (entity instanceof SculkShriekerBlockEntity shrieker)
            listener = shrieker.getListener();
        else
*///?}
        if (entity instanceof SculkCatalystBlockEntity catalyst) {

            //? if (>=1.19 && <=1.21.5) {
            /*CompoundTag cursors = new CompoundTag();
*///?} else if (>=1.21.6 && <=1.21.11) {
            /*var view = VersionStorage.writer(entity.getLevel().registryAccess());
*///?} else if (>=26.1 && <=26.3) {
            var view = VersionStorage.writer(entity.getLevel().registryAccess());
            //?}

            //? if (>=1.19 && <=1.19.4) {
            /*catalyst.getSculkSpreader().save(cursors);
*///?} else if (>=1.20 && <=1.21.5) {
            /*catalyst.getListener().getSculkSpreader().save(cursors);
*///?} else if (>=1.21.6 && <=1.21.11) {
            /*catalyst.getListener().getSculkSpreader().save(view);
*///?} else if (>=26.1 && <=26.3) {
            catalyst.getListener().getSculkSpreader().save(view);
            //?}

            //? if (>=1.19 && <=1.21.5) {
            /*return "catalyst|" + cursors;
*///?} else if (>=1.21.6 && <=1.21.11) {
            /*return "catalyst|" + view.buildResult();
*///?} else if (>=26.1 && <=26.3) {
            return "catalyst|" + view.buildResult();
            //?}

        } else
        //? if (>=1.20 && <=1.21.11) {
        /*if (!(entity instanceof VibrationSystem))
*///?} else if (>=26.1 && <=26.3) {
        if (!(entity instanceof VibrationSystem))
            //?}
            return null;

        //? if (>=1.19 && <=1.19.4) {
        /*return "listener|"
                + VibrationListener.codec((VibrationListener.VibrationListenerConfig) entity)
                        .encodeStart(JsonOps.INSTANCE, listener)
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
*///?} else if (>=1.20 && <=1.21.11) {
        /*return "listener|"
                + VibrationSystem.Data.CODEC
                        .encodeStart(
                                JsonOps.INSTANCE, ((VibrationSystem) entity).getVibrationData())
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
*///?} else if (>=26.1 && <=26.3) {
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
