package com.atsukimc.anticrash;

import net.fabricmc.api.ModInitializer;

public final class AntiCrashMod implements ModInitializer {
    public static final String MOD_ID = "anticrash";
    public static final String VERSION = "1.0.4";
    private static AntiCrashController controller;

    @Override
    public void onInitialize() {
        controller = new AntiCrashController();
    }

    public static String minecraftVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static AntiCrashController controller() {
        return controller;
    }
}
