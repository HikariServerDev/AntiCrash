package com.atsukimc.anticrash.core;

public enum AntiCrashState {
    NORMAL,
    RUNNING,
    CHECKPOINT_REQUESTED,
    CHECKPOINT_BARRIER,
    CHECKPOINTING,
    /** The logical cut and generation seal are durable; the world may resume. */
    CHECKPOINT_SEALED,
    /** Background manifest/checksum/READY finalization is in progress. */
    CHECKPOINT_FINALIZING,
    /** Background finalization failed; the previous checkpoint remains authoritative. */
    CHECKPOINT_FINALIZE_FAILED,
    CHECKPOINT_COMMITTING,
    /** Filesystem-only startup states; no ServerWorld exists in these states. */
    PREBOOT_INSPECT,
    PREBOOT_DECISION,
    PREBOOT_PRESERVE_RAW,
    PREBOOT_VERIFY,
    PREBOOT_RESTORE,
    DIRTY_EXIT_DETECTED,
    RECOVERY_DECISION,
    RESTORING,
    RECOVERY_FROZEN,
    WAITING_FOR_CHUNKS,
    RESTORING_FAKEPLAYERS,
    READY_TO_START,
    STARTING,
    RECOVERY_FAILED
}
