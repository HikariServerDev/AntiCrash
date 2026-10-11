package com.atsukimc.anticrash.recovery;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeapGuardTest {
    private static final long MB = 1024L * 1024L;

    @Test
    void heapFullOfGarbageIsCollectedInsteadOfBeingTreatedAsExhausted() {
        AtomicLong used = new AtomicLong(1000 * MB);
        AtomicInteger collections = new AtomicInteger();
        HeapGuard guard = new HeapGuard(64 * MB, () -> 1024 * MB, used::get, () -> {
            collections.incrementAndGet();
            used.set(300 * MB);
        }, () -> 0L);

        assertEquals(HeapGuard.Decision.PROCEED, guard.check(false));
        assertEquals(1, collections.get());
        assertFalse(guard.lowMemoryMode());
        assertEquals(HeapGuard.Decision.PROCEED, guard.check(false));
        assertEquals(1, collections.get());
    }

    @Test
    void realShortageWaitsInLowMemoryModeAndRecoversWhenMemoryIsFreed() {
        AtomicLong used = new AtomicLong(1000 * MB);
        AtomicLong clock = new AtomicLong();
        HeapGuard guard = new HeapGuard(64 * MB, () -> 1024 * MB, used::get, () -> { }, clock::get);

        assertEquals(HeapGuard.Decision.WAIT, guard.check(true));
        assertTrue(guard.lowMemoryMode());
        clock.addAndGet(HeapGuard.GIVE_UP_NANOS * 3);
        // Loads still running may free memory when they finish: never give up meanwhile.
        assertEquals(HeapGuard.Decision.WAIT, guard.check(true));

        used.set(500 * MB);
        assertEquals(HeapGuard.Decision.PROCEED, guard.check(false));
        assertTrue(guard.lowMemoryMode());
    }

    @Test
    void givesUpOnlyAfterAPersistentShortageWithNothingLeftToFinish() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger collections = new AtomicInteger();
        HeapGuard guard = new HeapGuard(64 * MB, () -> 1024 * MB, () -> 1000 * MB,
                collections::incrementAndGet, clock::get);

        assertEquals(HeapGuard.Decision.WAIT, guard.check(false));
        clock.addAndGet(HeapGuard.GIVE_UP_NANOS - 1);
        assertEquals(HeapGuard.Decision.WAIT, guard.check(false));
        clock.addAndGet(1);
        assertEquals(HeapGuard.Decision.EXHAUSTED, guard.check(false));
        assertTrue(collections.get() >= 2);
    }

    @Test
    void unboundedHeapNeverBlocks() {
        HeapGuard guard = new HeapGuard(64 * MB, () -> Long.MAX_VALUE, () -> 1L << 40, () -> { }, () -> 0L);
        assertEquals(HeapGuard.Decision.PROCEED, guard.check(false));
    }
}
