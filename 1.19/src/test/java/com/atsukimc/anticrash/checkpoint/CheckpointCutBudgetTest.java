package com.atsukimc.anticrash.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointCutBudgetTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    /**
     * Regression test: the cut waited for the disk after every 16 chunk saves whatever the heap
     * size. On a 16 GiB server saving a few hundred chunks per cut that was seconds of freeze.
     */
    @Test
    void aLargeHeapDoesNotWaitForTheDiskDuringAnOrdinaryCut() {
        assertTrue(CheckpointManager.queuedChunkBudget(16 * GIB, 256) >= 4096);
        assertTrue(CheckpointManager.queuedChunkBudget(4 * GIB, 256) >= 1024);
    }

    @Test
    void aSmallHeapStillBoundsTheQueuedSaves() {
        // A quarter of the heap at 512 KiB per queued save.
        assertEquals(512, CheckpointManager.queuedChunkBudget(GIB, 256));
        assertEquals(1024, CheckpointManager.queuedChunkBudget(2 * GIB, 256));
        assertEquals(16, CheckpointManager.queuedChunkBudget(16L * 1024L * 1024L, 256));
        assertEquals(65_536, CheckpointManager.queuedChunkBudget(1024 * GIB, 4096));
    }

    @Test
    void aBatchSetBelowTheOldLimitIsHonoured() {
        assertEquals(4, CheckpointManager.queuedChunkBudget(16 * GIB, 4));
        assertEquals(1, CheckpointManager.queuedChunkBudget(16 * GIB, 0));
    }
}
