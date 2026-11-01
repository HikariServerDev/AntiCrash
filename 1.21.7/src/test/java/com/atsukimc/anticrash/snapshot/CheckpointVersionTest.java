package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Map;

class CheckpointVersionTest {
    @Test
    void rejectsForeignMinecraftBeforeExaminingWorldPayload() throws Exception {
        CheckpointManifest manifest = new CheckpointManifest();
        assertEquals("1.21.7", manifest.minecraftVersion);
        manifest.minecraftVersion = "foreign-game-version";
        var validator =
                CheckpointStore.class.getDeclaredMethod(
                        "validateManifest", Path.class, CheckpointManifest.class, Map.class);
        validator.setAccessible(true);
        var error =
                assertThrows(
                        InvocationTargetException.class,
                        () -> validator.invoke(null, null, manifest, Map.of()));
        assertInstanceOf(IOException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("Minecraft version mismatch"));
    }
}
