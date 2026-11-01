package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.core.AntiCrashState;
import com.atsukimc.anticrash.core.AntiCrashStateMachine;
import com.atsukimc.anticrash.core.InternalStateManager;
import com.atsukimc.anticrash.integration.carpet.CarpetIntegration;
import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.mixin.SerializingRegionBasedStorageAccessorMixin;
import com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin;
import com.atsukimc.anticrash.player.PlayerSnapshotData;
import com.atsukimc.anticrash.player.PlayerSnapshotManager;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.RuntimeCapsuleManager;
import com.atsukimc.anticrash.runtime.ScheduledTickMutationTracer;
import com.atsukimc.anticrash.runtime.TransientChunkStateMarker;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;
import com.atsukimc.anticrash.snapshot.CheckpointStore;
import com.atsukimc.anticrash.snapshot.DetachedGeneration;
import com.atsukimc.anticrash.snapshot.GenerationSeal;
import com.atsukimc.anticrash.snapshot.LiveFileTracker;
import com.atsukimc.anticrash.snapshot.LiveWorldScan;
import com.atsukimc.anticrash.snapshot.ParentCheckpointChangedException;
import com.atsukimc.anticrash.snapshot.PortableGenerationBackend;
import com.atsukimc.anticrash.snapshot.SnapshotFile;
import com.atsukimc.anticrash.snapshot.SnapshotManager;
import com.atsukimc.anticrash.snapshot.SnapshotResult;
import com.atsukimc.anticrash.snapshot.VanillaFileSnapshot;
import com.atsukimc.anticrash.snapshot.WorldLocator;
import com.atsukimc.anticrash.snapshot.logical.CheckpointMirror;
import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;
import com.atsukimc.anticrash.snapshot.logical.EntityLifecycleTracker;
import com.atsukimc.anticrash.snapshot.logical.LogicalDelta;
import com.atsukimc.anticrash.snapshot.logical.LogicalDeltaEngine;
import com.atsukimc.anticrash.snapshot.logical.LogicalDeltaMaterializer;
import com.atsukimc.anticrash.snapshot.logical.LogicalTracking;
import com.atsukimc.anticrash.util.AtomicFiles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WrapperProtoChunk;
import net.minecraft.world.storage.StorageIoWorker;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Coordinates the short server-thread consistency barrier and the long filesystem operation.
 *
 * <p>The worker must never access Minecraft objects. Everything it needs is copied into {@link
 * CaptureContext} while the server thread owns the barrier.
 */
public final class CheckpointManager {
    /**
     * Longest a portable cut may take before it is abandoned (no checkpoint, the world resumes).
     *
     * <p>The cut always completes inside the tick that took it. Spreading it over ticks, as the old
     * barrier did, runs every other mod's server-tick hook (Carpet and scarpet, Fabric API tick
     * events, ...) between the Runtime Capsule and the file capture, and any of them may change the
     * world after the cut. The bound keeps the call well below the 60 s watchdog.
     */
    /** How long the cut waits for every IO worker to finish its current task and park. */
    private static final long IO_SEAL_TIMEOUT_NANOS = 10_000_000_000L;

    private static final long MAX_CUT_NANOS = 40_000_000_000L;

    /**
     * A legacy barrier still unfinished after this long is abandoned so the world is never held
     * forever.
     */
    private static final long MAX_BARRIER_NANOS = 120_000_000_000L;

    private final AntiCrashConfig config;
    private final InternalStateManager internal;
    private final AntiCrashStateMachine states;
    private final AntiCrashLogger logger;
    private final CheckpointStore store;
    private final SnapshotManager snapshots;
    private final RuntimeCapsuleManager runtime;
    private final PlayerSnapshotManager players;
    private final LogicalDeltaEngine logicalDeltaEngine;
    private final CheckpointFailureGate failureGate = new CheckpointFailureGate();
    private volatile String activePhase = "IDLE";
    private String pinnedParentId;
    private CompletableFuture<Void> rollingDrain;
    private final CheckpointMirror checkpointMirror;
    private final RollingBudgetController rollingBudget;
    private final ExecutorService ioExecutor;

    private long nextDeadlineNanos;
    private boolean requestPending;
    private boolean baseRequested;
    private long requestEnqueuedNanos;
    private boolean capturing;
    private boolean lastJobSuccessful;
    private Future<CaptureResult> activeJob;
    private Future<CompactionResult> compactionJob;
    private PendingBarrier pendingBarrier;
    private Future<PreviousCheckpoint> previousLookupJob;
    private boolean previousLookupComplete;
    private Future<Map<String, SnapshotFile>> liveScanJob;
    private boolean liveScanComplete;
    private Map<String, SnapshotFile> liveHashes = Map.of();
    private Path previousWorld;
    private CheckpointManifest previousManifest;
    private volatile boolean shutdownCancellation;
    private volatile boolean liveCopyComplete;
    private volatile String liveCopyCheckpointId;
    private volatile boolean portableGenerationSealed;
    private volatile String lastFinalizationError;
    private volatile String lastCompactionError;
    private volatile CheckpointTiming lastTiming;
    private volatile Path activeTemporary;
    private String lastCheckpointId = "none";
    private boolean saveOffReported;
    private CheckpointManifest lastManifest;

    /**
     * Committed checkpoint whose cut started the current {@link LiveFileTracker} interval. While it
     * is the parent of the next capture, region files the tracker did not see written can be
     * inherited without being examined.
     */
    private String trackerBaseCheckpointId;

    /** Result of the last POI/PersistentState probe; null until the first capture probed it. */
    private Boolean logicalPersistentStateBlocked;

    private String lastLogicalFallbackReason;

    /** Tracker interval handed to the running job; returned to the tracker if the job fails. */
    private Set<java.nio.file.Path> activeTrackerWrites;

    /** True when the running job's cut started the tracker interval now being recorded. */
    private boolean activeTrackerArmedAtCut;

    public CheckpointManager(
            AntiCrashConfig config,
            InternalStateManager internal,
            AntiCrashStateMachine states,
            AntiCrashLogger logger,
            RuntimeCapsuleManager runtime,
            PlayerSnapshotManager players)
            throws IOException {
        this.config = config;
        this.internal = internal;
        this.states = states;
        this.logger = logger;
        this.runtime = runtime;
        this.players = players;
        this.store = new CheckpointStore(internal.checkpointsDir(), config, logger);
        this.snapshots = new SnapshotManager(config, logger);
        this.logicalDeltaEngine =
                new LogicalDeltaEngine(
                        Math.min(
                                Math.max(16L, config.checkpoint.candidateCacheMaxMb)
                                        * 1024L
                                        * 1024L,
                                Math.max(
                                        16L * 1024 * 1024,
                                        Runtime.getRuntime().maxMemory() / 16L)));
        this.checkpointMirror = new CheckpointMirror(config.checkpoint);
        this.rollingBudget =
                new RollingBudgetController(
                        config.checkpoint.rollingBudgetMsPerTick,
                        config.checkpoint.rollingMaxBudgetMsPerTick,
                        config.checkpoint.adaptiveBudget);
        this.ioExecutor =
                Executors.newFixedThreadPool(
                        Math.max(1, config.checkpoint.backgroundThreads),
                        runnable -> {
                            Thread thread = new Thread(runnable, "AntiCrash-Checkpoint-IO");
                            thread.setDaemon(true);
                            return thread;
                        });
        resetDeadline();
    }

    public void resetDeadline() {
        nextDeadlineNanos = System.nanoTime() + config.checkpoint.intervalSeconds * 1_000_000_000L;
    }

    public void requestManual() {
        if (config.checkpoint.createOnManualCommand) {
            failureGate.manualRetry();
            requestPending = true;
            if (requestEnqueuedNanos == 0L) requestEnqueuedNanos = System.nanoTime();
            if (states.get() == AntiCrashState.RUNNING) {
                enterCheckpointState(AntiCrashState.CHECKPOINT_REQUESTED);
            }
        }
    }

    /**
     * Explicit operator request for the first/full BASE. Normal automatic checkpoints never enter
     * this path implicitly, because a cold BASE can otherwise freeze a live world for tens of
     * seconds on a large save.
     */
    public void requestBase() {
        if (!config.checkpoint.enabled || !config.checkpoint.createOnManualCommand) return;
        baseRequested = true;
        requestManual();
    }

    /**
     * Called from the end of the server tick. The only potentially non-trivial work here is the
     * consistency barrier and the Future state transition; all tree walking and hashing is
     * performed by {@link #ioExecutor}.
     */
    public void onTickEnd(MinecraftServer server) {
        checkpointMirror.onTick();
        syncLogicalTracking();
        if (states.get() == AntiCrashState.CHECKPOINT_FINALIZE_FAILED) {
            // Finalization is detached from the live world.  Expose the failure for one state
            // observation, then return to normal operation without re-freezing the server.
            returnToRunning();
        }
        pollCompaction(server);
        if (config.checkpoint.rollingEnabled
                && states.get() == AntiCrashState.RUNNING
                && failureGate.permits(System.nanoTime())
                && !savingDisabled(server)) {
            try {
                prepareDirtyChunksWithinRollingBudget(server);
            } catch (OutOfMemoryError failure) {
                activePhase = "ROLLING_PREPARATION";
                failBarrier(failure);
                return;
            }
        }
        // Recovery selection already verifies its candidate explicitly. Starting the incremental
        // base preflight at the same time duplicates a full-world hash pass and obscures ASK logs.
        if (!states.isRecovery() && requestPending && failureGate.permits(System.nanoTime())) {
            pollPreviousLookup();
            pollLiveScan();
        }
        if (pendingBarrier != null) {
            advanceBarrier(server);
            return;
        }
        if (pollActiveJob()) {
            return;
        }
        if (config.checkpoint.enabled
                && states.get() == AntiCrashState.RUNNING
                && failureGate.permits(System.nanoTime())
                && System.nanoTime() - nextDeadlineNanos >= 0) {
            requestPending = true;
            if (requestEnqueuedNanos == 0L) requestEnqueuedNanos = System.nanoTime();
        }
        // A compaction adopts its result as the newest parent when it finishes. A checkpoint
        // committed meanwhile would be newer than that result and the adoption would roll the
        // parent (and the recovery timeline) back, so captures wait for the compaction.
        if (requestPending
                && failureGate.permits(System.nanoTime())
                && previousLookupComplete
                && liveScanComplete
                && compactionJob == null
                && states.get() != AntiCrashState.RECOVERY_FAILED
                && !states.isRecovery()) {
            requestPending = false;
            beginCapture(server);
        }
    }

    /**
     * Opportunistically persists a small amount of dirty chunk state while the world is running.
     * This is preparation only: no runtime capsule is created and no chunk is considered part of a
     * checkpoint until the exact logical-cut barrier captures it again.
     */
    private void prepareDirtyChunksWithinRollingBudget(MinecraftServer server) {
        // Backpressure, not a wait: do not serialize another batch while storage still owns the
        // last batch's NBT. Previously a discarded completeAll Future allowed the queue to grow.
        if (rollingDrain != null) {
            if (!rollingDrain.isDone()) return;
            try {
                rollingDrain.getNow(null);
            } catch (CompletionException failed) {
                logger.verboseKey("checkpoint.rolling_prepare_failed", failed.getMessage());
            }
            rollingDrain = null;
        }
        long budget = rollingBudget.budgetNanos();
        if (budget <= 0L) return;
        if (logicalPersistentStateBlocked == null
                && CheckpointEnginePolicy.logicalDeltaRequested(config)) {
            // Probe once before the first capture so a world that can never use the logical
            // engine does not spend its rolling budget on candidates in the meantime.
            try {
                logicalPersistentStateBlocked =
                        LogicalDeltaEngine.hasUnsupportedPersistentFiles(server)
                                && !config.checkpoint.allowUnsupportedPersistentState;
            } catch (IOException exception) {
                logicalPersistentStateBlocked = true;
            }
        }
        if (logicalEngineUsable()) {
            LogicalDeltaEngine.PreparationReport report =
                    logicalDeltaEngine.prepareCandidates(
                            server,
                            Math.min(
                                    budget,
                                    (long)
                                            (config.checkpoint.candidateBudgetMsPerTick
                                                    * 1_000_000L)));
            if (report.captured() > 0) {
                logger.verboseKey(
                        "checkpoint.logical_candidates_prepared",
                        report.captured(),
                        report.inspected(),
                        report.bytes(),
                        report.evictions());
            }
            if (report.error() != null) {
                logger.verboseKey("checkpoint.logical_candidates_failed", report.error());
            }
            return;
        }
        long started = System.nanoTime();
        int prepared = 0;
        try {
            outer:
            for (ServerWorld world : server.getWorlds()) {
                world.getChunkManager().executeQueuedTasks();
                ThreadedAnvilChunkStorageAccessorMixin storage =
                        (ThreadedAnvilChunkStorageAccessorMixin)
                                world.getChunkManager().threadedAnvilChunkStorage;
                for (ChunkHolder holder : storage.anticrash$getCurrentChunkHolders().values()) {
                    if (prepared >= 16 || System.nanoTime() - started >= budget) break outer;
                    if (holder == null || !holder.isAccessible()) continue;
                    CompletableFuture<Chunk> saving = holder.getSavingFuture();
                    if (!saving.isDone()) continue;
                    Chunk chunk = saving.getNow(null);
                    if (chunk != null
                            && chunk.needsSaving()
                            && storage.anticrash$saveChunk(chunk)) {
                        prepared++;
                    }
                }
            }
            // Do not wait for these writes. Whatever is still queued at the cut is sealed with the
            // IO workers and taken into the checkpoint from the queue (see IoSeal).
            List<CompletableFuture<Void>> drains = new ArrayList<>();
            for (ServerWorld world : server.getWorlds()) {
                Object worker = world.getChunkManager().getChunkIoWorker();
                if (prepared > 0 && worker instanceof StorageIoWorker storageIoWorker)
                    drains.add(storageIoWorker.completeAll(false));
            }
            if (!drains.isEmpty())
                rollingDrain = CompletableFuture.allOf(drains.toArray(CompletableFuture[]::new));
        } catch (RuntimeException exception) {
            // Preparation is optional.  A failure leaves the chunk dirty for the fail-closed
            // barrier path and is not allowed to take down the running server.
            logger.verboseKey("checkpoint.rolling_prepare_failed", exception.getMessage());
        } finally {
            rollingBudget.observe(System.nanoTime() - started);
        }
        if (prepared > 0)
            logger.verboseKey(
                    "checkpoint.rolling_prepared",
                    prepared,
                    String.format(java.util.Locale.ROOT, "%.2f", rollingBudget.currentMs()));
    }

    /**
     * Starts the final checkpoint asynchronously. A caller that needs a clean shutdown must keep
     * the server alive until {@link #isCapturing()} becomes false.
     */
    public boolean captureFinal(MinecraftServer server) {
        if (failureGate.suspended()) return true; // shutdown never retries an OOM/invalid parent
        if (!config.checkpoint.enabled || !config.checkpoint.createOnCleanShutdown) {
            return true;
        }
        if (!previousLookupComplete && previousLookupJob != null && previousLookupJob.isDone()) {
            pollPreviousLookup();
        }
        if (!previousLookupComplete) {
            if (previousLookupJob != null) {
                previousLookupJob.cancel(true);
                previousLookupJob = null;
            }
            previousLookupComplete = true;
        }
        if (!liveScanComplete) {
            if (liveScanJob != null) {
                liveScanJob.cancel(true);
                liveScanJob = null;
            }
            liveScanComplete = true;
        }
        if (compactionJob != null) {
            // The final checkpoint is newer than any compaction result; never let a late
            // compaction adoption replace it as the parent/active checkpoint.
            compactionJob.cancel(true);
            compactionJob = null;
        }
        return beginCapture(server);
    }

    private boolean beginCapture(MinecraftServer server) {
        if (capturing || pendingBarrier != null || activeJob != null) {
            return false;
        }
        // Every cut completes within this call; see WriteHold and MAX_CUT_NANOS.
        WriteHold.hold();
        try {
            return beginCaptureWithWritesHeld(server);
        } finally {
            WriteHold.release();
        }
    }

    private boolean beginCaptureWithWritesHeld(MinecraftServer server) {

        shutdownCancellation = false;
        liveCopyComplete = false;
        liveCopyCheckpointId = null;
        portableGenerationSealed = false;
        lastFinalizationError = null;
        CheckpointTiming timing = new CheckpointTiming();
        if (requestEnqueuedNanos != 0L) {
            timing.record(
                    "wait_for_tick_boundary",
                    Math.max(0L, System.nanoTime() - requestEnqueuedNanos));
        }
        requestEnqueuedNanos = 0L;
        Path world;
        Path temporary = null;
        try {
            activePhase = "PREFLIGHT";
            checkMemoryHeadroom();
            if (Boolean.TRUE.equals(logicalPersistentStateBlocked) || baseRequested)
                logicalDeltaEngine.releaseCandidates();
            world = WorldLocator.resolve(internal.gameDir());
            if (!Files.isDirectory(world, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(world)) {
                throw new IOException("World directory is unavailable: " + world);
            }
            if (savingDisabled(server)) {
                // /save-off makes MinecraftServer#save skip entity and PersistentState writes while
                // AntiCrash's staged chunk save would still write region files. The result would
                // be a checkpoint whose chunks and entities come from different moments, and it
                // would also write into a world an external backup expects to be quiescent.
                if (!saveOffReported) {
                    saveOffReported = true;
                    logger.importantKey("checkpoint.skipped_save_off");
                }
                baseRequested = false;
                resetDeadline();
                returnToRunningAfterRequest();
                return false;
            }
            saveOffReported = false;

            boolean explicitBase = baseRequested;
            if (CheckpointEnginePolicy.logicalDeltaRequested(config)
                    && !explicitBase
                    && (previousWorld == null || previousManifest == null)) {
                // Do not silently turn the first ordinary 30-second checkpoint into a full
                // world freeze. An administrator must explicitly bootstrap a BASE (or provide a
                // READY parent during recovery) so the low-pause contract remains truthful.
                logger.importantKey("checkpoint.logical_base_required");
                baseRequested = false;
                resetDeadline();
                returnToRunningAfterRequest();
                return false;
            }
            baseRequested = false;

            String engineMode =
                    config.checkpoint.engineMode == null
                            ? "LOGICAL_DELTA"
                            : config.checkpoint
                                    .engineMode
                                    .trim()
                                    .toUpperCase(java.util.Locale.ROOT);
            boolean logicalDelta =
                    !explicitBase
                            && CheckpointEnginePolicy.logicalDeltaRequested(config)
                            && previousWorld != null
                            && previousManifest != null;
            boolean unsupportedPersistentState = false;
            // This probe is deliberately outside CHECKPOINT_BARRIER.  POI/data are not safely
            // represented by the generic semantic delta yet, but discovering their presence must
            // not add a filesystem walk to the frozen epoch.  The result is carried into the
            // PendingBarrier and is never rescanned at the cut.
            if (logicalDelta && CheckpointEnginePolicy.hasUnsafeLogicalOptions(config))
                logicalDelta = false;
            if (logicalDelta) {
                long persistentProbeStarted = System.nanoTime();
                try {
                    unsupportedPersistentState =
                            LogicalDeltaEngine.hasUnsupportedPersistentFiles(server);
                    logicalPersistentStateBlocked =
                            unsupportedPersistentState
                                    && !config.checkpoint.allowUnsupportedPersistentState;
                    if (unsupportedPersistentState
                            && !config.checkpoint.allowUnsupportedPersistentState) {
                        logicalDelta = false;
                        reportLogicalFallback(
                                "POI/PersistentState files require a semantic authority");
                    }
                } catch (IOException unsupported) {
                    logicalDelta = false;
                    logger.importantKey("checkpoint.logical_fallback", unsupported.getMessage());
                } finally {
                    timing.record(
                            "delta.persistent_state",
                            Math.max(0L, System.nanoTime() - persistentProbeStarted));
                }
            }

            // The measured barrier begins only after all preflight decisions have completed.
            long barrierStart = System.nanoTime();
            timing.begin("barrier");

            // Both normal and final captures freeze the world and hold the server task queue until
            // the cut is detached from the live files.
            enterCheckpointState(AntiCrashState.CHECKPOINT_BARRIER);
            // Before the capsule, so both describe the world with its pending entity sections
            // loaded; see CutSave.
            timing.begin("entity_flush");
            activePhase = "ENTITY_FLUSH";
            flushEntitiesAtCut(server);
            timing.end("entity_flush");
            timing.begin("runtime_capture");
            activePhase = "RUNTIME_CAPTURE";
            checkMemoryHeadroom();
            ScheduledTickMutationTracer.Capture initialScheduledTrace =
                    ScheduledTickMutationTracer.begin(
                            server.getTicks(), config.checkpoint.mirrorTraceLimit);
            RuntimeCapsule capsule;
            TransientChunkStateMarker.MarkResult transientState;
            try {
                transientState = TransientChunkStateMarker.markForCheckpoint(server);
                logger.normalKey(
                        "checkpoint.transient_state_marked",
                        transientState.inspectedChunks(),
                        transientState.criticalChunkCount(),
                        transientState.chunksMadeDirty(),
                        transientState.movingPistons(),
                        transientState.movingPistonChunks(),
                        transientState.scheduledTicks(),
                        transientState.scheduledTickChunks(),
                        transientState.transientPhysicsEntities(),
                        transientState.orderedScheduledTicks().size());
                capsule = runtime.capture(server, transientState, timing);
            } finally {
                ScheduledTickMutationTracer.end(initialScheduledTrace);
            }
            if (initialScheduledTrace.changed()) {
                throw new IllegalStateException(
                        "Scheduled-tick state changed during runtime capture: "
                                + initialScheduledTrace.diagnostic());
            }
            long playerCaptureStarted = System.nanoTime();
            List<PlayerSnapshotData> playerSnapshots = List.copyOf(players.capture(server));
            timing.record(
                    "runtime.players", Math.max(0L, System.nanoTime() - playerCaptureStarted));
            if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)
                    && config.recovery.strict.players.requireCheckpointPlayers) {
                RuntimeCapsuleManager.populateCheckpointPlayerIdentities(
                        capsule, players.captureCheckpointPlayers(server));
            }
            long fakePlayerCaptureStarted = System.nanoTime();
            List<FakePlayerSnapshot> fakeSnapshots =
                    List.copyOf(CarpetIntegration.captureForCheckpoint(server, config, logger));
            timing.record(
                    "runtime.fakeplayers",
                    Math.max(0L, System.nanoTime() - fakePlayerCaptureStarted));
            RuntimeCapsuleManager.populateFakePlayerFingerprint(capsule, fakeSnapshots);
            if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
                List<String> strictBlockers =
                        RuntimeCapsuleManager.strictCaptureBlockers(
                                capsule, config.recovery.strict);
                if (!strictBlockers.isEmpty()) {
                    throw new IllegalStateException(
                            "STRICT checkpoint capture is incomplete: "
                                    + String.join(", ", strictBlockers));
                }
            }
            timing.end("runtime_capture");
            timing.begin("temporary_generation");
            temporary = store.createTempDirectory();
            activeTemporary = temporary;
            timing.end("temporary_generation");
            if (!logicalDelta && logicalEngineUsable()) {
                // A complete BASE/fallback view becomes the authority for the next logical
                // epoch. Only the small generation ledger is retained; no live NBT is kept here.
                logicalDeltaEngine.establishGenerationBaseline(server);
            }

            // A Vanilla flush joins every accessible ChunkHolder and serializes every dirty chunk
            // in one server tick. With a 1 GiB heap this can retain thousands of NBT trees at once
            // and trip the watchdog before an OutOfMemoryError is even thrown. Keep the exact same
            // save boundary, but serialize dirty chunks in tiny IO-drained batches over multiple
            // frozen server ticks. The final Vanilla flush then has no large backlog to allocate.
            boolean portableFallback =
                    CheckpointEnginePolicy.usesPortableGeneration(config)
                            && "PORTABLE_GENERATION"
                                    .equalsIgnoreCase(config.checkpoint.engineFallback);
            // Keep the fallback backend selected even while the first attempt is logical.  A
            // logical cut may be rejected on the next frozen tick (candidate eviction, an
            // unregistered scheduler, or a mutation observed during the cut).  In that case the
            // same PendingBarrier must continue into the bounded Portable Generation path rather
            // than silently falling through to the legacy worker that would read the live World.
            boolean portableGeneration =
                    "PORTABLE_GENERATION".equals(engineMode)
                            || (portableFallback
                                    && ("LOGICAL_DELTA".equals(engineMode) || explicitBase));
            String previousId = previousManifest == null ? null : previousManifest.checkpointId;
            GenerationPlan generationPlan =
                    logicalDelta
                            ? new GenerationPlan(
                                    previousWorld,
                                    previousManifest.checkpointId,
                                    Math.max(0, previousManifest.generationChainDepth),
                                    SnapshotManager.index(previousManifest),
                                    false,
                                    previousManifest.checkpointId)
                            : portableGeneration
                                    ? generationPlan(explicitBase)
                                    : new GenerationPlan(
                                            previousWorld,
                                            previousId,
                                            previousManifest == null
                                                    ? 0
                                                    : Math.max(
                                                            0,
                                                            previousManifest.generationChainDepth),
                                            SnapshotManager.index(previousManifest),
                                            false,
                                            previousId);
            String timelineParent = internal.recoveryState().activeCheckpointId;
            if (generationPlan.sourceCheckpointId() != null) {
                pinnedParentId = generationPlan.sourceCheckpointId();
                store.pin(pinnedParentId);
            }
            pendingBarrier =
                    new PendingBarrier(
                            timelineParent,
                            server,
                            world,
                            capsule,
                            playerSnapshots,
                            fakeSnapshots,
                            barrierStart,
                            server.getTicks(),
                            server.getOverworld() == null ? 0L : server.getOverworld().getTime(),
                            captureDimensions(server),
                            FabricLoader.getInstance()
                                    .getModContainer("fabricloader")
                                    .map(container ->
                                            container
                                                    .getMetadata()
                                                    .getVersion()
                                                    .getFriendlyString())
                                    .orElse("unknown"),
                            FabricLoader.getInstance()
                                    .getModContainer("fabric-api")
                                    .map(container ->
                                            container
                                                    .getMetadata()
                                                    .getVersion()
                                                    .getFriendlyString())
                                    .orElse(null),
                            CarpetIntegration.version(),
                            transientState,
                            generationPlan.previousWorld(),
                            generationPlan.parentCheckpointId(),
                            generationPlan.parentChainDepth(),
                            generationPlan.previousFiles(),
                            liveHashes,
                            captureBarrierWorlds(server),
                            temporary,
                            timing,
                            logicalDelta,
                            portableGeneration,
                            unsupportedPersistentState,
                            generationPlan.rebase(),
                            generationPlan.sourceCheckpointId());
            capturing = true;
            lastJobSuccessful = false;
            // Take the rest of the cut right away: returning here would freeze the world for at
            // least one more paced tick. A failed cut has already cleared `capturing`.
            advanceBarrier(server);
            return capturing;
        } catch (OutOfMemoryError exception) {
            failBarrier(exception);
            return false;
        } catch (Exception exception) {
            failBarrier(exception);
            return false;
        }
    }

    private List<BarrierWorld> captureBarrierWorlds(MinecraftServer server) {
        List<BarrierWorld> result = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            ThreadedAnvilChunkStorageAccessorMixin storage =
                    (ThreadedAnvilChunkStorageAccessorMixin)
                            world.getChunkManager().threadedAnvilChunkStorage;
            List<ChunkHolder> holders = new ArrayList<>();
            for (ChunkHolder holder : storage.anticrash$getCurrentChunkHolders().values()) {
                if (holder != null && holder.isAccessible()) holders.add(holder);
            }
            // A chunk whose level dropped leaves the live map before its unload task saves it.
            // Vanilla's flush ran those unloads; without it their last changes would be missing.
            for (ChunkHolder holder : storage.anticrash$getChunksToUnload().values()) {
                if (holder != null && holder.isAccessible()) holders.add(holder);
            }
            result.add(new BarrierWorld(world, List.copyOf(holders)));
        }
        return List.copyOf(result);
    }

    /**
     * Takes the cut. A portable cut always completes within the calling tick; see {@link
     * #MAX_CUT_NANOS}. Only the legacy engines stay frozen across ticks.
     */
    private void advanceBarrier(MinecraftServer server) {
        PendingBarrier barrier = pendingBarrier;
        if (barrier == null) return;
        try {
            if (System.nanoTime() - barrier.barrierStartNanos > MAX_BARRIER_NANOS) {
                throw new IOException(
                        "Checkpoint barrier did not finish within "
                                + MAX_BARRIER_NANOS / 1_000_000_000L
                                + " seconds");
            }
            if (barrier.logicalDelta) {
                advanceLogicalDeltaBarrier(barrier);
                // A rejected logical cut falls back to the portable path below in the same call;
                // it needs no extra tick, and every frozen tick is felt by players.
                if (pendingBarrier != barrier || barrier.logicalDelta) return;
            }
            long cutDeadline = barrier.barrierStartNanos + MAX_CUT_NANOS;
            if (!saveBarrierChunks(
                    barrier, barrier.portableGeneration ? cutDeadline : Long.MAX_VALUE)) {
                throw new IOException(
                        "Checkpoint cut did not finish within "
                                + MAX_CUT_NANOS / 1_000_000_000L
                                + " seconds; abandoned without a checkpoint");
            }
            if (barrier.portableGeneration) {
                completePortableCut(server, barrier);
            } else {
                completeLegacyCut(server, barrier);
            }
        } catch (OutOfMemoryError exception) {
            failBarrier(exception);
        } catch (CompletionException exception) {
            failBarrier(exception.getCause() == null ? exception : exception.getCause());
        } catch (Exception exception) {
            failBarrier(exception);
        }
    }

    /**
     * Estimated heap one queued chunk save holds until it is written. Deliberately high: a chunk
     * full of block entities is far larger than an average one.
     */
    private static final long QUEUED_CHUNK_BYTES = 512L * 1024L;

    /**
     * How many chunk saves may sit in the IO queue before the cut waits for the disk.
     *
     * <p>A portable cut takes queued writes from the queue (see {@link IoSeal}) and never needs
     * them on disk. Waiting for them is only a memory safeguard: each queued save holds its NBT.
     * The safeguard used to wait after every 16 chunks whatever the heap size, and with synchronous
     * chunk writes each such wait is 16 fsyncs. A server saving a few hundred chunks per cut (chunk
     * generation, a schematic being pasted) froze for seconds at every checkpoint. The bound now
     * follows the heap (a quarter of it at a deliberately high estimate per chunk), and the queue
     * is written out earlier whenever the heap is actually tight; see {@link #heapTight}.
     */
    static int queuedChunkBudget(long maxHeapBytes, int configuredBatch) {
        // A batch set below the old fixed limit is an explicit request to hold less; honour it.
        if (configuredBatch < 16) return Math.max(1, configuredBatch);
        return (int) Math.max(16L, Math.min(maxHeapBytes / 4L / QUEUED_CHUNK_BYTES, 65_536L));
    }

    /**
     * True when little heap is left, counting uncollected garbage as used. Then the queued saves
     * are written out before more are added, which is what a small heap needs and what a large one
     * practically never reaches.
     */
    private static boolean heapTight() {
        Runtime jvm = Runtime.getRuntime();
        long available = jvm.maxMemory() - (jvm.totalMemory() - jvm.freeMemory());
        return available < Math.min(256L * 1024 * 1024, jvm.maxMemory() / 4);
    }

    /**
     * Saves every dirty chunk of the cut into the chunk IO queue.
     *
     * <p>A Vanilla flush joins every ChunkHolder and serializes every dirty chunk at once. With a 1
     * GiB heap that retained thousands of NBT trees and tripped the watchdog, so the number of
     * saves waiting in the queue is bounded (see {@link #queuedChunkBudget}); the legacy engines,
     * which flush everything anyway, keep their small fixed batches.
     *
     * @return false when the deadline passed before every dirty chunk was saved
     */
    private boolean saveBarrierChunks(PendingBarrier barrier, long deadline) throws IOException {
        activePhase = "CHUNK_SERIALIZATION";
        int batchLimit =
                barrier.portableGeneration
                        ? queuedChunkBudget(
                                Runtime.getRuntime().maxMemory(), config.checkpoint.saveBatchChunks)
                        : Math.max(1, Math.min(config.checkpoint.saveBatchChunks, 16));
        long started = System.nanoTime();
        long drainNanos = 0L;
        try {
            while (barrier.worldCursor < barrier.worlds.size()) {
                BarrierWorld barrierWorld = barrier.worlds.get(barrier.worldCursor);
                ThreadedAnvilChunkStorageAccessorMixin storage =
                        (ThreadedAnvilChunkStorageAccessorMixin)
                                barrierWorld.world.getChunkManager().threadedAnvilChunkStorage;
                while (barrier.holderCursor < barrierWorld.holders.size()) {
                    ChunkHolder holder = barrierWorld.holders.get(barrier.holderCursor++);
                    Chunk chunk = savableChunk(holder);
                    if (chunk == null || !chunk.needsSaving()) continue;
                    checkMemoryHeadroom();
                    if (storage.anticrash$saveChunk(chunk)) {
                        barrier.savedChunks++;
                        barrier.savedSinceDrain++;
                    }
                    if (barrier.savedSinceDrain >= batchLimit
                            || (barrier.savedSinceDrain >= 16 && heapTight())) {
                        // This drain waits for writes to finish, so they must be allowed to start.
                        long drainStarted = System.nanoTime();
                        WriteHold.release();
                        joinAll(beginChunkIoDrain(barrier.worlds));
                        drainNanos += System.nanoTime() - drainStarted;
                        barrier.savedSinceDrain = 0;
                        barrier.drainedBatches++;
                    }
                    if (System.nanoTime() - deadline >= 0) return false;
                }
                barrier.worldCursor++;
                barrier.holderCursor = 0;
            }
            return true;
        } finally {
            barrier.timing.record(
                    "chunk_save", Math.max(0L, System.nanoTime() - started - drainNanos));
            barrier.timing.record("chunk_drain", drainNanos);
            barrier.timing.count("savedChunks", barrier.savedChunks);
            barrier.timing.count("ioWaits", barrier.drainedBatches);
        }
    }

    /**
     * The chunk whose state at the cut must reach the checkpoint, without waiting for the holder.
     *
     * <p>A loaded chunk is taken from the highest completed status, not from the saving future:
     * that future stays pending while any status or ticket change is scheduled, and a holder whose
     * level dropped (a chunk being unloaded) has already had its accessible future replaced, yet
     * its WorldChunk can still hold changes nobody saved. A WrapperProtoChunk is unwrapped because
     * it always reports "nothing to save" even when the WorldChunk inside is dirty.
     *
     * <p>Waiting instead, as the old barrier did for up to 26 frozen ticks, lets chunk loads finish
     * during the cut and add block-entity tickers and entities to the world after the Runtime
     * Capsule was taken. A chunk that is still being generated is part of no one's world yet: it
     * holds world generation output only, never a player's block, and its saved form on disk is
     * what the cut contains, exactly as Vanilla's own autosave treats it.
     */
    private static Chunk savableChunk(ChunkHolder holder) {
        Chunk current = holder.getCurrentChunk();
        if (current instanceof WrapperProtoChunk readOnly) current = readOnly.getWrappedChunk();
        if (current instanceof WorldChunk) return current;
        CompletableFuture<Chunk> saving = holder.getSavingFuture();
        // No status work is pending when the future is done, so no generation thread touches
        // the proto chunk. getNow never blocks here; a failed future fails the cut closed.
        if (!saving.isDone()) return null;
        Chunk saved = saving.getNow(null);
        return saved instanceof WrapperProtoChunk readOnly ? readOnly.getWrappedChunk() : saved;
    }

    /**
     * Finishes a Portable Generation cut: Vanilla's save without its flush, a seal of every storage
     * IO queue, then {@link DetachedGeneration#capture}. The worker does the rest, including
     * writing the writes that were still queued into the checkpoint's copies.
     */
    private void completePortableCut(MinecraftServer server, PendingBarrier barrier)
            throws Exception {
        activePhase = "VANILLA_SAVE_POI_PERSISTENT";
        barrier.timing.begin("vanilla_save");
        CutSave.begin();
        try {
            // level.dat, PersistentState and every mod hook on MinecraftServer#save. Entities were
            // flushed before the capsule (see CutSave). The flush variant would also fsync every
            // region file and wait for every chunk holder, which dominated the old freeze.
            if (!server.save(true, false, false)) {
                throw new IOException("Minecraft refused the world save");
            }
        } finally {
            CutSave.end();
        }
        for (BarrierWorld barrierWorld : barrier.worlds) {
            savePointsOfInterest(barrierWorld.world);
        }
        barrier.timing.end("vanilla_save");
        long minimumFreeBytes = Math.max(0L, config.checkpoint.minimumFreeMb) * 1024L * 1024L;
        if (minimumFreeBytes > 0L
                && Files.getFileStore(barrier.temporary).getUsableSpace() < minimumFreeBytes) {
            throw new IOException("Insufficient checkpoint storage free space");
        }
        logger.verboseKey(
                "checkpoint.staged_save_complete", barrier.savedChunks, barrier.drainedBatches);

        // Every chunk, entity and POI write of the cut is queued. Park the IO workers instead of
        // waiting for each synchronous write: the files on disk plus the queued writes are the cut.
        barrier.timing.begin("io_seal");
        activePhase = "IO_SEAL";
        IoSeal ioSeal = IoSeal.seal(storageWorkers(barrier.worlds), IO_SEAL_TIMEOUT_NANOS);
        barrier.timing.end("io_seal");
        DetachedGeneration generation = null;
        try {
            // No write can reach a file while the workers are parked. The tracker's next interval
            // starts here; the files written in the interval that ends here are what changed since
            // the tracker's base checkpoint. Queued writes land after the seal, in the next
            // interval, and are detached from this cut through the seal instead.
            Set<java.nio.file.Path> written = LiveFileTracker.global().swap();
            boolean trackedParent =
                    written != null
                            && barrier.sourceCheckpointId != null
                            && barrier.sourceCheckpointId.equals(trackerBaseCheckpointId);
            barrier.trackerWrites = written;
            barrier.trackerArmedAtCut = true;

            barrier.timing.begin("logical_cut");
            barrier.timing.begin("generation_capture");
            activePhase = "FILE_DETACHMENT";
            generation =
                    DetachedGeneration.capture(
                            barrier.world,
                            barrier.temporary.resolve("world"),
                            barrier.previousFiles,
                            barrier.liveHashes,
                            trackedParent ? written : null,
                            ioSeal.writes());
            barrier.timing.end("generation_capture");
            // Vanilla playerdata/stats/advancements are an opt-in external snapshot. They are also
            // cut here; the worker must never read their mutable live copies.
            barrier.timing.begin("vanilla_file_capture");
            VanillaFileSnapshot.capture(barrier.world, barrier.temporary, config);
            barrier.timing.end("vanilla_file_capture");
            if (logicalEngineUsable()) {
                // Seed the next logical epoch while the exact cut is still frozen. Doing this after
                // the worker finishes would include post-checkpoint mutations in the baseline.
                logicalDeltaEngine.establishGenerationBaseline(barrier.server);
            }
            barrier.timing.end("logical_cut");
            if (!ioSeal.intact()) {
                throw new IOException(
                        "A storage IO worker resumed before the checkpoint cut finished");
            }
        } catch (Exception exception) {
            if (generation != null) generation.discard();
            throw exception;
        } finally {
            ioSeal.close();
        }
        barrier.timing.count("queuedWrites", generation.pendingWriteCount());
        String sealedSlowest = ioSeal.slowestQueue();
        long barrierDurationMs;
        barrierDurationMs = (System.nanoTime() - barrier.barrierStartNanos) / 1_000_000L;
        barrier.timing.end("barrier");
        barrier.timing.captureAfterJvm();
        lastTiming = barrier.timing;
        logger.verboseKey(
                "checkpoint.cut_detached",
                generation.tracked() ? "TRACKED" : "METADATA",
                generation.statedFiles(),
                generation.detachedFiles(),
                generation.detachedBytes(),
                generation.pendingWriteCount(),
                sealedSlowest);
        CaptureContext context =
                barrier.toCaptureContext(barrierDurationMs, null, null, generation);
        enterCheckpointState(AntiCrashState.CHECKPOINT_SEALED);
        activeTrackerWrites = barrier.trackerWrites;
        activeTrackerArmedAtCut = barrier.trackerArmedAtCut;
        barrier.trackerWrites = null;
        activeJob = ioExecutor.submit(() -> runCapture(context));
        pendingBarrier = null;
        portableGenerationSealed = true;
        liveCopyCheckpointId = checkpointId(barrier.temporary);
        liveCopyComplete = true;
        enterCheckpointState(AntiCrashState.CHECKPOINT_FINALIZING);
    }

    /** The legacy engines keep the full flush and stay frozen until their worker completes. */
    private void completeLegacyCut(MinecraftServer server, PendingBarrier barrier)
            throws Exception {
        // The flush below waits for every write.
        WriteHold.release();
        logger.verboseKey(
                "checkpoint.staged_save_complete", barrier.savedChunks, barrier.drainedBatches);
        if (!server.save(true, true, false)) {
            throw new IOException("Minecraft refused the world save");
        }
        long minimumFreeBytes = Math.max(0L, config.checkpoint.minimumFreeMb) * 1024L * 1024L;
        if (minimumFreeBytes > 0L
                && Files.getFileStore(barrier.temporary).getUsableSpace() < minimumFreeBytes) {
            throw new IOException("Insufficient checkpoint storage free space");
        }
        long barrierDurationMs = (System.nanoTime() - barrier.barrierStartNanos) / 1_000_000L;
        barrier.timing.end("barrier");
        barrier.timing.begin("logical_cut");
        barrier.timing.end("logical_cut");
        barrier.timing.captureAfterJvm();
        lastTiming = barrier.timing;
        CaptureContext context = barrier.toCaptureContext(barrierDurationMs, null);
        enterCheckpointState(AntiCrashState.CHECKPOINTING);
        // Legacy capture keeps the server frozen until its worker completes. Establish the
        // mutation baseline at that frozen boundary, not after the worker resumes the world, so
        // post-cut mutations remain visible to the next logical delta.
        if (logicalEngineUsable()) logicalDeltaEngine.establishGenerationBaseline(barrier.server);
        pendingBarrier = null;
        activeJob = ioExecutor.submit(() -> runCapture(context));
    }

    /**
     * Saves every entity section with Vanilla's flush, including sections still loading, and waits
     * for the entity IO queue without fsyncing each entity region file.
     */
    private static void flushEntitiesAtCut(MinecraftServer server) {
        CutSave.begin();
        try {
            for (ServerWorld world : server.getWorlds()) {
                if (world.savingDisabled) continue;
                ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                        .anticrash$getEntityManager()
                        .flush();
            }
        } finally {
            CutSave.end();
        }
    }

    /**
     * Queues every POI section not yet written.
     *
     * <p>Vanilla's flush reaches POI only through the chunks it saves, and never waits for the POI
     * queue. The POI queue is sealed with the others (see {@link IoSeal}).
     */
    private static void savePointsOfInterest(ServerWorld world) {
        SerializingRegionBasedStorageAccessorMixin poi =
                (SerializingRegionBasedStorageAccessorMixin) world.getPointOfInterestStorage();
        var unsaved = poi.anticrash$getUnsavedElements();
        // save() removes every section of the chunk from the set; the bound only protects the
        // frozen server thread from a storage that failed to do so.
        int remaining = unsaved.size() + 1;
        int queued = 0;
        while (!unsaved.isEmpty() && remaining-- > 0) {
            poi.anticrash$save(ChunkSectionPos.from(unsaved.firstLong()).toChunkPos());
            // POI data is a few kilobytes per chunk; the queue is sealed with the others and is
            // waited for only when an implausible number of sections is pending.
            if (++queued >= 4096 || (queued >= 16 && heapTight())) {
                WriteHold.release();
                ((com.atsukimc.anticrash.mixin.ChunkPosKeyedStorageAccessor)
                                poi.anticrash$getStorageAccess())
                        .anticrash$getWorker()
                        .completeAll(false)
                        .join();
                queued = 0;
            }
        }
        if (!unsaved.isEmpty()) {
            throw new IllegalStateException(
                    "POI sections could not be saved in " + world.getRegistryKey().getValue());
        }
    }

    /**
     * The chunk, entity and POI IO workers of every world, i.e. everything that writes region
     * files.
     */
    private static List<StorageIoWorker> storageWorkers(List<BarrierWorld> worlds) {
        List<StorageIoWorker> workers = new ArrayList<>(worlds.size() * 3);
        for (BarrierWorld barrierWorld : worlds) {
            ServerWorld world = barrierWorld.world;
            if (!(world.getChunkManager().getChunkIoWorker() instanceof StorageIoWorker chunks)) {
                throw new IllegalStateException(
                        "Unsupported chunk IO worker for " + world.getRegistryKey().getValue());
            }
            workers.add(chunks);
            Object entities =
                    ((com.atsukimc.anticrash.mixin.ServerEntityManagerAccessorMixin)
                                    ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                                            .anticrash$getEntityManager())
                            .anticrash$getDataAccess();
            if (!(entities
                    instanceof net.minecraft.world.storage.EntityChunkDataAccess entityAccess)) {
                throw new IllegalStateException(
                        "Unsupported entity storage for " + world.getRegistryKey().getValue());
            }
            workers.add(
                    ((com.atsukimc.anticrash.mixin.ChunkPosKeyedStorageAccessor)
                                    ((com.atsukimc.anticrash.mixin
                                                            .EntityChunkDataAccessAccessorMixin)
                                                    entityAccess)
                                            .anticrash$getStorage())
                            .anticrash$getWorker());
            workers.add(
                    ((com.atsukimc.anticrash.mixin.ChunkPosKeyedStorageAccessor)
                                    ((SerializingRegionBasedStorageAccessorMixin)
                                                    world.getPointOfInterestStorage())
                                            .anticrash$getStorageAccess())
                            .anticrash$getWorker());
        }
        return workers;
    }

    private static void joinAll(List<CompletableFuture<Void>> futures) {
        // Storage IO workers run on their own threads and never need the server thread, which is
        // why Vanilla itself joins them from a flush. The freeze is this wait and nothing else.
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void checkMemoryHeadroom() throws CheckpointFailureGate.MemoryBudgetException {
        Runtime jvm = Runtime.getRuntime();
        long required = Math.min(128L * 1024 * 1024, jvm.maxMemory() / 8);
        long available = jvm.maxMemory() - (jvm.totalMemory() - jvm.freeMemory());
        if (available < required) {
            // Only pressure triggers one collection; never increase heap or discard correctness.
            logicalDeltaEngine.releaseCandidates();
            System.gc();
            available = jvm.maxMemory() - (jvm.totalMemory() - jvm.freeMemory());
            if (available < required)
                throw new CheckpointFailureGate.MemoryBudgetException(available, required);
        }
    }

    private void releaseParentPin() {
        if (pinnedParentId != null) {
            store.unpin(pinnedParentId);
            pinnedParentId = null;
        }
    }

    public String engineIncidentStatus(String language) {
        boolean ja = "ja".equalsIgnoreCase(language);
        Runtime jvm = Runtime.getRuntime();
        String status =
                failureGate.suspended()
                        ? (failureGate.category() == CheckpointFailureGate.Category.PARENT_INVALID
                                ? "SUSPENDED_NEEDS_REBASE"
                                : "SUSPENDED")
                        : "ACTIVE";
        return (ja ? "Checkpointエンジン: " : "Checkpoint engine: ")
                + status
                + "; reason="
                + failureGate.category()
                + "; consecutiveFailures="
                + failureGate.consecutiveFailures()
                + "; lastValid="
                + (store.isQuarantined(lastCheckpointId) ? "UNAVAILABLE" : lastCheckpointId)
                + "; parent="
                + (previousManifest == null ? "UNAVAILABLE" : previousManifest.checkpointId)
                + "; phase="
                + activePhase
                + "; lowPause="
                + (Boolean.TRUE.equals(logicalPersistentStateBlocked)
                        ? "UNAVAILABLE_POI_PERSISTENT_STATE"
                        : "CONDITIONAL")
                + "; finalizer="
                + (activeJob != null)
                + "; headroomBytes="
                + (jvm.maxMemory() - (jvm.totalMemory() - jvm.freeMemory()))
                + "; reservedHeadroomBytes="
                + Math.min(128L * 1024 * 1024, jvm.maxMemory() / 8)
                + "; effectiveSaveBatch="
                + queuedChunkBudget(jvm.maxMemory(), config.checkpoint.saveBatchChunks)
                + (failureGate.suspended()
                        ? (ja
                                ? "; 確認後 /ac checkpoint base で独立BASEを作成できます"
                                : "; review, then /ac checkpoint base for independent rebase")
                        : "");
    }

    /**
     * Seals only immutable semantic bytes; no Vanilla lifecycle/task pump or global save occurs.
     */
    private void advanceLogicalDeltaBarrier(PendingBarrier barrier) {
        WriteHold.release();
        ScheduledTickMutationTracer.Capture trace =
                ScheduledTickMutationTracer.begin(
                        barrier.serverTick, config.checkpoint.mirrorTraceLimit);
        try {
            barrier.timing.begin("logical_cut");
            LogicalDeltaEngine.CaptureResult captured =
                    logicalDeltaEngine.captureAtCut(
                            barrier.server,
                            barrier.serverTick,
                            barrier.parentCheckpointId,
                            config.checkpoint.allowUnsupportedPersistentState,
                            barrier.unsupportedPersistentState,
                            barrier.timing);
            if (!captured.exact()) {
                com.atsukimc.anticrash.runtime.ScheduledTickMutationTracer.end(trace);
                if (captured.reason() != null && captured.reason().contains("uncertain")) {
                    checkpointMirror.invalidate(captured.reason());
                }
                barrier.timing.end("logical_cut");
                logger.importantKey("checkpoint.logical_fallback", captured.reason());
                barrier.logicalDelta = false;
                return;
            }
            if (trace.changed()) {
                // The scheduled-tick mixin is audit-only.  If Vanilla (or another mod) mutates
                // the queue while the semantic cut is being assembled, the cut is no longer an
                // exact epoch.  Keep the server frozen and let the already-captured barrier fall
                // back to the portable save path, which advanceBarrier runs in the same call.
                barrier.timing.end("logical_cut");
                checkpointMirror.invalidate("scheduled tick mutation during logical cut");
                logger.importantKey(
                        "checkpoint.logical_fallback",
                        "scheduled_tick_mutation_during_logical_cut: " + trace.diagnostic());
                barrier.logicalDelta = false;
                return;
            }
            LogicalDelta delta = captured.delta();
            barrier.logicalDeltaValue = delta;
            barrier.timing.end("logical_cut");
            barrier.timing.end("barrier");
            barrier.timing.captureAfterJvm();
            lastTiming = barrier.timing;
            long minimumFreeBytes = Math.max(0L, config.checkpoint.minimumFreeMb) * 1024L * 1024L;
            if (minimumFreeBytes > 0L
                    && Files.getFileStore(barrier.temporary).getUsableSpace() < minimumFreeBytes) {
                throw new IOException("Insufficient checkpoint storage free space");
            }
            CaptureContext context =
                    barrier.toCaptureContext(
                            Math.max(
                                    0L,
                                    (System.nanoTime() - barrier.barrierStartNanos) / 1_000_000L),
                            null,
                            delta);
            enterCheckpointState(AntiCrashState.CHECKPOINT_SEALED);
            activeJob = ioExecutor.submit(() -> runCapture(context));
            pendingBarrier = null;
            portableGenerationSealed = true;
            liveCopyCheckpointId = checkpointId(barrier.temporary);
            liveCopyComplete = true;
            enterCheckpointState(AntiCrashState.CHECKPOINT_FINALIZING);
        } catch (Exception exception) {
            failBarrier(exception);
        } finally {
            ScheduledTickMutationTracer.end(trace);
        }
    }

    private List<CompletableFuture<Void>> beginChunkIoDrain(List<BarrierWorld> worlds) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (BarrierWorld barrierWorld : worlds) {
            Object worker = barrierWorld.world.getChunkManager().getChunkIoWorker();
            if (!(worker instanceof StorageIoWorker storageIoWorker)) {
                throw new IllegalStateException(
                        "Unsupported chunk IO worker for "
                                + barrierWorld.world.getRegistryKey().getValue());
            }
            futures.add(storageIoWorker.completeAll(false));
        }
        return List.copyOf(futures);
    }

    private void failBarrier(Throwable failure) {
        failureGate.failed(failure, System.nanoTime());
        logicalDeltaEngine.resetCommittedBaseline();
        com.atsukimc.anticrash.snapshot.SealedNbt.clear();
        releaseParentPin();
        String message =
                failure == null || failure.getMessage() == null
                        ? String.valueOf(failure)
                        : failure.getMessage();
        logger.importantKey("checkpoint.barrier_failed", message);
        lastFinalizationError = activePhase + ": " + message;
        PendingBarrier barrier = pendingBarrier;
        pendingBarrier = null;
        if (barrier != null) {
            // The interval that ended at this failed cut still belongs to the next capture.
            LiveFileTracker.global().restore(barrier.trackerWrites);
        }
        if (activeJob != null) activeJob.cancel(true);
        Path temporary =
                activeTemporary != null
                        ? activeTemporary
                        : barrier == null ? null : barrier.temporary;
        if (temporary != null) {
            try {
                SnapshotManager.deleteTree(temporary);
            } catch (IOException cleanup) {
                logger.verboseKey("checkpoint.temp_cleanup_failed", cleanup.getMessage());
            }
        }
        capturing = false;
        liveCopyComplete = false;
        portableGenerationSealed = false;
        activeTemporary = null;
        activeJob = null;
        activeTrackerWrites = null;
        activeTrackerArmedAtCut = false;
        resetDeadline();
        if (!states.isRecovery()) returnToRunning();
    }

    private List<String> captureDimensions(MinecraftServer server) {
        return java.util.stream.StreamSupport.stream(server.getWorlds().spliterator(), false)
                .map(world -> world.getRegistryKey().getValue().toString())
                .toList();
    }

    private CaptureResult runCapture(CaptureContext context) {
        activePhase = "FINALIZER";
        Path temporary = context.temporary;
        try {
            SnapshotResult result;
            long snapshotMs;
            GenerationSeal seal = context.generationSeal;
            if (context.detachedGeneration != null) {
                // The cut already copied every changed file into the generation; only the
                // immutable parent checkpoint is read from here on, never the live world.
                long snapshotStart = System.nanoTime();
                long rehashedBefore =
                        com.atsukimc.anticrash.snapshot.JavaIncrementalBackend
                                .CONTENT_AUTHENTICATIONS
                                .get();
                context.timing.begin("finalize.generation");
                // Hashing and linking run beside the live server, so they stay on a couple of
                // threads unless the operator configured more.
                result =
                        context.detachedGeneration.materialize(
                                context.previousWorld,
                                context.previousFiles,
                                temporary.resolve("world"),
                                context.copyInherited,
                                PortableGenerationBackend.NAME,
                                config.snapshot.ioThreads > 0 ? config.snapshot.ioThreads : 2);
                seal =
                        new GenerationSeal(
                                result,
                                context.parentCheckpointId,
                                context.logicalCutServerTick,
                                context.logicalCutMonotonicNanos);
                if (context.parentCheckpointId != null) seal.addTombstones(context.previousFiles);
                seal.persist(temporary);
                context.timing.end("finalize.generation");
                context.timing.count(
                        "parentFilesRehashed",
                        com.atsukimc.anticrash.snapshot.JavaIncrementalBackend
                                        .CONTENT_AUTHENTICATIONS
                                        .get()
                                - rehashedBefore);
                snapshotMs = (System.nanoTime() - snapshotStart) / 1_000_000L;
            } else if (context.logicalDelta != null) {
                long snapshotStart = System.nanoTime();
                context.timing.begin("finalize.file_write");
                result =
                        LogicalDeltaMaterializer.materialize(
                                context.previousWorld,
                                temporary.resolve("world"),
                                context.previousFiles,
                                context.logicalDelta);
                // The semantic delta is immutable by this point. Serialize it on the worker,
                // never while the server thread owns the micro-cut.
                AtomicFiles.writeJson(
                        temporary.resolve("logical-delta.json"), context.logicalDelta);
                context.timing.end("finalize.file_write");
                snapshotMs = (System.nanoTime() - snapshotStart) / 1_000_000L;
            } else if (context.generationSeal != null) {
                // Portable generation already detached every file from the mutable world while
                // the barrier was held.  The worker is intentionally forbidden from calling
                // snapshots.snapshot(context.world, ...): doing so would mix epoch N with N+1.
                result = context.generationSeal.snapshotResult();
                if (result == null) throw new IOException("Generation seal has no snapshot result");
                snapshotMs = context.timing.durationsMs().getOrDefault("generation_capture", 0L);
            } else {
                // Explicit legacy accelerators remain a safe, fully frozen fallback.  Their old
                // semantics are intentionally retained rather than pretending they can resume
                // before a filesystem-level snapshot has completed.
                long snapshotStart = System.nanoTime();
                result =
                        snapshots.snapshot(
                                context.world,
                                temporary.resolve("world"),
                                context.previousWorld,
                                context.previousFiles,
                                context.liveHashes);
                VanillaFileSnapshot.capture(context.world, temporary, config);
                snapshotMs = (System.nanoTime() - snapshotStart) / 1_000_000L;
            }

            ensureNotCancelled();
            if (seal == null) {
                // Legacy accelerators keep the world frozen until this point-in-time copy has
                // completed.  Portable generation sets the same flag at the barrier hand-off.
                liveCopyCheckpointId = checkpointId(temporary);
                liveCopyComplete = true;
            }
            context.timing.begin("payload_serialization");
            context.timing.begin("finalize.serialize");
            AtomicFiles.writeJson(temporary.resolve("runtime-capsule.json"), context.capsule);
            AtomicFiles.writeJson(temporary.resolve("players.json"), context.playerSnapshots);
            AtomicFiles.writeJson(temporary.resolve("fakeplayers.json"), context.fakeSnapshots);
            context.timing.end("finalize.serialize");
            context.timing.end("payload_serialization");
            long runtimeCapsuleBytes = Files.size(temporary.resolve("runtime-capsule.json"));

            context.timing.begin("finalize.manifest");
            CheckpointManifest manifest = new CheckpointManifest();
            manifest.checkpointId = temporary.getFileName().toString().replaceFirst("\\.tmp$", "");
            manifest.parentCheckpointId = context.parentCheckpointId;
            manifest.timelineParentCheckpointId = context.timelineParentCheckpointId;
            manifest.utcTimestamp = Instant.now().toString();
            manifest.localTimestamp = ZonedDateTime.now(ZoneId.systemDefault()).toString();
            manifest.monotonicNanos = System.nanoTime();
            manifest.serverTick = context.serverTick;
            manifest.worldGameTime = context.worldGameTime;
            manifest.snapshotBackend = result.backend;
            manifest.checkpointEngine =
                    context.logicalDelta != null
                            ? "LOGICAL_DELTA"
                            : seal != null ? "PORTABLE_GENERATION" : "LEGACY_SAFE";
            manifest.snapshotDurationMs = snapshotMs;
            manifest.barrierDurationMs = context.barrierDurationMs;
            manifest.totalBytes = result.totalBytes;
            manifest.changedFiles = result.changedFiles;
            manifest.reusedFiles = result.reusedFiles;
            manifest.playerSnapshotCount = context.playerSnapshots.size();
            manifest.fakePlayerCount = context.fakeSnapshots.size();
            manifest.blockEventCount = context.capsule.blockEventCount();
            manifest.loadedChunkCount = context.capsule.loadedChunkCount();
            manifest.fullChunkCount =
                    context.capsule.chunks.stream()
                            .mapToInt(com.atsukimc.anticrash.runtime.ChunkStateSnapshot::fullCount)
                            .sum();
            manifest.blockTickingChunkCount =
                    context.capsule.chunks.stream()
                            .mapToInt(
                                    com.atsukimc.anticrash.runtime.ChunkStateSnapshot
                                            ::blockTickingCount)
                            .sum();
            manifest.entityTickingChunkCount = context.capsule.entityTickingChunkCount();
            manifest.forcedChunkCount =
                    context.capsule.chunks.stream()
                            .mapToInt(
                                    com.atsukimc.anticrash.runtime.ChunkStateSnapshot::forcedCount)
                            .sum();
            manifest.fidelity = context.capsule.fidelity;
            manifest.chunkDomainHash =
                    context.capsule.expectedFingerprints.get(
                            com.atsukimc.anticrash.runtime.EquivalenceCategory.CHUNK_DOMAIN);
            manifest.carpetCaptureStatus = context.capsule.carpetCaptureStatus;
            manifest.unsupportedRuntimeState.addAll(context.capsule.unsupportedRuntimeState);
            manifest.blockEntityTickerCount = context.capsule.blockEntityTickerCount();
            manifest.entityOrderCount = context.capsule.entityOrderCount();
            manifest.torchBurnoutCount = context.capsule.redstoneTorchBurnoutCount();
            manifest.ticketCount = context.capsule.ticketCount();
            manifest.checkpointPlayerCount =
                    context.capsule.checkpointPlayersCaptured
                            ? context.capsule.checkpointPlayers.size()
                            : 0;
            manifest.checkpointPlayerHash =
                    context.capsule.expectedFingerprints.get(
                            com.atsukimc.anticrash.runtime.EquivalenceCategory.PLAYERS);
            manifest.runtimeCapsuleVersion = context.capsule.formatVersion;
            manifest.transientChunkCount = context.transientState.criticalChunkCount();
            manifest.transientChunksMadeDirty = context.transientState.chunksMadeDirty();
            manifest.movingPistonCount = context.transientState.movingPistons();
            manifest.scheduledTickCount = context.transientState.scheduledTicks();
            manifest.orderedScheduledTickCount = context.capsule.orderedScheduledTickCount();
            manifest.orderedMovingPistonCount = context.capsule.orderedMovingPistonCount();
            manifest.orderedTransientEntityCount = context.capsule.orderedTransientEntityCount();
            manifest.fabricLoaderVersion = context.fabricLoaderVersion;
            manifest.fabricApiVersion = context.fabricApiVersion;
            manifest.carpetVersion = context.carpetVersion;
            manifest.dimensions.addAll(context.dimensions);
            manifest.worldFiles.addAll(result.files);
            if (context.logicalDelta != null) {
                LogicalDelta delta = context.logicalDelta;
                manifest.logicalDeltaFormatVersion = delta.formatVersion;
                manifest.logicalDeltaEpoch = delta.captureEpoch;
                manifest.logicalDeltaChunkCount = delta.chunkCount;
                manifest.logicalDeltaEntityCount = delta.entityCount;
                manifest.logicalDeltaEntityChunkCount = delta.entityChunkCount;
                manifest.logicalDeltaTombstoneCount = delta.tombstoneCount;
                manifest.logicalDeltaPayloadBytes = delta.payloadBytes;
                manifest.logicalDeltaFailureReason = delta.failureReason;
                manifest.logicalDeltaComponentStatus.putAll(delta.componentStatus);
            } else if (seal != null) {
                manifest.generationType = seal.generationType;
                manifest.baseCheckpointId = seal.parentCheckpointId;
                manifest.generationChainDepth = context.generationChainDepth;
                manifest.generationSealFormatVersion = seal.formatVersion;
                manifest.logicalCutMonotonicNanos = seal.logicalCutMonotonicNanos;
                manifest.logicalCutServerTick = seal.logicalCutServerTick;
                manifest.generationPresentFiles = seal.presentCount;
                manifest.generationAbsentFiles = seal.absentCount;
                manifest.generationInheritedFiles = seal.inheritedCount;
                manifest.generationSealedFiles = seal.sealedCount;
                manifest.generationTombstoneFiles = seal.tombstoneCount;
                manifest.fileSet.addAll(seal.fileSet);
            }
            context.timing.captureAfterJvm();
            manifest.finalizationDurationMs =
                    Math.max(0L, context.timing.totalMs() - context.barrierDurationMs);
            manifest.phaseDurationsMs.putAll(context.timing.durationsMs());
            CheckpointTiming.JvmSample beforeJvm = context.timing.beforeJvm();
            CheckpointTiming.JvmSample afterJvm = context.timing.afterJvm();
            manifest.heapUsedBeforeBytes = beforeJvm.heapUsedBytes;
            manifest.heapUsedAfterBytes = afterJvm.heapUsedBytes;
            manifest.heapMaxBytes = Math.max(beforeJvm.heapMaxBytes, afterJvm.heapMaxBytes);
            manifest.gcCountBefore = beforeJvm.gcCount;
            manifest.gcCountAfter = afterJvm.gcCount;
            manifest.gcTimeBeforeMs = beforeJvm.gcTimeMs;
            manifest.gcTimeAfterMs = afterJvm.gcTimeMs;
            context.timing.end("finalize.manifest");

            ensureNotCancelled();
            context.timing.begin("finalize.ready_commit");
            context.timing.begin("commit");
            Path committed = store.commit(temporary, manifest);
            context.timing.end("commit");
            context.timing.end("finalize.ready_commit");
            context.timing.captureAfterJvm();
            // The worker returns the immutable delta to the server thread. Updating the mutation
            // ledger here would race with mixin hooks and rolling preparation, so markCommitted
            // is deliberately performed by pollActiveJob on the server thread after commit.
            return CaptureResult.success(
                    committed,
                    manifest,
                    result,
                    context.logicalDelta,
                    CheckpointDiagnostics.describe(
                            context.capsule,
                            manifest,
                            runtimeCapsuleBytes,
                            manifest.heapUsedAfterBytes,
                            manifest.heapMaxBytes));
        } catch (Exception | OutOfMemoryError exception) {
            if (context.detachedGeneration != null) context.detachedGeneration.discard();
            // Small phase/allocation evidence, not a heap dump or a whole-world report.
            Runtime jvm = Runtime.getRuntime();
            logger.importantKey(
                    "checkpoint.failed",
                    "category="
                            + CheckpointFailureGate.classify(exception)
                            + "; phase="
                            + activePhase
                            + "; used/max="
                            + (jvm.totalMemory() - jvm.freeMemory())
                            + "/"
                            + jvm.maxMemory()
                            + "; at="
                            + (exception.getStackTrace().length == 0
                                    ? "unknown"
                                    : exception.getStackTrace()[0]));
            if (temporary != null) {
                try {
                    SnapshotManager.deleteTree(temporary);
                } catch (Exception cleanup) {
                    logger.verboseKey("checkpoint.temp_cleanup_failed", cleanup.getMessage());
                }
            }
            return CaptureResult.failure(exception);
        }
    }

    private PreviousCheckpoint loadPreviousCheckpoint() throws IOException {
        String preferredBaseCheckpointId = internal.recoveryState().activeCheckpointId;
        List<Path> candidates =
                preferredBaseCheckpointId == null
                        ? store.listComplete()
                        : store.timelineCandidates(preferredBaseCheckpointId);
        Path checkpoint = null;
        CheckpointManifest verifiedManifest = null;
        for (Path candidate : candidates) {
            if (store.isQuarantined(candidate.getFileName().toString())) continue;
            try {
                verifiedManifest = store.verifyForIncrementalReuse(candidate);
                checkpoint = candidate;
                break;
            } catch (IOException exception) {
                logger.importantKey(
                        "checkpoint.invalid_ignored",
                        candidate.getFileName(),
                        exception.getMessage());
            }
        }
        if (checkpoint == null) {
            return new PreviousCheckpoint(null, null);
        }
        return new PreviousCheckpoint(checkpoint.resolve("world"), verifiedManifest);
    }

    /**
     * Verifies only the newest usable base checkpoint before a live-world barrier is entered. The
     * lookup starts lazily on the first tick so crash recovery does not hash the same checkpoint
     * concurrently during controller construction.
     */
    private void pollPreviousLookup() {
        if (previousLookupComplete) {
            return;
        }
        if (previousLookupJob == null) {
            previousLookupJob = ioExecutor.submit(this::loadPreviousCheckpoint);
            return;
        }
        if (!previousLookupJob.isDone()) {
            return;
        }
        try {
            PreviousCheckpoint previous = previousLookupJob.get();
            previousWorld = previous.world;
            previousManifest = previous.manifest;
            if (previous.manifest != null && previous.manifest.checkpointId != null) {
                lastCheckpointId = previous.manifest.checkpointId;
                lastManifest = previous.manifest;
                if (!lastCheckpointId.equals(internal.recoveryState().activeCheckpointId)) {
                    internal.updateRecoveryOrThrow(
                            data -> data.activeCheckpointId = lastCheckpointId);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.importantKey("checkpoint.previous_lookup_failed", exception.getMessage());
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            logger.importantKey("checkpoint.previous_lookup_failed", cause.getMessage());
        } catch (IOException exception) {
            logger.importantKey("state.recovery_state_persist_failed", exception.getMessage());
            states.set(AntiCrashState.RECOVERY_FAILED);
        } catch (CancellationException ignored) {
            // A clean shutdown may deliberately skip this optional incremental base.
        } finally {
            previousLookupJob = null;
            previousLookupComplete = true;
        }
    }

    /**
     * Hashes live world files ahead of the freeze.
     *
     * <p>The first capture of a session normally cannot reuse the parent checkpoint from metadata,
     * because that checkpoint was written by an earlier run. Without this pass every unchanged file
     * is read inside the frozen window instead. The scan runs on the same single IO worker as the
     * incremental-base preflight, so it never competes with it for disk.
     */
    private void pollLiveScan() {
        if (!config.checkpoint.rollingEnabled) {
            liveHashes = Map.of();
            liveScanComplete = true;
            return;
        }
        if (liveScanComplete || !previousLookupComplete) return;
        if (liveScanJob == null) {
            final Path world;
            try {
                world = WorldLocator.resolve(internal.gameDir());
            } catch (IOException exception) {
                logger.importantKey("checkpoint.live_scan_failed", exception.getMessage());
                liveScanComplete = true;
                return;
            }
            Map<String, SnapshotFile> previousFiles = SnapshotManager.index(previousManifest);
            logger.normalKey("checkpoint.live_scan_started");
            liveScanJob =
                    ioExecutor.submit(() -> LiveWorldScan.hashUnmatchedFiles(world, previousFiles));
            return;
        }
        if (!liveScanJob.isDone()) return;
        try {
            liveHashes = liveScanJob.get();
            logger.normalKey("checkpoint.live_scan_complete", liveHashes.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.importantKey("checkpoint.live_scan_failed", exception.getMessage());
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            logger.importantKey("checkpoint.live_scan_failed", cause.getMessage());
        } catch (CancellationException ignored) {
            // A clean shutdown may deliberately skip this optional preflight.
        } finally {
            liveScanJob = null;
            liveScanComplete = true;
        }
    }

    private void ensureNotCancelled() throws InterruptedIOException {
        if (shutdownCancellation || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Checkpoint was cancelled for server shutdown");
        }
    }

    private boolean pollActiveJob() {
        if (activeJob == null) {
            return false;
        }
        if (!activeJob.isDone()) {
            return true;
        }

        finishLiveCopyBarrier();

        CaptureResult result;
        try {
            result = activeJob.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result = CaptureResult.failure(exception);
        } catch (ExecutionException exception) {
            result =
                    CaptureResult.failure(
                            exception.getCause() == null ? exception : exception.getCause());
        } catch (CancellationException exception) {
            result = CaptureResult.failure(new InterruptedIOException("Checkpoint was cancelled"));
        }

        activeJob = null;
        releaseParentPin();
        liveCopyComplete = false;
        liveCopyCheckpointId = null;
        portableGenerationSealed = false;
        activeTemporary = null;
        capturing = false;
        lastJobSuccessful = result.success;
        Set<java.nio.file.Path> trackerWrites = activeTrackerWrites;
        boolean trackerArmedAtCut = activeTrackerArmedAtCut;
        activeTrackerWrites = null;
        activeTrackerArmedAtCut = false;
        if (result.success) {
            failureGate.success();
            activePhase = "IDLE";
            // The tracker interval now being recorded started at this checkpoint's cut.
            if (trackerArmedAtCut) {
                trackerBaseCheckpointId = result.committed.getFileName().toString();
            }
            lastFinalizationError = null;
            lastCheckpointId = result.committed.getFileName().toString();
            lastManifest = result.manifest;
            previousWorld = result.committed.resolve("world");
            previousManifest = result.manifest;
            previousLookupComplete = true;
            // The committed manifest now records current live metadata for every file, so the
            // pre-freeze cache is redundant and only costs heap.
            liveHashes = Map.of();
            try {
                internal.updateRecoveryOrThrow(data -> data.activeCheckpointId = lastCheckpointId);
            } catch (IOException exception) {
                logger.importantKey("state.recovery_state_persist_failed", exception.getMessage());
                states.set(AntiCrashState.RECOVERY_FAILED);
            }
            logger.normalKey(
                    "checkpoint.committed",
                    lastCheckpointId,
                    result.snapshot.backend,
                    result.manifest.snapshotDurationMs,
                    result.manifest.barrierDurationMs,
                    result.snapshot.changedFiles,
                    result.snapshot.reusedFiles);
            logger.normalKey(
                    "checkpoint.diagnostic",
                    lastCheckpointId,
                    result.diagnostic == null ? "unavailable" : result.diagnostic);
            if (lastTiming != null) {
                logger.normalKey("checkpoint.performance", lastCheckpointId, lastTiming.summary());
            }
            if (result.logicalDelta != null) {
                // This is intentionally after the atomic commit and on the server thread.  The
                // next tick's candidate preparation now observes the new parent epoch.
                logicalDeltaEngine.markCommitted(result.logicalDelta);
            }
            if (result.manifest.barrierDurationMs > config.checkpoint.warnBarrierMs) {
                logger.importantKey("checkpoint.barrier_slow", result.manifest.barrierDurationMs);
            }
            if (result.manifest.barrierDurationMs > config.checkpoint.criticalBarrierMs) {
                logger.importantKey(
                        "checkpoint.barrier_critical",
                        result.manifest.barrierDurationMs,
                        config.checkpoint.criticalBarrierMs);
            }
            if (result.manifest.snapshotDurationMs > config.checkpoint.warnCheckpointMs) {
                logger.importantKey("checkpoint.snapshot_slow", result.manifest.snapshotDurationMs);
            }
            if (result.manifest.finalizationDurationMs > config.checkpoint.warnFinalizeMs) {
                logger.importantKey(
                        "checkpoint.finalize_slow", result.manifest.finalizationDurationMs);
            }
            resetDeadline();
        } else {
            logicalDeltaEngine.resetCommittedBaseline();
            // Every write since the last committed checkpoint must reach the next capture.
            LiveFileTracker.global().restore(trackerWrites);
            if (result.error instanceof ParentCheckpointChangedException) {
                // The parent itself is no longer what its manifest says. Choose and fully verify
                // the incremental parent again rather than failing on it at every interval.
                store.quarantine(
                        previousManifest == null ? null : previousManifest.checkpointId,
                        "PARENT_INVALID: " + result.error.getMessage());
                previousWorld = null;
                previousManifest = null;
                previousLookupComplete = true;
                trackerBaseCheckpointId = null;
            }
            lastFinalizationError = result.error == null ? "unknown" : result.error.getMessage();
            failureGate.failed(result.error, System.nanoTime());
            logger.importantKey("checkpoint.failed", lastFinalizationError);
            if (!states.isRecovery())
                enterCheckpointState(AntiCrashState.CHECKPOINT_FINALIZE_FAILED);
            resetDeadline();
        }

        if (!states.isRecovery() && result.success) {
            returnToRunning();
        }
        return false;
    }

    private void pollCompaction(MinecraftServer server) {
        if (compactionJob == null || !compactionJob.isDone()) return;
        try {
            CompactionResult compaction = compactionJob.get();
            Path compacted = compaction.path();
            lastCompactionError = null;
            logger.normalKey("checkpoint.compacted", compacted.getFileName());
            // The new root is a complete authenticated view. Adopt it as the next delta parent
            // only after the worker has committed it atomically.
            // The worker already verified the source and committed the copied payload atomically.
            // Reading this small manifest on the server thread avoids a second full-world hash
            // pass during the tick that adopts the compacted root.
            CheckpointManifest manifest = compaction.manifest();
            previousWorld = compacted.resolve("world");
            previousManifest = manifest;
            lastCheckpointId = manifest.checkpointId;
            lastManifest = manifest;
            // The compacted root holds exactly its source's files, so writes tracked since the
            // source's cut still describe everything that changed relative to it.
            if (trackerBaseCheckpointId != null
                    && trackerBaseCheckpointId.equals(manifest.timelineParentCheckpointId)) {
                trackerBaseCheckpointId = manifest.checkpointId;
            }
            // Compaction changes only the storage representation of the newest committed epoch.
            // Keep the existing mutation ledger: reseeding it from the current live World would
            // make the live-vs-parent differences look unchanged while the server continued to
            // run after the source checkpoint was created.
            internal.updateRecoveryOrThrow(data -> data.activeCheckpointId = lastCheckpointId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            lastCompactionError = exception.getMessage();
            logger.importantKey("checkpoint.compaction_failed", lastCompactionError);
        } catch (ExecutionException | IOException exception) {
            Throwable cause =
                    exception instanceof ExecutionException && exception.getCause() != null
                            ? exception.getCause()
                            : exception;
            lastCompactionError = cause.getMessage();
            logger.importantKey("checkpoint.compaction_failed", lastCompactionError);
        } finally {
            compactionJob = null;
        }
    }

    public boolean isCapturing() {
        return capturing || pendingBarrier != null || activeJob != null;
    }

    /** Waits for the current checkpoint without ever extending shutdown indefinitely. */
    public boolean awaitCurrentJob(long timeoutMillis) {
        long deadlineNanos =
                timeoutMillis <= 0
                        ? 0L
                        : System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        // Normal checkpoints advance once per server tick. During Minecraft#shutdown there are no
        // further ticks, so drive the same bounded state machine on the server thread while still
        // honoring the configured shutdown deadline.
        while (pendingBarrier != null) {
            if (deadlineNanos == 0L || System.nanoTime() - deadlineNanos >= 0L) return false;
            MinecraftServer barrierServer = pendingBarrier.server;
            // No server tick runs during shutdown, so chunk tasks that complete saving futures
            // would never execute and the staged save would wait out the whole timeout.
            for (ServerWorld world : barrierServer.getWorlds()) {
                world.getChunkManager().executeQueuedTasks();
            }
            advanceBarrier(barrierServer);
            if (pendingBarrier != null) {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        if (activeJob == null) return lastJobSuccessful;
        try {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) return false;
            activeJob.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            logger.importantKey(
                    "checkpoint.worker_terminated",
                    exception.getCause() == null
                            ? exception.getMessage()
                            : exception.getCause().getMessage());
        } catch (TimeoutException exception) {
            return false;
        } catch (CancellationException exception) {
            return false;
        }
        finishLiveCopyBarrier();
        pollActiveJob();
        return lastJobSuccessful;
    }

    /** Cancels optional checkpoint work so vanilla shutdown can proceed immediately. */
    public void cancelForShutdown() {
        rollingDrain = null;
        releaseParentPin();
        logicalDeltaEngine.resetCommittedBaseline();
        shutdownCancellation = true;
        liveCopyComplete = false;
        liveCopyCheckpointId = null;
        requestPending = false;
        requestEnqueuedNanos = 0L;
        pendingBarrier = null;
        if (previousLookupJob != null) {
            previousLookupJob.cancel(true);
            previousLookupJob = null;
            previousLookupComplete = true;
        }
        if (liveScanJob != null) {
            liveScanJob.cancel(true);
            liveScanJob = null;
            liveScanComplete = true;
        }
        if (activeJob != null) {
            activeJob.cancel(true);
        }
        if (compactionJob != null) {
            compactionJob.cancel(true);
            compactionJob = null;
        }
        Path temporary = activeTemporary;
        if (temporary != null) {
            try {
                SnapshotManager.deleteTree(temporary);
            } catch (IOException cleanup) {
                logger.verboseKey("checkpoint.temp_cleanup_failed", cleanup.getMessage());
            }
        }
        capturing = false;
        portableGenerationSealed = false;
        activeTemporary = null;
        if (!states.isRecovery()) {
            returnToRunning();
        }
    }

    public void close() {
        cancelForShutdown();
        ioExecutor.shutdownNow();
    }

    /** The single CheckpointStore view of {@code .anticrash/checkpoints} owned by this process. */
    public CheckpointStore store() {
        return store;
    }

    public List<Path> listComplete() throws IOException {
        return store.listComplete();
    }

    public Path newestValid() throws IOException {
        return store.newestValid();
    }

    public List<Path> timelineCandidates(String activeCheckpointId) throws IOException {
        return store.timelineCandidates(activeCheckpointId);
    }

    /** Metadata-only candidate discovery used by the pre-world ASK decision. */
    public com.atsukimc.anticrash.snapshot.CheckpointCandidate discoverLatestCandidate(
            String activeCheckpointId) throws IOException {
        return store.discoverLatestCandidate(activeCheckpointId);
    }

    public Path find(String id) throws IOException {
        return store.find(id);
    }

    public CheckpointManifest readManifest(Path path) throws IOException {
        return store.readManifest(path);
    }

    public void verify(Path path) throws IOException {
        store.verify(path);
    }

    /**
     * Fully verifies a recovery candidate and adopts the same authenticated checkpoint as the next
     * incremental snapshot base. Boot-time ASK selection calls this before the first server tick,
     * avoiding a second full-world hash pass after the operator chooses not to restore.
     */
    public void verifyAndUseAsIncrementalBase(Path path) throws IOException {
        CheckpointManifest manifest = store.verifyForIncrementalReuse(path);
        useVerifiedAsIncrementalBase(path, manifest);
    }

    /** Adopts a checkpoint that WorldRestorer has just authenticated and staged. */
    public void useVerifiedAsIncrementalBase(Path path, CheckpointManifest manifest)
            throws IOException {
        if (path == null
                || manifest == null
                || manifest.checkpointId == null
                || !path.getFileName().toString().equals(manifest.checkpointId)
                || !Files.isDirectory(
                        path.resolve("world"), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path.resolve("world"))) {
            throw new IOException("Verified incremental checkpoint base is invalid");
        }
        if (previousLookupJob != null) {
            previousLookupJob.cancel(true);
            previousLookupJob = null;
        }
        previousWorld = path.resolve("world");
        previousManifest = manifest;
        previousLookupComplete = true;
        lastCheckpointId = manifest.checkpointId;
        lastManifest = manifest;
        logicalDeltaEngine.resetCommittedBaseline();
    }

    public CheckpointManifest verifyRecoveryPayloads(Path path) throws IOException {
        return store.verifyRecoveryPayloads(path);
    }

    public String lastCheckpointId() {
        return lastCheckpointId;
    }

    /** ID to associate with mutations after the point-in-time copy has completed. */
    public String transactionCheckpointId() {
        if (!previousLookupComplete && !liveCopyComplete) return "*";
        String candidate = liveCopyCheckpointId;
        return liveCopyComplete && candidate != null ? candidate : lastCheckpointId;
    }

    /** Previous valid ID retained until the candidate checkpoint commits atomically. */
    public String transactionFallbackCheckpointId() {
        if (!liveCopyComplete) return null;
        String candidate = liveCopyCheckpointId;
        return candidate == null ? null : lastCheckpointId;
    }

    public CheckpointManifest lastManifest() {
        return lastManifest;
    }

    /**
     * Requests offline compaction of the newest verified view; never touches a live ServerWorld.
     */
    public boolean requestCompaction() {
        if (!config.checkpoint.enabled
                || !config.checkpoint.createOnManualCommand
                || compactionJob != null
                || isCapturing()
                || previousWorld == null
                || previousManifest == null) {
            return false;
        }
        Path source = previousWorld.getParent();
        if (source == null) return false;
        logger.normalKey("checkpoint.compaction_started", source.getFileName());
        compactionJob =
                ioExecutor.submit(() -> {
                    Path compacted = CheckpointCompactor.compact(store, source);
                    // Verify on the worker so the adopted parent carries fresh destination
                    // metadata.
                    // Otherwise the next capture would have to re-hash every file inside the
                    // barrier.
                    return new CompactionResult(
                            compacted, store.verifyForIncrementalReuse(compacted));
                });
        return true;
    }

    public boolean compactionInFlight() {
        return compactionJob != null;
    }

    public CheckpointTiming lastTiming() {
        return lastTiming;
    }

    public String checkpointPerformanceReport(String language) {
        return checkpointPerformanceReport(language, false);
    }

    public String checkpointPerformanceReport(String language, boolean verbose) {
        boolean japanese = "ja".equalsIgnoreCase(language);
        CheckpointManifest manifest = lastManifest;
        CheckpointTiming timing = lastTiming;
        StringBuilder result = new StringBuilder();
        result.append(
                        japanese
                                ? "AntiCrash Checkpoint Performance"
                                : "AntiCrash Checkpoint Performance")
                .append('\n')
                .append(japanese ? "状態: " : "State: ")
                .append(states.get())
                .append('\n')
                .append(japanese ? "進行中: " : "In flight: ")
                .append(isCapturing())
                .append('\n')
                .append(engineIncidentStatus(language))
                .append('\n')
                .append(
                        japanese
                                ? "閾値 target/warn/critical ms: "
                                : "Threshold target/warn/critical ms: ")
                .append(config.checkpoint.targetBarrierMs)
                .append('/')
                .append(config.checkpoint.warnBarrierMs)
                .append('/')
                .append(config.checkpoint.criticalBarrierMs)
                .append('\n');
        if (manifest == null) {
            result.append(japanese ? "まだ確定済みcheckpointはありません。" : "No committed checkpoint yet.");
            if (lastFinalizationError != null) {
                result.append('\n')
                        .append(japanese ? "直近エラー: " : "Last error: ")
                        .append(lastFinalizationError);
            }
            return result.toString();
        }
        result.append(japanese ? "Checkpoint: " : "Checkpoint: ")
                .append(manifest.checkpointId)
                .append('\n')
                .append(japanese ? "Backend: " : "Backend: ")
                .append(manifest.snapshotBackend)
                .append('\n')
                .append(japanese ? "Engine: " : "Engine: ")
                .append(manifest.checkpointEngine)
                .append('\n')
                .append(japanese ? "Parent: " : "Parent: ")
                .append(manifest.parentCheckpointId)
                .append('\n')
                .append(japanese ? "論理カット: " : "Logical cut: ")
                .append(manifest.logicalCutServerTick)
                .append('\n')
                .append(japanese ? "Barrier ms: " : "Barrier ms: ")
                .append(manifest.barrierDurationMs)
                .append('\n')
                .append(japanese ? "Finalization ms: " : "Finalization ms: ")
                .append(manifest.finalizationDurationMs)
                .append('\n')
                .append(
                        japanese
                                ? "Generation files present/absent/inherited/sealed/tombstone: "
                                : "Generation files present/absent/inherited/sealed/tombstone: ")
                .append(manifest.generationPresentFiles)
                .append('/')
                .append(manifest.generationAbsentFiles)
                .append('/')
                .append(manifest.generationInheritedFiles)
                .append('/')
                .append(manifest.generationSealedFiles)
                .append('/')
                .append(manifest.generationTombstoneFiles)
                .append('\n')
                .append(japanese ? "Heap before/after/max: " : "Heap before/after/max: ")
                .append(manifest.heapUsedBeforeBytes)
                .append('/')
                .append(manifest.heapUsedAfterBytes)
                .append('/')
                .append(manifest.heapMaxBytes)
                .append('\n')
                .append(
                        japanese
                                ? "Phase ms（barrier等の親phaseはinclusive）: "
                                : "Phase ms (parent phases such as barrier are inclusive): ")
                .append(timing == null ? manifest.phaseDurationsMs : timing.durationsMs())
                .append('\n')
                .append(japanese ? "Logical delta: " : "Logical delta: ")
                .append(manifest.logicalDeltaChunkCount)
                .append(" chunks / ")
                .append(manifest.logicalDeltaEntityChunkCount)
                .append(" entity chunks / ")
                .append(manifest.logicalDeltaTombstoneCount)
                .append(" tombstones / ")
                .append(manifest.logicalDeltaPayloadBytes)
                .append(" bytes")
                .append('\n')
                .append(japanese ? "Candidate cache: " : "Candidate cache: ")
                .append(logicalDeltaEngine.candidateReport())
                .append('\n')
                .append(japanese ? "Mirror: " : "Mirror: ")
                .append(checkpointMirror.snapshot().compact());
        if (lastCompactionError != null) {
            result.append('\n')
                    .append(japanese ? "直近compactionエラー: " : "Last compaction error: ")
                    .append(lastCompactionError);
        }
        if (verbose) {
            result.append('\n')
                    .append(
                            japanese
                                    ? "詳細phase（親phaseはinclusive）: "
                                    : "Detailed phases (parent phases are inclusive): ")
                    .append(timing == null ? manifest.phaseDurationsMs : timing.durationsMs())
                    .append('\n')
                    .append(japanese ? "未対応状態: " : "Unsupported state: ")
                    .append(manifest.logicalDeltaComponentStatus)
                    .append('\n')
                    .append(japanese ? "Runtime unsupported: " : "Runtime unsupported: ")
                    .append(manifest.unsupportedRuntimeState);
        }
        if (lastFinalizationError != null) {
            result.append('\n')
                    .append(japanese ? "直近エラー: " : "Last error: ")
                    .append(lastFinalizationError);
        }
        return result.toString();
    }

    public String checkpointMirrorReport(String language, boolean audit) {
        return (audit ? checkpointMirror.audit() : checkpointMirror.snapshot()).compact()
                + "\n"
                + checkpointMirror.render(language);
    }

    public void resetPerformance() {
        lastTiming = null;
        lastFinalizationError = null;
    }

    /**
     * Seconds until the next scheduled checkpoint, or -1 when none is scheduled. Operators use it
     * to decide whether to wait for the automatic checkpoint or request one explicitly.
     */
    public long secondsUntilNextCheckpoint() {
        if (!config.checkpoint.enabled) return -1L;
        if (isCapturing()) return 0L;
        long remaining = nextDeadlineNanos - System.nanoTime();
        return remaining <= 0 ? 0L : (remaining + 999_999_999L) / 1_000_000_000L;
    }

    private void finishLiveCopyBarrier() {
        if (portableGenerationSealed) return;
        if (liveCopyComplete && states.get() == AntiCrashState.CHECKPOINTING) {
            enterCheckpointState(AntiCrashState.CHECKPOINT_COMMITTING);
        }
    }

    private static boolean savingDisabled(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.savingDisabled) return true;
        }
        return false;
    }

    /**
     * Reports why the logical engine could not be used, once per distinct reason. A world with POI
     * or PersistentState files falls back on every capture, and the same warning every 30 seconds
     * buried every other message in the log.
     */
    private void reportLogicalFallback(String reason) {
        if (java.util.Objects.equals(reason, lastLogicalFallbackReason)) {
            logger.verboseKey("checkpoint.logical_fallback", reason);
            return;
        }
        lastLogicalFallbackReason = reason;
        logger.importantKey("checkpoint.logical_fallback", reason);
    }

    /** A refused request must not leave the state at CHECKPOINT_REQUESTED. */
    private void returnToRunningAfterRequest() {
        if (states.get() == AntiCrashState.CHECKPOINT_REQUESTED) returnToRunning();
    }

    /**
     * Moves between the checkpoint states without writing recovery-state.json.
     *
     * <p>These states only describe work inside this process. Boot classifies an exit from
     * run-state.json and never acts on a stored checkpoint state, yet persisting each transition
     * fsynced the state file and its directory three times inside the frozen window. On ext4 such
     * an fsync right after the cut's region writes can wait for all of them to reach the disk.
     */
    private void enterCheckpointState(AntiCrashState next) {
        states.setTransient(next);
    }

    /** Leaves the checkpoint states; writes the state file only if it does not say RUNNING yet. */
    private void returnToRunning() {
        if (AntiCrashState.RUNNING.name().equals(internal.recoveryState().state)) {
            states.setTransient(AntiCrashState.RUNNING);
        } else {
            states.set(AntiCrashState.RUNNING);
        }
    }

    /** Turns the logical engine's per-mutation observers off while it cannot be used. */
    private void syncLogicalTracking() {
        boolean usable = logicalEngineUsable();
        if (usable == LogicalTracking.enabled()) return;
        LogicalTracking.setEnabled(usable);
        if (usable) {
            // Nothing was recorded while the observers were off, so no earlier baseline may be
            // trusted. The next portable cut seeds a fresh one and clears this uncertainty.
            logicalDeltaEngine.resetCommittedBaseline();
            ChunkMutationTracker.global().markUncertain("logical tracking re-enabled");
        } else {
            EntityLifecycleTracker.global().clearAll();
        }
    }

    /**
     * True when the logical engine can produce a checkpoint at all. With POI or PersistentState
     * files present (every real world has them) it always falls back, so preparing candidates and
     * seeding its ledger would only cost server-thread time every tick and at every cut.
     */
    private boolean logicalEngineUsable() {
        return CheckpointEnginePolicy.logicalDeltaRequested(config)
                && !CheckpointEnginePolicy.hasUnsafeLogicalOptions(config)
                && !Boolean.TRUE.equals(logicalPersistentStateBlocked);
    }

    /**
     * Chooses the immutable parent used by the next portable generation.
     *
     * <p>A BASE starts a new lineage whose files share no inode with the old one. It is no longer
     * periodic by default: each BASE copied the whole world (19 GB on s1) about every ten minutes,
     * roughly 1.5 TB of writes a day, while restores always read a generation's own complete and
     * checksummed file set. {@code base_interval}/{@code max_chain_depth} still enable it. It used
     * to be captured by copying the whole live world while the server was frozen (19 GB took 25-30
     * s). With a verified portable parent it is now captured like any delta and the worker copies,
     * rather than links, the inherited files from the immutable parent. A legacy or pre-generation
     * checkpoint is never treated as a parent for the generation protocol, even though it remains a
     * valid recovery candidate.
     */
    private GenerationPlan generationPlan(boolean explicitBase) {
        if (previousManifest == null || previousWorld == null) {
            return new GenerationPlan(null, null, 0, Map.of(), false, null);
        }
        boolean portableParent =
                "PORTABLE_GENERATION".equalsIgnoreCase(previousManifest.snapshotBackend)
                        && previousManifest.generationSealFormatVersion > 0
                        && previousManifest.formatVersion >= 3;
        if (!portableParent) return new GenerationPlan(null, null, 0, Map.of(), false, null);
        // 0 disables either limit. Every generation is a complete, individually checksummed file
        // set whatever its depth, so a periodic BASE only adds a physical copy of the whole world.
        int depth = previousManifest.generationChainDepth;
        boolean startBase =
                explicitBase
                        || (config.checkpoint.baseInterval > 0
                                && depth >= config.checkpoint.baseInterval)
                        || (config.checkpoint.maxChainDepth > 0
                                && depth >= config.checkpoint.maxChainDepth);
        Map<String, SnapshotFile> previousFiles = SnapshotManager.index(previousManifest);
        if (startBase) {
            return new GenerationPlan(
                    previousWorld, null, 0, previousFiles, true, previousManifest.checkpointId);
        }
        return new GenerationPlan(
                previousWorld,
                previousManifest.checkpointId,
                Math.max(0, previousManifest.generationChainDepth),
                previousFiles,
                false,
                previousManifest.checkpointId);
    }

    private static String checkpointId(Path temporary) {
        if (temporary == null || temporary.getFileName() == null) return "unknown";
        return temporary.getFileName().toString().replaceFirst("\\.tmp$", "");
    }

    private record CaptureContext(
            Path world,
            RuntimeCapsule capsule,
            List<PlayerSnapshotData> playerSnapshots,
            List<FakePlayerSnapshot> fakeSnapshots,
            long barrierDurationMs,
            long serverTick,
            long worldGameTime,
            List<String> dimensions,
            String fabricLoaderVersion,
            String fabricApiVersion,
            String carpetVersion,
            TransientChunkStateMarker.MarkResult transientState,
            Path previousWorld,
            String parentCheckpointId,
            Map<String, SnapshotFile> previousFiles,
            Map<String, SnapshotFile> liveHashes,
            Path temporary,
            LogicalDelta logicalDelta,
            GenerationSeal generationSeal,
            int generationChainDepth,
            CheckpointTiming timing,
            String timelineParentCheckpointId,
            DetachedGeneration detachedGeneration,
            boolean copyInherited,
            long logicalCutServerTick,
            long logicalCutMonotonicNanos) {}

    private record GenerationPlan(
            Path previousWorld,
            String parentCheckpointId,
            int parentChainDepth,
            Map<String, SnapshotFile> previousFiles,
            boolean rebase,
            String sourceCheckpointId) {}

    private record BarrierWorld(ServerWorld world, List<ChunkHolder> holders) {}

    private static final class PendingBarrier {
        private final String timelineParentCheckpointId;
        private final MinecraftServer server;
        private final Path world;
        private final RuntimeCapsule capsule;
        private final List<PlayerSnapshotData> playerSnapshots;
        private final List<FakePlayerSnapshot> fakeSnapshots;
        private final long barrierStartNanos;
        private final long serverTick;
        private final long worldGameTime;
        private final List<String> dimensions;
        private final String fabricLoaderVersion;
        private final String fabricApiVersion;
        private final String carpetVersion;
        private final TransientChunkStateMarker.MarkResult transientState;
        private final Path previousWorld;
        private final String parentCheckpointId;
        private final int parentChainDepth;
        private final Map<String, SnapshotFile> previousFiles;
        private final Map<String, SnapshotFile> liveHashes;
        private final List<BarrierWorld> worlds;
        private final Path temporary;
        private final CheckpointTiming timing;
        private boolean logicalDelta;
        private LogicalDelta logicalDeltaValue;
        private final boolean portableGeneration;
        private final boolean unsupportedPersistentState;

        /** BASE generation: inherited files are copied from the parent instead of linked. */
        private final boolean rebase;

        /** Checkpoint whose files are {@link #previousFiles}; also set for a rebase. */
        private final String sourceCheckpointId;

        private int worldCursor;
        private int holderCursor;
        private int savedChunks;
        private int savedSinceDrain;
        private int drainedBatches;

        /** Tracker interval ended by this cut, until the worker owns it. */
        private Set<java.nio.file.Path> trackerWrites;

        private boolean trackerArmedAtCut;

        private PendingBarrier(
                String timelineParentCheckpointId,
                MinecraftServer server,
                Path world,
                RuntimeCapsule capsule,
                List<PlayerSnapshotData> playerSnapshots,
                List<FakePlayerSnapshot> fakeSnapshots,
                long barrierStartNanos,
                long serverTick,
                long worldGameTime,
                List<String> dimensions,
                String fabricLoaderVersion,
                String fabricApiVersion,
                String carpetVersion,
                TransientChunkStateMarker.MarkResult transientState,
                Path previousWorld,
                String parentCheckpointId,
                int parentChainDepth,
                Map<String, SnapshotFile> previousFiles,
                Map<String, SnapshotFile> liveHashes,
                List<BarrierWorld> worlds,
                Path temporary,
                CheckpointTiming timing,
                boolean logicalDelta,
                boolean portableGeneration,
                boolean unsupportedPersistentState,
                boolean rebase,
                String sourceCheckpointId) {
            this.timelineParentCheckpointId = timelineParentCheckpointId;
            this.server = server;
            this.world = world;
            this.capsule = capsule;
            this.playerSnapshots = playerSnapshots;
            this.fakeSnapshots = fakeSnapshots;
            this.barrierStartNanos = barrierStartNanos;
            this.serverTick = serverTick;
            this.worldGameTime = worldGameTime;
            this.dimensions = dimensions;
            this.fabricLoaderVersion = fabricLoaderVersion;
            this.fabricApiVersion = fabricApiVersion;
            this.carpetVersion = carpetVersion;
            this.transientState = transientState;
            this.previousWorld = previousWorld;
            this.parentCheckpointId = parentCheckpointId;
            this.parentChainDepth = parentChainDepth;
            this.previousFiles = previousFiles;
            this.liveHashes = liveHashes;
            this.worlds = worlds;
            this.temporary = temporary;
            this.timing = timing;
            this.logicalDelta = logicalDelta;
            this.portableGeneration = portableGeneration;
            this.unsupportedPersistentState = unsupportedPersistentState;
            this.rebase = rebase;
            this.sourceCheckpointId = sourceCheckpointId;
        }

        private CaptureContext toCaptureContext(long barrierDurationMs, GenerationSeal seal) {
            return toCaptureContext(barrierDurationMs, seal, null);
        }

        private CaptureContext toCaptureContext(
                long barrierDurationMs, GenerationSeal seal, LogicalDelta logicalDelta) {
            return toCaptureContext(barrierDurationMs, seal, logicalDelta, null);
        }

        private CaptureContext toCaptureContext(
                long barrierDurationMs,
                GenerationSeal seal,
                LogicalDelta logicalDelta,
                DetachedGeneration detached) {
            String generationParent =
                    seal != null
                            ? seal.parentCheckpointId
                            : detached != null ? parentCheckpointId : null;
            int chainDepth = generationParent == null ? 0 : parentChainDepth + 1;
            boolean frozenLiveReader = seal == null && detached == null;
            return new CaptureContext(
                    frozenLiveReader ? world : null,
                    capsule,
                    playerSnapshots,
                    fakeSnapshots,
                    barrierDurationMs,
                    serverTick,
                    worldGameTime,
                    dimensions,
                    fabricLoaderVersion,
                    fabricApiVersion,
                    carpetVersion,
                    transientState,
                    previousWorld,
                    parentCheckpointId,
                    previousFiles,
                    liveHashes,
                    temporary,
                    logicalDelta,
                    seal,
                    chainDepth,
                    timing,
                    timelineParentCheckpointId,
                    detached,
                    rebase,
                    serverTick,
                    System.nanoTime());
        }
    }

    private record PreviousCheckpoint(Path world, CheckpointManifest manifest) {}

    private record CompactionResult(Path path, CheckpointManifest manifest) {}

    private static final class CaptureResult {
        private final boolean success;
        private final Path committed;
        private final CheckpointManifest manifest;
        private final SnapshotResult snapshot;
        private final LogicalDelta logicalDelta;
        private final String diagnostic;
        private final Exception error;

        private CaptureResult(
                boolean success,
                Path committed,
                CheckpointManifest manifest,
                SnapshotResult snapshot,
                LogicalDelta logicalDelta,
                String diagnostic,
                Exception error) {
            this.success = success;
            this.committed = committed;
            this.manifest = manifest;
            this.snapshot = snapshot;
            this.logicalDelta = logicalDelta;
            this.diagnostic = diagnostic;
            this.error = error;
        }

        private static CaptureResult success(
                Path committed,
                CheckpointManifest manifest,
                SnapshotResult snapshot,
                LogicalDelta logicalDelta,
                String diagnostic) {
            return new CaptureResult(
                    true, committed, manifest, snapshot, logicalDelta, diagnostic, null);
        }

        private static CaptureResult failure(Throwable failure) {
            Exception exception =
                    failure instanceof Exception
                            ? (Exception) failure
                            : new IOException(failure.getMessage(), failure);
            return new CaptureResult(false, null, null, null, null, null, exception);
        }
    }
}
