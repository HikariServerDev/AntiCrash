package com.atsukimc.anticrash.restart;

import com.atsukimc.anticrash.util.AtomicFiles;
import com.atsukimc.anticrash.util.FileSecurity;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/**
 * One-shot handoff between the mod, the foreground Supervisor, and the detached compatibility
 * restart helper. The marker is consumed before a replacement process starts, so one restore
 * request can produce at most one protocol-driven restart.
 */
public final class RestartRequest {
    public static final String SUPERVISED_ENV = "ANTICRASH_SUPERVISED";
    public static final String MARKER_ENV = "ANTICRASH_RESTART_MARKER";
    private static final String MARKER_NAME = "restart-request";

    private RestartRequest() {}

    public static Path markerPath(Path gameDirectory) {
        Path expected =
                gameDirectory
                        .toAbsolutePath()
                        .normalize()
                        .resolve(".anticrash")
                        .resolve("state")
                        .resolve(MARKER_NAME);
        String configured = System.getenv(MARKER_ENV);
        if (configured != null && !configured.isBlank()) {
            // The Supervisor exports this value as a protocol consistency check, not as an
            // arbitrary write target. Never let an inherited environment redirect an internal
            // marker outside the server root.
            Path supplied = Path.of(configured).toAbsolutePath().normalize();
            if (supplied.equals(expected)) return supplied;
        }
        return expected;
    }

    public static void create(Path marker) throws IOException {
        Path normalized = marker.toAbsolutePath().normalize();
        ensureSafeMarkerLocation(normalized);
        Path temporary =
                normalized.resolveSibling(normalized.getFileName() + ".tmp-" + UUID.randomUUID());
        String content =
                "request="
                        + UUID.randomUUID()
                        + System.lineSeparator()
                        + "created_at="
                        + Instant.now()
                        + System.lineSeparator();
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            FileSecurity.hardenCreatedFile(temporary);
            force(temporary);
            try {
                Files.move(
                        temporary,
                        normalized,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(normalized.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static boolean claim(Path marker) throws IOException {
        Path normalized = marker.toAbsolutePath().normalize();
        ensureSafeMarkerLocation(normalized);
        if (!Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return false;
        validateMarker(normalized);
        boolean deleted = Files.deleteIfExists(normalized);
        if (deleted) forceDirectory(normalized.getParent());
        return deleted;
    }

    private static void validateMarker(Path marker) throws IOException {
        String request = null;
        String createdAt = null;
        for (String rawLine : AtomicFiles.readText(marker, 1024L).split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("Malformed AntiCrash restart marker");
            String key = line.substring(0, equals);
            String value = line.substring(equals + 1);
            if (key.equals("request") && request == null) request = value;
            else if (key.equals("created_at") && createdAt == null) createdAt = value;
            else throw new IOException("Malformed AntiCrash restart marker");
        }
        try {
            if (request == null || createdAt == null)
                throw new IllegalArgumentException("missing field");
            UUID.fromString(request);
            Instant.parse(createdAt);
        } catch (RuntimeException exception) {
            throw new IOException("Malformed AntiCrash restart marker", exception);
        }
    }

    public static void cancel(Path marker) {
        try {
            Path normalized = marker.toAbsolutePath().normalize();
            ensureSafeMarkerLocation(normalized);
            Files.deleteIfExists(normalized);
        } catch (IOException ignored) {
            // The original preparation failure remains the actionable error.
        }
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void ensureSafeMarkerLocation(Path marker) throws IOException {
        Path parent = marker.getParent();
        if (parent == null
                || !Files.isDirectory(parent, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent)) {
            throw new IOException("Unsafe AntiCrash restart marker directory: " + parent);
        }
        Path internal = parent.getParent();
        if (internal != null
                && (Files.isSymbolicLink(internal)
                        || !Files.isDirectory(internal, java.nio.file.LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Unsafe AntiCrash internal directory: " + internal);
        }
        if (Files.exists(marker, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(marker)
                        || !Files.isRegularFile(marker, java.nio.file.LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Unsafe AntiCrash restart marker: " + marker);
        }
    }

    private static void forceDirectory(Path directory) {
        if (directory == null) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Directory fsync is unavailable on some supported filesystems.
        }
    }
}
