package com.atsukimc.anticrash.core;

import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.atsukimc.anticrash.util.FileSecurity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Owns .anticrash only; it intentionally has no reference to the user TOML. */
public final class InternalStateManager {
    private static final Pattern CHECKPOINT_ID = Pattern.compile("cp-[0-9]{6,18}");
    private static final Pattern RAW_ID =
            Pattern.compile(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:-[0-9]{1,19})?");
    private final Path gameDir;
    private final Path internalRoot;
    private final Path stateDir;
    private final Path checkpointsDir;
    private final Path crashRawDir;
    private final Path journalDir;
    private final Path locksDir;
    private final Path logsDir;
    private final Consumer<String> warning;
    private volatile String language = MessageCatalog.DEFAULT_LANGUAGE;
    private RunMarker currentRun;
    private RecoveryStateData recoveryState;
    private boolean previousRunUnreadable;
    private boolean recoveryStateUnreadable;

    public InternalStateManager(Path gameDir, Consumer<String> warning) {
        this.gameDir = gameDir.toAbsolutePath().normalize();
        this.internalRoot = this.gameDir.resolve(".anticrash");
        this.stateDir = internalRoot.resolve("state");
        this.checkpointsDir = internalRoot.resolve("checkpoints");
        this.crashRawDir = internalRoot.resolve("crash-raw");
        this.journalDir = internalRoot.resolve("journal");
        this.locksDir = internalRoot.resolve("locks");
        this.logsDir = internalRoot.resolve("logs");
        this.warning = warning;
    }

    public synchronized void setLanguage(String language) {
        this.language = MessageCatalog.normalize(language);
    }

    public synchronized void initializeDirectories() throws IOException {
        if (!Files.exists(gameDir, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(gameDir);
        if (Files.isSymbolicLink(gameDir)
                || !Files.isDirectory(gameDir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe game directory: " + gameDir);
        }
        ensureDirectory(internalRoot);
        ensureDirectory(stateDir);
        ensureDirectory(checkpointsDir);
        ensureDirectory(crashRawDir);
        ensureDirectory(journalDir);
        ensureDirectory(locksDir);
        ensureDirectory(logsDir);
        if (FileSecurity.hasGroupOrOtherAccess(internalRoot)) {
            emitWarning("security.internal_permissions", internalRoot);
        }
    }

    public synchronized RunMarker readPreviousRun() {
        previousRunUnreadable = false;
        Path file = stateDir.resolve("run-state.json");
        if (!Files.exists(file)) return null;
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
                throw new IOException("run-state.json is not a regular file");
            RunMarker marker = AtomicFiles.readJson(file, RunMarker.class, 1024L * 1024L);
            validateRunMarker(marker);
            return marker;
        } catch (Exception exception) {
            previousRunUnreadable = true;
            emitWarning("state.run_state_unreadable", exception.getMessage());
            return null;
        }
    }

    public synchronized RecoveryStateData readRecoveryState() {
        recoveryStateUnreadable = false;
        Path file = stateDir.resolve("recovery-state.json");
        if (!Files.exists(file)) {
            recoveryState = new RecoveryStateData();
            return copy(recoveryState);
        }
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))
                throw new IOException("recovery-state.json is not a regular file");
            recoveryState = AtomicFiles.readJson(file, RecoveryStateData.class, 1024L * 1024L);
            validateRecoveryState(recoveryState);
            return copy(recoveryState);
        } catch (Exception exception) {
            recoveryStateUnreadable = true;
            emitWarning("state.recovery_state_unreadable", exception.getMessage());
            recoveryState = new RecoveryStateData();
            recoveryState.state = AntiCrashState.RECOVERY_FAILED.name();
            recoveryState.lastError = exception.toString();
            return copy(recoveryState);
        }
    }

    public synchronized void beginRun(RunMarker previous) throws IOException {
        currentRun = new RunMarker();
        currentRun.runId = UUID.randomUUID().toString();
        currentRun.cleanShutdown = false;
        currentRun.startedAtUtc = Instant.now().toString();
        currentRun.previousRunId = previous == null ? null : previous.runId;
        // `previous` is the run that last had the world open (RunMarker#effectivePrevious).
        currentRun.worldOpened = Boolean.FALSE;
        currentRun.worldCleanAtStart = previous == null || previous.cleanShutdown;
        if (previous != null && !previous.cleanShutdown) {
            currentRun.carriedRunId = previous.runId;
            currentRun.carriedStartedAtUtc = previous.startedAtUtc;
        }
        writeRunState();
    }

    /** From here on an unclean end of this run leaves the world in an unknown state. */
    public synchronized void markWorldOpened() {
        if (currentRun == null || Boolean.TRUE.equals(currentRun.worldOpened)) return;
        currentRun.worldOpened = Boolean.TRUE;
        try {
            writeRunState();
        } catch (IOException exception) {
            // The marker on disk still says "not opened". Losing the distinction is the unsafe
            // direction, so the caller is told and the run is treated as failed to start.
            emitWarning("state.world_opened_write_failed", exception.getMessage());
            throw new IllegalStateException(
                    "Could not record that the world is being opened", exception);
        }
    }

    public synchronized void markCleanShutdown(String cause) {
        if (currentRun == null) return;
        currentRun.cleanShutdown = true;
        currentRun.endedAtUtc = Instant.now().toString();
        currentRun.crashCause = cause;
        try {
            writeRunState();
        } catch (IOException exception) {
            emitWarning("state.clean_shutdown_write_failed", exception.getMessage());
        }
    }

    public synchronized void markPreviousDirtyCause(String cause) {
        if (currentRun == null) return;
        currentRun.previousCrashCause = cause;
        try {
            writeRunState();
        } catch (IOException exception) {
            emitWarning("state.shutdown_cause_write_failed", exception.getMessage());
        }
    }

    public synchronized void writeRunState() throws IOException {
        AtomicFiles.writeJson(stateDir.resolve("run-state.json"), currentRun);
    }

    /**
     * Persists a state transition before it becomes visible to the state machine.
     *
     * @return {@code true} only when the durable state was replaced successfully
     */
    public synchronized boolean setState(AntiCrashState state) {
        if (recoveryState == null) recoveryState = readRecoveryState();
        if (recoveryStateUnreadable) {
            emitWarning("state.recovery_state_write_blocked");
            return false;
        }
        RecoveryStateData candidate = copy(recoveryState);
        candidate.state = state.name();
        candidate.stateChangedAtEpochMs = System.currentTimeMillis();
        try {
            AtomicFiles.writeJson(stateDir.resolve("recovery-state.json"), candidate);
            recoveryState = candidate;
            return true;
        } catch (IOException exception) {
            emitWarning("state.recovery_state_write_failed", state, exception.getMessage());
            return false;
        }
    }

    public synchronized void updateRecovery(Consumer<RecoveryStateData> updater) {
        if (recoveryState == null) recoveryState = readRecoveryState();
        if (recoveryStateUnreadable) {
            emitWarning("state.recovery_state_write_blocked");
            return;
        }
        RecoveryStateData candidate = copy(recoveryState);
        updater.accept(candidate);
        candidate.stateChangedAtEpochMs = System.currentTimeMillis();
        try {
            AtomicFiles.writeJson(stateDir.resolve("recovery-state.json"), candidate);
            recoveryState = candidate;
        } catch (IOException exception) {
            emitWarning("state.recovery_state_persist_failed", exception.getMessage());
        }
    }

    /** Persists an update or leaves the in-memory state unchanged and reports the failure. */
    public synchronized void updateRecoveryOrThrow(Consumer<RecoveryStateData> updater)
            throws IOException {
        if (recoveryState == null) readRecoveryState();
        if (recoveryStateUnreadable)
            throw new IOException("Refusing to overwrite unreadable recovery-state.json");
        RecoveryStateData candidate = copy(recoveryState);
        updater.accept(candidate);
        candidate.stateChangedAtEpochMs = System.currentTimeMillis();
        AtomicFiles.writeJson(stateDir.resolve("recovery-state.json"), candidate);
        recoveryState = candidate;
    }

    public synchronized RecoveryStateData recoveryState() {
        if (recoveryState == null) readRecoveryState();
        return copy(recoveryState);
    }

    public synchronized String currentRunId() {
        return currentRun == null ? "unknown" : currentRun.runId;
    }

    public Path gameDir() {
        return gameDir;
    }

    public Path internalRoot() {
        return internalRoot;
    }

    public Path stateDir() {
        return stateDir;
    }

    public Path checkpointsDir() {
        return checkpointsDir;
    }

    public Path crashRawDir() {
        return crashRawDir;
    }

    public Path journalDir() {
        return journalDir;
    }

    public Path locksDir() {
        return locksDir;
    }

    public Path logsDir() {
        return logsDir;
    }

    public synchronized boolean previousRunUnreadable() {
        return previousRunUnreadable;
    }

    public synchronized boolean recoveryStateUnreadable() {
        return recoveryStateUnreadable;
    }

    private static void validateRunMarker(RunMarker marker) throws IOException {
        if (marker == null) throw new IOException("empty run state");
        if (marker.formatVersion != 1)
            throw new IOException("unsupported run state format: " + marker.formatVersion);
        if (marker.runId == null || marker.runId.isBlank())
            throw new IOException("runId is missing");
        try {
            UUID.fromString(marker.runId);
        } catch (IllegalArgumentException exception) {
            throw new IOException("runId is invalid", exception);
        }
        if (marker.startedAtUtc == null) throw new IOException("startedAtUtc is missing");
        try {
            Instant.parse(marker.startedAtUtc);
        } catch (Exception exception) {
            throw new IOException("startedAtUtc is invalid", exception);
        }
        if (marker.endedAtUtc != null)
            try {
                Instant.parse(marker.endedAtUtc);
            } catch (Exception exception) {
                throw new IOException("endedAtUtc is invalid", exception);
            }
        if (marker.previousRunId != null)
            try {
                UUID.fromString(marker.previousRunId);
            } catch (IllegalArgumentException exception) {
                throw new IOException("previousRunId is invalid", exception);
            }
        if (marker.carriedRunId != null)
            try {
                UUID.fromString(marker.carriedRunId);
            } catch (IllegalArgumentException exception) {
                throw new IOException("carriedRunId is invalid", exception);
            }
        if (marker.carriedStartedAtUtc != null)
            try {
                Instant.parse(marker.carriedStartedAtUtc);
            } catch (Exception exception) {
                throw new IOException("carriedStartedAtUtc is invalid", exception);
            }
    }

    private static void validateRecoveryState(RecoveryStateData state) throws IOException {
        if (state == null) throw new IOException("empty recovery state");
        if (state.formatVersion != 1)
            throw new IOException("unsupported recovery state format: " + state.formatVersion);
        if (state.state == null) throw new IOException("recovery state is missing");
        try {
            AntiCrashState.valueOf(state.state);
        } catch (IllegalArgumentException exception) {
            throw new IOException("unknown recovery state: " + state.state, exception);
        }
        validateOptionalId("activeCheckpointId", state.activeCheckpointId, CHECKPOINT_ID);
        validateOptionalId("selectedCheckpointId", state.selectedCheckpointId, CHECKPOINT_ID);
        if ((state.pendingRestoreKind == null) != (state.pendingRestorePath == null)) {
            throw new IOException(
                    "pending restore kind/path must either both be present or both be absent");
        }
        if (state.pendingRestoreKind != null
                && !(state.pendingRestoreKind.equals("CHECKPOINT")
                        || state.pendingRestoreKind.equals("RAW"))) {
            throw new IOException("unknown pending restore kind: " + state.pendingRestoreKind);
        }
        if ("CHECKPOINT".equals(state.pendingRestoreKind))
            validateOptionalId("pendingRestorePath", state.pendingRestorePath, CHECKPOINT_ID);
        if ("RAW".equals(state.pendingRestoreKind))
            validateOptionalId("pendingRestorePath", state.pendingRestorePath, RAW_ID);
        validateOptionalId("crashRawPath", state.crashRawPath, RAW_ID);
        if (state.lastError != null && state.lastError.length() > 65_536)
            throw new IOException("lastError is too large");
    }

    private static void validateOptionalId(String field, String value, Pattern pattern)
            throws IOException {
        if (value != null && !pattern.matcher(value).matches())
            throw new IOException(field + " is invalid");
    }

    private static RecoveryStateData copy(RecoveryStateData source) {
        RecoveryStateData target = new RecoveryStateData();
        target.formatVersion = source.formatVersion;
        target.state = source.state;
        target.activeCheckpointId = source.activeCheckpointId;
        target.selectedCheckpointId = source.selectedCheckpointId;
        target.pendingRestoreKind = source.pendingRestoreKind;
        target.pendingRestorePath = source.pendingRestorePath;
        target.crashRawPath = source.crashRawPath;
        target.lastError = source.lastError;
        target.preWorldRestoreDone = source.preWorldRestoreDone;
        target.decisionDirty = source.decisionDirty;
        target.stateChangedAtEpochMs = source.stateChangedAtEpochMs;
        return target;
    }

    private static void ensureDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unsafe AntiCrash internal directory: " + directory);
            }
            return;
        }
        Files.createDirectory(directory);
        FileSecurity.hardenCreatedDirectory(directory);
    }

    private void emitWarning(String key, Object... arguments) {
        warning.accept(MessageCatalog.text(language, key, arguments));
    }
}
