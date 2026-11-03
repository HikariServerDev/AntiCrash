package com.atsukimc.anticrash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.core.AntiCrashState;
import com.atsukimc.anticrash.core.RecoveryStateData;

import org.junit.jupiter.api.Test;

class AntiCrashControllerShutdownTest {
    @Test
    void onlyMinecraftServerThreadCanMarkAStopAsClean() {
        Thread serverThread = new Thread("Server thread");
        Thread shutdownHook = new Thread("Server Shutdown Thread");

        assertTrue(AntiCrashController.isTrustedNormalShutdownCaller(serverThread, serverThread));
        assertFalse(AntiCrashController.isTrustedNormalShutdownCaller(shutdownHook, serverThread));
        assertFalse(AntiCrashController.isTrustedNormalShutdownCaller(null, serverThread));
        assertFalse(AntiCrashController.isTrustedNormalShutdownCaller(serverThread, null));
    }

    @Test
    void cleanStopBeforeStartIsResumedAsAnUnfinishedRestore() {
        RecoveryStateData frozen = new RecoveryStateData();
        frozen.state = AntiCrashState.READY_TO_START.name();
        frozen.selectedCheckpointId = "cp-000007";
        frozen.preWorldRestoreDone = true;
        assertTrue(AntiCrashController.isUnfinishedRestoredRecovery(frozen));

        RecoveryStateData started = new RecoveryStateData();
        started.state = AntiCrashState.RUNNING.name();
        started.selectedCheckpointId = "cp-000007";
        started.preWorldRestoreDone = true;
        assertFalse(AntiCrashController.isUnfinishedRestoredRecovery(started));

        // A manual /ac freeze after a completed recovery has no selection left.
        RecoveryStateData manualFreeze = new RecoveryStateData();
        manualFreeze.state = AntiCrashState.RECOVERY_FROZEN.name();
        assertFalse(AntiCrashController.isUnfinishedRestoredRecovery(manualFreeze));
    }
}
