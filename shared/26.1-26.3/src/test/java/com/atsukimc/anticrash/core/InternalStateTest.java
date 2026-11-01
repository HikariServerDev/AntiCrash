package com.atsukimc.anticrash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

class InternalStateTest {
    @TempDir Path temp;

    @Test
    void cleanAndDirtyMarkersClassifySeparateRuns() throws Exception {
        InternalStateManager internal =
                new InternalStateManager(temp.resolve("game"), message -> {});
        internal.initializeDirectories();
        internal.beginRun(null);
        assertFalse(internal.readPreviousRun().cleanShutdown);

        internal.markCleanShutdown("NORMAL_SHUTDOWN");
        RunMarker clean = internal.readPreviousRun();
        assertTrue(clean.cleanShutdown);

        internal.beginRun(clean);
        RunMarker dirty = internal.readPreviousRun();
        assertFalse(dirty.cleanShutdown);
        assertTrue(clean.runId.equals(dirty.previousRunId));
    }

    private RunMarker restart(InternalStateManager internal) throws Exception {
        RunMarker effective = RunMarker.effectivePrevious(internal.readPreviousRun());
        internal.beginRun(effective);
        return effective;
    }

    /**
     * Regression test: another mod refused to start after a clean stop. AntiCrash had already
     * written its marker, so the next start saw a "crash" and offered to roll the world back.
     */
    @Test
    void aStartThatNeverOpenedTheWorldDoesNotTurnACleanStopIntoACrash() throws Exception {
        InternalStateManager internal =
                new InternalStateManager(temp.resolve("game"), message -> {});
        internal.initializeDirectories();
        internal.beginRun(null);
        internal.markWorldOpened();
        internal.markCleanShutdown("NORMAL_SHUTDOWN");

        // Two starts in a row die during mod initialisation.
        assertTrue(restart(internal).cleanShutdown);
        assertTrue(restart(internal).cleanShutdown);
        // The third one works: the previous exit is still the clean stop.
        RunMarker previous = restart(internal);
        assertTrue(previous.cleanShutdown);
        internal.markWorldOpened();

        // It crashes with the world open: now the exit is dirty, however often the restart fails.
        RunMarker crashed = restart(internal);
        assertFalse(crashed.cleanShutdown);
        String crashedRun = crashed.runId;
        RunMarker afterFailedStart = restart(internal);
        assertFalse(afterFailedStart.cleanShutdown);
        assertEquals(crashedRun, afterFailedStart.runId);
        assertEquals(crashed.startedAtUtc, afterFailedStart.startedAtUtc);
        assertEquals(crashedRun, restart(internal).runId);
    }

    @Test
    void aMarkerFromAnOlderBuildCountsAsHavingOpenedTheWorld() {
        RunMarker old = new RunMarker();
        old.runId = java.util.UUID.randomUUID().toString();
        old.startedAtUtc = java.time.Instant.now().toString();
        assertEquals(old, RunMarker.effectivePrevious(old));
        assertFalse(RunMarker.effectivePrevious(old).cleanShutdown);
    }

    @Test
    void recoveryStateMachinePersistsFrozenStates() throws Exception {
        InternalStateManager internal =
                new InternalStateManager(temp.resolve("game"), message -> {});
        internal.initializeDirectories();
        AntiCrashStateMachine states = new AntiCrashStateMachine(internal);

        states.set(AntiCrashState.RECOVERY_FROZEN);
        assertTrue(states.isFrozen());
        assertTrue(states.canResumeWithStart());
        states.set(AntiCrashState.READY_TO_START);
        assertTrue(states.canResumeWithStart());
        states.set(AntiCrashState.RECOVERY_DECISION);
        assertFalse(states.canResumeWithStart());
        states.set(AntiCrashState.RUNNING);
        assertFalse(states.isFrozen());
        assertFalse(states.canResumeWithStart());
        assertTrue(internal.recoveryState().state.equals(AntiCrashState.RUNNING.name()));
    }

    @Test
    void checkpointStatesFreezeSimulationWithoutBecomingRecoveryStates() throws Exception {
        InternalStateManager internal =
                new InternalStateManager(temp.resolve("game"), message -> {});
        internal.initializeDirectories();
        AntiCrashStateMachine states = new AntiCrashStateMachine(internal);

        states.set(AntiCrashState.CHECKPOINT_BARRIER);
        assertTrue(states.isFrozen());
        assertFalse(states.isRecovery());

        states.set(AntiCrashState.CHECKPOINTING);
        assertTrue(states.isFrozen());
        assertFalse(states.isRecovery());

        states.set(AntiCrashState.CHECKPOINT_COMMITTING);
        assertFalse(states.isFrozen());
        assertFalse(states.isRecovery());

        states.set(AntiCrashState.CHECKPOINT_SEALED);
        assertFalse(states.isFrozen());
        assertFalse(states.isRecovery());
        states.set(AntiCrashState.CHECKPOINT_FINALIZING);
        assertFalse(states.isFrozen());
        assertFalse(states.isRecovery());
        states.set(AntiCrashState.CHECKPOINT_FINALIZE_FAILED);
        assertFalse(states.isFrozen());
        assertFalse(states.isRecovery());
    }

    @Test
    void unreadableRunMarkerIsPreservedAndReported() throws Exception {
        Path game = temp.resolve("game");
        InternalStateManager internal = new InternalStateManager(game, message -> {});
        internal.initializeDirectories();
        Path marker = game.resolve(".anticrash/state/run-state.json");
        String corrupt = "{not-json";
        Files.writeString(marker, corrupt);

        assertNull(internal.readPreviousRun());
        assertTrue(internal.previousRunUnreadable());
        assertEquals(corrupt, Files.readString(marker));
    }

    @Test
    void unreadableRecoveryStateCannotBeOverwrittenOrUnfrozenInMemory() throws Exception {
        Path game = temp.resolve("game");
        InternalStateManager internal = new InternalStateManager(game, message -> {});
        internal.initializeDirectories();
        Path recovery = game.resolve(".anticrash/state/recovery-state.json");
        String corrupt = "{\"formatVersion\":99}";
        Files.writeString(recovery, corrupt);

        AntiCrashStateMachine states = new AntiCrashStateMachine(internal);
        assertEquals(AntiCrashState.RECOVERY_FAILED, states.get());
        assertTrue(internal.recoveryStateUnreadable());

        states.set(AntiCrashState.RUNNING);

        assertEquals(AntiCrashState.RECOVERY_FAILED, states.get());
        assertTrue(states.isFrozen());
        assertEquals(corrupt, Files.readString(recovery));
    }

    @Test
    void onlyOneProcessLockCanOwnTheInternalState() throws Exception {
        InternalStateManager internal =
                new InternalStateManager(temp.resolve("game"), message -> {});
        internal.initializeDirectories();

        try (InternalProcessLock first = InternalProcessLock.acquire(internal.locksDir())) {
            assertThrows(
                    java.io.IOException.class,
                    () -> InternalProcessLock.acquire(internal.locksDir()));
        }

        try (InternalProcessLock ignored = InternalProcessLock.acquire(internal.locksDir())) {
            assertTrue(Files.isRegularFile(internal.locksDir().resolve("instance.lock")));
        }
    }

    @Test
    void newlyCreatedInternalDirectoriesAreOwnerOnlyOnPosixFilesystems() throws Exception {
        InternalStateManager internal =
                new InternalStateManager(temp.resolve("private-game"), message -> {});
        internal.initializeDirectories();

        if (Files.getFileStore(internal.internalRoot()).supportsFileAttributeView("posix")) {
            assertEquals(
                    PosixFilePermissions.fromString("rwx------"),
                    Files.getPosixFilePermissions(internal.internalRoot()));
            assertEquals(
                    PosixFilePermissions.fromString("rwx------"),
                    Files.getPosixFilePermissions(internal.stateDir()));
        }
    }

    @Test
    void warnsWhenExistingInternalDataIsAccessibleByOtherUsers() throws Exception {
        Path game = temp.resolve("shared-game");
        Path root = game.resolve(".anticrash");
        Files.createDirectories(root);
        if (!Files.getFileStore(root).supportsFileAttributeView("posix")) return;
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
        List<String> warnings = new ArrayList<>();

        InternalStateManager internal = new InternalStateManager(game, warnings::add);
        internal.initializeDirectories();

        assertTrue(warnings.stream().anyMatch(message -> message.contains("0700")));
    }
}
