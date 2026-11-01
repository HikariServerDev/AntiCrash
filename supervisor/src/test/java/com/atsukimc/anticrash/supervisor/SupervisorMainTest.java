package com.atsukimc.anticrash.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SupervisorMainTest {
    @TempDir Path temp;

    @Test
    void claimsOnlyARegularMarkerUnderTheWorkDirectory() throws Exception {
        Path state = temp.resolve(".anticrash/state");
        Files.createDirectories(state);
        Path marker = state.resolve("restart-request");
        Files.writeString(
                marker,
                "request=123e4567-e89b-12d3-a456-426614174000\n"
                        + "created_at=2026-08-31T00:00:00Z\n");

        assertTrue(SupervisorMain.claimRestartMarker(temp, marker));
        assertFalse(SupervisorMain.claimRestartMarker(temp, marker));
        assertThrows(
                java.io.IOException.class,
                () -> SupervisorMain.claimRestartMarker(temp, temp.resolve("elsewhere")));
    }

    @Test
    void rejectsAnUnsafeInternalDirectoryInsteadOfFollowingIt() throws Exception {
        Files.writeString(temp.resolve(".anticrash"), "not a directory");
        Path marker = temp.resolve(".anticrash/state/restart-request");

        assertThrows(
                java.io.IOException.class, () -> SupervisorMain.claimRestartMarker(temp, marker));
    }

    @Test
    void rejectsMalformedRestartMarkers() throws Exception {
        Path state = temp.resolve(".anticrash/state");
        Files.createDirectories(state);
        Path marker = state.resolve("restart-request");
        Files.writeString(marker, "request=not-a-uuid\n");

        assertThrows(
                java.io.IOException.class, () -> SupervisorMain.claimRestartMarker(temp, marker));
        assertTrue(Files.exists(marker));
    }

    @Test
    void normalExitNeverRestarts() {
        assertEquals(-1, SupervisorMain.nextRestartCount(0, 0, 1_000, 3, 60_000));
    }

    @Test
    void limitsOnlyConsecutiveShortLivedFailures() {
        assertEquals(1, SupervisorMain.nextRestartCount(1, 0, 1_000, 3, 60_000));
        assertEquals(3, SupervisorMain.nextRestartCount(1, 2, 1_000, 3, 60_000));
        assertEquals(-1, SupervisorMain.nextRestartCount(1, 3, 1_000, 3, 60_000));
    }

    @Test
    void stableRunResetsRetryBudgetBeforeRestarting() {
        assertEquals(1, SupervisorMain.nextRestartCount(137, 3, 60_000, 3, 60_000));
        assertEquals(-1, SupervisorMain.nextRestartCount(137, 3, 60_000, 3, 0));
    }

    @Test
    void makesJavaOomAnObservableProcessFailure() {
        List<String> command =
                SupervisorMain.hardenedCommand(
                        List.of("/usr/bin/java", "-Xmx1G", "-jar", "server.jar"));

        assertEquals("-XX:+ExitOnOutOfMemoryError", command.get(1));
        assertEquals(command, SupervisorMain.hardenedCommand(command));
    }

    @Test
    void redactsCredentialsFromDryRunDiagnostics() {
        List<String> redacted =
                SupervisorMain.redactedCommand(
                        List.of(
                                "java",
                                "-Ddb.password=hunter2",
                                "--token",
                                "abc123",
                                "--name=value",
                                "-jar",
                                "server.jar"));

        assertEquals("-Ddb.password=<redacted>", redacted.get(1));
        assertEquals("--token", redacted.get(2));
        assertEquals("<redacted>", redacted.get(3));
        assertEquals("--name=value", redacted.get(4));
        assertFalse(redacted.toString().contains("hunter2"));
        assertFalse(redacted.toString().contains("abc123"));
    }

    @Test
    void normalChildExitStopsWithoutLaunchingAnotherChild() throws Exception {
        Path launches = temp.resolve("normal-launches.txt");
        Process supervisor = startSupervisor("normal", launches, 3);
        String output = waitFor(supervisor);

        assertEquals("1", Files.readString(launches));
        assertTrue(output.contains("started child pid="));
        assertTrue(output.contains("child exited pid=") && output.contains("code=0"));
        assertFalse(output.contains("abnormal exit detected"));
    }

    @Test
    void abnormalChildExitIsRetriedAndThenStopsAtTheConfiguredLimit() throws Exception {
        Path launches = temp.resolve("abnormal-launches.txt");
        Process supervisor = startSupervisor("abnormal", launches, 1);
        String output = waitFor(supervisor);

        assertEquals("2", Files.readString(launches));
        assertTrue(output.contains("abnormal exit detected"));
        assertTrue(output.contains("short-failure retry limit reached"));
        assertTrue(output.contains("launch=2"));
    }

    @Test
    void oomChildIsAnObservableFailureAndIsRetried() throws Exception {
        Path launches = temp.resolve("oom-launches.txt");
        Process supervisor = startSupervisor("oom", launches, 1);
        String output = waitFor(supervisor);

        assertEquals("2", Files.readString(launches));
        assertTrue(output.contains("started child pid="));
        assertTrue(output.contains("abnormal exit detected"));
        assertTrue(output.contains("short-failure retry limit reached"));
    }

    @Test
    void oomWithANonDaemonWorkerStillTerminatesTheChildJvm() throws Exception {
        Path launches = temp.resolve("oom-worker-launches.txt");
        Process supervisor = startSupervisor("oom-worker", launches, 0);
        String output = waitFor(supervisor);

        assertEquals("1", Files.readString(launches));
        assertTrue(output.contains("child exited pid=") && output.contains("code="));
        assertTrue(output.contains("short-failure retry limit reached"));
    }

    @Test
    void controlledRestartReusesTheForegroundSupervisorAndConsole() throws Exception {
        Path launches = temp.resolve("controlled-launches.txt");
        Path marker = temp.resolve(".anticrash/state/restart-request");
        Process supervisor = startSupervisor("controlled", launches, 0, marker);
        String output = waitFor(supervisor);

        assertEquals("2", Files.readString(launches));
        assertTrue(output.contains("accepted a restore restart request"));
        assertTrue(output.contains("launch=2"));
        assertEquals(0, supervisor.exitValue());
    }

    private Process startSupervisor(
            String mode, Path launches, int maxRestarts, Path... extraArguments) throws Exception {
        Path java =
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name", "").toLowerCase().contains("win")
                                ? "java.exe"
                                : "java");
        String classpath = System.getProperty("java.class.path");
        List<String> child =
                new ArrayList<>(
                        List.of(
                                java.toString(),
                                "-cp",
                                classpath,
                                SupervisorChildFixture.class.getName(),
                                mode,
                                launches.toString()));
        for (Path extraArgument : extraArguments) child.add(extraArgument.toString());

        List<String> command =
                new ArrayList<>(
                        List.of(
                                java.toString(),
                                "-cp",
                                classpath,
                                SupervisorMain.class.getName(),
                                "--workdir",
                                temp.toString(),
                                "--max-restarts",
                                Integer.toString(maxRestarts),
                                "--restart-delay-ms",
                                "0",
                                "--stable-runtime-ms",
                                "0",
                                "--language",
                                "en",
                                "--"));
        command.addAll(child);
        return new ProcessBuilder(command)
                .directory(temp.toFile())
                .redirectErrorStream(true)
                .start();
    }

    private String waitFor(Process process) throws Exception {
        try {
            assertTrue(
                    process.waitFor(20, TimeUnit.SECONDS), "Supervisor did not terminate in time");
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
