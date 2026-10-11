package com.atsukimc.anticrash.core;

import java.util.EnumSet;

public final class AntiCrashStateMachine {
    private static final EnumSet<AntiCrashState> FROZEN = EnumSet.of(
            AntiCrashState.CHECKPOINT_BARRIER,
            AntiCrashState.CHECKPOINTING,
            AntiCrashState.DIRTY_EXIT_DETECTED,
            AntiCrashState.RECOVERY_DECISION,
            AntiCrashState.RESTORING,
            AntiCrashState.RECOVERY_FROZEN,
            AntiCrashState.WAITING_FOR_CHUNKS,
            AntiCrashState.RESTORING_FAKEPLAYERS,
            AntiCrashState.READY_TO_START,
            AntiCrashState.STARTING,
            AntiCrashState.RECOVERY_FAILED
    );

    private final InternalStateManager internal;
    private AntiCrashState state;

    public AntiCrashStateMachine(InternalStateManager internal) {
        this.internal = internal;
        RecoveryStateData saved = internal.recoveryState();
        AntiCrashState parsed;
        try { parsed = AntiCrashState.valueOf(saved.state); }
        catch (Exception ignored) { parsed = AntiCrashState.RECOVERY_FAILED; }
        state = parsed;
    }

    public synchronized AntiCrashState get() { return state; }

    public synchronized boolean set(AntiCrashState next) {
        // Never expose a transition that could not be made durable. Otherwise an unreadable or
        // unwritable recovery-state.json could unfreeze the world for this process only.
        boolean persisted = internal.setState(next);
        state = persisted ? next : AntiCrashState.RECOVERY_FAILED;
        return persisted;
    }

    /**
     * Changes the in-memory state only. Used when persisting would overwrite unreadable state or a
     * lock is not owned, and for the checkpoint states, which boot never reads back.
     */
    public synchronized void setTransient(AntiCrashState next) {
        state = next;
    }

    public synchronized boolean isFrozen() { return FROZEN.contains(state); }
    public synchronized boolean canResumeWithStart() {
        return state == AntiCrashState.RECOVERY_FROZEN || state == AntiCrashState.READY_TO_START;
    }
    public synchronized boolean isRecovery() {
        return state != AntiCrashState.NORMAL && state != AntiCrashState.RUNNING
                && state != AntiCrashState.CHECKPOINT_REQUESTED
                && state != AntiCrashState.CHECKPOINT_BARRIER
                && state != AntiCrashState.CHECKPOINTING
                && state != AntiCrashState.CHECKPOINT_SEALED
                && state != AntiCrashState.CHECKPOINT_FINALIZING
                && state != AntiCrashState.CHECKPOINT_FINALIZE_FAILED
                && state != AntiCrashState.CHECKPOINT_COMMITTING;
    }
}
