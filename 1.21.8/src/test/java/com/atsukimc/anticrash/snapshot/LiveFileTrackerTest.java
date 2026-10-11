package com.atsukimc.anticrash.snapshot;

import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveFileTrackerTest {
    private final LiveFileTracker tracker = LiveFileTracker.global();

    @AfterEach
    void disarm() {
        tracker.disarm();
    }

    @Test
    void recordsNothingUntilTheFirstCutArmsIt() {
        tracker.disarm();
        tracker.recordRegionWrite(Path.of("world/region"), new ChunkPos(0, 0));
        assertNull(tracker.swap());
        tracker.recordRegionWrite(Path.of("world/region"), new ChunkPos(33, -1));
        Set<Path> written = tracker.swap();
        Path region = Path.of("world/region").toAbsolutePath().normalize();
        assertEquals(Set.of(region.resolve("r.1.-1.mca"), region.resolve("c.33.-1.mcc")), written);
        assertTrue(tracker.swap().isEmpty());
    }

    @Test
    void failedIntervalIsCarriedIntoTheNextOne() {
        tracker.swap();
        tracker.recordRegionWrite(Path.of("world/entities"), new ChunkPos(0, 0));
        Set<Path> failed = tracker.swap();
        tracker.recordRegionWrite(Path.of("world/poi"), new ChunkPos(0, 0));
        tracker.restore(failed);
        Set<Path> next = tracker.swap();
        assertEquals(4, next.size());
        assertTrue(next.containsAll(failed));
    }
}
