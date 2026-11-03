package com.atsukimc.anticrash.consistency;

public final class TransactionRecord {
    public int formatVersion = 1;
    public String transactionId;
    public String checkpointId;

    /**
     * Previous valid checkpoint while {@link #checkpointId} is still being committed. A transaction
     * must be considered for either ID until atomic commit succeeds.
     */
    public String fallbackCheckpointId;

    public long serverTick;
    public long wallClockEpochMs;
    public String playerUuid;
    public String transactionType;
    public String source;
    public String destination;
    public String dimension;
    public int x, y, z;
    public String itemStackId;
    public int count;
    public String nbt;
    public String before;
    public String after;
    public String reconciliationStatus = "UNRESOLVED";

    /** Durable WARN-mode record that intentionally excludes player/item/NBT details. */
    public static TransactionRecord warningSummary(TransactionRecord source) {
        TransactionRecord summary = new TransactionRecord();
        summary.transactionId = source.transactionId;
        summary.checkpointId = source.checkpointId;
        summary.fallbackCheckpointId = source.fallbackCheckpointId;
        summary.serverTick = source.serverTick;
        summary.wallClockEpochMs = source.wallClockEpochMs;
        summary.transactionType = source.transactionType;
        summary.reconciliationStatus = source.reconciliationStatus;
        return summary;
    }
}
