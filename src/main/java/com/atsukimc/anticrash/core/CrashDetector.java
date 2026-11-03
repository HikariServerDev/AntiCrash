package com.atsukimc.anticrash.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;

public final class CrashDetector {
    private static final int MAX_LOG_TAIL_BYTES = 1024 * 1024;

    private CrashDetector() {}

    public static CrashCause classify(Path gameDir, RunMarker previous) {
        try {
            long startedAt =
                    previous == null || previous.startedAtUtc == null
                            ? Long.MIN_VALUE
                            : Instant.parse(previous.startedAtUtc).toEpochMilli();
            long earliestRelevant =
                    startedAt == Long.MIN_VALUE ? Long.MIN_VALUE : startedAt - 5_000L;
            Path reports = gameDir.resolve("crash-reports");
            if (Files.isDirectory(reports))
                try (var stream = Files.list(reports)) {
                    Path latest =
                            stream.filter(Files::isRegularFile)
                                    .filter(path -> mtime(path) >= earliestRelevant)
                                    .max(Comparator.comparingLong(CrashDetector::mtime))
                                    .orElse(null);
                    if (latest != null) {
                        String text = readTail(latest).toLowerCase(Locale.ROOT);
                        if (isWatchdog(text)) return CrashCause.WATCHDOG;
                        if (isOutOfMemory(text)) return CrashCause.OUT_OF_MEMORY;
                        return CrashCause.CRASH_REPORT;
                    }
                }
            Path log = gameDir.resolve("logs").resolve("latest.log");
            if (Files.isRegularFile(log) && mtime(log) >= earliestRelevant) {
                String text = readTail(log).toLowerCase(Locale.ROOT);
                if (isWatchdog(text)) return CrashCause.WATCHDOG;
                if (isOutOfMemory(text)) return CrashCause.OUT_OF_MEMORY;
                if (text.contains("exception in server tick loop")
                        || text.contains("uncaught exception in server thread"))
                    return CrashCause.JAVA_EXCEPTION;
            }
        } catch (Exception ignored) {
        }
        return CrashCause.UNKNOWN_DIRTY_EXIT;
    }

    private static String readTail(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            int length = (int) Math.min(size, MAX_LOG_TAIL_BYTES);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.position(size - length);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {}
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isWatchdog(String text) {
        return text.contains("description: watching server")
                || text.contains("watchdog")
                || text.contains("server thread dump");
    }

    private static boolean isOutOfMemory(String text) {
        // Do not match the JVM option -XX:+ExitOnOutOfMemoryError. It is present in every crash
        // report for this server, including pure watchdog reports.
        return text.contains("java.lang.outofmemoryerror")
                || text.contains("java heap space")
                || text.contains("gc overhead limit exceeded")
                || text.contains("unable to create native thread");
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
