package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.config.AntiCrashConfig;

import java.util.Locale;

/** Configuration decisions only; no IO, world mutation, or changes to checkpoint scheduling. */
public final class CheckpointEnginePolicy {
    private CheckpointEnginePolicy() {}

    public static boolean usesPortableGeneration(AntiCrashConfig config) {
        String backend = normalized(config.snapshot.backend, "AUTO");
        return backend.equals("AUTO")
                || backend.equals("PORTABLE_GENERATION")
                || backend.equals("GENERATION");
    }

    public static boolean logicalDeltaRequested(AntiCrashConfig config) {
        String mode = normalized(config.checkpoint.engineMode, "LOGICAL_DELTA");
        boolean portable = usesPortableGeneration(config);
        return "LOGICAL_DELTA".equals(mode) && portable;
    }

    public static boolean hasUnsafeLogicalOptions(AntiCrashConfig config) {
        return config.checkpoint.allowUnsupportedPersistentState
                || config.playerSnapshot.vanillaFiles.restorePlayerdata
                || config.playerSnapshot.vanillaFiles.restoreStats
                || config.playerSnapshot.vanillaFiles.restoreAdvancements;
    }

    private static String normalized(String value, String fallback) {
        return value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
