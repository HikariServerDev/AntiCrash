package com.atsukimc.anticrash.snapshot.logical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkCandidateCacheTest {
    @Test
    void rejectsFutureAndMutatedCandidates() {
        ChunkCandidateCache cache = new ChunkCandidateCache(1024);
        cache.put("minecraft:overworld:1", 10L, 4L, new byte[]{1, 2, 3}, "hash");

        assertNotNull(cache.getIfValid("minecraft:overworld:1", 10L, 4L));
        assertNotNull(cache.getIfValid("minecraft:overworld:1", 11L, 4L));
        assertNull(cache.getIfValid("minecraft:overworld:1", 9L, 4L));
        assertNull(cache.getIfValid("minecraft:overworld:1", 10L, 5L));
    }

    @Test
    void evictsOldEntriesWithoutWeakeningEpochChecks() {
        ChunkCandidateCache cache = new ChunkCandidateCache(5);
        cache.put("a", 1L, 0L, new byte[]{1, 2, 3}, "a");
        cache.put("b", 1L, 0L, new byte[]{4, 5, 6}, "b");

        assertTrue(cache.bytes() <= 5);
        assertTrue(cache.evictions() >= 1);
        assertNull(cache.getIfValid("a", 1L, 0L));
    }
}
