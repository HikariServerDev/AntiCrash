package com.atsukimc.anticrash.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFilesTest {
    @Test
    void streamingChecksumIsByteCompatibleAndJsonRejectsTrailingData() throws Exception {
        List<String> lines = List.of("abc  world/level.dat", "日本語  world/data/スコア.dat");
        assertEquals(Hashing.sha256(String.join("\n", lines)), Hashing.sha256JoinedLines(lines));
        assertEquals(Hashing.sha256(""), Hashing.sha256JoinedLines(List.of()));
        Path target = temp.resolve("checksums"); AtomicFiles.writeLines(target, lines);
        assertEquals(String.join(System.lineSeparator(), lines) + System.lineSeparator(), Files.readString(target));
        Files.writeString(temp.resolve("trailing.json"), "{}{}");
        assertThrows(Exception.class, () -> AtomicFiles.readJson(temp.resolve("trailing.json"), Object.class, 100));
    }
    @TempDir
    Path temp;

    @Test
    void concurrentWritersPublishOneCompleteValueWithoutTempCollisions() throws Exception {
        Path target = temp.resolve("state/recovery-state.json");
        List<String> candidates = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            candidates.add("writer-" + index + "\n" + String.valueOf((char) ('a' + index)).repeat(16_384));
        }

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(candidates.size());
        try {
            var futures = candidates.stream().map(value -> executor.submit(() -> {
                start.await();
                AtomicFiles.writeText(target, value);
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }

        assertTrue(candidates.contains(Files.readString(target)));
        if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
            assertEquals(PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(target));
        }
        try (var files = Files.list(target.getParent())) {
            assertEquals(List.of("recovery-state.json"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void boundedReadsAndHashesRejectOversizedFilesAndSymlinks() throws Exception {
        Path file = temp.resolve("large.txt");
        Files.writeString(file, "12345");
        assertThrows(java.io.IOException.class, () -> AtomicFiles.readText(file, 4));

        Path link = temp.resolve("link.txt");
        Files.createSymbolicLink(link, file.getFileName());
        assertThrows(java.io.IOException.class, () -> AtomicFiles.readText(link, 1024));
        assertThrows(java.io.IOException.class, () -> Hashing.sha256(link));
    }
}
