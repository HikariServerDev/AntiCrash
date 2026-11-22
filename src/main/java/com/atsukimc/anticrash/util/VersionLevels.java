package com.atsukimc.anticrash.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Reaching the Level and the server from an entity. 1.20 renamed
 * getLevel to level, and 1.21.6 made ServerPlayer.server private. Entity.getServer, removed in
 * 1.21.9, only forwarded to the Level. An Entity can be on the client, so only ServerPlayer gets a
 * ServerLevel.
 */
public final class VersionLevels {
    private VersionLevels() {}

    public static Level level(Entity entity) {
        //? if <=1.19.4 {
        /*return entity.getLevel();
        *///?} else {
        return entity.level();
        //?}
    }

    public static ServerLevel level(ServerPlayer player) {
        //? if <=1.19.4 {
        /*return player.getLevel();
        *///?} else if <=1.21.5 {
        /*return player.serverLevel();
        *///?} else {
        return player.level();
        //?}
    }

    public static MinecraftServer server(ServerPlayer player) {
        //? if <=1.21.5 {
        /*return player.server;
        *///?} else {
        return player.level().getServer();
        //?}
    }
}
