package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

class CheckpointVersionTest {
    @Test
    void checkpointMinecraftVersionMatchesTheModMetadata() throws IOException {
        var metadata = CheckpointVersionTest.class.getResourceAsStream("/fabric.mod.json");
        assertNotNull(metadata, "Missing generated Fabric metadata");
        try (var reader = new InputStreamReader(metadata, StandardCharsets.UTF_8)) {
            String requiredVersion =
                    JsonParser.parseReader(reader)
                            .getAsJsonObject()
                            .getAsJsonObject("depends")
                            .get("minecraft")
                            .getAsString();
            assertEquals(requiredVersion, "=" + new CheckpointManifest().minecraftVersion);
        }
    }

    @Test
    void rejectsForeignMinecraftBeforeExaminingWorldPayload() throws Exception {
        CheckpointManifest manifest = new CheckpointManifest();
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
