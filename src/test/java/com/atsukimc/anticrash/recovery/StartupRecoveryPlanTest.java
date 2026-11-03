package com.atsukimc.anticrash.recovery;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.core.CrashCause;

import org.junit.jupiter.api.Test;

class StartupRecoveryPlanTest {
    @Test
    void continuePlanCannotAccidentallyRequireRuntimeRestore() {
        StartupRecoveryPlan plan =
                StartupRecoveryPlan.continueCurrentWorld(
                        StartupRecoveryPlan.PreviousExit.OUT_OF_MEMORY);

        assertEquals(StartupRecoveryPlan.Decision.CONTINUE_CURRENT_WORLD, plan.decision());
        assertEquals(StartupRecoveryPlan.PreviousExit.OUT_OF_MEMORY, plan.previousExit());
        assertNull(plan.checkpointId());
        assertFalse(plan.candidateVerified());
        assertFalse(plan.requiresRuntimeRecovery());
    }

    @Test
    void crashCauseMappingIsStableAndExplicit() {
        assertEquals(
                StartupRecoveryPlan.PreviousExit.WATCHDOG,
                StartupRecoveryPlan.from(CrashCause.WATCHDOG));
        assertEquals(
                StartupRecoveryPlan.PreviousExit.OUT_OF_MEMORY,
                StartupRecoveryPlan.from(CrashCause.OUT_OF_MEMORY));
        assertEquals(
                StartupRecoveryPlan.PreviousExit.CRASH,
                StartupRecoveryPlan.from(CrashCause.JAVA_EXCEPTION));
        assertEquals(
                StartupRecoveryPlan.PreviousExit.UNKNOWN,
                StartupRecoveryPlan.from(CrashCause.UNKNOWN_DIRTY_EXIT));
    }

    @Test
    void restorePlanCarriesTheFixedCheckpointAndVerificationBoundary() {
        StartupRecoveryPlan plan =
                StartupRecoveryPlan.restoreCheckpoint(
                        StartupRecoveryPlan.PreviousExit.CRASH, "cp-000123", true, "raw-id");

        assertEquals(StartupRecoveryPlan.Decision.RESTORE_CHECKPOINT, plan.decision());
        assertEquals("cp-000123", plan.checkpointId());
        assertTrue(plan.candidateVerified());
        assertEquals("raw-id", plan.crashRawId());
        assertTrue(plan.requiresRuntimeRecovery());
    }
}
