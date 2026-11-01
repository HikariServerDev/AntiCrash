package com.atsukimc.anticrash.runtime;

import com.mojang.serialization.JsonOps;
import net.minecraft.block.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.event.listener.VibrationListener;

/** Version-native codecs include every persisted candidate, source UUID and spread cursor. */
public final class SculkRuntimeFingerprint {
    private SculkRuntimeFingerprint() {}
    public static String capture(BlockEntity entity) {
        VibrationListener listener;
        if (entity instanceof SculkSensorBlockEntity sensor) listener = sensor.getEventListener();
        else if (entity instanceof SculkShriekerBlockEntity shrieker) listener = shrieker.getVibrationListener();
        else if (entity instanceof SculkCatalystBlockEntity catalyst) {
            NbtCompound cursors = new NbtCompound();
            catalyst.getSpreadManager().writeNbt(cursors);
            return "catalyst|" + cursors;
        } else return null;
        return "listener|" + VibrationListener.createCodec((VibrationListener.Callback) entity)
                .encodeStart(JsonOps.INSTANCE, listener).result()
                .orElseThrow(() -> new IllegalStateException("Sculk listener capture failed"));
    }
}
