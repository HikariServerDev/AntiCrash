package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.util.AtomicFiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class CheckpointWriteGuardTest {
    @TempDir Path temp;

    @Test
    void readyPayloadAndControlWritesAreRefused() throws Exception {
        Path cp = temp.resolve("cp-000001");
        Files.createDirectories(cp.resolve("world"));
        Files.writeString(cp.resolve("world/level.dat"), "immutable");
        Files.writeString(cp.resolve("READY"), "ready");
        assertThrows(
                IOException.class,
                () -> AtomicFiles.writeText(cp.resolve("world/level.dat"), "corrupt"));
        assertThrows(
                IOException.class,
                () -> AtomicFiles.writeJson(cp.resolve("manifest.json"), "corrupt"));
        assertEquals("immutable", Files.readString(cp.resolve("world/level.dat")));
    }

    @Test
    void sharedMutableStageMustDetachBeforeRegionWrite() throws Exception {
        Path cp = temp.resolve("old");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("region"), "bytes");
        Path stage = temp.resolve("stage");
        Files.createDirectories(stage);
        Files.createLink(stage.resolve("region"), cp.resolve("region"));
        assertThrows(
                IOException.class,
                () -> CheckpointWriteGuard.requireDetached(stage.resolve("region")));
        Files.delete(stage.resolve("region"));
        Files.copy(cp.resolve("region"), stage.resolve("region"));
        CheckpointWriteGuard.requireDetached(stage.resolve("region"));
    }
}
