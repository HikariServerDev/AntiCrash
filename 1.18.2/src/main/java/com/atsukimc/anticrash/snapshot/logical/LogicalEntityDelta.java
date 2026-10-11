package com.atsukimc.anticrash.snapshot.logical;

/** One immutable Entity#writeNbt payload captured on the server thread. */
public final class LogicalEntityDelta {
    public String dimension;
    public String uuid;
    public String entityType;
    public int networkId;
    public long packedChunkPosition;
    public long captureEpoch;
    public String payloadBase64;
    public String sha256;

    public long payloadBytes() {
        return payloadBase64 == null ? 0L : payloadBase64.length();
    }
}
