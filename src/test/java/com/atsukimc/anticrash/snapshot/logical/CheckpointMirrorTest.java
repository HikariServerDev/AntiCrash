package com.atsukimc.anticrash.snapshot.logical;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.config.AntiCrashConfig;

import org.junit.jupiter.api.Test;

class CheckpointMirrorTest {
    @Test
    void reportsUnsupportedInsteadOfPretendingToMatch() {
        AntiCrashConfig.Checkpoint config = new AntiCrashConfig.Checkpoint();
        CheckpointMirror mirror = new CheckpointMirror(config);

        CheckpointMirror.Snapshot snapshot = mirror.audit();
        assertEquals("UNSUPPORTED", snapshot.state());
        assertFalse(snapshot.fastPathUsable());
        assertTrue(snapshot.reason().contains("not safely exposed"));
    }

    @Test
    void disabledMirrorIsExplicit() {
        AntiCrashConfig.Checkpoint config = new AntiCrashConfig.Checkpoint();
        config.mirrorEnabled = false;

        CheckpointMirror.Snapshot snapshot = new CheckpointMirror(config).snapshot();
        assertEquals("DISABLED", snapshot.state());
        assertFalse(snapshot.enabled());
    }
}
