package com.atsukimc.anticrash.runtime;

import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

/** Version-native codecs include every persisted candidate, source UUID and spread cursor. */
public final class SculkRuntimeFingerprint {
    private SculkRuntimeFingerprint() {}
    public static String capture(BlockEntity entity) {
        if (entity instanceof SculkCatalystBlockEntity catalyst) {
            var view = VersionStorage.writer(entity.getLevel().registryAccess());
            catalyst.getListener().getSculkSpreader().save(view);
            return "catalyst|" + view.buildResult();
        } else if (!(entity instanceof VibrationSystem)) return null;
        return "listener|" + VibrationSystem.Data.CODEC
                .encodeStart(JsonOps.INSTANCE, ((VibrationSystem) entity).getVibrationData()).result()
                .orElseThrow(() -> new IllegalStateException("Sculk listener capture failed"));
    }
}
