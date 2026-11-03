package com.atsukimc.anticrash.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecoveryCommandPolicyTest {
    @Test
    void checkpointBarrierAllowsOnlyExactInspectionCommandsAndStop() {
        assertTrue(RecoveryCommandPolicy.allowedDuringCheckpoint("stop"));
        assertTrue(RecoveryCommandPolicy.allowedDuringCheckpoint("/ac help"));
        assertTrue(RecoveryCommandPolicy.allowedDuringCheckpoint("ac status"));
        assertFalse(RecoveryCommandPolicy.allowedDuringCheckpoint("ac status mutate"));
        assertFalse(RecoveryCommandPolicy.allowedDuringCheckpoint("ac checkpoint"));
        assertFalse(RecoveryCommandPolicy.allowedDuringCheckpoint("time set day"));
    }

    @Test
    void recoveryControlsRemainAvailableWhenOrdinaryCommandsAreDisabled() {
        assertTrue(RecoveryCommandPolicy.allowed("/ac start", false));
        assertTrue(RecoveryCommandPolicy.allowed("Y", false));
        assertTrue(RecoveryCommandPolicy.allowed("n", false));
        assertTrue(RecoveryCommandPolicy.allowed("stop", false));
        assertFalse(RecoveryCommandPolicy.allowed("list", false));
    }

    @Test
    void allowsReadOnlyCommandsButRejectsWorldMutations() {
        assertTrue(RecoveryCommandPolicy.allowed("list", true));
        assertTrue(RecoveryCommandPolicy.allowed("time query daytime", true));
        assertTrue(RecoveryCommandPolicy.allowed("gamerule doDaylightCycle", true));
        assertTrue(RecoveryCommandPolicy.allowed("data get entity @s Pos", true));
        assertTrue(RecoveryCommandPolicy.allowed("scoreboard objectives list", true));
        assertTrue(RecoveryCommandPolicy.allowed("scoreboard players get @s points", true));

        assertFalse(RecoveryCommandPolicy.allowed("setblock 0 64 0 stone", true));
        assertFalse(RecoveryCommandPolicy.allowed("time set day", true));
        assertFalse(RecoveryCommandPolicy.allowed("gamerule doDaylightCycle true", true));
        assertFalse(
                RecoveryCommandPolicy.allowed("data modify entity @s Health set value 20", true));
        assertFalse(RecoveryCommandPolicy.allowed("scoreboard players add @s points 1", true));
        assertFalse(RecoveryCommandPolicy.allowed("execute as @a run setblock 0 64 0 stone", true));
        assertFalse(RecoveryCommandPolicy.allowed(null, true));
    }
}
