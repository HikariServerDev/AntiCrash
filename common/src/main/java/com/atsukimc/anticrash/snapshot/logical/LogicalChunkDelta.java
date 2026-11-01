package com.atsukimc.anticrash.snapshot.logical;

/** One immutable ChunkSerializer NBT payload. */
public final class LogicalChunkDelta {
    public String dimension;
    public long packedPosition;
    public long captureEpoch;
    public long mutationGeneration;
    public String state = "PRESENT";
    public String payloadBase64;
    public String sha256;

    public String key() {
        return ChunkMutationTracker.key(dimension, packedPosition);
    }

    public long payloadBytes() {
        return payloadBase64 == null ? 0L : payloadBase64.length();
    }
}
