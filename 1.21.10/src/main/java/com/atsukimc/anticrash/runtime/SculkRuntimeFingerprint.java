package com.atsukimc.anticrash.runtime;

import com.mojang.serialization.JsonOps;
import net.minecraft.block.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.event.Vibrations;

/** Version-native codecs include every persisted candidate, source UUID and spread cursor. */
public final class SculkRuntimeFingerprint {
    private SculkRuntimeFingerprint() {}
    public static String capture(BlockEntity entity) {
        if (entity instanceof SculkCatalystBlockEntity catalyst) {
            var view = VersionStorage.writer(entity.getWorld().getRegistryManager());
            catalyst.getEventListener().getSpreadManager().writeData(view);
            return "catalyst|" + view.getNbt();
        } else if (!(entity instanceof Vibrations)) return null;
        return "listener|" + Vibrations.ListenerData.CODEC
                .encodeStart(JsonOps.INSTANCE, ((Vibrations) entity).getVibrationListenerData()).result()
                .orElseThrow(() -> new IllegalStateException("Sculk listener capture failed"));
    }
}
