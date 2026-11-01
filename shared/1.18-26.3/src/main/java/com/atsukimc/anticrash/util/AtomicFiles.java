package com.atsukimc.anticrash.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Small, deliberately conservative atomic-file helpers for internal state. */
public final class AtomicFiles {
    public static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(
                            com.atsukimc.anticrash.snapshot.CheckpointManifest.class,
                            (com.google.gson.InstanceCreator<
                                            com.atsukimc.anticrash.snapshot.CheckpointManifest>)
                                    type -> {
                                        var manifest =
                                                new com.atsukimc.anticrash.snapshot
                                                        .CheckpointManifest();
                                        // Unversioned checkpoints predate the multi-version port
                                        // and belong to
                                        // the original 1.18.2 engine. Never stamp them with this
                                        // port's version.
                                        manifest.minecraftVersion = "1.18.2";
                                        return manifest;
                                    })
                    .create();

    private AtomicFiles() {}

    public static void writeText(Path target, String content) throws IOException {
        com.atsukimc.anticrash.snapshot.CheckpointWriteGuard.requireMutable(target);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null)
            throw new IOException("Target has no parent directory: " + normalizedTarget);
        Files.createDirectories(parent);

        // A fixed "<name>.tmp" races when two state updates overlap.  A unique file also
        // prevents a stale temporary left by a killed process from being reused.
        String prefix = "." + normalizedTarget.getFileName() + ".";
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        FileSecurity.hardenCreatedFile(temporary);
        boolean moved = false;
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            force(temporary);
            moveAtomically(temporary, normalizedTarget);
            moved = true;
            forceDirectory(parent);
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public static <T> void writeJson(Path target, T value) throws IOException {
        com.atsukimc.anticrash.snapshot.CheckpointWriteGuard.requireMutable(target);
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("Target has no parent directory: " + normalized);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + normalized.getFileName() + ".", ".tmp");
        FileSecurity.hardenCreatedFile(temporary);
        boolean moved = false;
        try {
            // Never allocate a second whole-capsule/manifest JSON String on the finalizer.
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            force(temporary);
            moveAtomically(temporary, normalized);
            moved = true;
            forceDirectory(parent);
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public static void writeLines(Path target, Iterable<String> lines) throws IOException {
        com.atsukimc.anticrash.snapshot.CheckpointWriteGuard.requireMutable(target);
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IOException("Target has no parent: " + normalized);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + normalized.getFileName() + ".", ".tmp");
        FileSecurity.hardenCreatedFile(temporary);
        boolean moved = false;
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            force(temporary);
            moveAtomically(temporary, normalized);
            moved = true;
            forceDirectory(parent);
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public static <T> T readJson(Path source, Class<T> type) throws IOException {
        return readJson(source, type, 128L * 1024L * 1024L);
    }

    public static <T> T readJson(Path source, Class<T> type, long maxBytes) throws IOException {
        try (var reader = boundedJsonReader(source, maxBytes)) {
            T value = GSON.fromJson(reader, type);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                throw new IOException("Trailing JSON data: " + source);
            return value;
        }
    }

    /** Bounded streaming JSON, including files that grow after stat; never buffers whole text. */
    public static com.google.gson.stream.JsonReader boundedJsonReader(Path source, long maxBytes)
            throws IOException {
        return new com.google.gson.stream.JsonReader(boundedTextReader(source, maxBytes));
    }

    public static java.io.BufferedReader boundedTextReader(Path source, long maxBytes)
            throws IOException {
        if (maxBytes < 0
                || !Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source)
                || Files.size(source) > maxBytes) {
            throw new IOException("Unsafe or oversized JSON: " + source);
        }
        InputStream bounded =
                new java.io.FilterInputStream(
                        Files.newInputStream(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    private long remaining = maxBytes;

                    private void consume(int count) throws IOException {
                        if (count > 0 && (remaining -= count) < 0)
                            throw new IOException("JSON exceeds size limit: " + source);
                    }

                    @Override
                    public int read() throws IOException {
                        int value = super.read();
                        consume(value < 0 ? 0 : 1);
                        return value;
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        int count = in.read(bytes, offset, length);
                        consume(count);
                        return count;
                    }
                };
        return new java.io.BufferedReader(
                new java.io.InputStreamReader(bounded, StandardCharsets.UTF_8), 8192);
    }

    /** Reads a regular file without following its final symlink and enforces a race-safe cap. */
    public static String readText(Path source, long maxBytes) throws IOException {
        if (maxBytes < 0 || maxBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes must fit in a Java array");
        }
        if (!Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source)) {
            throw new IOException("Not a safe regular file: " + source);
        }
        long size = Files.size(source);
        if (size > maxBytes) throw new IOException("File is too large: " + source);
        try (InputStream input =
                        Files.newInputStream(source, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream((int) Math.min(size, 8192L))) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes)
                    throw new IOException("File grew beyond its size limit: " + source);
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    public static void force(Path file) throws IOException {
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    public static void forceDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var channel = java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Directory fsync is not available on every supported filesystem.
        }
    }

    public static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
