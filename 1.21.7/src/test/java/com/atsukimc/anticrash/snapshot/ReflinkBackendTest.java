package com.atsukimc.anticrash.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflinkBackendTest {
    @Test
    void autoDetectionDoesNotAttemptReflinkOnExtFileSystems() {
        assertFalse(ReflinkBackend.supportsFileStoreType("ext4"));
        assertFalse(ReflinkBackend.supportsFileStoreType("ext3"));
        assertTrue(ReflinkBackend.supportsFileStoreType("xfs"));
        assertTrue(ReflinkBackend.supportsFileStoreType("btrfs"));
        assertTrue(ReflinkBackend.supportsFileStoreType("bcachefs"));
    }
}
