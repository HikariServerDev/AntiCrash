package com.atsukimc.anticrash.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 26.1 split overlay and normal system messages; preserve the requested destination. */
public final class VersionMessages {
    private VersionMessages() {}

    public static void send(ServerPlayer player, Component message, boolean overlay) {
        if (overlay) player.sendOverlayMessage(message);
        else player.sendSystemMessage(message);
    }
}
