//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import com.mojang.serialization.JsonOps;

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.block.entity.*;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.*;
^///?}

//? if (>=1.19 && <=1.21.5) {
/^import net.minecraft.nbt.NbtCompound;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BlockEntity;
^///?}

//? if (>=1.19 && <=1.19.4) {
/^import net.minecraft.world.event.listener.VibrationListener;
^///?}
//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.world.event.Vibrations;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
^///?}


/^* Version-native codecs include every persisted candidate, source UUID and spread cursor. ^/
public final class SculkRuntimeFingerprint {
    private SculkRuntimeFingerprint() {}

    public static String capture(BlockEntity entity) {

//? if (>=1.19 && <=1.19.4) {
/^VibrationListener listener;
^///?}


//? if (>=1.19 && <=1.19.4) {
/^if (entity instanceof SculkSensorBlockEntity sensor) listener = sensor.getEventListener();
        else if (entity instanceof SculkShriekerBlockEntity shrieker)
            listener = shrieker.getVibrationListener();
        else
^///?}
if (entity instanceof SculkCatalystBlockEntity catalyst) {

//? if (>=1.19 && <=1.21.5) {
/^NbtCompound cursors = new NbtCompound();
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^var view = VersionStorage.writer(entity.getWorld().getRegistryManager());
^///?}
//? if (>=26.1 && <=26.3) {
/^var view = VersionStorage.writer(entity.getLevel().registryAccess());
^///?}


//? if (>=1.19 && <=1.19.4) {
/^catalyst.getSpreadManager().writeNbt(cursors);
^///?}
//? if (>=1.20 && <=1.21.5) {
/^catalyst.getEventListener().getSpreadManager().writeNbt(cursors);
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^catalyst.getEventListener().getSpreadManager().writeData(view);
^///?}
//? if (>=26.1 && <=26.3) {
/^catalyst.getListener().getSculkSpreader().save(view);
^///?}


//? if (>=1.19 && <=1.21.5) {
/^return "catalyst|" + cursors;
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^return "catalyst|" + view.getNbt();
^///?}
//? if (>=26.1 && <=26.3) {
/^return "catalyst|" + view.buildResult();
^///?}

        } else
//? if (>=1.20 && <=1.21.11) {
/^if (!(entity instanceof Vibrations))
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!(entity instanceof VibrationSystem))
^///?}
return null;

//? if (>=1.19 && <=1.19.4) {
/^return "listener|"
                + VibrationListener.createCodec((VibrationListener.Callback) entity)
                        .encodeStart(JsonOps.INSTANCE, listener)
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
^///?}
//? if (>=1.20 && <=1.21.11) {
/^return "listener|"
                + Vibrations.ListenerData.CODEC
                        .encodeStart(
                                JsonOps.INSTANCE, ((Vibrations) entity).getVibrationListenerData())
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
^///?}
//? if (>=26.1 && <=26.3) {
/^return "listener|"
                + VibrationSystem.Data.CODEC
                        .encodeStart(
                                JsonOps.INSTANCE, ((VibrationSystem) entity).getVibrationData())
                        .result()
                        .orElseThrow(
                                () -> new IllegalStateException("Sculk listener capture failed"));
^///?}

    }
}
*///?}
