package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.core.CrashCause;

import java.util.Objects;

/**
 * Immutable hand-off from the pre-world disk phase to the post-world runtime phase.
 *
 * <p>The plan deliberately contains no Minecraft object. It can therefore be produced before {@code
 * MinecraftServer.loadWorld()} and remains safe to inspect while the server is still constructing
 * its level storage. A checkpoint is only marked verified after the full integrity pass and the
 * staged restore have completed.
 */
public final class StartupRecoveryPlan {
    public enum Decision {
        CONTINUE_CURRENT_WORLD,
        RESTORE_CHECKPOINT,
        RESTORE_CRASH_RAW
    }

    public enum PreviousExit {
        CLEAN,
        WATCHDOG,
        OUT_OF_MEMORY,
        CRASH,
        UNKNOWN
    }

    private final Decision decision;
    private final PreviousExit previousExit;
    private final String checkpointId;
    private final boolean candidateVerified;
    private final String crashRawId;
    private final boolean requiresRuntimeRecovery;

    private StartupRecoveryPlan(
            Decision decision,
            PreviousExit previousExit,
            String checkpointId,
            boolean candidateVerified,
            String crashRawId,
            boolean requiresRuntimeRecovery) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.previousExit = Objects.requireNonNull(previousExit, "previousExit");
        this.checkpointId = checkpointId;
        this.candidateVerified = candidateVerified;
        this.crashRawId = crashRawId;
        this.requiresRuntimeRecovery = requiresRuntimeRecovery;
        if (decision == Decision.RESTORE_CHECKPOINT
                && (checkpointId == null || checkpointId.isBlank())) {
            throw new IllegalArgumentException(
                    "A checkpoint restore plan requires a checkpoint ID");
        }
        if (decision == Decision.RESTORE_CRASH_RAW
                && (crashRawId == null || crashRawId.isBlank())) {
            throw new IllegalArgumentException("A crash-raw restore plan requires a raw ID");
        }
        if (decision == Decision.CONTINUE_CURRENT_WORLD
                && (candidateVerified || requiresRuntimeRecovery)) {
            throw new IllegalArgumentException("A continue plan cannot require recovery state");
        }
    }

    public static StartupRecoveryPlan continueCurrentWorld(PreviousExit previousExit) {
        return new StartupRecoveryPlan(
                Decision.CONTINUE_CURRENT_WORLD, previousExit, null, false, null, false);
    }

    public static StartupRecoveryPlan restoreCheckpoint(
            PreviousExit previousExit,
            String checkpointId,
            boolean candidateVerified,
            String crashRawId) {
        return new StartupRecoveryPlan(
                Decision.RESTORE_CHECKPOINT,
                previousExit,
                checkpointId,
                candidateVerified,
                crashRawId,
                true);
    }

    public static StartupRecoveryPlan restoreCrashRaw(
            PreviousExit previousExit, String crashRawId) {
        return new StartupRecoveryPlan(
                Decision.RESTORE_CRASH_RAW, previousExit, null, true, crashRawId, false);
    }

    public Decision decision() {
        return decision;
    }

    public PreviousExit previousExit() {
        return previousExit;
    }

    public String checkpointId() {
        return checkpointId;
    }

    public boolean candidateVerified() {
        return candidateVerified;
    }

    public String crashRawId() {
        return crashRawId;
    }

    public boolean requiresRuntimeRecovery() {
        return requiresRuntimeRecovery;
    }

    public static PreviousExit from(CrashCause cause) {
        if (cause == null) return PreviousExit.UNKNOWN;
        return switch (cause) {
            case WATCHDOG -> PreviousExit.WATCHDOG;
            case OUT_OF_MEMORY -> PreviousExit.OUT_OF_MEMORY;
            case JAVA_EXCEPTION, CRASH_REPORT, EXTERNAL_TERMINATION, PROCESS_EXIT_CODE ->
                    PreviousExit.CRASH;
            case POWER_LOSS_OR_UNKNOWN, UNKNOWN_DIRTY_EXIT -> PreviousExit.UNKNOWN;
        };
    }

    @Override
    public String toString() {
        return "StartupRecoveryPlan{"
                + "decision="
                + decision
                + ", previousExit="
                + previousExit
                + ", checkpointId='"
                + checkpointId
                + '\''
                + ", candidateVerified="
                + candidateVerified
                + ", crashRawId='"
                + crashRawId
                + '\''
                + ", requiresRuntimeRecovery="
                + requiresRuntimeRecovery
                + '}';
    }
}
