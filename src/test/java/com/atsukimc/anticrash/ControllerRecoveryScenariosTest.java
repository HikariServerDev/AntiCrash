package com.atsukimc.anticrash;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.core.*;
import com.atsukimc.anticrash.recovery.PreWorldDecisionInput;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

class ControllerRecoveryScenariosTest {
    @TempDir Path temp;

    private AntiCrashController open() {
        return new AntiCrashController(
                temp, temp.resolve("config"), (p, t, a) -> PreWorldDecisionInput.Answer.CONTINUE);
    }

    private static Object get(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static AntiCrashStateMachine states(AntiCrashController c) throws Exception {
        return (AntiCrashStateMachine) get(c, "states");
    }

    @Test
    void acceptsOneStartAndRejectsDuplicateBeforeFirstTick() throws Exception {
        var c = open();
        try {
            states(c).set(AntiCrashState.RECOVERY_FROZEN);
            assertNull(c.startRejectionReason(false));
            assertTrue(c.requestStart(false));
            assertEquals(AntiCrashState.STARTING, states(c).get());
            assertTrue(c.isWorldFrozen());
            assertFalse(c.requestStart(false));
            assertNotNull(c.startRejectionReason(false));
            assertTrue((Boolean) get(c, "pendingStart"));
        } finally {
            c.onShutdownCompleted();
        }
    }

    @Test
    void refusesMissingWorldRuntimeAndStrictMismatchEvenWithForce() throws Exception {
        var c = open();
        try {
            states(c).set(AntiCrashState.RECOVERY_FROZEN);
            set(c, "selectedCheckpointId", "cp-test");
            assertFalse(c.requestStart(false));
            assertTrue(
                    c.startRejectionReason(false).contains("World")
                            || c.startRejectionReason(false).contains("ワールド"));
            set(c, "worldRestored", true);
            assertFalse(c.requestStart(false));
            set(c, "runtimeRestored", true);
            c.config().recovery.fidelity = "STRICT";
            c.config().recovery.allowStartWithFailedValidation = true;
            set(c, "strictBlocked", true);
            assertFalse(c.requestStart(true));
            assertEquals(AntiCrashState.RECOVERY_FROZEN, states(c).get());
        } finally {
            c.onShutdownCompleted();
        }
    }

    @Test
    void failedPersistenceCannotExposeStartingOrPendingStart() throws Exception {
        var c = open();
        try {
            states(c).set(AntiCrashState.RECOVERY_FROZEN);
            Path state = temp.resolve(".anticrash/state/recovery-state.json");
            Files.delete(state);
            Files.createDirectory(state);
            Files.writeString(state.resolve("block"), "preserve");
            assertFalse(c.requestStart(false));
            assertEquals(AntiCrashState.RECOVERY_FAILED, states(c).get());
            assertTrue(c.isWorldFrozen());
            assertFalse((Boolean) get(c, "pendingStart"));
        } finally {
            c.onShutdownCompleted();
        }
    }

    @Test
    void corruptConfigurationFailsClosedAndPreservesEvidence() throws Exception {
        Path file = temp.resolve("config/anticrash.toml");
        Files.createDirectories(file.getParent());
        String bad = "[recovery\n";
        Files.writeString(file, bad);
        var c = open();
        try {
            assertEquals(AntiCrashState.RECOVERY_FAILED, states(c).get());
            assertFalse(c.requestStart(true));
            assertEquals(bad, Files.readString(file));
        } finally {
            c.onShutdownCompleted();
        }
    }

    @Test
    void cleanBootNeverAsksForAutoOrManualWithoutCheckpoint() throws Exception {
        for (String policy : new String[] {"AUTO", "ASK", "MANUAL"}) {
            Path game = temp.resolve(policy);
            AntiCrashConfig config = new AntiCrashConfig();
            config.recovery.policy = policy;
            config.save(game.resolve("config/anticrash.toml"));
            var marker = new InternalStateManager(game, m -> {});
            marker.initializeDirectories();
            marker.beginRun(null);
            marker.markWorldOpened();
            marker.markCleanShutdown("NORMAL_SHUTDOWN");
            AtomicInteger questions = new AtomicInteger();
            var c =
                    new AntiCrashController(
                            game,
                            game.resolve("config"),
                            (p, t, a) -> {
                                questions.incrementAndGet();
                                return PreWorldDecisionInput.Answer.TIMEOUT;
                            });
            try {
                assertFalse(c.isWorldFrozen(), policy);
                assertEquals(0, questions.get(), policy + ": no candidate to ask about");
            } finally {
                c.onShutdownCompleted();
            }
        }
    }

    @TestFactory
    java.util.stream.Stream<DynamicTest> exitPolicyAndDecisionMatrixUsesRealBootstrap() {
        java.util.List<DynamicTest> tests = new java.util.ArrayList<>();
        for (boolean clean : new boolean[] {true, false})
            for (String policy : new String[] {"AUTO", "ASK", "MANUAL"})
                for (var answer : PreWorldDecisionInput.Answer.values()) {
                    String name = (clean ? "CLEAN" : "DIRTY") + "-" + policy + "-" + answer;
                    tests.add(
                            DynamicTest.dynamicTest(
                                    name,
                                    () -> {
                                        Path game = temp.resolve(name);
                                        AntiCrashConfig config = new AntiCrashConfig();
                                        config.recovery.policy = policy;
                                        config.logging.console = false;
                                        config.save(game.resolve("config/anticrash.toml"));
                                        var marker = new InternalStateManager(game, m -> {});
                                        marker.initializeDirectories();
                                        marker.beginRun(null);
                                        marker.markWorldOpened();
                                        if (clean) marker.markCleanShutdown("NORMAL_SHUTDOWN");
                                        Path candidate =
                                                game.resolve(".anticrash/checkpoints/cp-000001");
                                        Files.createDirectories(candidate);
                                        // Discovery can display this candidate. Authentication must
                                        // reject it on Y.
                                        Files.writeString(candidate.resolve("READY"), "unverified");
                                        Files.writeString(
                                                candidate.resolve("manifest.json"),
                                                "{\"checkpointId\":\"cp-000001\",\"utcTimestamp\":\"2026-11-02T00:00:00Z\",\"worldFiles\":[]}");
                                        AtomicInteger questions = new AtomicInteger();
                                        var c =
                                                new AntiCrashController(
                                                        game,
                                                        game.resolve("config"),
                                                        (p, t, a) -> {
                                                            questions.incrementAndGet();
                                                            return answer;
                                                        });
                                        try {
                                            boolean asks =
                                                    policy.equals("ASK")
                                                            || (!clean && policy.equals("MANUAL"));
                                            assertEquals(asks ? 1 : 0, questions.get(), name);
                                            boolean refused =
                                                    !clean && policy.equals("AUTO")
                                                            || asks
                                                                    && answer
                                                                            == PreWorldDecisionInput
                                                                                    .Answer.RESTORE
                                                            || !clean
                                                                    && asks
                                                                    && answer
                                                                            != PreWorldDecisionInput
                                                                                    .Answer
                                                                                    .CONTINUE;
                                            assertEquals(refused, c.isWorldFrozen(), name);
                                            if (refused) {
                                                assertFalse(c.requestStart(false), name);
                                                assertTrue(
                                                        Files.exists(candidate.resolve("READY")),
                                                        "keep corrupt evidence");
                                            }
                                        } finally {
                                            c.onShutdownCompleted();
                                        }
                                    }));
                }
        return tests.stream();
    }

    @Test
    void oneMissingRequiredChunkRefusesStartUntilAllAreReady() throws Exception {
        var c = open();
        try {
            states(c).set(AntiCrashState.RECOVERY_FROZEN);
            set(c, "selectedCheckpointId", "cp-test");
            set(c, "worldRestored", true);
            set(c, "runtimeRestored", true);
            Object chunks = get(c, "chunks");
            set(chunks, "expected", 654);
            set(chunks, "ready", 653);
            assertFalse(c.requestStart(false));
            assertTrue(c.isWorldFrozen());
            set(chunks, "ready", 654);
            assertTrue(c.requestStart(false));
        } finally {
            c.onShutdownCompleted();
        }
    }
}
