package com.atsukimc.anticrash.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldLocatorTest {
    @TempDir
    Path temp;

    @Test
    void usesVanillaDefaultOnlyWhenServerPropertiesIsAbsent() throws Exception {
        assertEquals(temp.resolve("world").toAbsolutePath().normalize(), WorldLocator.resolve(temp));
    }

    @Test
    void resolvesAConfiguredWorldInsideTheServerRoot() throws Exception {
        Files.writeString(temp.resolve("server.properties"), "level-name=worlds/s1\n");

        assertEquals(temp.resolve("worlds/s1").toAbsolutePath().normalize(), WorldLocator.resolve(temp));
    }

    @Test
    void neverFallsBackToAnotherWorldForAnEscapingLevelName() throws Exception {
        Files.writeString(temp.resolve("server.properties"), "level-name=../other-server/world\n");

        assertThrows(java.io.IOException.class, () -> WorldLocator.resolve(temp));
    }

    @Test
    void rejectsSymlinkedWorldPathComponents() throws Exception {
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.createSymbolicLink(temp.resolve("linked"), outside);
        Files.writeString(temp.resolve("server.properties"), "level-name=linked/world\n");

        assertThrows(java.io.IOException.class, () -> WorldLocator.resolve(temp));
    }
}
