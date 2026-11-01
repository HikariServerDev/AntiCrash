package com.atsukimc.anticrash.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

/** Preserve configurable 0..4 command levels using Vanilla's new permission predicate. */
public final class VersionPermissions {
    private VersionPermissions() {}

    public static boolean hasLevel(CommandSourceStack source, int level) {
        return hasLevel(source.permissions(), level);
    }

    public static boolean hasLevel(PermissionSet permissions, int level) {
        if (level < 0 || level > 4 || permissions == null) return false;
        return level == 0
                || permissions.hasPermission(
                        new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}
