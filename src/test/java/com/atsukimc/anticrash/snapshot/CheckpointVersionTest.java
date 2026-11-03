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

        //? if 1.18.2 {
        assertEquals("1.18.2", manifest.minecraftVersion);
        //?}
        //? if 1.18 {
        /*assertEquals("1.18", manifest.minecraftVersion);
*///?}
        //? if 1.18.1 {
        /*assertEquals("1.18.1", manifest.minecraftVersion);
*///?}
        //? if 1.19 {
        /*assertEquals("1.19", manifest.minecraftVersion);
*///?}
        //? if 1.19.1 {
        /*assertEquals("1.19.1", manifest.minecraftVersion);
*///?}
        //? if 1.19.2 {
        /*assertEquals("1.19.2", manifest.minecraftVersion);
*///?}
        //? if 1.19.3 {
        /*assertEquals("1.19.3", manifest.minecraftVersion);
*///?}
        //? if 1.19.4 {
        /*assertEquals("1.19.4", manifest.minecraftVersion);
*///?}
        //? if 1.20 {
        /*assertEquals("1.20", manifest.minecraftVersion);
*///?}
        //? if 1.20.1 {
        /*assertEquals("1.20.1", manifest.minecraftVersion);
*///?}
        //? if 1.20.2 {
        /*assertEquals("1.20.2", manifest.minecraftVersion);
*///?}
        //? if 1.20.3 {
        /*assertEquals("1.20.3", manifest.minecraftVersion);
*///?}
        //? if 1.20.4 {
        /*assertEquals("1.20.4", manifest.minecraftVersion);
*///?}
        //? if 1.20.5 {
        /*assertEquals("1.20.5", manifest.minecraftVersion);
*///?}
        //? if 1.20.6 {
        /*assertEquals("1.20.6", manifest.minecraftVersion);
*///?}
        //? if 1.21 {
        /*assertEquals("1.21", manifest.minecraftVersion);
*///?}
        //? if 1.21.1 {
        /*assertEquals("1.21.1", manifest.minecraftVersion);
*///?}
        //? if 1.21.2 {
        /*assertEquals("1.21.2", manifest.minecraftVersion);
*///?}
        //? if 1.21.3 {
        /*assertEquals("1.21.3", manifest.minecraftVersion);
*///?}
        //? if 1.21.4 {
        /*assertEquals("1.21.4", manifest.minecraftVersion);
*///?}
        //? if 1.21.5 {
        /*assertEquals("1.21.5", manifest.minecraftVersion);
*///?}
        //? if 1.21.6 {
        /*assertEquals("1.21.6", manifest.minecraftVersion);
*///?}
        //? if 1.21.7 {
        /*assertEquals("1.21.7", manifest.minecraftVersion);
*///?}
        //? if 1.21.8 {
        /*assertEquals("1.21.8", manifest.minecraftVersion);
*///?}
        //? if 1.21.9 {
        /*assertEquals("1.21.9", manifest.minecraftVersion);
*///?}
        //? if 1.21.10 {
        /*assertEquals("1.21.10", manifest.minecraftVersion);
*///?}
        //? if 1.21.11 {
        /*assertEquals("1.21.11", manifest.minecraftVersion);
*///?}
        //? if 26.1 {
        /*assertEquals("26.1", manifest.minecraftVersion);
*///?}
        //? if 26.2 {
        /*assertEquals("26.2", manifest.minecraftVersion);
*///?}
        //? if 26.3 {
        /*assertEquals("26.3", manifest.minecraftVersion);
*///?}

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
