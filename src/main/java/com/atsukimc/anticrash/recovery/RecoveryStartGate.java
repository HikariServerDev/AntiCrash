package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.core.AntiCrashState;

/** The same start decision is used for command feedback and the actual durable transition. */
public final class RecoveryStartGate {
    private RecoveryStartGate() {}

    public record Decision(boolean allowed, String reason) {}

    public static Decision evaluate(
            AntiCrashState state,
            boolean force,
            boolean allowForce,
            boolean forceEligible,
            String validationReason,
            String wrongStateReason) {
        if (validationReason != null) return new Decision(false, validationReason);
        boolean failedOverride =
                state == AntiCrashState.RECOVERY_FAILED && force && allowForce && forceEligible;
        boolean startable =
                state == AntiCrashState.READY_TO_START
                        || state == AntiCrashState.RECOVERY_FROZEN
                        || failedOverride;
        return startable ? new Decision(true, null) : new Decision(false, wrongStateReason);
    }
}
