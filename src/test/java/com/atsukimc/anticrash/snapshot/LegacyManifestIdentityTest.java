package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.util.AtomicFiles;

import org.junit.jupiter.api.Test;

class LegacyManifestIdentityTest {
    @Test
    void missingVersionBelongsToOriginal1182NotToTheCurrentPort() {
        var legacy = AtomicFiles.GSON.fromJson("{}", CheckpointManifest.class);
        assertEquals("1.18.2", legacy.minecraftVersion);
        var current = new CheckpointManifest();
        var decoded =
                AtomicFiles.GSON.fromJson(
                        AtomicFiles.GSON.toJson(current), CheckpointManifest.class);
        assertEquals(CheckpointManifest.CURRENT_MINECRAFT_VERSION, decoded.minecraftVersion);
    }

    @Test
    void explicitUnknownVersionIsNotRepairedIntoTheCurrentPort() {
        var missing =
                AtomicFiles.GSON.fromJson("{\"minecraftVersion\":null}", CheckpointManifest.class);
        assertNull(missing.minecraftVersion);
        var foreign =
                AtomicFiles.GSON.fromJson(
                        "{\"minecraftVersion\":\"foreign\"}", CheckpointManifest.class);
        assertEquals("foreign", foreign.minecraftVersion);
    }
}
