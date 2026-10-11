package com.atsukimc.anticrash.snapshot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** The StorageKey labels diagnostics/JFR; detached file contents/coordinates stay authoritative. */
public final class RegionStorageAdapter {
    public static final RegionStorageInfo KEY = new RegionStorageInfo("anticrash-checkpoint",
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("anticrash", "offline")), "checkpoint-overlay");
    private RegionStorageAdapter() {}
}
