package com.atsukimc.anticrash.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Creating plain text and sending it to a player; 1.19 and 26.1 changed these APIs. */
public final class VersionMessages {
    private VersionMessages() {}

    public static MutableComponent literal(String text) {
        //? if <=1.18.2 {
        /*return new net.minecraft.network.chat.TextComponent(text);
        *///?} else {
        return Component.literal(text);
        //?}
    }

    /** 26.1 split overlay and normal system messages; preserve the requested destination. */
    public static void send(ServerPlayer player, Component message, boolean overlay) {
        //? if <=1.21.11 {
        /*player.displayClientMessage(message, overlay);
        *///?} else {
        if (overlay) player.sendOverlayMessage(message);
        else player.sendSystemMessage(message);
        //?}
    }
}
