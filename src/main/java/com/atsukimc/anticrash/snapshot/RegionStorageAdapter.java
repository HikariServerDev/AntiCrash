//? if >=1.20.5 {
package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/**
 * The RegionStorageInfo labels diagnostics/JFR; detached file contents/coordinates stay
 * authoritative.
 */
public final class RegionStorageAdapter {
    public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
                    ResourceKey.create(Registries.DIMENSION, VersionIds.of("anticrash", "offline")),
                    "checkpoint-overlay");

    private RegionStorageAdapter() {}
}
//?}
