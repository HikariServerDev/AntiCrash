//? if (>=1.21.11 && <=26.3) {
/*package com.atsukimc.anticrash.command;

import static org.junit.jupiter.api.Assertions.*;

//? if 1.21.11 {
/^import net.minecraft.command.permission.*;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.*;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.LevelBasedPermissionSet;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.PermissionLevel;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.permissions.PermissionSet;
^///?}


import org.junit.jupiter.api.Test;

class VersionPermissionsTest {
    @Test
    void allConfiguredLevelsPreserveMinimumLevelAndNonOpStart() {
        for (int actor = 0; actor <= 4; actor++)
            for (int required = 0; required <= 4; required++)

//? if 1.21.11 {
/^assertEquals(
                        actor >= required,
                        VersionPermissions.hasLevel(
                                LeveledPermissionPredicate.fromLevel(
                                        PermissionLevel.fromLevel(actor)),
                                required));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertEquals(
                        actor >= required,
                        VersionPermissions.hasLevel(
                                LevelBasedPermissionSet.forLevel(PermissionLevel.byId(actor)),
                                required));
^///?}


//? if 1.21.11 {
/^assertTrue(VersionPermissions.hasLevel(PermissionPredicate.NONE, 0));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(VersionPermissions.hasLevel(PermissionSet.NO_PERMISSIONS, 0));
^///?}


//? if 1.21.11 {
/^assertFalse(VersionPermissions.hasLevel(PermissionPredicate.NONE, 1));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertFalse(VersionPermissions.hasLevel(PermissionSet.NO_PERMISSIONS, 1));
^///?}

    }

    @Test
    void invalidLevelsNeverClampToAnAllowedPermission() {

//? if 1.21.11 {
/^assertFalse(VersionPermissions.hasLevel(PermissionPredicate.ALL, -1));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertFalse(VersionPermissions.hasLevel(PermissionSet.ALL_PERMISSIONS, -1));
^///?}


//? if 1.21.11 {
/^assertFalse(VersionPermissions.hasLevel(PermissionPredicate.ALL, 5));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertFalse(VersionPermissions.hasLevel(PermissionSet.ALL_PERMISSIONS, 5));
^///?}


//? if 1.21.11 {
/^assertFalse(VersionPermissions.hasLevel((PermissionPredicate) null, 0));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertFalse(VersionPermissions.hasLevel((PermissionSet) null, 0));
^///?}

    }
}
*///?}
