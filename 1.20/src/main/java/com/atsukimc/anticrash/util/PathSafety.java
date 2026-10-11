package com.atsukimc.anticrash.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class PathSafety {
    private PathSafety() {
    }

    public static Path requireChild(Path root, Path candidate) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot) || normalizedCandidate.equals(normalizedRoot)) {
            throw new IOException("Path escapes the AntiCrash root: " + candidate);
        }
        return normalizedCandidate;
    }

    public static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Symbolic links are not supported in a snapshot path: " + path);
        }
    }

    public static boolean isRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /** True for anything a snapshot must skip rather than read as ordinary file content. */
    public static boolean isSymlinkOrIrregular(Path path) {
        return Files.isSymbolicLink(path) || !isRegularFile(path);
    }
}
