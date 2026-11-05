//? if (>=1.21.11 && <=26.3) {
package com.atsukimc.anticrash.command;

//? if (>=26.1 && <=26.3) {
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
//?}

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import net.minecraft.server.permissions.*;

class VersionPermissionsTest {
    @Test
    void allConfiguredLevelsPreserveMinimumLevelAndNonOpStart() {
        for (int actor = 0; actor <= 4; actor++)
            for (int required = 0; required <= 4; required++)
                assertEquals(
                        actor >= required,
                        VersionPermissions.hasLevel(
                                LevelBasedPermissionSet.forLevel(PermissionLevel.byId(actor)),
                                required));

        assertTrue(VersionPermissions.hasLevel(PermissionSet.NO_PERMISSIONS, 0));

        assertFalse(VersionPermissions.hasLevel(PermissionSet.NO_PERMISSIONS, 1));
    }

    @Test
    void invalidLevelsNeverClampToAnAllowedPermission() {

        assertFalse(VersionPermissions.hasLevel(PermissionSet.ALL_PERMISSIONS, -1));

        assertFalse(VersionPermissions.hasLevel(PermissionSet.ALL_PERMISSIONS, 5));

        assertFalse(VersionPermissions.hasLevel((PermissionSet) null, 0));
    }
}
//?}
