package com.atsukimc.anticrash;

import com.atsukimc.anticrash.checkpoint.CheckpointManager;
import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.consistency.ConsistencyManager;
import com.atsukimc.anticrash.core.AntiCrashState;
import com.atsukimc.anticrash.core.AntiCrashStateMachine;
import com.atsukimc.anticrash.core.CrashCause;
import com.atsukimc.anticrash.core.CrashDetector;
import com.atsukimc.anticrash.core.InternalProcessLock;
import com.atsukimc.anticrash.core.InternalStateManager;
import com.atsukimc.anticrash.core.RecoveryStateData;
import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.atsukimc.anticrash.integration.carpet.CarpetIntegration;
import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.player.PlayerSnapshotData;
import com.atsukimc.anticrash.player.PlayerSnapshotManager;
import com.atsukimc.anticrash.recovery.ChunkRecoveryManager;
import com.atsukimc.anticrash.recovery.ChunkRecoveryPlan;
import com.atsukimc.anticrash.recovery.PreWorldDecisionInput;
import com.atsukimc.anticrash.recovery.RecoveryPayloadValidator;
import com.atsukimc.anticrash.recovery.StartupRecoveryPlan;
import com.atsukimc.anticrash.recovery.WorldRestorer;
import com.atsukimc.anticrash.restart.RestartCoordinator;
import com.atsukimc.anticrash.runtime.BlockEventSnapshot;
import com.atsukimc.anticrash.runtime.EquivalenceCategory;
import com.atsukimc.anticrash.runtime.RecoveryEquivalenceReport;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.RuntimeCapsuleManager;
import com.atsukimc.anticrash.snapshot.CheckpointCandidate;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;
import com.atsukimc.anticrash.snapshot.CrashRawManager;
import com.atsukimc.anticrash.snapshot.WorldLocator;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AntiCrashController {
    private static final Logger BOOT_LOGGER = LoggerFactory.getLogger("anticrash");

    /** Accommodates bounded v3 exact scheduled-tick queues without accepting unbounded JSON. */
    private static final long MAX_RUNTIME_CAPSULE_BYTES = 64L * 1024L * 1024L;

    private static final long MAX_PLAYER_SNAPSHOT_BYTES = 64L * 1024L * 1024L;
    private final Path configFile;
    private final AntiCrashConfig config;
    private final InternalStateManager internal;
    private final AntiCrashLogger logger;
    private final AntiCrashStateMachine states;
    private final RuntimeCapsuleManager runtime;
    private final PlayerSnapshotManager playerSnapshots;
    private final ChunkRecoveryManager chunks;
    private final CheckpointManager checkpoints;
    private final CrashRawManager crashRaw;
    private final WorldRestorer restorer;
    private final ConsistencyManager consistency;
    private final RestartCoordinator restartCoordinator;
    private final InternalProcessLock processLock;
    private final com.atsukimc.anticrash.core.RunMarker previousRun;
    private MinecraftServer server;
    private volatile boolean normalShutdownRequested;
    private boolean finalCheckpointDone;
    private boolean pendingStart;
    private boolean worldRestored;
    private boolean runtimeRestored;
    private boolean rawWorldRestored;
    private BlockEventSnapshot.RestoreResult blockEventRestore =
            new BlockEventSnapshot.RestoreResult();
    private CarpetIntegration.RestoreSummary fakePlayerRestore =
            new CarpetIntegration.RestoreSummary();
    private List<FakePlayerSnapshot> pendingFakeSnapshots = List.of();
    private RuntimeCapsule pendingRuntimeCapsule;
    private String selectedCheckpointId;
    private String bootError;
    private long askDeadlineNanos;
    private long askReminderNanos;
    private boolean askPromptPending;
    private boolean recoveryDecisionWasDirty;
    private boolean forceStartEligible;
    private boolean strictBlocked;
    private RecoveryEquivalenceReport lastEquivalenceReport;
    private final java.util.Map<String, String> recoveryDegraded = new java.util.LinkedHashMap<>();

    /** Retained only while the recovery is frozen so verify/debug can explain a failed start. */
    private RuntimeCapsule equivalenceExpectedCapsule;

    private List<FakePlayerSnapshot> equivalenceExpectedFakePlayers = List.of();
    private String equivalenceLogSignature;
    private int frozenTicks;
    private boolean actionBarVisible;
    private long worldLoadStartNanos;

    /** Immutable decision handed from the pre-world disk phase to onServerStarted. */
    private StartupRecoveryPlan startupPlan =
            StartupRecoveryPlan.continueCurrentWorld(StartupRecoveryPlan.PreviousExit.CLEAN);

    public AntiCrashController() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("anticrash.toml");
        internal =
                new InternalStateManager(
                        FabricLoader.getInstance().getGameDir(), BOOT_LOGGER::warn);
        String initializationError = null;
        try {
            internal.initializeDirectories();
        } catch (IOException exception) {
            BOOT_LOGGER.error(
                    MessageCatalog.text(
                            MessageCatalog.DEFAULT_LANGUAGE, "state.directory_init_failed"),
                    exception);
            initializationError = exception.toString();
        }
        config = AntiCrashConfig.load(configFile, BOOT_LOGGER::warn);
        if (!config.loadedSafely())
            initializationError =
                    "The AntiCrash configuration is unreadable: " + config.loadError();
        internal.setLanguage(config.language);
        logger =
                new AntiCrashLogger(internal.logsDir(), config.logging, () -> this.config.language);
        restartCoordinator = new RestartCoordinator(internal.gameDir(), logger);

        InternalProcessLock acquiredLock = null;
        if (initializationError == null) {
            try {
                acquiredLock = InternalProcessLock.acquire(internal.locksDir());
            } catch (IOException exception) {
                initializationError = exception.toString();
                logger.fatalKey("anticrash.init_internal_failed", exception.getMessage());
            }
        }
        processLock = acquiredLock;

        com.atsukimc.anticrash.core.RunMarker prior = null;
        if (initializationError == null) prior = internal.readPreviousRun();
        // A start that ended before the world was loaded says nothing about the world.
        previousRun = com.atsukimc.anticrash.core.RunMarker.effectivePrevious(prior);
        if (prior != null && previousRun != prior) {
            logger.importantKey(
                    "startup.previous_start_never_opened_world",
                    previousRun.cleanShutdown ? "CLEAN" : "DIRTY");
        }
        states = new AntiCrashStateMachine(internal);
        if (internal.previousRunUnreadable())
            initializationError = "The previous run marker is unreadable";
        if (internal.recoveryStateUnreadable())
            initializationError = "The recovery state is unreadable";
        runtime = new RuntimeCapsuleManager(config, logger);
        playerSnapshots = new PlayerSnapshotManager(config, logger);
        chunks = new ChunkRecoveryManager(config, logger);
        CheckpointManager checkpointManager = null;
        CrashRawManager raw = null;
        WorldRestorer worldRestorer = null;
        ConsistencyManager consistencyManager = null;
        bootError = initializationError;
        try {
            if (bootError != null) throw new IOException(bootError);
            checkpointManager =
                    new CheckpointManager(
                            config, internal, states, logger, runtime, playerSnapshots);
            raw = new CrashRawManager(internal.crashRawDir(), config, logger);
            // Share the manager's store so retention/commit and restore verification see one
            // instance of the checkpoint directory instead of two independent views of it.
            worldRestorer =
                    new WorldRestorer(
                            checkpointManager.store(),
                            config,
                            logger,
                            internal.internalRoot(),
                            WorldLocator.resolve(internal.gameDir()));
            internal.beginRun(previousRun);
            consistencyManager = new ConsistencyManager(config, internal, logger);
        } catch (Exception exception) {
            if (bootError == null) bootError = exception.toString();
            logger.fatalKey("anticrash.init_internal_failed", exception.getMessage());
        }
        checkpoints = checkpointManager;
        crashRaw = raw;
        restorer = worldRestorer;
        consistency = consistencyManager;
        CarpetIntegration.initialize(logger);
        bootstrapRecovery();
        logger.normalKey("anticrash.initialized", AntiCrashMod.VERSION);
    }

    private void bootstrapRecovery() {
        if (bootError != null || checkpoints == null || restorer == null || crashRaw == null) {
            // Do not overwrite unreadable evidence and never mutate shared state without the
            // process lock.  The in-memory state still freezes the server fail-closed.
            states.setTransient(AntiCrashState.RECOVERY_FAILED);
            return;
        }
        RunContext context = new RunContext(previousRun, internal.recoveryState());
        // beginRun has written the new marker, so classify using the marker read above.
        boolean dirty = context.previous != null && !context.previous.cleanShutdown;
        CrashCause previousCause =
                dirty ? CrashDetector.classify(internal.gameDir(), context.previous) : null;
        if (dirty) internal.markPreviousDirtyCause(previousCause.name());
        try {
            if (context.recovery.pendingRestoreKind != null) {
                handlePendingRestore(context.recovery);
            } else if (dirty) {
                handleDirtyExit(context.previous, previousCause, context.recovery);
            } else if (context.previous != null && isUnfinishedRestoredRecovery(context.recovery)) {
                resumeUnfinishedRestore(context.recovery);
            } else if (context.previous != null
                    && AntiCrashState.RECOVERY_DECISION.name().equals(context.recovery.state)
                    && context.recovery.decisionDirty) {
                // /stop while a dirty-exit decision was pending. Vanilla saved the untouched crash
                // world, so a clean marker must not turn it into a normal start without the
                // operator's answer.
                logger.importantKey("recovery.resume_unfinished_decision");
                handleDirtyExit(context.previous, CrashCause.UNKNOWN_DIRTY_EXIT, context.recovery);
            } else if (context.previous != null && config.recovery.policy.equalsIgnoreCase("ASK")) {
                // ASK asks after every exit. After a normal stop the question is whether to
                // continue from the stop-time checkpoint (machinery keeps running) or to start
                // the saved world as Vanilla would; no answer means the latter.
                handleCleanExitAsk(context.previous, context.recovery);
            } else {
                // AUTO and MANUAL start a cleanly stopped world without a question; a checkpoint
                // can still be restored with the explicit pending restore command.
                clearRecoveryForNormalBoot();
            }
        } catch (Exception exception) {
            bootError = exception.toString();
            logger.fatalKey("recovery.preparation_failed", exception.getMessage());
            states.set(AntiCrashState.RECOVERY_FAILED);
            internal.updateRecovery(data -> data.lastError = exception.toString());
        }
    }

    /**
     * A checkpoint was restored before the world loaded, but the server stopped cleanly before
     * {@code /ac start}. Vanilla's final save wrote the frozen restored world, yet the Runtime
     * Capsule (block events, scheduled-tick order, moving pistons, FakePlayers) lives only in
     * memory and was lost. Treat the recovery as still in progress and restore it again.
     */
    static boolean isUnfinishedRestoredRecovery(RecoveryStateData recovery) {
        if (recovery == null
                || !recovery.preWorldRestoreDone
                || recovery.selectedCheckpointId == null) {
            return false;
        }
        String state = recovery.state;
        return AntiCrashState.RESTORING.name().equals(state)
                || AntiCrashState.WAITING_FOR_CHUNKS.name().equals(state)
                || AntiCrashState.RESTORING_FAKEPLAYERS.name().equals(state)
                || AntiCrashState.READY_TO_START.name().equals(state)
                || AntiCrashState.RECOVERY_FROZEN.name().equals(state)
                || AntiCrashState.STARTING.name().equals(state);
        // RECOVERY_FAILED is deliberately excluded: retrying a failed restore on every boot would
        // leave no way out, while a clean stop after a failure is an explicit operator decision.
    }

    private void resumeUnfinishedRestore(RecoveryStateData recovery) throws IOException {
        String checkpointId = recovery.selectedCheckpointId;
        logger.importantKey("recovery.resume_unfinished_restore", checkpointId);
        internal.updateRecoveryOrThrow(data -> {
            data.pendingRestoreKind = "CHECKPOINT";
            data.pendingRestorePath = checkpointId;
        });
        handlePendingRestore(internal.recoveryState());
    }

    private void handlePendingRestore(RecoveryStateData recovery) throws IOException {
        String kind = recovery.pendingRestoreKind;
        if ("CHECKPOINT".equals(kind)) {
            Path checkpoint = checkpoints.find(recovery.pendingRestorePath);
            if (checkpoint == null)
                throw new IOException(
                        "Pending checkpoint does not exist: " + recovery.pendingRestorePath);
            transitionOrThrow(AntiCrashState.PREBOOT_VERIFY);
            // A normal/manual restore has no dirty-exit crash-raw yet. Preserve the exact world
            // that is about to be replaced so a failed restore or /ac recovery abort is
            // recoverable. Dirty ASK/AUTO already populated this field before scheduling.
            if (recovery.crashRawPath == null && previousRun != null) preserveCrashRaw(previousRun);
            transitionOrThrow(AntiCrashState.PREBOOT_RESTORE);
            CheckpointManifest restoredManifest =
                    restorer.restoreCheckpoint(
                            checkpoint, WorldLocator.resolve(internal.gameDir()));
            checkpoints.useVerifiedAsIncrementalBase(checkpoint, restoredManifest);
            selectedCheckpointId = checkpoint.getFileName().toString();
            worldRestored = true;
            internal.updateRecoveryOrThrow(data -> {
                data.pendingRestoreKind = null;
                data.pendingRestorePath = null;
                data.activeCheckpointId = selectedCheckpointId;
                data.selectedCheckpointId = selectedCheckpointId;
                data.preWorldRestoreDone = true;
                data.decisionDirty = false;
                data.lastError = null;
            });
            restartCoordinator.completeHandoff();
            String crashRawId = internal.recoveryState().crashRawPath;
            startupPlan =
                    StartupRecoveryPlan.restoreCheckpoint(
                            previousExit(), selectedCheckpointId, true, crashRawId);
            transitionOrThrow(AntiCrashState.RESTORING);
            logger.importantKey("recovery.pending_checkpoint_restored", selectedCheckpointId);
        } else if ("RAW".equals(kind)) {
            String rawId = recovery.pendingRestorePath;
            Path raw = crashRaw.find(rawId);
            if (raw == null)
                throw new IOException(
                        "Pending crash-raw world does not exist: " + recovery.pendingRestorePath);
            transitionOrThrow(AntiCrashState.PREBOOT_RESTORE);
            restorer.restoreCrashRaw(raw, WorldLocator.resolve(internal.gameDir()));
            rawWorldRestored = true;
            internal.updateRecoveryOrThrow(data -> {
                data.pendingRestoreKind = null;
                data.pendingRestorePath = null;
                data.selectedCheckpointId = null;
                data.preWorldRestoreDone = true;
                data.decisionDirty = false;
                data.lastError = null;
            });
            restartCoordinator.completeHandoff();
            startupPlan = StartupRecoveryPlan.restoreCrashRaw(previousExit(), rawId);
            transitionOrThrow(AntiCrashState.RESTORING);
            logger.importantKey("recovery.pending_crashraw_restored");
        } else {
            throw new IOException("Unknown pending restore kind: " + kind);
        }
    }

    private void handleDirtyExit(
            com.atsukimc.anticrash.core.RunMarker previous,
            CrashCause cause,
            RecoveryStateData recovery)
            throws IOException {
        transitionOrThrow(AntiCrashState.PREBOOT_INSPECT);
        logger.importantKey("recovery.previous_session", cause);
        if (config.recovery.policy.equalsIgnoreCase("AUTO")) {
            if (!config.recovery.restoreLatestValidCheckpoint) {
                // AUTO with this switch disabled is an explicit operator choice to keep the
                // current crash-state world.  It must not accidentally fall through to the ASK
                // input path, because AUTO never asks a pre-world console question.
                logger.importantKey("recovery.auto_restore_disabled");
                clearRecoveryForNormalBoot(StartupRecoveryPlan.from(cause));
                return;
            }
            List<Path> candidates = orderedRecoveryCandidates(recovery.activeCheckpointId);
            if (candidates.isEmpty()) {
                failNoValidCheckpoint();
                return;
            }
            // Candidate selection is cheap; only after the policy has committed to restore do we
            // preserve the crash world and perform the expensive authenticated verification.
            restoreCheckpointBeforeWorld(
                    previous, cause, recovery, candidates.get(0).getFileName().toString());
            return;
        }
        // ASK/MANUAL intentionally inspect only metadata before asking.  A corrupt candidate is
        // allowed to be displayed as "unverified"; its payload is authenticated only after Y.
        long candidateDiscoveryStarted = System.nanoTime();
        CheckpointCandidate candidate =
                checkpoints.discoverLatestCandidate(recovery.activeCheckpointId);
        logStartupPhase("startup.candidate_discovery", candidateDiscoveryStarted);
        if (candidate == null) {
            if (config.recovery.policy.equalsIgnoreCase("ASK")) {
                logger.importantKey("recovery.ask_no_checkpoint");
                clearRecoveryForNormalBoot(StartupRecoveryPlan.from(cause));
            } else {
                failNoValidCheckpoint();
            }
            return;
        }
        selectedCheckpointId = candidate.id();
        enterRecoveryDecision(selectedCheckpointId, true);
        awaitPreWorldRecoveryDecision(previous, cause, recovery);
    }

    /**
     * ASK after a normal stop. Nothing is wrong with the world, so the default is to start it as it
     * is: only an explicit Y restores, and a missing console never blocks the start.
     */
    private void handleCleanExitAsk(
            com.atsukimc.anticrash.core.RunMarker previous, RecoveryStateData recovery)
            throws IOException {
        long candidateDiscoveryStarted = System.nanoTime();
        CheckpointCandidate candidate =
                checkpoints.discoverLatestCandidate(recovery.activeCheckpointId);
        logStartupPhase("startup.candidate_discovery", candidateDiscoveryStarted);
        if (candidate == null) {
            logger.normalKey("recovery.ask_no_checkpoint");
            clearRecoveryForNormalBoot();
            return;
        }
        transitionOrThrow(AntiCrashState.PREBOOT_INSPECT);
        enterRecoveryDecision(candidate.id(), false);
        long timeoutSeconds = Math.max(0L, config.recovery.askTimeoutSeconds);
        String timeoutText =
                timeoutSeconds == 0
                        ? MessageCatalog.text(config.language, "recovery.preworld_no_timeout")
                        : MessageCatalog.text(
                                config.language, "recovery.preworld_timeout", timeoutSeconds, "N");
        String exit = MessageCatalog.text(config.language, "recovery.exit_clean");
        String prompt =
                MessageCatalog.text(
                        config.language,
                        "recovery.preworld_ask_prompt_clean",
                        exit,
                        selectedCheckpointId,
                        timeoutText);
        logger.importantKey("recovery.preworld_ask_deferred", selectedCheckpointId, exit);
        long decisionWaitStarted = System.nanoTime();
        PreWorldDecisionInput.Answer answer =
                PreWorldDecisionInput.await(
                        prompt, safeMillis(timeoutSeconds), PreWorldDecisionInput.Answer.TIMEOUT);
        logStartupPhase("startup.decision_wait", decisionWaitStarted);

        if (answer == PreWorldDecisionInput.Answer.RESTORE) {
            restoreCheckpointBeforeWorld(
                    previous,
                    StartupRecoveryPlan.PreviousExit.CLEAN,
                    recovery,
                    selectedCheckpointId);
            return;
        }
        if (answer == PreWorldDecisionInput.Answer.CONTINUE) {
            logger.importantKey("recovery.preworld_continue");
        } else if (answer == PreWorldDecisionInput.Answer.TIMEOUT) {
            logger.importantKey("recovery.preworld_timeout_clean", timeoutSeconds);
        } else {
            logger.normalKey("recovery.preworld_input_unavailable_clean");
        }
        clearRecoveryForNormalBoot();
    }

    private void awaitPreWorldRecoveryDecision(
            com.atsukimc.anticrash.core.RunMarker previous,
            CrashCause cause,
            RecoveryStateData recovery)
            throws IOException {
        long timeoutSeconds = Math.max(0L, config.recovery.askTimeoutSeconds);
        String timeoutText =
                timeoutSeconds == 0
                        ? MessageCatalog.text(config.language, "recovery.preworld_no_timeout")
                        : MessageCatalog.text(
                                config.language, "recovery.preworld_timeout", timeoutSeconds, "Y");
        String prompt =
                MessageCatalog.text(
                        config.language,
                        "recovery.preworld_ask_prompt",
                        cause,
                        selectedCheckpointId,
                        timeoutText);
        logger.importantKey("recovery.preworld_ask_deferred", selectedCheckpointId, cause);
        long decisionWaitStarted = System.nanoTime();
        PreWorldDecisionInput.Answer answer =
                PreWorldDecisionInput.await(
                        prompt, safeMillis(timeoutSeconds), PreWorldDecisionInput.Answer.TIMEOUT);
        logStartupPhase("startup.decision_wait", decisionWaitStarted);

        if (answer == PreWorldDecisionInput.Answer.RESTORE) {
            restoreCheckpointBeforeWorld(previous, cause, recovery, selectedCheckpointId);
            return;
        }
        if (answer == PreWorldDecisionInput.Answer.CONTINUE) {
            logger.importantKey("recovery.preworld_continue");
            clearRecoveryForNormalBoot(StartupRecoveryPlan.from(cause));
            return;
        }
        if ("MANUAL".equalsIgnoreCase(config.recovery.policy)) {
            // MANUAL intentionally has no implicit rollback.  If there is no interactive input,
            // stop before World load rather than silently converting an unresolved decision into a
            // potentially destructive normal boot.
            failNoValidCheckpoint();
            return;
        }
        if (answer == PreWorldDecisionInput.Answer.TIMEOUT) {
            logger.importantKey("recovery.preworld_timeout_answer", timeoutSeconds, "Y");
        } else {
            logger.importantKey("recovery.preworld_input_unavailable", "Y");
        }
        restoreCheckpointBeforeWorld(previous, cause, recovery, selectedCheckpointId);
    }

    /**
     * Performs the expensive part of a restore only after the policy has selected it. The live
     * world is still unopened at this point, so a failed verification cannot leave a half-loaded
     * {@code RegionFile} view behind.
     */
    private void restoreCheckpointBeforeWorld(
            com.atsukimc.anticrash.core.RunMarker previous,
            CrashCause cause,
            RecoveryStateData recovery,
            String requestedCheckpointId)
            throws IOException {
        restoreCheckpointBeforeWorld(
                previous, StartupRecoveryPlan.from(cause), recovery, requestedCheckpointId);
    }

    private void restoreCheckpointBeforeWorld(
            com.atsukimc.anticrash.core.RunMarker previous,
            StartupRecoveryPlan.PreviousExit previousExit,
            RecoveryStateData recovery,
            String requestedCheckpointId)
            throws IOException {
        transitionOrThrow(AntiCrashState.PREBOOT_PRESERVE_RAW);
        if (recovery.crashRawPath == null || !crashRaw.contains(recovery.crashRawPath)) {
            // This is deliberately after Y/AUTO selection and immediately before any live-world
            // replacement.  N and ASK metadata discovery never create a crash-raw copy.
            long rawStarted = System.nanoTime();
            preserveCrashRaw(previous, previousExit == StartupRecoveryPlan.PreviousExit.CLEAN);
            logStartupPhase("startup.crash_raw", rawStarted);
        }

        transitionOrThrow(AntiCrashState.PREBOOT_VERIFY);
        List<Path> candidates = new ArrayList<>();
        Path requested = checkpoints.find(requestedCheckpointId);
        if (requested != null) candidates.add(requested);
        if (recoveryFallbackEnabled()) {
            for (Path candidate : orderedRecoveryCandidates(recovery.activeCheckpointId)) {
                if (!candidates.contains(candidate)) candidates.add(candidate);
            }
        }
        if (candidates.isEmpty())
            throw new IOException("Selected checkpoint does not exist: " + requestedCheckpointId);

        IOException last = null;
        for (Path candidate : candidates) {
            try {
                transitionOrThrow(AntiCrashState.PREBOOT_RESTORE);
                long restoreStarted = System.nanoTime();
                CheckpointManifest restoredManifest =
                        restorer.restoreCheckpoint(
                                candidate, WorldLocator.resolve(internal.gameDir()));
                logStartupPhase("startup.checkpoint_restore", restoreStarted);
                checkpoints.useVerifiedAsIncrementalBase(candidate, restoredManifest);
                selectedCheckpointId = candidate.getFileName().toString();
                worldRestored = true;
                internal.updateRecoveryOrThrow(data -> {
                    data.activeCheckpointId = selectedCheckpointId;
                    data.selectedCheckpointId = selectedCheckpointId;
                    data.preWorldRestoreDone = true;
                    data.decisionDirty = false;
                    data.lastError = null;
                });
                String crashRawId = internal.recoveryState().crashRawPath;
                startupPlan =
                        StartupRecoveryPlan.restoreCheckpoint(
                                previousExit, selectedCheckpointId, true, crashRawId);
                transitionOrThrow(AntiCrashState.RESTORING);
                logger.importantKey("recovery.preworld_restore_selected", selectedCheckpointId);
                return;
            } catch (IOException exception) {
                last = exception;
                logger.importantKey(
                        "recovery.checkpoint_restore_failed",
                        candidate.getFileName(),
                        exception.getMessage());
                if (!recoveryFallbackEnabled()) break;
            }
        }
        throw last == null ? new IOException("No checkpoint could be restored") : last;
    }

    private static long safeMillis(long seconds) {
        if (seconds <= 0) return 0L;
        return seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L;
    }

    private void logStartupPhase(String phase, long startedNanos) {
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedNanos);
        long elapsedMillis = (elapsedNanos + 999_999L) / 1_000_000L;
        logger.normalKey("startup.phase", phase, elapsedMillis);
    }

    private StartupRecoveryPlan.PreviousExit previousExit() {
        if (previousRun == null || previousRun.cleanShutdown) {
            return StartupRecoveryPlan.PreviousExit.CLEAN;
        }
        return StartupRecoveryPlan.from(CrashDetector.classify(internal.gameDir(), previousRun));
    }

    /**
     * Orders recovery candidates by the active timeline, not merely by the largest retained ID.
     * This prevents a deliberate restore to an older checkpoint from rolling forward again if the
     * server crashes before a fresh checkpoint is committed.
     */
    private List<Path> orderedRecoveryCandidates(String activeCheckpointId) throws IOException {
        List<Path> result = new ArrayList<>(checkpoints.timelineCandidates(activeCheckpointId));
        if (!recoveryFallbackEnabled() && !result.isEmpty()) return List.of(result.get(0));
        return List.copyOf(result);
    }

    private Path preferredValidCheckpoint(String activeCheckpointId) throws IOException {
        for (Path candidate : orderedRecoveryCandidates(activeCheckpointId)) {
            try {
                checkpoints.verifyAndUseAsIncrementalBase(candidate);
                return candidate;
            } catch (IOException exception) {
                logger.importantKey(
                        "checkpoint.invalid_ignored",
                        candidate.getFileName(),
                        exception.getMessage());
                if (!recoveryFallbackEnabled()) break;
            }
        }
        return null;
    }

    private boolean recoveryFallbackEnabled() {
        return config.recovery.allowRestoreOlderCheckpoint
                && config.recovery.fallbackToPreviousCheckpoint;
    }

    private void preserveCrashRaw(com.atsukimc.anticrash.core.RunMarker previous)
            throws IOException {
        preserveCrashRaw(previous, false);
    }

    /** Keeps the world that is about to be replaced, so `/ac recovery abort` can bring it back. */
    private void preserveCrashRaw(com.atsukimc.anticrash.core.RunMarker previous, boolean cleanExit)
            throws IOException {
        if (!config.snapshot.keepCrashRawWorld) return;
        logger.importantKey(
                cleanExit
                        ? "recovery.crashraw_preserve_started_clean"
                        : "recovery.crashraw_preserve_started");
        try {
            Path rawPath =
                    crashRaw.capture(WorldLocator.resolve(internal.gameDir()), previous.runId);
            String rawId = rawPath.getFileName().toString();
            internal.updateRecoveryOrThrow(data -> data.crashRawPath = rawId);
        } catch (Exception exception) {
            logger.importantKey("recovery.crashraw_preserve_failed", exception.getMessage());
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Could not preserve the dirty world", exception);
        }
    }

    private void failNoValidCheckpoint() {
        bootError = "No valid checkpoint is available";
        states.set(AntiCrashState.RECOVERY_FAILED);
        internal.updateRecovery(data -> data.lastError = bootError);
    }

    private void enterRecoveryDecision(String checkpointId, boolean dirty) throws IOException {
        recoveryDecisionWasDirty = dirty;
        selectedCheckpointId = checkpointId;
        internal.updateRecoveryOrThrow(data -> {
            data.selectedCheckpointId = selectedCheckpointId;
            data.preWorldRestoreDone = false;
            data.decisionDirty = dirty;
            data.lastError = null;
        });
        transitionOrThrow(AntiCrashState.PREBOOT_DECISION);
        // A dirty ASK/MANUAL decision is now completed before Minecraft creates ServerWorld.  The
        // flags remain for compatibility with older persisted states, but no post-world prompt is
        // scheduled here; doing so would already be too late to choose a disk restore safely.
        askDeadlineNanos = 0;
        askReminderNanos = 0;
        askPromptPending = false;
        if (checkpointId != null
                && !config.recovery.policy.equalsIgnoreCase("ASK")
                && !config.recovery.policy.equalsIgnoreCase("MANUAL")) {
            logger.importantKey(
                    "recovery.policy_requires_decision",
                    config.recovery.policy,
                    selectedCheckpointId);
        }
    }

    private void clearRecoveryForNormalBoot() throws IOException {
        clearRecoveryForNormalBoot(StartupRecoveryPlan.PreviousExit.CLEAN);
    }

    private void clearRecoveryForNormalBoot(StartupRecoveryPlan.PreviousExit previousExit)
            throws IOException {
        askDeadlineNanos = 0;
        askReminderNanos = 0;
        askPromptPending = false;
        recoveryDecisionWasDirty = false;
        selectedCheckpointId = null;
        worldRestored = false;
        runtimeRestored = false;
        rawWorldRestored = false;
        pendingFakeSnapshots = List.of();
        equivalenceExpectedCapsule = null;
        equivalenceExpectedFakePlayers = List.of();
        lastEquivalenceReport = null;
        equivalenceLogSignature = null;
        strictBlocked = false;
        transitionOrThrow(AntiCrashState.NORMAL);
        startupPlan = StartupRecoveryPlan.continueCurrentWorld(previousExit);
        internal.updateRecoveryOrThrow(data -> {
            data.selectedCheckpointId = null;
            data.preWorldRestoreDone = false;
            data.decisionDirty = false;
            data.crashRawPath = null;
            data.lastError = null;
        });
    }

    public void onServerStarted(MinecraftServer server) {
        if (this.server != null) return;
        this.server = server;
        finalCheckpointDone = false;
        if (worldLoadStartNanos != 0L) {
            logStartupPhase("startup.world_load", worldLoadStartNanos);
            worldLoadStartNanos = 0L;
        }
        if (bootError != null) {
            states.setTransient(AntiCrashState.RECOVERY_FAILED);
            return;
        }
        forceStartEligible = false;
        try {
            if (selectedCheckpointId != null && worldRestored) prepareRestoredServer(server);
            else if (rawWorldRestored) prepareRawRestoredServer();
            else if (states.get() == AntiCrashState.RECOVERY_DECISION) {
                // A fresh process cannot reach this branch: setupServer() rejects an unresolved
                // pre-world decision before Minecraft starts its console reader.  Keep the guard
                // for old persisted state, but never claim that a post-world ASK is safe.
                logger.importantKey("recovery.server_frozen");
            } else if (states.get() == AntiCrashState.RECOVERY_FAILED) {
            } else transitionOrThrow(AntiCrashState.RUNNING);
        } catch (Exception exception) {
            failRecoverySetup(exception);
        }
    }

    /** Called at loadWorld HEAD, after the pre-world plan but before ServerWorld construction. */
    public void onWorldLoadBeginning() {
        internal.markWorldOpened();
        worldLoadStartNanos = System.nanoTime();
    }

    private void prepareRestoredServer(MinecraftServer server) throws IOException {
        Path checkpoint = checkpoints.find(selectedCheckpointId);
        if (checkpoint == null) throw new IOException("Selected checkpoint is missing");
        CheckpointManifest manifest = checkpoints.verifyRecoveryPayloads(checkpoint);
        RuntimeCapsule capsule =
                AtomicFiles.readJson(
                        checkpoint.resolve("runtime-capsule.json"),
                        RuntimeCapsule.class,
                        MAX_RUNTIME_CAPSULE_BYTES);
        if (capsule == null) throw new IOException("Runtime capsule is missing");
        List<PlayerSnapshotData> savedPlayers =
                readList(checkpoint.resolve("players.json"), PlayerSnapshotData.class);
        List<FakePlayerSnapshot> savedFakePlayers =
                readList(checkpoint.resolve("fakeplayers.json"), FakePlayerSnapshot.class);
        RecoveryPayloadValidator.validate(manifest, capsule, savedPlayers, savedFakePlayers);
        // Keep a validated legacy capsule available for the diagnostic command even when STRICT
        // later rejects it for missing v6 equivalence fields. It is never used to start a world.
        equivalenceExpectedCapsule = capsule;
        equivalenceExpectedFakePlayers = List.copyOf(savedFakePlayers);
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
            List<String> missingStrict =
                    RuntimeCapsuleManager.strictMissingFields(capsule, config.recovery.strict);
            if (!missingStrict.isEmpty()) {
                throw new IOException(
                        "STRICT recovery requires Runtime Capsule v6 state: "
                                + String.join(", ", missingStrict));
            }
            validateStrictManifest(manifest, capsule);
            if (!config.fakePlayer.restoreBeforeUnfreeze && !savedFakePlayers.isEmpty()) {
                throw new IOException(
                        "STRICT recovery requires fakeplayer.restore_before_unfreeze=true");
            }
        }
        strictBlocked = false;
        lastEquivalenceReport = null;
        playerSnapshots.load(savedPlayers);
        CarpetIntegration.validateSnapshots(savedFakePlayers, config);
        transitionOrThrow(AntiCrashState.RESTORING);
        blockEventRestore = runtime.restore(server, capsule);
        if (blockEventRestore.failed > 0)
            throw new IOException("Block event restore failed: " + blockEventRestore.failed);
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)
                && !runtime.carpetRestoreSucceeded()
                && !config.recovery.strict.allowUnsupportedCarpetState) {
            throw new IOException(
                    "Carpet runtime restore failed: " + runtime.carpetRestoreDetail());
        }
        runtimeRestored = true;
        pendingRuntimeCapsule = capsule;
        recoveryDegraded.clear();
        permanentTicketWaitTicks = 0;
        permanentTicketsMissing = -1;
        permanentTicketStallTicks = 0;
        pendingFakeSnapshots = List.copyOf(savedFakePlayers);
        transitionOrThrow(AntiCrashState.WAITING_FOR_CHUNKS);
        // STRICT is defined by the checkpoint's measured execution domain. Do not even build the
        // legacy player/block-event anchor set here: apart from being unnecessary, doing so could
        // reject a valid exact plan because a heuristic anchor count happens to exceed the same
        // limit. OPTIMIZED keeps the historical anchor planner for low-memory best-effort restore.
        Set<ChunkRecoveryPlan.Anchor> anchors =
                "STRICT".equalsIgnoreCase(config.recovery.fidelity)
                        ? Set.of()
                        : recoveryChunkAnchors(
                                capsule,
                                savedPlayers,
                                savedFakePlayers,
                                config.recovery.maxPreloadChunks);
        chunks.begin(server, capsule, anchors);
        if (chunks.isComplete()) finishRestoredServerPreparation();
    }

    /**
     * The manifest is authenticated independently of the JSON capsule. In STRICT mode both records
     * must describe the same exact capture; otherwise an old or hand-edited manifest could make a
     * partial capsule look complete.
     */
    private static void validateStrictManifest(CheckpointManifest manifest, RuntimeCapsule capsule)
            throws IOException {
        if (manifest == null || !"STRICT".equalsIgnoreCase(manifest.fidelity)) {
            throw new IOException("STRICT recovery requires a STRICT checkpoint manifest");
        }
        if (capsule == null
                || capsule.expectedFingerprints == null
                || capsule.chunks == null
                || capsule.unsupportedRuntimeState == null
                || manifest.unsupportedRuntimeState == null) {
            throw new IOException("STRICT checkpoint manifest or Runtime Capsule state is missing");
        }
        String expectedDomainHash =
                capsule.expectedFingerprints.get(EquivalenceCategory.CHUNK_DOMAIN);
        if (expectedDomainHash == null || !expectedDomainHash.equals(manifest.chunkDomainHash)) {
            throw new IOException(
                    "STRICT checkpoint manifest chunk-domain fingerprint does not match");
        }
        int full =
                capsule.chunks.stream()
                        .mapToInt(com.atsukimc.anticrash.runtime.ChunkStateSnapshot::fullCount)
                        .sum();
        int block =
                capsule.chunks.stream()
                        .mapToInt(
                                com.atsukimc.anticrash.runtime.ChunkStateSnapshot
                                        ::blockTickingCount)
                        .sum();
        int forced =
                capsule.chunks.stream()
                        .mapToInt(com.atsukimc.anticrash.runtime.ChunkStateSnapshot::forcedCount)
                        .sum();
        if (manifest.fullChunkCount != full
                || manifest.blockTickingChunkCount != block
                || manifest.entityTickingChunkCount != capsule.entityTickingChunkCount()
                || manifest.forcedChunkCount != forced
                || manifest.blockEntityTickerCount != capsule.blockEntityTickerCount()
                || manifest.entityOrderCount != capsule.entityOrderCount()
                || manifest.torchBurnoutCount != capsule.redstoneTorchBurnoutCount()
                || manifest.ticketCount != capsule.ticketCount()
                || manifest.checkpointPlayerCount
                        != (capsule.checkpointPlayersCaptured
                                ? capsule.checkpointPlayers.size()
                                : 0)
                || !java.util.Objects.equals(
                        manifest.checkpointPlayerHash,
                        capsule.expectedFingerprints.get(EquivalenceCategory.PLAYERS))
                || !java.util.Objects.equals(
                        manifest.carpetCaptureStatus, capsule.carpetCaptureStatus)
                || !new HashSet<>(manifest.unsupportedRuntimeState)
                        .equals(new HashSet<>(capsule.unsupportedRuntimeState))) {
            throw new IOException("STRICT checkpoint manifest runtime counters do not match");
        }
    }

    /**
     * Returns the chunks that must be ready before the first restored tick. Ordinary loaded and
     * entity-ticking lists are runtime caches and are deliberately not used as a pre-start load
     * request in the default CRITICAL mode. Forced chunks, block-event targets and saved player
     * positions are the minimum set needed to create/apply the protected runtime state safely.
     */
    private Set<ChunkRecoveryPlan.Anchor> recoveryChunkAnchors(
            RuntimeCapsule capsule,
            List<PlayerSnapshotData> players,
            List<FakePlayerSnapshot> fakePlayers,
            int maxAnchors)
            throws IOException {
        Set<ChunkRecoveryPlan.Anchor> anchors = new LinkedHashSet<>();
        if (config.playerSnapshot.enabled
                && config.playerSnapshot.dimension
                && config.playerSnapshot.position) {
            for (PlayerSnapshotData player : players) {
                addRecoveryAnchor(anchors, player.dimension, player.x, player.z, maxAnchors);
            }
        }
        if (config.fakePlayer.enabled
                && config.fakePlayer.snapshot.dimension
                && config.fakePlayer.snapshot.position) {
            for (FakePlayerSnapshot player : fakePlayers) {
                addRecoveryAnchor(anchors, player.dimension, player.x, player.z, maxAnchors);
            }
        }
        if (capsule.blockEvents != null && capsule.blockEvents.entries != null) {
            for (BlockEventSnapshot.Entry event : capsule.blockEvents.entries) {
                addRecoveryAnchor(anchors, event.dimension, event.x, event.z, maxAnchors);
            }
        }
        return anchors;
    }

    private static void addRecoveryAnchor(
            Set<ChunkRecoveryPlan.Anchor> anchors,
            String dimension,
            double x,
            double z,
            int maxAnchors)
            throws IOException {
        if (dimension == null
                || dimension.isBlank()
                || !Double.isFinite(x)
                || !Double.isFinite(z)) {
            throw new IOException("Recovery chunk anchor is invalid");
        }
        long chunkX = (long) Math.floor(x / 16.0D);
        long chunkZ = (long) Math.floor(z / 16.0D);
        if (chunkX < Integer.MIN_VALUE
                || chunkX > Integer.MAX_VALUE
                || chunkZ < Integer.MIN_VALUE
                || chunkZ > Integer.MAX_VALUE) {
            throw new IOException(
                    "Recovery chunk anchor is outside the Minecraft coordinate range");
        }
        ChunkRecoveryPlan.Anchor anchor =
                new ChunkRecoveryPlan.Anchor(
                        dimension, new ChunkPos((int) chunkX, (int) chunkZ).toLong());
        if (!anchors.contains(anchor) && anchors.size() >= maxAnchors) {
            throw new IOException(
                    "Recovery chunk anchors exceed the configured preload limit of " + maxAnchors);
        }
        anchors.add(anchor);
    }

    private void continueRestoredServerPreparation() {
        try {
            chunks.poll();
            if (chunks.isComplete()) finishRestoredServerPreparation();
        } catch (Exception exception) {
            failRecoverySetup(exception);
        }
    }

    private void finishRestoredServerPreparation() throws IOException {
        if (!chunks.isReady())
            throw new IOException(
                    "Required chunks are not ready: "
                            + chunks.ready()
                            + "/"
                            + chunks.expected()
                            + ", failed="
                            + chunks.failed());
        if (pendingRuntimeCapsule == null)
            throw new IOException("Runtime capsule is no longer available");
        try {
            int[] inhabited = runtime.restoreInhabitedTimes(server, pendingRuntimeCapsule);
            if (inhabited[1] > 0)
                degradeRecovery(
                        "CHUNK_RUNTIME", inhabited[1] + " chunks of the checkpoint are not loaded");
            runtime.restoreMovingPistonTickOrder(server, pendingRuntimeCapsule);
            if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
                runtime.restoreBlockEntityTickerOrder(server, pendingRuntimeCapsule);
            }
            runtime.restoreScheduledTicks(server, pendingRuntimeCapsule);
            if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
                runtime.restoreRedstoneTorchBurnout(server, pendingRuntimeCapsule);
            } else {
                // The burnout history decides whether a torch that toggles again burns out. It is
                // captured in every checkpoint; OPTIMIZED used to drop it at restore.
                try {
                    runtime.restoreRedstoneTorchBurnout(server, pendingRuntimeCapsule);
                } catch (RuntimeException exception) {
                    degradeRecovery("REDSTONE_RUNTIME", String.valueOf(exception.getMessage()));
                }
            }
        } catch (IllegalStateException exception) {
            throw new IOException(
                    "Runtime ordering restore failed: " + exception.getMessage(), exception);
        }
        transitionOrThrow(AntiCrashState.RESTORING_FAKEPLAYERS);
        List<FakePlayerSnapshot> fakeSnapshots = pendingFakeSnapshots;
        if (config.fakePlayer.restoreBeforeUnfreeze) {
            fakePlayerRestore = CarpetIntegration.restore(server, fakeSnapshots, config, logger);
            if (config.fakePlayer.requireAllReadyBeforeStart && !fakePlayerRestore.allReady())
                throw new IOException("Not all FakePlayers are ready");
        }
        if (!config.fakePlayer.restoreBeforeUnfreeze) {
            fakePlayerRestore = CarpetIntegration.RestoreSummary.pending(fakeSnapshots.size());
        }
        pendingFakeSnapshots = List.of();
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
            refreshEquivalenceReport();
            if (lastEquivalenceReport == null || !lastEquivalenceReport.ready) {
                throw new IOException(
                        "STRICT recovery equivalence is not ready: "
                                + (lastEquivalenceReport == null
                                        ? "no report"
                                        : lastEquivalenceReport.blockingReason));
            }
        }
        if (consistency != null) consistency.onRecovery(selectedCheckpointId);
        transitionOrThrow(AntiCrashState.READY_TO_START);
        logger.normalKey(
                "recovery.ready",
                selectedCheckpointId,
                chunks.ready(),
                chunks.expected(),
                fakePlayerRestore.ready,
                fakePlayerRestore.expected,
                chunks.deferredLoaded(),
                chunks.deferredEntityTicking());
        if (!config.recovery.freezeAfterRestore || !config.recovery.requireManualStart) {
            transitionOrThrow(AntiCrashState.STARTING);
            pendingStart = true;
        }
    }

    private void failRecoverySetup(Exception exception) {
        pendingFakeSnapshots = List.of();
        chunks.abort();
        bootError = exception.toString();
        logger.fatalKey("recovery.setup_failed", exception.getMessage());
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
            strictBlocked = true;
            forceStartEligible = false;
            if (lastEquivalenceReport == null) {
                lastEquivalenceReport =
                        new RecoveryEquivalenceReport(selectedCheckpointId, "STRICT");
                String detail =
                        chunks.blockingReason() == null
                                ? safeDetail(exception)
                                : chunks.blockingReason();
                lastEquivalenceReport.add(
                        RecoveryEquivalenceReport.compare(
                                EquivalenceCategory.CHECKPOINT_INTEGRITY,
                                List.of("STRICT_RECOVERY_READY"),
                                List.of("BLOCKED: " + detail),
                                true));
                lastEquivalenceReport.finalizeResult(false);
            }
            // A strict mismatch is an expected recoverable operator state, not a generic failed
            // validation that can be bypassed by a stale --force setting. Keep the world frozen so
            // the operator can inspect/adjust configuration and start a fresh recovery attempt.
            states.set(AntiCrashState.RECOVERY_FROZEN);
        } else {
            forceStartEligible = worldRestored && !(exception instanceof StatePersistenceException);
            states.set(AntiCrashState.RECOVERY_FAILED);
        }
        internal.updateRecovery(data -> data.lastError = bootError);
    }

    private void prepareRawRestoredServer() throws IOException {
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
            throw new IOException(
                    "STRICT recovery requires a Runtime Capsule; crash-raw restore is not"
                            + " equivalent");
        }
        transitionOrThrow(AntiCrashState.READY_TO_START);
        if (!config.recovery.freezeAfterRestore || !config.recovery.requireManualStart) {
            transitionOrThrow(AntiCrashState.STARTING);
            pendingStart = true;
        }
    }

    private void transitionOrThrow(AntiCrashState state) throws StatePersistenceException {
        if (!states.set(state))
            throw new StatePersistenceException("Could not persist " + state + " state");
    }

    private <T> List<T> readList(Path path, Class<T> type) throws IOException {
        // Every committed checkpoint contains both list files, even when the list is empty.
        // Treat disappearance or a symlink swap after integrity verification as corruption,
        // rather than silently turning it into an authenticated empty payload.
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException(
                    "Required checkpoint payload is missing or unsafe: " + path.getFileName());
        }
        Type listType = TypeToken.getParameterized(List.class, type).getType();
        final List<T> value;
        try {
            value =
                    AtomicFiles.GSON.fromJson(
                            AtomicFiles.readText(path, MAX_PLAYER_SNAPSHOT_BYTES), listType);
        } catch (RuntimeException exception) {
            throw new IOException("Malformed checkpoint payload: " + path.getFileName(), exception);
        }
        if (value == null)
            throw new IOException("Checkpoint payload is not a JSON list: " + path.getFileName());
        return value;
    }

    public void onTickEnd(MinecraftServer server) {
        if (this.server != server) return;
        if (states.get() == AntiCrashState.WAITING_FOR_CHUNKS) {
            continueRestoredServerPreparation();
        }
        if (askPromptPending) {
            askPromptPending = false;
            if (states.get() == AntiCrashState.RECOVERY_DECISION
                    && config.recovery.policy.equalsIgnoreCase("ASK")) {
                announceAskPrompt();
            }
        }
        remindAskIfNeeded();
        resolveAskTimeout();
        if (pendingStart && states.get() == AntiCrashState.STARTING && !awaitPermanentTickets()) {
            pendingStart = false;
            if (states.set(AntiCrashState.RUNNING)) {
                chunks.armRelease();
                // The recovery is complete. Clearing the selection keeps a later /ac freeze and
                // clean stop from being mistaken for an unfinished restore on the next boot.
                internal.updateRecovery(data -> {
                    data.crashRawPath = null;
                    data.selectedCheckpointId = null;
                    data.preWorldRestoreDone = false;
                    data.decisionDirty = false;
                });
                logger.importantKey("recovery.started");
                String notice =
                        MessageCatalog.text(config.language, "recovery.player_resumed_notice");
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (!CarpetIntegration.isFakePlayer(player))
                        player.sendMessage(chat(notice, null), false);
                }
                restoreRuntimeOrderBeforeFirstTick();
                // The contract is needed only until the first resumed tick. Release the large
                // semantic capsule/reference lists promptly; retain the last report for status.
                pendingRuntimeCapsule = null;
                equivalenceExpectedCapsule = null;
                equivalenceExpectedFakePlayers = List.of();
            }
        }
        deliverDeferredPayloads();
        if (states.get() == AntiCrashState.RUNNING) chunks.tick(server);
        else if (states.isFrozen() && !isCheckpointFrozen()) pumpEntityLoading(server);
        if (checkpoints != null) checkpoints.onTickEnd(server);
        if (consistency != null && checkpoints != null) {
            consistency.setCheckpoint(checkpoints.lastCheckpointId());
            consistency.setTransactionCheckpoint(
                    checkpoints.transactionCheckpointId(),
                    checkpoints.transactionFallbackCheckpointId());
        }
        updateFrozenActionBar();
    }

    /**
     * ServerWorld#tick is cancelled during a recovery freeze, and with it the entity manager tick
     * that attaches entity sections read from disk. Attach them while frozen (nothing ticks) so the
     * first resumed tick sees the same entities as the checkpoint instead of adding them one tick
     * late.
     */
    /** Server ticks spent waiting for Vanilla to re-create its own tickets before the resume. */
    private int permanentTicketWaitTicks;

    private static final int MAX_PERMANENT_TICKET_WAIT_TICKS = 200;

    /**
     * Player tickets are added by Vanilla asynchronously, some ticks after a player is placed, and
     * only while the chunk manager is ticked. The world is frozen, so that is driven here. The
     * resume waits until every permanent ticket of the checkpoint exists again: only then do the
     * recovery tickets hold nothing that the checkpoint's own tickets would not hold.
     *
     * @return true while the resume should wait another server tick
     */
    private static final int PERMANENT_TICKET_STALL_TICKS = 20;

    private int permanentTicketsMissing = -1;
    private int permanentTicketStallTicks;

    private final com.atsukimc.anticrash.network.DeferredPayloads<
                    net.minecraft.server.network.ServerPlayNetworkHandler,
                    net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket>
            deferredPayloads = new com.atsukimc.anticrash.network.DeferredPayloads<>();

    /** Keeps a mod payload received during the recovery freeze; any thread. */
    public void deferCustomPayload(
            net.minecraft.server.network.ServerPlayNetworkHandler handler,
            net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket packet) {
        deferredPayloads.defer(handler, packet);
    }

    /**
     * Delivers what clients sent during the freeze once the world runs.
     *
     * <p>Each payload re-enters the packet handler the way it first arrived: on the connection's
     * network thread. Fabric API handles a custom payload only on that first, off-thread call and
     * ignores the repeat Minecraft schedules on the server thread, so a delivery made from the
     * server thread would silently do nothing for every Fabric channel. Using the connection's own
     * event loop also keeps the payloads in order with whatever the client sends next.
     */
    private void deliverDeferredPayloads() {
        if (deferredPayloads.isEmpty() || isRecoveryFrozen() || server == null) return;
        int delivered =
                deferredPayloads.deliver((handler, packet) -> {
                    if (handler.player == null || handler.player.isDisconnected()) return;
                    Runnable delivery =
                            () -> {
                                try {
                                    handler.onCustomPayload(packet);
                                } catch (net.minecraft.network.OffThreadException rescheduled) {
                                    // Minecraft moved the packet to the server thread, as it does
                                    // for every packet.
                                } catch (RuntimeException failure) {
                                    logger.verboseKey(
                                            "recovery.deferred_payload_failed",
                                            packet.getChannel(),
                                            String.valueOf(failure));
                                }
                            };
                    io.netty.channel.Channel channel =
                            ((com.atsukimc.anticrash.mixin.ClientConnectionAccessorMixin)
                                            handler.connection)
                                    .anticrash$getChannel();
                    if (channel != null && channel.isOpen()) channel.eventLoop().execute(delivery);
                    else payloadDelivery().execute(delivery);
                });
        int dropped = deferredPayloads.takeDropped();
        if (delivered > 0) logger.normalKey("recovery.deferred_payloads_delivered", delivered);
        if (dropped > 0) logger.importantKey("recovery.deferred_payloads_dropped", dropped);
    }

    private java.util.concurrent.ExecutorService payloadDeliveryExecutor;

    /** For a connection without a network channel; started only if one ever defers a payload. */
    private synchronized java.util.concurrent.ExecutorService payloadDelivery() {
        if (payloadDeliveryExecutor == null) {
            payloadDeliveryExecutor =
                    java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                        Thread thread = new Thread(runnable, "AntiCrash-Payload-Delivery");
                        thread.setDaemon(true);
                        return thread;
                    });
        }
        return payloadDeliveryExecutor;
    }

    private boolean awaitPermanentTickets() {
        com.atsukimc.anticrash.runtime.RuntimeCapsule capsule = pendingRuntimeCapsule;
        if (capsule == null || capsule.ticketLedger == null || server == null) return false;
        int missing =
                com.atsukimc.anticrash.runtime.RuntimeOrderRestorer.missingPermanentTickets(
                        com.atsukimc.anticrash.runtime.TicketLedgerSnapshot.capture(
                                server.getWorlds()),
                        capsule.ticketLedger);
        if (missing == 0) return false;
        if (permanentTicketWaitTicks++ >= MAX_PERMANENT_TICKET_WAIT_TICKS) return false;
        // The tickets still missing belong to someone who is not here (a player of the checkpoint
        // who did not reconnect). Waiting the full limit for them only delays the restart.
        if (missing != permanentTicketsMissing) {
            permanentTicketsMissing = missing;
            permanentTicketStallTicks = 0;
        } else if (++permanentTicketStallTicks >= PERMANENT_TICKET_STALL_TICKS) {
            return false;
        }
        for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
            // No chunk is ticked (second argument); this only advances tickets and chunk loading.
            world.getChunkManager().tick(() -> true, false);
        }
        return true;
    }

    private void pumpEntityLoading(MinecraftServer server) {
        for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
            try {
                ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                        .anticrash$getEntityManager()
                        .tick();
            } catch (RuntimeException exception) {
                logger.verboseKey(
                        "recovery.entity_load_pump_failed",
                        world.getRegistryKey().getValue(),
                        exception.getMessage());
            }
        }
    }

    /**
     * Explains an intentional world freeze on the action bar.
     *
     * <p>Both a checkpoint barrier and recovery stop the world on purpose. Without any feedback a
     * player only sees the server stop responding, so the current phase is shown while the freeze
     * lasts and cleared once simulation resumes. This never changes freeze behaviour itself.
     */
    private void updateFrozenActionBar() {
        if (server == null) return;
        AntiCrashState state = states.get();
        boolean frozen = config.notifications.frozenActionBar && states.isFrozen();
        if (!frozen) {
            frozenTicks = 0;
            if (actionBarVisible) {
                actionBarVisible = false;
                broadcastActionBar(Text.literal(""));
            }
            return;
        }
        frozenTicks++;
        boolean checkpointFreeze =
                state == AntiCrashState.CHECKPOINT_BARRIER || state == AntiCrashState.CHECKPOINTING;
        // Checkpoint creation is intentionally silent in the action bar.  Checkpoint barriers
        // are short and the action bar is a player-facing recovery/status channel; showing the
        // checkpoint text here caused a transient message during every scheduled checkpoint.
        // Clear a previous recovery message as well, so a state transition cannot leave stale
        // action-bar text visible while a checkpoint is being created.
        if (checkpointFreeze) {
            frozenTicks = 0;
            if (actionBarVisible) {
                actionBarVisible = false;
                broadcastActionBar(Text.literal(""));
            }
            return;
        }
        // The action bar fades on its own, so refresh it while the freeze continues.
        if (actionBarVisible && frozenTicks % 10 != 0) return;
        actionBarVisible = true;
        broadcastActionBar(frozenActionBarText(state));
    }

    private Text frozenActionBarText(AntiCrashState state) {
        return switch (state) {
            case CHECKPOINT_BARRIER, CHECKPOINTING ->
                    actionBar(Formatting.YELLOW, "actionbar.checkpoint");
            case WAITING_FOR_CHUNKS ->
                    actionBar(
                            Formatting.GOLD,
                            "actionbar.recovery_chunks",
                            chunks.ready(),
                            chunks.expected());
            case RESTORING_FAKEPLAYERS ->
                    actionBar(Formatting.GOLD, "actionbar.recovery_fakeplayers");
            case READY_TO_START, RECOVERY_FROZEN ->
                    actionBar(
                            Formatting.GREEN,
                            "actionbar.recovery_ready",
                            config.recovery.startCommand);
            case STARTING -> actionBar(Formatting.GREEN, "actionbar.starting");
            case RECOVERY_DECISION -> actionBar(Formatting.GOLD, "actionbar.recovery_decision");
            case RECOVERY_FAILED -> actionBar(Formatting.RED, "actionbar.recovery_failed");
            default -> actionBar(Formatting.GOLD, "actionbar.recovery_restoring");
        };
    }

    /**
     * Last step before the world moves again: the checkpoint's block-entity and entity tick order
     * and its portal tickets. Done here rather than when recovery became ready because FakePlayers
     * and players who joined meanwhile are entities too, and tickets age while frozen.
     */
    private void restoreRuntimeOrderBeforeFirstTick() {
        com.atsukimc.anticrash.runtime.RuntimeCapsule capsule = pendingRuntimeCapsule;
        if (capsule == null || server == null) return;
        boolean strict = "STRICT".equalsIgnoreCase(config.recovery.fidelity);
        try {
            java.util.Map<String, net.minecraft.server.world.ServerWorld> worlds =
                    com.atsukimc.anticrash.runtime.RuntimeOrderRestorer.worlds(server.getWorlds());
            int tickers = 0, tickersUnknown = 0, tickersMissing = 0;
            int entities = 0, entitiesUnknown = 0, entitiesMissing = 0;
            int absentPlayers = 0;
            // STRICT already restored both orders with its own all-or-nothing checks.
            for (java.util.Map.Entry<String, net.minecraft.server.world.ServerWorld> world :
                    strict
                            ? java.util.Map.<String, net.minecraft.server.world.ServerWorld>of()
                                    .entrySet()
                            : worlds.entrySet()) {
                java.util.List<com.atsukimc.anticrash.runtime.BlockEntityTickerSnapshot>
                        tickerOrder = new ArrayList<>();
                if (capsule.blockEntityTickerOrder != null) {
                    for (var snapshot : capsule.blockEntityTickerOrder) {
                        if (snapshot != null && world.getKey().equals(snapshot.dimension))
                            tickerOrder.add(snapshot);
                    }
                }
                var tickerResult =
                        com.atsukimc.anticrash.runtime.RuntimeOrderRestorer.blockEntityTickers(
                                world.getValue(), tickerOrder);
                if (tickerResult == null) {
                    degradeRecovery(
                            "BLOCK_ENTITY_TICKERS", world.getKey() + ": duplicate live ticker");
                } else {
                    tickers += tickerResult.restored();
                    tickersUnknown += tickerResult.unknownLive();
                    tickersMissing += tickerResult.missing();
                }
                java.util.List<com.atsukimc.anticrash.runtime.EntityRuntimeSnapshot> entityOrder =
                        new ArrayList<>();
                if (capsule.entityOrder != null) {
                    for (var snapshot : capsule.entityOrder) {
                        if (snapshot != null
                                && !snapshot.removed
                                && world.getKey().equals(snapshot.dimension)) {
                            // A player of the checkpoint who is not connected now is not an
                            // entity that went missing; FakePlayers are restored and reported
                            // on their own.
                            if ("minecraft:player".equals(snapshot.type)
                                    && !playerOnline(snapshot.uuid)) {
                                absentPlayers++;
                                continue;
                            }
                            entityOrder.add(snapshot);
                        }
                    }
                }
                var entityResult =
                        com.atsukimc.anticrash.runtime.RuntimeOrderRestorer.entities(
                                world.getValue(), entityOrder);
                if (entityResult == null) {
                    degradeRecovery("ENTITIES", world.getKey() + ": entity list is in use");
                } else {
                    entities += entityResult.restored();
                    entitiesUnknown += entityResult.unknownLive();
                    entitiesMissing += entityResult.missing();
                }
            }
            // Only a capsule that recorded the tickets' lifetimes can say which ones belong.
            int stray =
                    capsule.ticketLedger == null || capsule.formatVersion < 7
                            ? 0
                            : com.atsukimc.anticrash.runtime.RuntimeOrderRestorer
                                    .removeStrayExpiringTickets(worlds, capsule.ticketLedger);
            if (stray > 0) logger.verboseKey("runtime.stray_tickets_removed", stray);
            int expiring =
                    capsule.ticketLedger == null
                            ? 0
                            : com.atsukimc.anticrash.runtime.RuntimeOrderRestorer.expiringTickets(
                                    worlds, capsule.ticketLedger);
            if (!strict) {
                logger.normalKey(
                        "runtime.order_restored",
                        tickers,
                        tickersUnknown,
                        tickersMissing,
                        entities,
                        entitiesUnknown,
                        entitiesMissing,
                        expiring);
                // Something the checkpoint ticked is gone: the order of what is left is restored,
                // but the next tick is not the checkpoint's next tick.
                if (tickersMissing > 0)
                    degradeRecovery("BLOCK_ENTITY_TICKERS", tickersMissing + " missing");
                if (entitiesMissing > 0) degradeRecovery("ENTITIES", entitiesMissing + " missing");
                if (absentPlayers > 0)
                    logger.normalKey("runtime.players_not_connected", absentPlayers);
            }
            if (capsule.entityIdCounter != null) {
                // Entities created from now on get ids with the same remainder as they would have.
                java.util.concurrent.atomic.AtomicInteger ids =
                        com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin
                                .anticrash$currentId();
                ids.set(
                        com.atsukimc.anticrash.runtime.RuntimeExtensionSnapshot.nextEntityId(
                                ids.get(), capsule.entityIdCounter));
            }
            verifyPistonRuntime(capsule, worlds);
            // AntiCrash's own tickets held the chunks while they were prepared. Left in place for
            // the configured delay they keep chunks loaded that were about to unload, which is
            // not what the checkpoint's world would have done.
            if (capsule.ticketLedger != null
                    && com.atsukimc.anticrash.runtime.RuntimeOrderRestorer.permanentTicketsCovered(
                            server.getWorlds(), capsule.ticketLedger)) {
                chunks.releaseNow(server);
                logger.normalKey("chunk.recovery_tickets_released_at_start");
            } else {
                degradeRecovery(
                        "TICKET_RUNTIME",
                        MessageCatalog.text(
                                config.language,
                                "chunk.recovery_tickets_kept",
                                config.recovery.temporaryTicketReleaseTicks));
            }
            // After the release, so the chunk domain is checked without AntiCrash's own tickets.
            verifyRuntimeCategories(capsule);
        } catch (RuntimeException exception) {
            // The world resumes with Minecraft's own order, which is what happened before this
            // step existed, but the operator must know the restore was not complete.
            degradeRecovery("RUNTIME_ORDER", String.valueOf(exception));
        }
    }

    /**
     * Last check before the first tick, after the order restore and after the recovery tickets are
     * gone: the state is captured again exactly as at the checkpoint cut and compared category by
     * category. A category the checkpoint did not record (an older capsule) is reported as such and
     * never counted as a match.
     */
    private void verifyRuntimeCategories(com.atsukimc.anticrash.runtime.RuntimeCapsule expected) {
        for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
            // Apply the ticket changes to the chunk holders without ageing any ticket.
            world.getChunkManager()
                    .threadedAnvilChunkStorage
                    .getTicketManager()
                    .tick(world.getChunkManager().threadedAnvilChunkStorage);
        }
        com.atsukimc.anticrash.runtime.RuntimeCapsule actual =
                runtime.captureCurrentForEquivalence(server);
        java.util.List<String> categories =
                new ArrayList<>(
                        java.util.List.of(
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.CHUNK_DOMAIN,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.SCHEDULED_TICKS,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.BLOCK_EVENTS,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.MOVING_PISTONS,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory
                                        .BLOCK_ENTITY_TICKERS,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.REDSTONE_RUNTIME,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.WORLD_RUNTIME));
        categories.addAll(com.atsukimc.anticrash.runtime.RuntimeExtensionSnapshot.CATEGORIES);
        StringBuilder summary = new StringBuilder();
        for (String category : categories) {
            String want = expected.expectedFingerprints.get(category);
            String have = actual.expectedFingerprints.get(category);
            Integer wantCount = expected.runtimeExtensionCounts.get(category);
            Integer haveCount = actual.runtimeExtensionCounts.get(category);
            String result =
                    com.atsukimc.anticrash.runtime.RuntimeExtensionSnapshot.verdict(
                            want, have, wantCount, haveCount);
            if (result.startsWith("MISMATCH")) {
                // Name what differs: "MISMATCH" alone cannot be acted on or investigated.
                String detail =
                        com.atsukimc.anticrash.runtime.RuntimeDiff.describe(
                                com.atsukimc.anticrash.runtime.RuntimeExtensionSnapshot
                                        .diagnosticLines(expected, category),
                                com.atsukimc.anticrash.runtime.RuntimeExtensionSnapshot
                                        .diagnosticLines(actual, category),
                                3);
                if (detail.length() > 700) detail = detail.substring(0, 700) + "...";
                degradeRecovery(category, detail.isEmpty() ? result : result + " (" + detail + ")");
            }
            if (summary.length() > 0) summary.append(", ");
            summary.append(category).append('=').append(result);
        }
        summary.append(", PISTON_RUNTIME=").append(lastPistonVerification);
        String fakePlayers = verifyFakePlayerUse();
        if (fakePlayers.startsWith("MISMATCH"))
            degradeRecovery("FAKEPLAYER_USE_RUNTIME", fakePlayers);
        summary.append(", FAKEPLAYER_USE_RUNTIME=").append(fakePlayers);
        String carpetRules = verifyCarpetRules(expected);
        if (carpetRules.startsWith("MISMATCH")) degradeRecovery("CARPET_RULES", carpetRules);
        summary.append(", CARPET_RULES=").append(carpetRules);
        // An open container (a trapped chest's signal, a shulker box's lid) belongs to a session
        // that cannot be reopened for the player; the checkpoint says so instead of guessing.
        boolean sessions =
                expected.unsupportedRuntimeState != null
                        && expected.unsupportedRuntimeState.contains("CONTAINER_SESSION");
        summary.append(", CONTAINER_SESSION=")
                .append(
                        sessions
                                ? "UNSUPPORTED " + expected.containerSessions.size()
                                : "NOT_APPLICABLE");
        if (sessions)
            degradeRecovery("CONTAINER_SESSION", "UNSUPPORTED " + expected.containerSessions);
        lastRuntimeVerification = summary.toString();
        logger.normalKey("runtime.categories_verified", lastRuntimeVerification);
    }

    private boolean playerOnline(String uuid) {
        try {
            return uuid != null
                    && server.getPlayerManager().getPlayer(java.util.UUID.fromString(uuid)) != null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private String lastRuntimeVerification = "";
    private String lastPistonVerification = "NOT_APPLICABLE";

    /** Compares Carpet's rule values with the checkpoint's. */
    private static String verifyCarpetRules(
            com.atsukimc.anticrash.runtime.RuntimeCapsule expected) {
        if (!CarpetIntegration.isInstalled()) return "NOT_APPLICABLE";
        if (expected.carpetRules == null) return "MISSING_FROM_OLD_FORMAT";
        java.util.Map<String, java.util.Map<String, String>> now =
                com.atsukimc.anticrash.integration.carpet.CarpetRules.capture();
        if (now == null) return "INCOMPLETE";
        int count =
                com.atsukimc.anticrash.integration.carpet.CarpetRules.count(expected.carpetRules);
        if (expected.carpetRules.equals(now)) return "MATCH " + count + "/" + count;
        java.util.List<String> want = new ArrayList<>(), have = new ArrayList<>();
        expected.carpetRules.forEach((manager, rules) ->
                rules.forEach((rule, value) -> want.add(manager + ":" + rule + "=" + value)));
        now.forEach((manager, rules) ->
                rules.forEach((rule, value) -> have.add(manager + ":" + rule + "=" + value)));
        return "MISMATCH "
                + have.size()
                + "/"
                + count
                + " ("
                + com.atsukimc.anticrash.runtime.RuntimeDiff.describe(want, have, 3)
                + ")";
    }

    /** Compares what each restored FakePlayer is doing with an item against the checkpoint. */
    private String verifyFakePlayerUse() {
        java.util.List<FakePlayerSnapshot> expected = equivalenceExpectedFakePlayers;
        if (expected == null || expected.isEmpty()) return "NOT_APPLICABLE";
        java.util.Map<String, String> live = new java.util.HashMap<>();
        try {
            for (FakePlayerSnapshot snapshot :
                    CarpetIntegration.captureForVerification(server, config, logger)) {
                if (snapshot != null && snapshot.uuid != null)
                    live.put(snapshot.uuid, snapshot.useRuntimeCanonical());
            }
        } catch (IOException | RuntimeException exception) {
            return "INCOMPLETE";
        }
        int total = 0, match = 0, unrecorded = 0;
        for (FakePlayerSnapshot snapshot : expected) {
            if (snapshot == null) continue;
            total++;
            String want = snapshot.useRuntimeCanonical();
            if (want == null) unrecorded++;
            else if (want.equals(live.get(snapshot.uuid))) match++;
        }
        if (unrecorded == total) return "MISSING_FROM_OLD_FORMAT";
        return (match + unrecorded == total ? "MATCH " : "MISMATCH ")
                + match
                + "/"
                + (total - unrecorded);
    }

    /** Result of the last pre-resume verification, one "CATEGORY=RESULT" per category. */
    public String runtimeVerificationSummary() {
        return lastRuntimeVerification;
    }

    /** Compares every moving piston with the progress the checkpoint recorded for it. */
    private void verifyPistonRuntime(
            com.atsukimc.anticrash.runtime.RuntimeCapsule capsule,
            java.util.Map<String, net.minecraft.server.world.ServerWorld> worlds) {
        lastPistonVerification = "NOT_APPLICABLE";
        if (capsule.movingPistonTickOrder == null || capsule.movingPistonTickOrder.isEmpty())
            return;
        int total = 0, match = 0, unrecorded = 0;
        for (var snapshot : capsule.movingPistonTickOrder) {
            if (snapshot == null) continue;
            total++;
            if (snapshot.progress == null || snapshot.lastProgress == null) {
                unrecorded++;
                continue;
            }
            net.minecraft.server.world.ServerWorld world = worlds.get(snapshot.dimension);
            if (world != null
                    && com.atsukimc.anticrash.runtime.BlockEntityTickerSnapshot.loadedBlockEntity(
                                    world, snapshot.position())
                            instanceof net.minecraft.block.entity.PistonBlockEntity piston) {
                var accessor = (com.atsukimc.anticrash.mixin.PistonBlockEntityAccessorMixin) piston;
                if (accessor.anticrash$getProgress() == snapshot.progress
                        && accessor.anticrash$getLastProgress() == snapshot.lastProgress) match++;
            }
        }
        logger.normalKey("runtime.piston_verified", match, total, unrecorded);
        lastPistonVerification =
                unrecorded > 0
                        ? "MISSING_FROM_OLD_FORMAT " + unrecorded + "/" + total
                        : (match == total ? "MATCH " : "MISMATCH ") + match + "/" + total;
        if (unrecorded > 0) {
            degradeRecovery(
                    "PISTON_RUNTIME", "MISSING_FROM_OLD_FORMAT " + unrecorded + "/" + total);
        } else if (match != total) {
            degradeRecovery("PISTON_RUNTIME", "MISMATCH " + (total - match) + "/" + total);
        }
    }

    /**
     * Records that this recovery did not reach the checkpoint's exact state in one category. It
     * stays visible in /ac status until the next recovery; nothing is hidden behind a log line.
     */
    private void degradeRecovery(String category, String reason) {
        recoveryDegraded.put(category, reason);
        logger.importantKey("recovery.degraded", category, reason);
    }

    /** "CATEGORY: reason; ..." for the last recovery, or an empty string. */
    public String recoveryDegradedSummary() {
        StringBuilder summary = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : recoveryDegraded.entrySet()) {
            if (summary.length() > 0) summary.append("; ");
            summary.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return summary.toString();
    }

    /** A chat message coloured by {@link com.atsukimc.anticrash.logging.ChatStyle}. */
    public Text chat(String message, Formatting base) {
        return com.atsukimc.anticrash.logging.ChatStyle.text(
                message, base, config.logging.chatColor);
    }

    public AntiCrashLogger logger() {
        return logger;
    }

    private Text actionBar(Formatting color, String key, Object... arguments) {
        return Text.literal(MessageCatalog.text(config.language, key, arguments)).formatted(color);
    }

    private void broadcastActionBar(Text message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!CarpetIntegration.isFakePlayer(player)) player.sendMessage(message, true);
        }
    }

    private void announceAskPrompt() {
        long timeoutSeconds = config.recovery.askTimeoutSeconds;
        if (timeoutSeconds > 0) {
            long now = System.nanoTime();
            askDeadlineNanos = now + timeoutSeconds * 1_000_000_000L;
            askReminderNanos = timeoutSeconds > 10 ? askDeadlineNanos - 10_000_000_000L : 0;
            logger.importantKey(
                    "recovery.ask_prompt",
                    selectedCheckpointId,
                    timeoutSeconds,
                    recoveryDecisionWasDirty ? "Y" : "N",
                    askExitStatus());
            broadcastAskPrompt();
        } else {
            askDeadlineNanos = 0;
            askReminderNanos = 0;
            // No timeout means there is no default answer to announce, so only the checkpoint
            // ID and the previous exit status are rendered.
            logger.importantKey(
                    "recovery.ask_prompt_no_timeout", selectedCheckpointId, askExitStatus());
            broadcastAskPrompt();
        }
    }

    private String askExitStatus() {
        return MessageCatalog.text(
                config.language,
                recoveryDecisionWasDirty ? "recovery.exit_dirty" : "recovery.exit_clean");
    }

    private void remindAskIfNeeded() {
        if (askReminderNanos == 0 || states.get() != AntiCrashState.RECOVERY_DECISION) return;
        long now = System.nanoTime();
        if (now - askReminderNanos < 0) return;
        long remainingNanos = askDeadlineNanos - now;
        askReminderNanos = 0;
        if (remainingNanos <= 0) return;
        long remainingSeconds = Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L);
        logger.importantKey("recovery.ask_reminder", selectedCheckpointId, remainingSeconds);
        broadcastAskPrompt();
    }

    /**
     * The ASK question is otherwise console-only, so an operator who is already in the game sees a
     * frozen server without ever being told that it is waiting for their answer.
     */
    private void broadcastAskPrompt() {
        if (!config.notifications.askPromptInGame || server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!CarpetIntegration.isFakePlayer(player)) sendAskPrompt(player);
        }
    }

    /** Sends the ASK question to one player who is allowed to answer it. */
    private void sendAskPrompt(ServerPlayerEntity player) {
        if (selectedCheckpointId == null
                || !player.getCommandSource().hasPermissionLevel(config.commands.recovery)) {
            return;
        }
        long remaining = askRemainingSeconds();
        Text message =
                chat(
                        remaining < 0
                                ? MessageCatalog.text(
                                        config.language,
                                        "recovery.ask_prompt_ingame_no_timeout",
                                        selectedCheckpointId,
                                        askExitStatus())
                                : MessageCatalog.text(
                                        config.language,
                                        "recovery.ask_prompt_ingame",
                                        selectedCheckpointId,
                                        askExitStatus(),
                                        remaining),
                        Formatting.YELLOW);
        player.sendMessage(message, false);
    }

    /** Seconds left in the ASK window, or -1 when no answer window is running. */
    public long askRemainingSeconds() {
        if (askDeadlineNanos == 0 || states.get() != AntiCrashState.RECOVERY_DECISION) return -1L;
        long remaining = askDeadlineNanos - System.nanoTime();
        return remaining <= 0 ? 0L : (remaining + 999_999_999L) / 1_000_000_000L;
    }

    private void resolveAskTimeout() {
        if (askDeadlineNanos == 0
                || states.get() != AntiCrashState.RECOVERY_DECISION
                || System.nanoTime() - askDeadlineNanos < 0) return;
        askDeadlineNanos = 0;
        askReminderNanos = 0;
        if (recoveryDecisionWasDirty) {
            logger.importantKey(
                    "recovery.ask_timeout_dirty",
                    config.recovery.askTimeoutSeconds,
                    selectedCheckpointId);
            if (!answerRecovery(true)) {
                states.set(AntiCrashState.RECOVERY_FAILED);
                internal.updateRecovery(
                        data -> data.lastError = "ASK timeout restore could not be scheduled");
            }
        } else {
            logger.importantKey("recovery.ask_timeout_clean", config.recovery.askTimeoutSeconds);
            if (!answerRecovery(false)) {
                states.set(AntiCrashState.RECOVERY_FAILED);
                internal.updateRecovery(
                        data -> data.lastError = "ASK timeout continuation could not be applied");
            }
        }
    }

    /** Applies the explicit Y/N answer while the server is waiting in RECOVERY_DECISION. */
    public boolean answerRecovery(boolean restore) {
        if (states.get() != AntiCrashState.RECOVERY_DECISION || selectedCheckpointId == null)
            return false;
        String checkpointId = selectedCheckpointId;
        askDeadlineNanos = 0;
        askReminderNanos = 0;
        askPromptPending = false;
        if (restore) {
            if (!scheduleCheckpointRestore(checkpointId)) {
                states.set(AntiCrashState.RECOVERY_FAILED);
                internal.updateRecovery(
                        data -> data.lastError = "ASK restore could not be scheduled");
                return false;
            }
            logger.importantKey("recovery.ask_restore", checkpointId);
            return true;
        }
        boolean dirty = recoveryDecisionWasDirty;
        if (!continueRecovery()) return false;
        logger.importantKey(dirty ? "recovery.ask_continue_dirty" : "recovery.ask_continue_clean");
        return true;
    }

    /**
     * Cheap, thread-safe pre-check used by the network thread. The answer itself is applied by
     * {@link #answerRecoveryFromChat} on the server thread.
     */
    public boolean isRecoveryAnswerCandidate(ServerPlayerEntity player, String message) {
        if (player == null || message == null) return false;
        String answer = message.trim();
        return (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("N"))
                && states.get() == AntiCrashState.RECOVERY_DECISION
                && player.getCommandSource().hasPermissionLevel(config.commands.recovery);
    }

    /** Accepts a plain Y/N chat message from an operator during an ASK prompt. */
    public boolean answerRecoveryFromChat(ServerPlayerEntity player, String message) {
        if (player == null || message == null) return false;
        String answer = message.trim();
        if (!(answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("N"))) return false;
        if (!player.getCommandSource().hasPermissionLevel(config.commands.recovery)) return false;
        boolean restore = answer.equalsIgnoreCase("Y");
        String checkpointId = selectedCheckpointId;
        boolean accepted = answerRecovery(restore);
        if (accepted) {
            player.sendMessage(
                    chat(
                            restore
                                    ? MessageCatalog.text(
                                            config.language,
                                            "command.recovery.answer_restore",
                                            checkpointId)
                                    : MessageCatalog.text(
                                            config.language, "command.recovery.answer_continue"),
                            null),
                    false);
        }
        return accepted;
    }

    public void onPlayerConnected(ServerPlayerEntity player) {
        // A real player may join either while recovery is frozen or after /ac start. Apply the
        // selected checkpoint exactly once on that player's first post-restore login.
        if (worldRestored
                && states.get() != AntiCrashState.RECOVERY_FAILED
                && config.recovery.lock.allowLogin
                && playerSnapshots != null
                && !playerSnapshots.restoreOnJoin(player)) {
            failRecoverySetup(
                    new IOException(
                            "Real Player snapshot restore failed for " + player.getUuidAsString()));
        }
        if (CarpetIntegration.isFakePlayer(player)) return;
        if (states.canResumeWithStart()) {
            player.sendMessage(
                    chat(
                            MessageCatalog.text(
                                    config.language,
                                    "recovery.join_frozen_notice",
                                    config.recovery.startCommand),
                            Formatting.YELLOW),
                    false);
        } else if (states.get() == AntiCrashState.RECOVERY_DECISION
                && config.notifications.askPromptInGame) {
            // An operator joining a frozen server must be told what it is waiting for.
            sendAskPrompt(player);
        }
    }

    /** Classifies the stop request. It never delays or cancels Minecraft's own stop transition. */
    public void onStopRequested() {
        if (server == null) {
            return;
        }
        // Minecraft 1.18.2 invokes server.stop(true) from its JVM shutdown hook named
        // "Server Shutdown Thread". That hook also runs for SIGTERM (plain `kill`), so the
        // call must not be treated as an operator-requested clean stop. Only a stop initiated
        // on Minecraft's own server thread is trusted as /stop or a controlled AntiCrash stop.
        if (!isTrustedNormalShutdownCaller(Thread.currentThread(), server.getThread())) {
            normalShutdownRequested = false;
            if (checkpoints != null) checkpoints.cancelForShutdown();
            return;
        }
        normalShutdownRequested = true;
    }

    static boolean isTrustedNormalShutdownCaller(Thread caller, Thread serverThread) {
        return caller != null && serverThread != null && caller == serverThread;
    }

    public void onShutdownBeginning() {
        if (server == null
                || !normalShutdownRequested
                || finalCheckpointDone
                || checkpoints == null) {
            return;
        }
        finalCheckpointDone = true;
        if (!config.checkpoint.enabled
                || !config.checkpoint.createOnCleanShutdown
                || states.isRecovery()) {
            checkpoints.cancelForShutdown();
            return;
        }

        long timeoutMillis = config.checkpoint.shutdownTimeoutSeconds * 1_000L;
        if (timeoutMillis <= 0) {
            checkpoints.cancelForShutdown();
            return;
        }
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        boolean completed;
        if (checkpoints.isCapturing()) {
            completed = checkpoints.awaitCurrentJob(remainingMillis(deadline));
        } else {
            boolean accepted = checkpoints.captureFinal(server);
            completed = accepted && checkpoints.awaitCurrentJob(remainingMillis(deadline));
        }
        if (completed) {
            logger.normalKey("checkpoint.shutdown_complete");
        } else {
            logger.importantKey(
                    "checkpoint.shutdown_timeout", config.checkpoint.shutdownTimeoutSeconds);
            checkpoints.cancelForShutdown();
        }
    }

    private static long remainingMillis(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) return 0;
        return Math.max(1L, remainingNanos / 1_000_000L);
    }

    public void onShutdownCompleted() {
        // Captured before the internal state is torn down below. It is non-null only on the stop
        // that was requested in order to apply a restore, never on a later ordinary stop, because
        // the next startup clears the pending request as soon as it has replaced the world.
        String pendingRestore =
                internal.recoveryStateUnreadable()
                        ? null
                        : internal.recoveryState().pendingRestoreKind;
        if (normalShutdownRequested) {
            internal.markCleanShutdown("NORMAL_SHUTDOWN");
            normalShutdownRequested = false;
        }
        if (checkpoints != null) checkpoints.close();
        if (consistency != null) consistency.close();
        if (processLock != null)
            try {
                processLock.close();
            } catch (IOException exception) {
                logger.importantKey("state.process_lock_release_failed", exception.getMessage());
            }
        // Injected at the return of MinecraftServer.shutdown, so this is the last line AntiCrash
        // can emit before the process ends.
        if (pendingRestore != null) logger.importantKey("recovery.restore_ready_start_server");
    }

    public boolean isWorldFrozen() {
        return states.isFrozen();
    }

    /**
     * Frozen for recovery, where the configurable recovery lock applies. A checkpoint barrier is
     * excluded: it holds the server task queue instead, so player input waits rather than being
     * refused under rules meant for an operator-controlled recovery.
     */
    public boolean isRecoveryFrozen() {
        return states.isFrozen() && !isCheckpointFrozen();
    }

    public boolean isCheckpointFrozen() {
        AntiCrashState state = states.get();
        return state == AntiCrashState.CHECKPOINT_BARRIER || state == AntiCrashState.CHECKPOINTING;
    }

    /**
     * True while the server task queue must wait for a checkpoint. Player packets and queued
     * console commands are kept in order and run once the checkpoint has its cut, instead of being
     * refused, so no client ever sees an input silently undone.
     */
    public boolean holdsServerTasks() {
        return isCheckpointFrozen();
    }

    public boolean needsSpawnInfoFallback() {
        return states.isFrozen() || worldRestored;
    }

    /** True while AntiCrash itself refuses logins, independent of any Vanilla reason. */
    public boolean isLoginBlocked() {
        if (isCheckpointFrozen()) return true;
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)
                && isStrictDomainPreparationState(states.get())) return true;
        return isRecoveryFrozen() && !config.recovery.lock.allowLogin;
    }

    /**
     * A player login creates/changes Vanilla chunk tickets. In STRICT those tickets must not be
     * allowed to race with the base checkpoint domain, including a failed preparation that is
     * waiting for an operator decision. READY_TO_START is deliberately excluded: its start gate
     * performs the final domain comparison, so an explicitly allowed login is reported as a
     * concrete mismatch instead of being silently folded into the checkpoint contract.
     */
    static boolean isStrictDomainPreparationState(AntiCrashState state) {
        return state == AntiCrashState.DIRTY_EXIT_DETECTED
                || state == AntiCrashState.PREBOOT_INSPECT
                || state == AntiCrashState.PREBOOT_DECISION
                || state == AntiCrashState.PREBOOT_PRESERVE_RAW
                || state == AntiCrashState.PREBOOT_VERIFY
                || state == AntiCrashState.PREBOOT_RESTORE
                || state == AntiCrashState.RECOVERY_DECISION
                || state == AntiCrashState.RESTORING
                || state == AntiCrashState.RECOVERY_FROZEN
                || state == AntiCrashState.WAITING_FOR_CHUNKS
                || state == AntiCrashState.RESTORING_FAKEPLAYERS
                || state == AntiCrashState.STARTING
                || state == AntiCrashState.RECOVERY_FAILED;
    }

    /**
     * Decides how long a login that AntiCrash is refusing may be held and retried.
     *
     * <p>A checkpoint barrier is short, so disconnecting a player who happened to finish the
     * handshake inside it is pure noise. Holding the accept keeps the connection alive until the
     * freeze clears. The hold is bounded so a long recovery freeze still ends in the normal
     * refusal, and it never applies to Vanilla's own reasons.
     *
     * @param currentDeadlineNanos the deadline already assigned to this login, or 0 for none
     * @return the deadline to keep holding until, or 0 to let Vanilla decide now
     */
    public long loginHoldDeadlineNanos(long currentDeadlineNanos) {
        if (!isLoginBlocked()) return 0L;
        long seconds = config.login.retryTimeoutSeconds;
        if (seconds <= 0) return 0L;
        if (currentDeadlineNanos != 0L) return currentDeadlineNanos;
        logger.normalKey("login.hold_started", seconds);
        return System.nanoTime() + seconds * 1_000_000_000L;
    }

    public AntiCrashState state() {
        return states.get();
    }

    /**
     * Gate called at the head of DedicatedServer.setupServer(), before its console reader and
     * before Minecraft binds the network or invokes loadWorld(). A failed or unresolved pre-world
     * decision must terminate setup rather than opening a world in an ambiguous state.
     */
    public boolean allowWorldLoadBeforeServer() {
        if (bootError != null || startupPlan == null) return false;
        AntiCrashState state = states.get();
        return state == AntiCrashState.NORMAL || state == AntiCrashState.RESTORING;
    }

    public StartupRecoveryPlan startupRecoveryPlan() {
        return startupPlan;
    }

    /** Read-only access for diagnostics and the runtime qualification probe. */
    public RuntimeCapsuleManager runtimeCapsules() {
        return runtime;
    }

    public AntiCrashConfig config() {
        return config;
    }

    public String selectedCheckpointId() {
        return selectedCheckpointId;
    }

    /**
     * Re-captures the observable state used by STRICT immediately before an operator starts the
     * world. This is intentionally not a background cache: a login or Vanilla ticket transition
     * between preparation and /ac start must be visible to the gate.
     */
    private void refreshEquivalenceReport() {
        if (!"STRICT".equalsIgnoreCase(config.recovery.fidelity)) return;
        RecoveryEquivalenceReport report;
        if (equivalenceExpectedCapsule == null) {
            report = new RecoveryEquivalenceReport(selectedCheckpointId, config.recovery.fidelity);
            report.add(
                    RecoveryEquivalenceReport.missing(
                            EquivalenceCategory.CHECKPOINT_INTEGRITY,
                            true,
                            "No STRICT Runtime Capsule is selected"));
        } else if (server == null) {
            report = new RecoveryEquivalenceReport(selectedCheckpointId, config.recovery.fidelity);
            report.add(
                    RecoveryEquivalenceReport.missing(
                            EquivalenceCategory.CHECKPOINT_INTEGRITY,
                            true,
                            "Minecraft server is not available for equivalence verification"));
        } else {
            try {
                report =
                        runtime.compareEquivalence(
                                server,
                                equivalenceExpectedCapsule,
                                equivalenceExpectedFakePlayers,
                                config.recovery.strict,
                                selectedCheckpointId);
            } catch (RuntimeException exception) {
                report =
                        new RecoveryEquivalenceReport(
                                selectedCheckpointId, config.recovery.fidelity);
                report.add(
                        RecoveryEquivalenceReport.compare(
                                EquivalenceCategory.CHECKPOINT_INTEGRITY,
                                List.of("observable runtime state"),
                                List.of("verification failed: " + safeDetail(exception)),
                                true));
            }
        }
        report.finalizeResult(strictAllowedUnsupportedCategories());
        lastEquivalenceReport = report;
        String signature =
                report.ready
                        + "|"
                        + String.valueOf(report.blockingReason)
                        + "|"
                        + report.checks.stream()
                                .map(check -> check.category + "=" + check.status)
                                .reduce("", (left, right) -> left + ";" + right);
        if (!signature.equals(equivalenceLogSignature)) {
            equivalenceLogSignature = signature;
            logger.importantKey(
                    "equivalence.result",
                    selectedCheckpointId == null ? "none" : selectedCheckpointId,
                    report.ready ? "READY" : "NOT_READY",
                    report.blockingReason == null ? "none" : report.blockingReason);
        }
        if (!report.ready) {
            strictBlocked = true;
            // A post-preparation mismatch is a new recovery freeze, not a permission to bypass
            // the contract with --force. The next /ac start can retry only after the observable
            // state is corrected and verify reports READY.
            if (states.get() == AntiCrashState.READY_TO_START)
                states.set(AntiCrashState.RECOVERY_FROZEN);
        } else {
            strictBlocked = false;
        }
    }

    private Set<String> strictAllowedUnsupportedCategories() {
        Set<String> allowed = new java.util.LinkedHashSet<>();
        if (config.recovery.strict.allowUnsupportedRuntimeState) {
            allowed.add(EquivalenceCategory.WORLD_RNG);
            if (!config.recovery.strict.requireEntityOrder)
                allowed.add(EquivalenceCategory.ENTITIES);
            if (!config.recovery.strict.requireBlockEntityTickerOrder) {
                allowed.add(EquivalenceCategory.BLOCK_ENTITY_TICKERS);
            }
            if (!config.recovery.strict.requireTicketLedger) {
                allowed.add(EquivalenceCategory.TICKET_LEDGER);
            }
        }
        if (config.recovery.strict.allowUnsupportedCarpetState) {
            allowed.add(EquivalenceCategory.CARPET);
            allowed.add(EquivalenceCategory.FAKEPLAYERS);
        }
        return allowed;
    }

    /** Renders the current contract report for /ac recovery verify and /ac debug equivalence. */
    public String renderEquivalence(boolean verbose) {
        refreshEquivalenceReport();
        if (lastEquivalenceReport == null) {
            lastEquivalenceReport =
                    new RecoveryEquivalenceReport(selectedCheckpointId, config.recovery.fidelity);
            lastEquivalenceReport.add(
                    RecoveryEquivalenceReport.missing(
                            EquivalenceCategory.CHECKPOINT_INTEGRITY,
                            true,
                            "No recovery equivalence report is available"));
            lastEquivalenceReport.finalizeResult(false);
        }
        return lastEquivalenceReport.render(verbose, config.language);
    }

    public StatusSnapshot statusSnapshot() {
        RecoveryStateData persisted = internal.recoveryState();
        String active =
                persisted.activeCheckpointId == null ? "none" : persisted.activeCheckpointId;
        CheckpointManifest manifest = checkpoints == null ? null : checkpoints.lastManifest();
        String backend =
                manifest == null || manifest.snapshotBackend == null
                        ? "unknown"
                        : manifest.snapshotBackend;
        String integrity =
                bootError != null
                        ? "FAILED"
                        : (manifest != null || (selectedCheckpointId != null && worldRestored)
                                ? "OK"
                                : "PENDING");
        List<String> realPlayers =
                server == null
                        ? List.of()
                        : server.getPlayerManager().getPlayerList().stream()
                                .filter(player -> !CarpetIntegration.isFakePlayer(player))
                                .map(player -> player.getName().getString())
                                .toList();
        String world;
        try {
            world = WorldLocator.resolve(internal.gameDir()).toString();
        } catch (IOException exception) {
            world = "unavailable: " + exception.getMessage();
        }
        RecoveryEquivalenceReport equivalence = lastEquivalenceReport;
        String equivalenceStatus =
                equivalence == null ? "PENDING" : equivalence.ready ? "READY" : "NOT_READY";
        String blockingCategory =
                equivalence == null || equivalence.blockingReason == null
                        ? "none"
                        : equivalence.blockingReason.split("\\s+", 2)[0];
        String carpetCompleteness =
                equivalenceExpectedCapsule == null
                        ? "NOT_APPLICABLE"
                        : String.valueOf(equivalenceExpectedCapsule.carpetCaptureStatus);
        List<String> unsupported =
                equivalence == null
                        ? (equivalenceExpectedCapsule == null
                                ? List.of()
                                : equivalenceExpectedCapsule.unsupportedRuntimeState == null
                                        ? List.of()
                                        : List.copyOf(
                                                equivalenceExpectedCapsule.unsupportedRuntimeState))
                        : equivalence.unsupportedCategories();
        return new StatusSnapshot(
                states.get().name(),
                selectedCheckpointId == null ? "none" : selectedCheckpointId,
                active,
                states.isFrozen(),
                checkStartValidation(false),
                backend,
                integrity,
                chunks.ready(),
                chunks.expected(),
                chunks.failed(),
                blockEventRestore.restored,
                blockEventRestore.failed,
                fakePlayerRestore.ready,
                fakePlayerRestore.expected,
                fakePlayerRestore.failed,
                realPlayers,
                config.consistency.mode,
                world,
                bootError == null ? "none" : bootError,
                checkpoints == null ? -1L : checkpoints.secondsUntilNextCheckpoint(),
                askRemainingSeconds(),
                // "wrong state" is not a problem during normal running or a checkpoint barrier,
                // so the blocker line is only meaningful inside a recovery flow.
                states.isRecovery() ? startRejectionReason(false) : null,
                config.recovery.fidelity,
                chunks.expected(),
                chunks.ready(),
                chunks.currentConcurrentLoads(),
                chunks.pendingFutureCount(),
                equivalenceStatus,
                blockingCategory,
                carpetCompleteness,
                unsupported);
    }

    public boolean checkStartValidation(boolean force) {
        return startValidationReason(force) == null;
    }

    /**
     * Localized explanation of why start validation currently fails, or {@code null} when it
     * passes. {@link #checkStartValidation(boolean)} is derived from this so the boolean and the
     * operator-facing reason can never disagree.
     */
    private String startValidationReason(boolean force) {
        AntiCrashState state = states.get();
        if (state == AntiCrashState.RECOVERY_DECISION) return reason("start.reason.ask_pending");
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
            if (strictBlocked) return reason("start.reason.equivalence", equivalenceBlockerText());
            if (equivalenceExpectedCapsule != null
                    && (lastEquivalenceReport == null || !lastEquivalenceReport.ready)) {
                return reason("start.reason.equivalence", equivalenceBlockerText());
            }
        }
        if (state == AntiCrashState.RECOVERY_FAILED) {
            if (force && config.recovery.allowStartWithFailedValidation && forceStartEligible)
                return null;
            if (!config.recovery.allowStartWithFailedValidation)
                return reason("start.reason.force_disabled", bootErrorText());
            if (!force) return reason("start.reason.force_required", bootErrorText());
            return reason("start.reason.force_unavailable", bootErrorText());
        }
        if (force && config.recovery.allowStartWithFailedValidation) return null;
        if (selectedCheckpointId == null) return null;
        if (!worldRestored) return reason("start.reason.world_not_restored");
        if (!runtimeRestored) return reason("start.reason.runtime_not_restored");
        if (blockEventRestore.failed > 0)
            return reason("start.reason.block_events_failed", blockEventRestore.failed);
        if (!chunks.isReady())
            return reason(
                    "start.reason.chunks_not_ready",
                    chunks.ready(),
                    chunks.expected(),
                    chunks.failed());
        if (config.fakePlayer.requireAllReadyBeforeStart && !fakePlayerRestore.allReady())
            return reason(
                    "start.reason.fakeplayers_not_ready",
                    fakePlayerRestore.ready,
                    fakePlayerRestore.expected,
                    fakePlayerRestore.failed);
        return null;
    }

    /** Reason a start request would be refused right now, or {@code null} when it is accepted. */
    public String startRejectionReason(boolean force) {
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)
                && equivalenceExpectedCapsule != null) refreshEquivalenceReport();
        String validation = startValidationReason(force);
        if (validation != null) return validation;
        AntiCrashState state = states.get();
        boolean failedOverride =
                state == AntiCrashState.RECOVERY_FAILED
                        && force
                        && config.recovery.allowStartWithFailedValidation
                        && forceStartEligible;
        if (state != AntiCrashState.READY_TO_START
                && state != AntiCrashState.RECOVERY_FROZEN
                && !failedOverride) return reason("start.reason.wrong_state", state);
        return null;
    }

    private String reason(String key, Object... arguments) {
        return MessageCatalog.text(config.language, key, arguments);
    }

    private String bootErrorText() {
        return bootError == null
                ? MessageCatalog.text(config.language, "start.reason.no_detail")
                : bootError;
    }

    private String equivalenceBlockerText() {
        if (lastEquivalenceReport == null || lastEquivalenceReport.blockingReason == null) {
            return MessageCatalog.text(config.language, "start.reason.equivalence_pending");
        }
        return lastEquivalenceReport.blockingReason;
    }

    public boolean requestStart(boolean force) {
        if ("STRICT".equalsIgnoreCase(config.recovery.fidelity)
                && equivalenceExpectedCapsule != null) refreshEquivalenceReport();
        AntiCrashState state = states.get();
        boolean failedOverride =
                state == AntiCrashState.RECOVERY_FAILED
                        && force
                        && config.recovery.allowStartWithFailedValidation
                        && forceStartEligible;
        if (state != AntiCrashState.READY_TO_START
                && state != AntiCrashState.RECOVERY_FROZEN
                && !failedOverride) return false;
        if (!checkStartValidation(force) || !states.set(AntiCrashState.STARTING)) return false;
        pendingStart = true;
        return true;
    }

    public boolean freeze() {
        return states.get() == AntiCrashState.RUNNING && states.set(AntiCrashState.RECOVERY_FROZEN);
    }

    public boolean requestCheckpoint() {
        if (checkpoints == null
                || !config.checkpoint.enabled
                || !config.checkpoint.createOnManualCommand) return false;
        checkpoints.requestManual();
        return true;
    }

    public boolean requestBaseCheckpoint() {
        if (checkpoints == null
                || !config.checkpoint.enabled
                || !config.checkpoint.createOnManualCommand) return false;
        checkpoints.requestBase();
        return true;
    }

    public boolean requestCheckpointCompaction() {
        return checkpoints != null && checkpoints.requestCompaction();
    }

    public CarpetIntegration.RestoreSummary restoreFakePlayersNow() throws IOException {
        if (server == null) throw new IOException("Minecraft server is not ready");
        if (selectedCheckpointId == null)
            throw new IOException("No checkpoint is selected for recovery");
        Path checkpoint = checkpoints.find(selectedCheckpointId);
        if (checkpoint == null) throw new IOException("Selected checkpoint is missing");
        CheckpointManifest manifest = checkpoints.verifyRecoveryPayloads(checkpoint);
        List<FakePlayerSnapshot> saved =
                readList(checkpoint.resolve("fakeplayers.json"), FakePlayerSnapshot.class);
        if (saved.size() != manifest.fakePlayerCount) {
            throw new IOException(
                    "FakePlayer snapshot count does not match the checkpoint manifest");
        }
        CarpetIntegration.validateSnapshots(saved, config);
        fakePlayerRestore = CarpetIntegration.restore(server, saved, config, logger);
        return fakePlayerRestore;
    }

    public List<FakePlayerSnapshot> currentFakePlayers() {
        return server == null ? List.of() : CarpetIntegration.capture(server, config, logger);
    }

    public boolean scheduleCheckpointRestore(String id) {
        AntiCrashState previousState = states.get();
        try {
            if (server == null)
                throw new IOException("Minecraft server is not ready for controlled restart");
            boolean latest = "latest".equalsIgnoreCase(id);
            Path path = latest ? latestTimelineCheckpoint() : checkpoints.find(id);
            if (path == null) return false;
            // Authenticate READY/checksums/manifest and the small recovery payloads only. Hashing
            // the whole world here runs on the server thread and can exceed the watchdog's
            // max-tick-time on a large save; WorldRestorer re-verifies every world file before
            // anything is replaced anyway.
            if (!latest) checkpoints.verifyRecoveryPayloads(path);
            String checkpointId = path.getFileName().toString();
            internal.updateRecoveryOrThrow(data -> {
                data.pendingRestoreKind = "CHECKPOINT";
                data.pendingRestorePath = checkpointId;
                data.lastError = null;
            });
            restartCoordinator.arm(config.language, config.recovery.spawnRestartHelper);
            if (!states.set(AntiCrashState.RESTORING))
                throw new IOException("Could not persist RESTORING state");
            server.stop(false);
            return true;
        } catch (Exception exception) {
            restartCoordinator.cancel();
            boolean cleared = clearPendingRestoreAfterFailure(exception);
            if (cleared) states.set(previousState);
            else states.set(AntiCrashState.RECOVERY_FAILED);
            logger.importantKey("recovery.restore_rejected", exception.getMessage());
            return false;
        }
    }

    /**
     * Newest checkpoint on the active recovery timeline whose control metadata is intact. Using the
     * numerically newest ID instead would roll forward into a branch that an earlier restore
     * deliberately abandoned.
     */
    private Path latestTimelineCheckpoint() throws IOException {
        String active = internal.recoveryState().activeCheckpointId;
        for (Path candidate : checkpoints.timelineCandidates(active)) {
            try {
                checkpoints.verifyRecoveryPayloads(candidate);
                return candidate;
            } catch (IOException exception) {
                logger.importantKey(
                        "checkpoint.invalid_ignored",
                        candidate.getFileName(),
                        exception.getMessage());
            }
        }
        return null;
    }

    public boolean abortRecovery() {
        RecoveryStateData data = internal.recoveryState();
        if (!states.isRecovery() || data.crashRawPath == null || server == null) return false;
        AntiCrashState previousState = states.get();
        try {
            internal.updateRecoveryOrThrow(state -> {
                state.pendingRestoreKind = "RAW";
                state.pendingRestorePath = data.crashRawPath;
                state.lastError = null;
            });
            restartCoordinator.arm(config.language, config.recovery.spawnRestartHelper);
            if (!states.set(AntiCrashState.RESTORING))
                throw new IOException("Could not persist RESTORING state");
            server.stop(false);
            return true;
        } catch (Exception exception) {
            restartCoordinator.cancel();
            boolean cleared = clearPendingRestoreAfterFailure(exception);
            if (cleared) states.set(previousState);
            else states.set(AntiCrashState.RECOVERY_FAILED);
            logger.importantKey("recovery.restore_rejected", exception.getMessage());
            return false;
        }
    }

    public boolean continueRecovery() {
        if (states.get() != AntiCrashState.RECOVERY_DECISION) return false;
        boolean dirty = recoveryDecisionWasDirty;
        // /ac recovery continue can reach this without going through answerRecovery, so clear the
        // whole ASK prompt state here rather than only the deadline.
        askDeadlineNanos = 0;
        askReminderNanos = 0;
        askPromptPending = false;
        selectedCheckpointId = null;
        worldRestored = false;
        runtimeRestored = false;
        rawWorldRestored = false;
        pendingFakeSnapshots = List.of();
        // N means that this recovery candidate is intentionally discarded. Do not retain its
        // capsule/report: a later /ac start must not compare the live crash/clean world against a
        // checkpoint that was never restored.
        pendingRuntimeCapsule = null;
        equivalenceExpectedCapsule = null;
        equivalenceExpectedFakePlayers = List.of();
        lastEquivalenceReport = null;
        equivalenceLogSignature = null;
        strictBlocked = false;
        forceStartEligible = false;
        // CONTINUE_CURRENT_WORLD always means an ordinary server run.  In particular, a dirty
        // ASK=N answer must not leave the process in READY_TO_START or require /ac start.
        AntiCrashState nextState = server == null ? AntiCrashState.NORMAL : AntiCrashState.RUNNING;
        if (!states.set(nextState)) return false;
        if (server == null) {
            startupPlan = StartupRecoveryPlan.continueCurrentWorld(previousExit());
        }
        recoveryDecisionWasDirty = false;
        try {
            internal.updateRecoveryOrThrow(data -> {
                data.selectedCheckpointId = null;
                data.preWorldRestoreDone = false;
                data.decisionDirty = false;
            });
            return true;
        } catch (IOException exception) {
            states.set(AntiCrashState.RECOVERY_FAILED);
            logger.importantKey("recovery.restore_rejected", exception.getMessage());
            return false;
        }
    }

    public void reloadConfig() throws IOException {
        config.reloadFrom(configFile, logger::importantKey);
        internal.setLanguage(config.language);
        logger.update(config.logging);
        if (checkpoints != null) checkpoints.resetDeadline();
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                server.getPlayerManager().sendCommandTree(player);
            }
        }
        logger.normalKey("config.reloaded");
    }

    public List<Path> availableCheckpoints() throws IOException {
        return checkpoints == null ? List.of() : checkpoints.listComplete();
    }

    public CheckpointManifest manifest(String id) throws IOException {
        Path path = checkpoints.find(id);
        if (path == null) throw new IOException("Unknown checkpoint: " + id);
        // Inspection runs on the server thread: authenticate the control metadata only.
        return checkpoints.verifyRecoveryPayloads(path);
    }

    public long journalCount() {
        return consistency == null ? 0 : consistency.journalCount();
    }

    public long transactionWarningCount() {
        return consistency == null ? 0 : consistency.warningCount();
    }

    public List<Path> journalFiles() throws IOException {
        return consistency == null ? List.of() : consistency.listJournalFiles();
    }

    public void debugDump() throws IOException {
        AtomicFiles.writeJson(
                internal.stateDir().resolve("debug-dump.json"),
                new DebugDump(
                        states.get().name(),
                        selectedCheckpointId,
                        worldRestored,
                        runtimeRestored,
                        chunks.ready(),
                        chunks.expected(),
                        fakePlayerRestore.ready,
                        fakePlayerRestore.expected,
                        blockEventRestore.restored,
                        blockEventRestore.failed));
    }

    public String checkpointPerformanceReport() {
        return checkpointPerformanceReport(false);
    }

    public String checkpointPerformanceReport(boolean verbose) {
        return checkpoints == null
                ? "AntiCrash Checkpoint Performance\nNo checkpoint manager."
                : checkpoints.checkpointPerformanceReport(config.language, verbose);
    }

    public String checkpointMirrorReport(boolean audit) {
        return checkpoints == null
                ? "AntiCrash Checkpoint Mirror\nNo checkpoint manager."
                : checkpoints.checkpointMirrorReport(config.language, audit);
    }

    public void resetCheckpointPerformance() {
        if (checkpoints != null) checkpoints.resetPerformance();
    }

    public Path internalRoot() {
        return internal.internalRoot();
    }

    public void recordTransaction(
            String type,
            ServerPlayerEntity player,
            String source,
            String destination,
            net.minecraft.util.math.BlockPos pos,
            net.minecraft.item.ItemStack stack) {
        if (consistency != null)
            consistency.record(type, player, source, destination, pos, stack, server);
    }

    public void registerExternalBridge(
            com.atsukimc.anticrash.consistency.ExternalPlayerDataBridge bridge) {
        if (consistency != null) consistency.registerBridge(bridge);
    }

    private static String safeDetail(Throwable exception) {
        if (exception == null) return "unknown error";
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) detail = exception.getClass().getSimpleName();
        detail = detail.replace('\n', ' ').replace('\r', ' ');
        return detail.length() > 512 ? detail.substring(0, 512) + "..." : detail;
    }

    private boolean clearPendingRestoreAfterFailure(Exception original) {
        try {
            internal.updateRecoveryOrThrow(data -> {
                data.pendingRestoreKind = null;
                data.pendingRestorePath = null;
                data.lastError = original.toString();
            });
            return true;
        } catch (IOException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
            logger.fatalKey("state.recovery_state_persist_failed", cleanupFailure.getMessage());
            return false;
        }
    }

    private record RunContext(
            com.atsukimc.anticrash.core.RunMarker previous, RecoveryStateData recovery) {}

    private record DebugDump(
            String state,
            String checkpoint,
            boolean worldRestored,
            boolean runtimeRestored,
            int chunksReady,
            int chunksExpected,
            int fakeReady,
            int fakeExpected,
            int blockEventsRestored,
            int blockEventsFailed) {}

    private static final class StatePersistenceException extends IOException {
        private StatePersistenceException(String message) {
            super(message);
        }
    }

    public record StatusSnapshot(
            String state,
            String selectedCheckpoint,
            String activeCheckpoint,
            boolean worldFrozen,
            boolean startValidation,
            String backend,
            String integrity,
            int chunksReady,
            int chunksExpected,
            int chunksFailed,
            int blockEventsRestored,
            int blockEventsFailed,
            int fakePlayersReady,
            int fakePlayersExpected,
            int fakePlayersFailed,
            List<String> realPlayers,
            String consistencyMode,
            String world,
            String error,
            long nextCheckpointSeconds,
            long askRemainingSeconds,
            String startRejectionReason,
            String fidelity,
            int expectedChunks,
            int readyChunks,
            int currentConcurrentLoads,
            int pendingFutureCount,
            String equivalenceStatus,
            String blockingCategory,
            String carpetCompleteness,
            List<String> unsupportedRuntimeState) {}
}
