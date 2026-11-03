//? if (>=1.20.5 && <=26.3) {
/*package com.atsukimc.anticrash.snapshot;

//? if (>=1.20.5 && <=1.21.11) {
/^import net.minecraft.registry.RegistryKey;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.registries.Registries;
^///?}

//? if (>=1.20.5 && <=1.21.11) {
/^import net.minecraft.registry.RegistryKeys;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.resources.Identifier;
^///?}

//? if (>=1.20.5 && <=1.21.11) {
/^import net.minecraft.util.Identifier;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.resources.ResourceKey;
^///?}

//? if (>=1.20.5 && <=1.21.11) {
/^import net.minecraft.world.storage.StorageKey;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
^///?}


/^* The StorageKey labels diagnostics/JFR; detached file contents/coordinates stay authoritative. ^/
public final class RegionStorageAdapter {

//? if (>=1.20.5 && <=1.20.6) {
/^public static final StorageKey KEY =
            new StorageKey(
                    "anticrash-checkpoint",
                    RegistryKey.of(RegistryKeys.WORLD, new Identifier("anticrash", "offline")),
                    "checkpoint-overlay");
^///?}
//? if (>=1.21 && <=1.21.11) {
/^public static final StorageKey KEY =
            new StorageKey(
                    "anticrash-checkpoint",
                    RegistryKey.of(RegistryKeys.WORLD, Identifier.of("anticrash", "offline")),
                    "checkpoint-overlay");
^///?}
//? if (>=26.1 && <=26.3) {
/^public static final RegionStorageInfo KEY =
            new RegionStorageInfo(
                    "anticrash-checkpoint",
                    ResourceKey.create(
                            Registries.DIMENSION,
                            Identifier.fromNamespaceAndPath("anticrash", "offline")),
                    "checkpoint-overlay");
^///?}


    private RegionStorageAdapter() {}
}
*///?}
