package com.atsukimc.anticrash.snapshot.logical;

import static org.junit.jupiter.api.Assertions.*;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.ChunkPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.ChunkPos;
//?}

import org.junit.jupiter.api.Test;

class LogicalDeltaModelTest {
    @Test
    void recordsCompleteIndexesAndOnlyPayloadCountersForChangedValues() {
        LogicalDelta delta = new LogicalDelta();
        delta.captureEpoch = 42L;
        delta.indexChunk("minecraft:overworld:0", "old", 7L);
        delta.indexEntityChunk("minecraft:overworld:0", "entity-old");

        LogicalChunkDelta chunk = new LogicalChunkDelta();
        chunk.dimension = "minecraft:overworld";
        chunk.packedPosition = 0L;
        chunk.payloadBase64 = "AQI=";
        chunk.sha256 = "new";
        delta.addChunk(chunk);

        assertEquals(1, delta.chunkCount);
        assertEquals(1, delta.chunkGenerationIndex.size());
        assertEquals("new", delta.chunkIndex.get(chunk.key()));
        assertEquals("entity-old", delta.entityChunkIndex.get("minecraft:overworld:0"));
    }

    @Test
    void chunkZeroTombstoneIsRepresentedAndNotTreatedAsMissing() {
        LogicalTombstone tombstone =
                new LogicalTombstone(
                        "ENTITY_REMOVED", "minecraft:overworld", "uuid", 1L, "dead", 0L);
        assertNotNull(tombstone.packedPosition);
        assertEquals(0L, tombstone.packedPosition.longValue());
    }

    @Test
    void schedulerMutationUsesRegisteredDimensionAndChunkGeneration() {
        ChunkMutationTracker tracker = ChunkMutationTracker.global();
        tracker.resetForTests();
        Object scheduler = new Object();

        ChunkMutationTracker.registerScheduler(scheduler, "minecraft:overworld");
        ChunkMutationTracker.recordScheduler(scheduler, new ChunkPos(3, -2), "schedule");

        //? if (>=1.18 && <=1.21.11) {
        /*assertEquals(1L, tracker.generation("minecraft:overworld", new ChunkPos(3, -2).toLong()));
        *///?} else if (>=26.1 && <=26.3) {
        assertEquals(1L, tracker.generation("minecraft:overworld", new ChunkPos(3, -2).pack()));
        //?}

        assertFalse(tracker.isUncertain());
        tracker.resetForTests();
    }

    @Test
    void unregisteredSchedulerIsFailClosedInsteadOfGuessingDimension() {
        ChunkMutationTracker tracker = ChunkMutationTracker.global();
        tracker.resetForTests();

        ChunkMutationTracker.recordScheduler(new Object(), new ChunkPos(3, -2), "schedule");

        assertTrue(tracker.isUncertain());

        //? if (>=1.18 && <=1.21.11) {
        /*assertEquals(0L, tracker.generation("minecraft:overworld", new ChunkPos(3, -2).toLong()));
        *///?} else if (>=26.1 && <=26.3) {
        assertEquals(0L, tracker.generation("minecraft:overworld", new ChunkPos(3, -2).pack()));
        //?}

        tracker.resetForTests();
    }
}
