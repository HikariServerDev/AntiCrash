//? if (>=1.20.5 && <=26.3) {
package com.atsukimc.anticrash.snapshot;

//? if (>=1.20.5 && <=1.21.10) {
/*import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.resources.Identifier;
//?}

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** The StorageKey labels diagnostics/JFR; detached file contents/coordinates stay authoritative. */
public final class RegionStorageAdapter {

    //? if (>=1.20.5 && <=1.20.6) {
    /*public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
                    ResourceKey.create(
                            Registries.DIMENSION, new ResourceLocation("anticrash", "offline")),
                    "checkpoint-overlay");

    *///?}
    //? if (>=1.21 && <=1.21.11) {
    /*public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
    *///?}
                    //? if (>=1.21 && <=1.21.10) {
                    /*ResourceKey.create(
                            Registries.DIMENSION,
                            ResourceLocation.fromNamespaceAndPath("anticrash", "offline")),
                    *///?}
                    //? if 1.21.11 {
                    /*ResourceKey.create(
                            Registries.DIMENSION,
                            Identifier.fromNamespaceAndPath("anticrash", "offline")),
                    *///?}
                    //? if (>=1.21 && <=1.21.11) {
                    /*"checkpoint-overlay");

                    *///?}
    //? if (>=26.1 && <=26.3) {
    public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
                    ResourceKey.create(
                            Registries.DIMENSION,
                            Identifier.fromNamespaceAndPath("anticrash", "offline")),
                    "checkpoint-overlay");

    //?}

    private RegionStorageAdapter() {}
}
//?}
