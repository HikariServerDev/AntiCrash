package com.atsukimc.anticrash.checkpoint;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.snapshot.ParentCheckpointChangedException;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class CheckpointFailureGateTest {
    @Test
    void threeFailuresSuspendAndManualRetryIsExplicit() {
        CheckpointFailureGate gate = new CheckpointFailureGate();
        gate.failed(new IOException("disk"), 1);
        assertFalse(gate.permits(2));
        assertTrue(gate.permits(31_000_000_001L));
        gate.failed(new IOException("disk"), 40_000_000_000L);
        gate.failed(new IOException("disk"), 120_000_000_000L);
        assertTrue(gate.suspended());
        assertFalse(gate.permits(Long.MAX_VALUE));
        gate.manualRetry();
        assertTrue(gate.permits(140_000_000_000L));
        gate.success();
        assertEquals(0, gate.consecutiveFailures());
    }

    @Test
    void fiftyFaultCyclesNeverAutoResumeOomOrParent() {
        CheckpointFailureGate gate = new CheckpointFailureGate();
        for (int i = 0; i < 50; i++) {
            gate.failed(new OutOfMemoryError("injected, not real heap exhaustion"), 1);
            assertTrue(gate.suspended());
            assertEquals(CheckpointFailureGate.Category.OOM, gate.category());
            assertFalse(gate.permits(9_000_000_000_000L));
            gate.manualRetry();
            gate.success();
            gate.failed(new ParentCheckpointChangedException("missing"), 1);
            assertTrue(gate.suspended());
            gate.manualRetry();
            gate.success();
        }
    }

    @Test
    void memoryBudgetAndChecksumAreDistinct() {
        assertEquals(
                CheckpointFailureGate.Category.MEMORY_BUDGET,
                CheckpointFailureGate.classify(
                        new CheckpointFailureGate.MemoryBudgetException(4, 16)));
        assertEquals(
                CheckpointFailureGate.Category.CHECKSUM_FAILURE,
                CheckpointFailureGate.classify(new IOException("Checksum mismatch")));
    }
}
