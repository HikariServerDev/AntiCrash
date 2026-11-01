package com.atsukimc.anticrash.snapshot;

import java.util.ArrayList;
import java.util.List;

public final class CheckpointManifest {
    /** Version 4 adds the Exact-Epoch semantic delta engine evidence. */
    public int formatVersion = 4;

    public String antiCrashVersion = com.atsukimc.anticrash.AntiCrashMod.VERSION;
    public static final String CURRENT_MINECRAFT_VERSION = "1.19";
    public String minecraftVersion = CURRENT_MINECRAFT_VERSION;
    public String fabricLoaderVersion;
    public String fabricApiVersion;
    public String carpetVersion;
    public String checkpointId;

    /**
     * Committed checkpoint whose unchanged files this generation reuses, or null for a BASE. Also
     * used as the lineage for manifests written before {@link #timelineParentCheckpointId}.
     */
    public String parentCheckpointId;

    /**
     * Previous committed checkpoint on the active recovery timeline, independent of file reuse. A
     * BASE generation has no file parent but still continues the timeline, so recovery can fall
     * back past it to older checkpoints.
     */
    public String timelineParentCheckpointId;

    public String utcTimestamp;
    public String localTimestamp;
    public long monotonicNanos;
    public long serverTick;
    public long worldGameTime;
    public final List<String> dimensions = new ArrayList<>();
    public String snapshotBackend;

    /** LOGICAL_DELTA, PORTABLE_GENERATION, or LEGACY_SAFE. */
    public String checkpointEngine = "PORTABLE_GENERATION";

    public int logicalDeltaFormatVersion;
    public long logicalDeltaEpoch;
    public int logicalDeltaChunkCount;
    public int logicalDeltaEntityCount;
    public int logicalDeltaEntityChunkCount;
    public int logicalDeltaTombstoneCount;
    public long logicalDeltaPayloadBytes;
    public String logicalDeltaFailureReason;
    public final java.util.Map<String, String> logicalDeltaComponentStatus =
            new java.util.LinkedHashMap<>();

    /** BASE or DELTA. DELTA is a complete materialized view with an authenticated parent. */
    public String generationType = "BASE";

    public String baseCheckpointId;
    public int generationChainDepth;
    public int generationSealFormatVersion;
    public long logicalCutMonotonicNanos;
    public long logicalCutServerTick;
    public int generationPresentFiles;
    public int generationAbsentFiles;
    public int generationInheritedFiles;
    public int generationSealedFiles;
    public int generationTombstoneFiles;

    /** PRESENT/ABSENT/TOMBSTONE records captured at the logical cut. */
    public final List<GenerationFileEntry> fileSet = new ArrayList<>();

    public long snapshotDurationMs;
    public long barrierDurationMs;
    public long finalizationDurationMs;
    public final java.util.Map<String, Long> phaseDurationsMs = new java.util.LinkedHashMap<>();
    public long heapUsedBeforeBytes;
    public long heapUsedAfterBytes;
    public long heapMaxBytes;
    public long gcCountBefore;
    public long gcCountAfter;
    public long gcTimeBeforeMs;
    public long gcTimeAfterMs;
    public long totalBytes;
    public int changedFiles;
    public int reusedFiles;
    public int runtimeCapsuleVersion = 1;
    public int playerSnapshotCount;
    public int fakePlayerCount;
    public int blockEventCount;
    public int loadedChunkCount;
    public int fullChunkCount;
    public int blockTickingChunkCount;
    public int entityTickingChunkCount;
    public int forcedChunkCount;
    public String fidelity;
    public String chunkDomainHash;
    public String carpetCaptureStatus;
    public int blockEntityTickerCount;
    public int entityOrderCount;
    public int torchBurnoutCount;
    public int ticketCount;
    public int checkpointPlayerCount;
    public String checkpointPlayerHash;
    public final List<String> unsupportedRuntimeState = new ArrayList<>();

    /** Chunks made eligible for serialization because they contain transient server state. */
    public int transientChunkCount;

    public int transientChunksMadeDirty;
    public int movingPistonCount;
    public long scheduledTickCount;

    /** Active scheduled ticks whose exact sub-tick ordering is in Runtime Capsule v3. */
    public long orderedScheduledTickCount;

    public long orderedMovingPistonCount;
    public long orderedTransientEntityCount;
    public String checksum;
    public boolean clean = true;
    public final List<SnapshotFile> worldFiles = new ArrayList<>();
}
