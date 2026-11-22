package com.atsukimc.anticrash.command;

import net.minecraft.commands.CommandSourceStack;

/** Configurable 0..4 command levels; 1.21.11 replaced the integer check with a predicate. */
public final class VersionPermissions {
    private VersionPermissions() {}

    //? if <=1.21.10 {
    /*public static boolean hasLevel(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }
    *///?} else {
    public static boolean hasLevel(CommandSourceStack source, int level) {
        return hasLevel(source.permissions(), level);
    }

    public static boolean hasLevel(
            net.minecraft.server.permissions.PermissionSet permissions, int level) {
        if (level < 0 || level > 4 || permissions == null) return false;
        return level == 0
                || permissions.hasPermission(
                        new net.minecraft.server.permissions.Permission.HasCommandLevel(
                                net.minecraft.server.permissions.PermissionLevel.byId(level)));
    }
    //?}
}
