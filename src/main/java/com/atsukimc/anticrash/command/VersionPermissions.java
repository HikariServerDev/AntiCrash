//? if (>=1.21.11 && <=26.3) {
/*package com.atsukimc.anticrash.command;

//? if 1.21.11 {
/^import net.minecraft.command.permission.Permission;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.commands.CommandSourceStack;
^///?}

//? if 1.21.11 {
/^import net.minecraft.command.permission.PermissionLevel;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.Permission;
^///?}

//? if 1.21.11 {
/^import net.minecraft.command.permission.PermissionPredicate;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.PermissionLevel;
^///?}

//? if 1.21.11 {
/^import net.minecraft.server.command.ServerCommandSource;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.PermissionSet;
^///?}


/^* Preserve configurable 0..4 command levels using Vanilla's new permission predicate. ^/
public final class VersionPermissions {
    private VersionPermissions() {}


//? if 1.21.11 {
/^public static boolean hasLevel(ServerCommandSource source, int level)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static boolean hasLevel(CommandSourceStack source, int level)
^///?}
{

//? if 1.21.11 {
/^return hasLevel(source.getPermissions(), level);
^///?}
//? if (>=26.1 && <=26.3) {
/^return hasLevel(source.permissions(), level);
^///?}

    }


//? if 1.21.11 {
/^public static boolean hasLevel(PermissionPredicate permissions, int level)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static boolean hasLevel(PermissionSet permissions, int level)
^///?}
{
        if (level < 0 || level > 4 || permissions == null) return false;

//? if 1.21.11 {
/^return level == 0
                || permissions.hasPermission(
                        new Permission.Level(PermissionLevel.fromLevel(level)));
^///?}
//? if (>=26.1 && <=26.3) {
/^return level == 0
                || permissions.hasPermission(
                        new Permission.HasCommandLevel(PermissionLevel.byId(level)));
^///?}

    }
}
*///?}
