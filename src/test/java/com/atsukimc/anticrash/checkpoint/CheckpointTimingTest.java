package com.atsukimc.anticrash.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CheckpointTimingTest {
    @Test
    void recordsDetailedRuntimeAndDeltaPhasesWithoutWallClockArithmetic() {
        CheckpointTiming timing = new CheckpointTiming();

        timing.record("runtime.chunk_domain", 2_000_000L);
        timing.record("delta.entity_capture", 3_000_000L);
        timing.begin("barrier");
        timing.end("barrier");

        assertEquals(2L, timing.durationsMs().get("runtime.chunk_domain"));
        assertEquals(3L, timing.durationsMs().get("delta.entity_capture"));
        assertTrue(timing.durationsMs().containsKey("barrier"));
        assertTrue(timing.summary().contains("phases="));
    }
}
