package com.atsukimc.anticrash.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


class RuntimeFingerprintTest {
    @Test
    void sameOrderedLinesHaveTheSameFingerprint() {
        assertEquals(RuntimeFingerprint.hashLines(List.of("a", "b")),
                RuntimeFingerprint.hashLines(List.of("a", "b")));
    }

    @Test
    void orderAndCountArePartOfTheFingerprint() {
        String ordered = RuntimeFingerprint.hashLines(List.of("a", "b"));
        assertNotEquals(ordered, RuntimeFingerprint.hashLines(List.of("b", "a")));
        assertNotEquals(ordered, RuntimeFingerprint.hashLines(List.of("a", "b", "")));
    }

    @Test
    void worldRngFingerprintIsAStableUnsupportedCapabilityMarker() {
        assertEquals(RuntimeFingerprint.hashLines(RuntimeFingerprint.worldRngLines()),
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldRngLines()));
        assertEquals(3, RuntimeFingerprint.worldRngLines().size());
    }

    @Test
    void optionalRealPlayerFilteringKeepsFakePlayersInEntityOrder() {
        EntityRuntimeSnapshot fake = entity("fake", 0);
        EntityRuntimeSnapshot real = entity("REAL", 1);
        List<EntityRuntimeSnapshot> checkpoint = List.of(fake, real);
        List<EntityRuntimeSnapshot> afterRestart = List.of(entity("fake", 0));

        assertEquals(RuntimeFingerprint.entityLines(checkpoint, Set.of("FAKE"), false),
                RuntimeFingerprint.entityLines(afterRestart, Set.of("FAKE"), false));
        assertNotEquals(RuntimeFingerprint.entityLines(checkpoint, Set.of("FAKE"), true),
                RuntimeFingerprint.entityLines(afterRestart, Set.of("FAKE"), true));
    }

    @Test
    void chunkDomainFingerprintIgnoresHolderMetadataAndIncidentalSetOrder() {
        ChunkStateSnapshot first = chunk("minecraft:overworld", 4L, 33,
                "ENTITY_TICKING", "holder");
        ChunkStateSnapshot second = chunk("minecraft:overworld", 5L, 31,
                "TICKING", "holder");
        ChunkStateSnapshot restartedFirst = chunk("minecraft:overworld", 4L, 31,
                "ENTITY_TICKING", "recovery");
        ChunkStateSnapshot restartedSecond = chunk("minecraft:overworld", 5L, 32,
                "TICKING", "vanilla");

        assertEquals(RuntimeFingerprint.chunkDomainLines(List.of(first, second)),
                RuntimeFingerprint.chunkDomainLines(List.of(restartedSecond, restartedFirst)));
        assertEquals(RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.chunkDomainLines(List.of(first, second))),
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.chunkDomainLines(List.of(restartedSecond, restartedFirst))));

        restartedSecond.domain.get(0).blockTicking = false;
        assertNotEquals(RuntimeFingerprint.chunkDomainLines(List.of(first, second)),
                RuntimeFingerprint.chunkDomainLines(List.of(restartedSecond, restartedFirst)));
    }

    private static ChunkStateSnapshot chunk(String dimension, long position, int level,
                                            String levelType, String source) {
        ChunkStateSnapshot snapshot = new ChunkStateSnapshot();
        snapshot.dimension = dimension;
        ChunkStateSnapshot.ChunkReadiness readiness = new ChunkStateSnapshot.ChunkReadiness();
        readiness.packedPosition = position;
        readiness.loaded = true;
        readiness.full = true;
        readiness.blockTicking = true;
        readiness.entityTicking = true;
        readiness.forced = false;
        readiness.level = level;
        readiness.completedLevel = level;
        readiness.levelType = levelType;
        readiness.source = source;
        snapshot.domain.add(readiness);
        snapshot.loadedChunks.add(position);
        snapshot.fullChunks.add(position);
        snapshot.blockTickingChunks.add(position);
        snapshot.entityTickingChunks.add(position);
        return snapshot;
    }

    private static EntityRuntimeSnapshot entity(String uuid, int order) {
        EntityRuntimeSnapshot entity = new EntityRuntimeSnapshot();
        entity.dimension = "minecraft:overworld";
        entity.uuid = uuid;
        entity.type = "minecraft:player";
        entity.networkEntityId = order + 100;
        entity.order = order;
        return entity;
    }
}
