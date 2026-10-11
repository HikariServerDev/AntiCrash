package com.atsukimc.anticrash.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {
    private static final int BUFFER_SIZE = 64 * 1024;

    private Hashing() {
    }

    public static String sha256(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Refusing to hash a non-regular or symbolic-link file: " + file);
        }
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Hashing was interrupted: " + file);
                }
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hashes a sequence without first creating one large joined String. A newline delimiter and
     * an explicit item boundary make order and count changes visible while keeping the temporary
     * allocation proportional to one item.
     */
    public static String sha256Lines(Iterable<?> lines) {
        MessageDigest digest = digest();
        if (lines != null) {
            for (Object line : lines) {
                byte[] bytes = String.valueOf(line).getBytes(StandardCharsets.UTF_8);
                digest.update(bytes);
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Same bytes as String.join("\n", lines), without allocating that joined checksum table. */
    public static String sha256JoinedLines(Iterable<String> lines) {
        MessageDigest digest = digest();
        boolean first = true;
        for (String line : lines) {
            if (!first) digest.update((byte) '\n');
            digest.update(line.getBytes(StandardCharsets.UTF_8));
            first = false;
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }
}
