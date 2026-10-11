package com.atsukimc.anticrash.snapshot.logical;

/** Explicit deletion/move evidence; absence is never inferred from an unloaded chunk. */
public final class LogicalTombstone {
    public String kind;
    public String dimension;
    public String key;
    public long captureEpoch;
    public String reason;
    /** Nullable because chunk (0,0) is a valid position and must not be confused with absent. */
    public Long packedPosition;

    public LogicalTombstone() {
    }

    public LogicalTombstone(String kind, String dimension, String key, long captureEpoch, String reason) {
        this(kind, dimension, key, captureEpoch, reason, 0L);
    }

    public LogicalTombstone(String kind, String dimension, String key, long captureEpoch,
                            String reason, long packedPosition) {
        this.kind = kind;
        this.dimension = dimension;
        this.key = key;
        this.captureEpoch = captureEpoch;
        this.reason = reason;
        this.packedPosition = packedPosition;
    }
}
