package com.atsukimc.anticrash.snapshot.logical;

/** Complete entity-list replacement for one captured chunk, including an intentional empty list. */
public final class LogicalEntityChunkDelta {
    public String dimension;
    public long packedPosition;
    public long captureEpoch;
    public String payloadBase64;
    public String sha256;

    public String key() {
        return ChunkMutationTracker.key(dimension, packedPosition);
    }
}
