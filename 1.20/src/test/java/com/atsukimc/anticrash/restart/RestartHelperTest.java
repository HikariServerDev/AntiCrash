package com.atsukimc.anticrash.restart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartHelperTest {
    @TempDir
    Path temp;

    @Test
    void parsesServerCommandWithoutSplittingItsArguments() throws Exception {
        Path workdir = temp.resolve("server root");
        Files.createDirectories(workdir.resolve(".anticrash/state"));
        RestartHelper.Options options = RestartHelper.Options.parse(new String[]{
                "--wait-pid", "42",
                "--marker", workdir.resolve(".anticrash/state/restart-request").toString(),
                "--workdir", workdir.toString(),
                "--language", "en",
                "--", "/usr/bin/java", "-Xmx4G", "-jar", "server jar.jar", "nogui"
        });

        assertEquals(42L, options.waitPid);
        assertEquals("en", options.language);
        assertEquals("server jar.jar", options.command.get(3));
    }

    @Test
    void rejectsMissingCommand() {
        assertThrows(IllegalArgumentException.class, () -> RestartHelper.Options.parse(new String[]{
                "--wait-pid", "42", "--marker", "/tmp/marker", "--workdir", "/tmp", "--"
        }));
    }

    @Test
    void makesJavaOomAnObservableProcessFailureWithoutDuplicatingTheFlag() {
        List<String> command = List.of("/usr/bin/java", "-Xmx1G", "-jar", "server.jar");

        List<String> hardened = RestartHelper.hardenedCommand(command);

        assertEquals("-XX:+ExitOnOutOfMemoryError", hardened.get(1));
        assertEquals("-Xmx1G", hardened.get(2));
        assertEquals(hardened, RestartHelper.hardenedCommand(hardened));
        assertEquals(List.of("/usr/bin/java", "-XX:+ExitOnOutOfMemoryError", "-jar", "server.jar"),
                RestartHelper.hardenedCommand(List.of("/usr/bin/java", "-XX:-ExitOnOutOfMemoryError",
                        "-jar", "server.jar")));
        assertEquals(List.of("server-wrapper", "java", "-jar", "server.jar"),
                RestartHelper.hardenedCommand(List.of("server-wrapper", "java", "-jar", "server.jar")));
    }

    @Test
    void retriesOnlyAbnormalOrCrashReportReplacementFailures() {
        assertTrue(RestartHelper.shouldRetry(3, false, 1));
        assertTrue(RestartHelper.shouldRetry(0, true, 1));
        assertFalse(RestartHelper.shouldRetry(0, false, 1));
        assertFalse(RestartHelper.shouldRetry(3, false, 3));
    }
}
