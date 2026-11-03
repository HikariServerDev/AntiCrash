package com.atsukimc.anticrash.snapshot.logical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portable, JSON-serializable semantic delta sealed at one logical epoch. */
public final class LogicalDelta {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public int formatVersion = CURRENT_FORMAT_VERSION;
    public String backend = "LOGICAL_DELTA";
    public String captureKind = "EXACT_EPOCH";
    public long captureEpoch;
    public long logicalCutMonotonicNanos;
    public long lifecycleSequence;
    public String parentCheckpointId;
    public boolean exact;
    public String failureReason;
    public int chunkCount;
    public int entityCount;
    public int entityChunkCount;
    public int tombstoneCount;
    public long payloadBytes;
    public final List<LogicalChunkDelta> chunks = new ArrayList<>();
    public final List<LogicalEntityDelta> entities = new ArrayList<>();
    public final List<LogicalEntityChunkDelta> entityChunks = new ArrayList<>();
    public final List<LogicalTombstone> tombstones = new ArrayList<>();
    public final Map<String, String> componentStatus = new LinkedHashMap<>();

    /** Key -> payload hash for diagnostics and future offline compaction. */
    public final Map<String, String> chunkIndex = new LinkedHashMap<>();

    /** Key -> mutation generation for the complete observable chunk domain at this epoch. */
    public final Map<String, Long> chunkGenerationIndex = new LinkedHashMap<>();

    /** Key -> entity-region hash for the complete observable entity domain at this epoch. */
    public final Map<String, String> entityChunkIndex = new LinkedHashMap<>();

    public static LogicalDelta blocked(long epoch, String reason) {
        LogicalDelta delta = new LogicalDelta();
        delta.captureEpoch = epoch;
        delta.exact = false;
        delta.failureReason = reason;
        delta.componentStatus.put("LOGICAL_DELTA", "BARRIER_REQUIRED");
        return delta;
    }

    public void addChunk(LogicalChunkDelta chunk) {
        if (chunk == null) return;
        chunks.add(chunk);
        chunkCount++;
        payloadBytes += Math.max(0L, chunk.payloadBytes());
        chunkIndex.put(chunk.key(), chunk.sha256);
    }

    public void indexChunk(String key, String sha256, long generation) {
        if (key == null) return;
        chunkIndex.put(key, sha256);
        chunkGenerationIndex.put(key, generation);
    }

    public void indexEntityChunk(String key, String sha256) {
        if (key != null) entityChunkIndex.put(key, sha256);
    }

    public void addEntity(LogicalEntityDelta entity) {
        if (entity == null) return;
        entities.add(entity);
        entityCount++;
        payloadBytes += Math.max(0L, entity.payloadBytes());
    }

    public void addEntityChunk(LogicalEntityChunkDelta chunk) {
        if (chunk == null) return;
        entityChunks.add(chunk);
        entityChunkCount++;
        payloadBytes += chunk.payloadBase64 == null ? 0L : chunk.payloadBase64.length();
    }

    public void addTombstone(LogicalTombstone tombstone) {
        if (tombstone == null) return;
        tombstones.add(tombstone);
        tombstoneCount++;
    }
}
