package com.atsukimc.anticrash.command;
import net.minecraft.command.permission.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VersionPermissionsTest {
    @Test void allConfiguredLevelsPreserveMinimumLevelAndNonOpStart() {
        for(int actor=0;actor<=4;actor++) for(int required=0;required<=4;required++)
            assertEquals(actor>=required,VersionPermissions.hasLevel(LeveledPermissionPredicate.fromLevel(PermissionLevel.fromLevel(actor)),required));
        assertTrue(VersionPermissions.hasLevel(PermissionPredicate.NONE,0));
        assertFalse(VersionPermissions.hasLevel(PermissionPredicate.NONE,1));
    }
    @Test void invalidLevelsNeverClampToAnAllowedPermission() {
        assertFalse(VersionPermissions.hasLevel(PermissionPredicate.ALL,-1));
        assertFalse(VersionPermissions.hasLevel(PermissionPredicate.ALL,5));
        assertFalse(VersionPermissions.hasLevel((PermissionPredicate)null,0));
    }
}
