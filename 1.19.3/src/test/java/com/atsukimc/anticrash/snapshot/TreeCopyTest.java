package com.atsukimc.anticrash.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeCopyTest {
    @TempDir
    Path temp;

    @Test
    void copiesDirectoryRootWithoutTreatingItAsAnUnsafeChild() throws Exception {
        Path source = temp.resolve("source");
        Path destination = temp.resolve("destination");
        Files.createDirectories(source.resolve("nested"));
        Files.writeString(source.resolve("nested/data.txt"), "payload");

        TreeCopy.copy(source, destination);

        assertEquals("payload", Files.readString(destination.resolve("nested/data.txt")));
    }
}
