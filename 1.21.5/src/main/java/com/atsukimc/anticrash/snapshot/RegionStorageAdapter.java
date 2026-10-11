package com.atsukimc.anticrash.snapshot;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.storage.StorageKey;

/** The StorageKey labels diagnostics/JFR; detached file contents/coordinates stay authoritative. */
public final class RegionStorageAdapter {
    public static final StorageKey KEY = new StorageKey("anticrash-checkpoint",
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("anticrash", "offline")), "checkpoint-overlay");
    private RegionStorageAdapter() {}
}
