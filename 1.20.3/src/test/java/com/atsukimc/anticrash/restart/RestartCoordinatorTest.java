package com.atsukimc.anticrash.restart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartCoordinatorTest {
    @TempDir
    Path temp;

    @Test
    void supervisorRequiresBothTheFlagAndTheExactMarkerPath() {
        Path marker = temp.resolve(".anticrash/state/restart-request").toAbsolutePath().normalize();

        assertFalse(RestartCoordinator.isSupervisorHandshake(marker, "1", null));
        assertFalse(RestartCoordinator.isSupervisorHandshake(marker, "1", temp.resolve("other").toString()));
        assertFalse(RestartCoordinator.isSupervisorHandshake(marker, "0", marker.toString()));
        assertTrue(RestartCoordinator.isSupervisorHandshake(marker, "1", marker.toString()));
    }
}
