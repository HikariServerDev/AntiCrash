package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeCapsule {
    public static final int MIN_SUPPORTED_FORMAT_VERSION = 1;
    public static final int CURRENT_FORMAT_VERSION = 8;

    /**
     * Version 4 additionally records each world's next scheduled-tick ordering value.
     * Version 5 adds Carpet's global tick speed, which Carpet itself never persists.
     * Version 6 adds the observable tick domain, semantic runtime ordering snapshots and
     * category fingerprints used by the Recovery Equivalence Contract. It also carries an
     * optional lightweight Real Player identity list for the STRICT player-readiness policy. It
     * deliberately does not pretend that opaque JVM/worker state is serializable.
     * Version 7 records, for verification, the exact progress of every moving piston (the value
     * itself lives in the chunk data, see RuntimeExtensionNbt), the remaining lifetime of expiring
     * chunk tickets, and the container screens players had open, which cannot be restored.
     */
    public int formatVersion = CURRENT_FORMAT_VERSION;
    public String fidelity = "OPTIMIZED";
    public long serverTick;
    /** Null in older capsules; missing state must not be treated as an equivalent default. */
    public NativeTickState nativeTickState;
    /** v8/26.1: null means missing, NOT an equivalent empty set. */
    public List<ClockSnapshot> clockStates;
    public long capturedAtEpochMs;
    public final List<WorldRuntime> worlds = new ArrayList<>();
    public BlockEventSnapshot blockEvents = new BlockEventSnapshot();
    public final List<ChunkStateSnapshot> chunks = new ArrayList<>();
    public final List<ScheduledTickSnapshot> scheduledTicks = new ArrayList<>();
    public final List<MovingPistonTickSnapshot> movingPistonTickOrder = new ArrayList<>();
    public final List<TransientEntityTickSnapshot> transientEntityTickOrder = new ArrayList<>();
    public final List<EntityRuntimeSnapshot> entityOrder = new ArrayList<>();
    public final List<BlockEntityTickerSnapshot> blockEntityTickerOrder = new ArrayList<>();
    public final List<RedstoneTorchBurnoutSnapshot> redstoneTorchBurnout = new ArrayList<>();
    public final List<TicketLedgerSnapshot.Entry> ticketLedger = new ArrayList<>();
    /** Present only when STRICT player readiness was enabled at checkpoint time. */
    public boolean checkpointPlayersCaptured;
    public final List<CheckpointPlayerSnapshot> checkpointPlayers = new ArrayList<>();
    /**
     * Version of the FAKEPLAYERS fingerprint. 0 (every capsule written before it existed) hashed
     * the live action pack including null members that fakeplayers.json never contains, so such a
     * fingerprint can never be reproduced from the authenticated file.
     */
    public int fakePlayerFingerprintVersion;
    public static final int CURRENT_FAKE_PLAYER_FINGERPRINT_VERSION = 1;
    /** Fingerprints are diagnostic evidence, never a substitute for category comparison. */
    public final java.util.Map<String, String> expectedFingerprints = new java.util.LinkedHashMap<>();
    /** CAPTURED, NOT_APPLICABLE, INCOMPLETE or UNSUPPORTED. */
    public String carpetCaptureStatus = "NOT_APPLICABLE";
    public final List<String> unsupportedRuntimeState = new ArrayList<>();
    /**
     * "player|screen handler" for every player with a container open at the checkpoint (v7).
     * Observed only: the viewer count of a chest (a trapped chest's redstone output) or a shulker
     * box belongs to a player's open screen, and that session cannot be re-created faithfully.
     */
    public final List<String> containerSessions = new ArrayList<>();
    /**
     * InhabitedTime of every loaded chunk at the cut (v7). Vanilla saves the value but adds to it
     * every tick without marking the chunk for saving, so the chunk data of a checkpoint holds
     * whatever it was at the chunk's last save. Marking every ticking chunk dirty would rewrite the
     * whole loaded world at each checkpoint; the values are carried here instead.
     */
    public final List<InhabitedTimes> inhabitedTimes = new ArrayList<>();
    /**
     * Number of entries behind each RuntimeExtensionSnapshot category (v7); the matching digest is
     * in {@link #expectedFingerprints}. Absent in older capsules.
     */
    public final java.util.Map<String, Integer> runtimeExtensionCounts = new java.util.LinkedHashMap<>();

    /**
     * Next network entity id at the cut (v7; null in older capsules). Items and experience orbs
     * created after a restore must get ids with the same remainder as in an uninterrupted game:
     * an item's move phase is (age + id) % 4 and orbs merge when their ids differ by 40.
     */
    public Integer entityIdCounter;

    /**
     * The canonical lines behind the smaller RuntimeExtensionSnapshot categories (v7, optional).
     * The digest decides whether a recovery matched; these lines only let a mismatch say what
     * differs. Omitted for a category with more lines than {@link #MAX_DIAGNOSTIC_LINES}.
     */
    public final java.util.Map<String, List<String>> runtimeExtensionLines = new java.util.LinkedHashMap<>();
    public static final int MAX_DIAGNOSTIC_LINES = 20_000;

    public static final class InhabitedTimes {
        public String dimension;
        public final List<Long> chunks = new ArrayList<>();
        public final List<Long> times = new ArrayList<>();
    }
    /**
     * Carpet rule values that were not at their built-in default, per settings manager (v7,
     * optional). Null when Carpet is absent, unreadable, or the capsule predates the field.
     */
    public java.util.Map<String, java.util.Map<String, String>> carpetRules;
    /** Null when Carpet is absent or its tick speed could not be read. */
    public com.atsukimc.anticrash.integration.carpet.TickSpeedSnapshot carpetTickSpeed;

    public int blockEventCount() { return blockEvents == null ? 0 : blockEvents.entries.size(); }
    public int loadedChunkCount() { return chunks.stream().mapToInt(ChunkStateSnapshot::loadedCount).sum(); }
    public int entityTickingChunkCount() { return chunks.stream().mapToInt(ChunkStateSnapshot::entityTickingCount).sum(); }
    public int orderedScheduledTickCount() { return scheduledTicks == null ? 0 : scheduledTicks.size(); }
    public int orderedMovingPistonCount() {
        return movingPistonTickOrder == null ? 0 : movingPistonTickOrder.size();
    }
    public int orderedTransientEntityCount() {
        return transientEntityTickOrder == null ? 0 : transientEntityTickOrder.size();
    }
    public int entityOrderCount() { return entityOrder == null ? 0 : entityOrder.size(); }
    public int blockEntityTickerCount() {
        return blockEntityTickerOrder == null ? 0 : blockEntityTickerOrder.size();
    }
    public int redstoneTorchBurnoutCount() {
        if (redstoneTorchBurnout == null) return 0;
        return redstoneTorchBurnout.stream().mapToInt(snapshot ->
                snapshot == null || snapshot.entries == null ? 0 : snapshot.entries.size()).sum();
    }
    public int ticketCount() { return ticketLedger == null ? 0 : ticketLedger.size(); }

    public static boolean supportsFormatVersion(int version) {
        return version >= MIN_SUPPORTED_FORMAT_VERSION && version <= CURRENT_FORMAT_VERSION;
    }

    public static final class WorldRuntime {
        public String dimension;
        public long gameTime;
        public long timeOfDay;
        /** The next value returned by World#getTickOrder, not the last emitted value. */
        public long nextTickOrder;
        public int loadedChunkCount;
        public int fullChunkCount;
        public int blockTickingChunkCount;
        public int entityTickingChunkCount;
        public int forcedChunkCount;
        /** Current World#lcgBlockSeed; safe to access through the mapped accessor. */
        public int lcgBlockSeed;
        /** java.util.Random internals are intentionally not forced open by AntiCrash. */
        public boolean randomStateSupported;
        /**
         * v7. ServerWorld#idleTimeout: a dimension with no player and no forced chunk stops
         * ticking entities and block entities after 300 ticks; Vanilla restarts the count at 0.
         * Null in older capsules.
         */
        public Integer idleTimeout;
        /** v7. Weather transition values; Vanilla snaps them to 0 or 1 on load. */
        public Float rainGradient;
        public Float thunderGradient;
    }
}
