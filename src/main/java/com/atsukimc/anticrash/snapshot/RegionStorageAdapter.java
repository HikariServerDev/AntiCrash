//? if (>=1.20.5 && <=26.3) {
package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** The StorageKey labels diagnostics/JFR; detached file contents/coordinates stay authoritative. */
public final class RegionStorageAdapter {

    //? if (>=1.20.5 && <=1.20.6) {
    /*public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
                    ResourceKey.create(Registries.DIMENSION, VersionIds.of("anticrash", "offline")),
                    "checkpoint-overlay");

    *///?}
    //? if (>=1.21 && <=1.21.11) {
    /*public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
    *///?}
                    //? if (>=1.21 && <=1.21.10) {
                    /*ResourceKey.create(Registries.DIMENSION, VersionIds.of("anticrash", "offline")),
                    *///?}
                    //? if 1.21.11 {
                    /*ResourceKey.create(Registries.DIMENSION, VersionIds.of("anticrash", "offline")),
                    *///?}
                    //? if (>=1.21 && <=1.21.11) {
                    /*"checkpoint-overlay");

                    *///?}
    //? if (>=26.1 && <=26.3) {
    public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
                    ResourceKey.create(Registries.DIMENSION, VersionIds.of("anticrash", "offline")),
                    "checkpoint-overlay");

    //?}

    private RegionStorageAdapter() {}
}
//?}
