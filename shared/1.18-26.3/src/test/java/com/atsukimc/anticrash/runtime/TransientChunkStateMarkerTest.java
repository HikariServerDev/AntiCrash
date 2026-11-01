package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransientChunkStateMarkerTest {
    @Test
    void movingPistonRequiresSerializationEvenWithoutScheduledTicks() {
        assertTrue(TransientChunkStateMarker.needsTransientSave(true, 0, 0));
    }

    @Test
    void blockOrFluidScheduledTicksRequireSerialization() {
        assertTrue(TransientChunkStateMarker.needsTransientSave(false, 1, 0));
        assertTrue(TransientChunkStateMarker.needsTransientSave(false, 0, 1));
    }

    @Test
    void idleChunkIsNotMarked() {
        assertFalse(TransientChunkStateMarker.needsTransientSave(false, 0, 0));
    }
}
