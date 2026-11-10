package com.atsukimc.anticrash.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Reaching the Level and the server from an entity, and the side of a Level. 1.20 renamed
 * getLevel to level, and 1.21.6 made ServerPlayer.server private. Entity.getServer, removed in
 * 1.21.9, only forwarded to the Level. 1.21.9 also made Level.isClientSide private; older versions
 * keep reading the field. An Entity can be on the client, so only ServerPlayer gets a ServerLevel.
 */
public final class VersionLevels {
    private VersionLevels() {}

    public static Level level(Entity entity) {
        //? if (>=1.18 && <=1.19.4) {
        /*return entity.getLevel();
        *///?} else {
        return entity.level();
        //?}
    }

    public static ServerLevel level(ServerPlayer player) {
        //? if (>=1.18 && <=1.19.4) {
        /*return player.getLevel();
        *///?}
        //? if (>=1.20 && <=1.21.5) {
        /*return player.serverLevel();
        *///?}
        //? if (>=1.21.6 && <=26.3) {
        return player.level();
        //?}
    }

    /**
     * @deprecated Use just {@code .isClientSide()} instead.
     * {@code level.isClientSide() } is just call {@code return level.isClientSide } in all versions,
     * so no difference between the versions.
     */
    @Deprecated
    public static boolean isClientSide(Level level) {
        //? if (>=1.18 && <=1.21.8) {
        /*return level.isClientSide;
        *///?} else {
        return level.isClientSide();
        //?}
    }

    public static MinecraftServer server(ServerPlayer player) {
        //? if (>=1.18 && <=1.21.5) {
        /*return player.server;
        *///?} else {
        return player.level().getServer();
        //?}
    }
}
