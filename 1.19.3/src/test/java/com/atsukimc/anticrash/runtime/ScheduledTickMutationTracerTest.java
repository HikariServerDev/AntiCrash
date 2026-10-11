package com.atsukimc.anticrash.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledTickMutationTracerTest {
    @Test
    void recordsBoundedEvidenceAndClosesCapture() {
        ScheduledTickMutationTracer.Capture capture =
                ScheduledTickMutationTracer.begin(99L, 2);
        try {
            ScheduledTickMutationTracer.record("schedule", "a");
            ScheduledTickMutationTracer.record("attach", "b");
            ScheduledTickMutationTracer.record("detach", "c");
            assertTrue(capture.changed());
            assertEquals(3L, capture.mutations());
            assertEquals(2, capture.events().size());
            assertTrue(capture.events().get(0).contains("thread="));
            assertTrue(capture.events().get(0).contains("callers="));
        } finally {
            ScheduledTickMutationTracer.end(capture);
        }
        assertTrue(capture.closed());
        assertFalse(ScheduledTickMutationTracer.isActive());
    }
}
