package com.atsukimc.anticrash.restart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

class RestartRequestTest {
    @TempDir Path temporaryDirectory;

    @Test
    void markerIsConsumedExactlyOnce() throws Exception {
        Path marker = temporaryDirectory.resolve("state/restart-request");
        Files.createDirectories(marker.getParent());
        RestartRequest.create(marker);

        assertTrue(Files.isRegularFile(marker));
        if (Files.getFileStore(marker).supportsFileAttributeView("posix")) {
            assertEquals(
                    PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(marker));
        }
        assertTrue(RestartRequest.claim(marker));
        assertFalse(RestartRequest.claim(marker));
    }

    @Test
    void malformedMarkerIsPreservedAndRejected() throws Exception {
        Path marker = temporaryDirectory.resolve("state/restart-request");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "request=forged\n");

        assertThrows(java.io.IOException.class, () -> RestartRequest.claim(marker));
        assertTrue(Files.exists(marker));
    }
}
