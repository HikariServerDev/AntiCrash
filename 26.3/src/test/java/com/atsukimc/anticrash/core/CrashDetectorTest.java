package com.atsukimc.anticrash.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;


class CrashDetectorTest {
    @TempDir
    Path temp;

    @Test
    void ignoresCrashReportsThatPredateTheRun() throws Exception {
        RunMarker previous = marker(Instant.now().minusSeconds(30));
        Path reports = temp.resolve("crash-reports");
        Files.createDirectories(reports);
        Path stale = reports.resolve("stale.txt");
        Files.writeString(stale, "java.lang.OutOfMemoryError");
        Files.setLastModifiedTime(stale,
                FileTime.from(Instant.parse(previous.startedAtUtc).minusSeconds(60)));

        assertEquals(CrashCause.UNKNOWN_DIRTY_EXIT, CrashDetector.classify(temp, previous));
    }

    @Test
    void classifiesOnlyFreshCrashEvidence() throws Exception {
        RunMarker previous = marker(Instant.now().minusSeconds(30));
        Path reports = temp.resolve("crash-reports");
        Files.createDirectories(reports);
        Path fresh = reports.resolve("fresh.txt");
        Files.writeString(fresh, "Caused by: java.lang.OutOfMemoryError: Java heap space");
        Files.setLastModifiedTime(fresh, FileTime.from(Instant.now()));

        assertEquals(CrashCause.OUT_OF_MEMORY, CrashDetector.classify(temp, previous));
    }

    @Test
    void watchdogIsNotMisclassifiedByExitOnOutOfMemoryFlag() throws Exception {
        RunMarker previous = marker(Instant.now().minusSeconds(30));
        Path reports = temp.resolve("crash-reports");
        Files.createDirectories(reports);
        Path fresh = reports.resolve("watchdog.txt");
        Files.writeString(fresh, "Description: Watching Server\n"
                + "JVM Flags: -XX:+ExitOnOutOfMemoryError -Xms1G -Xmx1G\n"
                + "Server thread dump\n");
        Files.setLastModifiedTime(fresh, FileTime.from(Instant.now()));

        assertEquals(CrashCause.WATCHDOG, CrashDetector.classify(temp, previous));
    }

    @Test
    void usesTheBoundedTailOfLatestLog() throws Exception {
        RunMarker previous = marker(Instant.now().minusSeconds(30));
        Path logs = temp.resolve("logs");
        Files.createDirectories(logs);
        Path latest = logs.resolve("latest.log");
        Files.writeString(latest, "OutOfMemoryError\n" + "ordinary log line\n".repeat(70_000));
        Files.setLastModifiedTime(latest, FileTime.from(Instant.now()));

        assertEquals(CrashCause.UNKNOWN_DIRTY_EXIT, CrashDetector.classify(temp, previous));

        Files.writeString(latest, "[Server thread/ERROR] Exception in server tick loop\n");
        assertEquals(CrashCause.JAVA_EXCEPTION, CrashDetector.classify(temp, previous));
    }

    private static RunMarker marker(Instant started) {
        RunMarker marker = new RunMarker();
        marker.runId = "a57e99da-e16b-48f4-814f-f47c56644ba5";
        marker.startedAtUtc = started.toString();
        return marker;
    }
}
