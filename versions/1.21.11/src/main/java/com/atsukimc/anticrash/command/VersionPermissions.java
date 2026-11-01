package com.atsukimc.anticrash.command;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.server.command.ServerCommandSource;

/** Preserve configurable 0..4 command levels using Vanilla's new permission predicate. */
public final class VersionPermissions {
    private VersionPermissions() { }
    public static boolean hasLevel(ServerCommandSource source,int level) {
        return hasLevel(source.getPermissions(),level);
    }
    public static boolean hasLevel(PermissionPredicate permissions,int level) {
        if(level<0 || level>4 || permissions==null) return false;
        return level==0 || permissions.hasPermission(new Permission.Level(PermissionLevel.fromLevel(level)));
    }
}
