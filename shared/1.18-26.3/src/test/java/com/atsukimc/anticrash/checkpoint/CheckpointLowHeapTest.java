package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.util.AtomicFiles;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointLowHeapTest {
    @TempDir Path temp;
    @Test void fiftyCyclesAndLargeJsonUseBoundedMemoryInSmallChildJvm() throws Exception {
        String cp = String.join(java.io.File.pathSeparator,
                Path.of(CheckpointLowHeapWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Path.of(AtomicFiles.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Path.of(Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        Path output = temp.resolve("child.log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Xmx32m", "-cp", cp, CheckpointLowHeapWorker.class.getName(), temp.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(child.waitFor(45, TimeUnit.SECONDS), "bounded test deadline");
            assertEquals(0, child.exitValue(), Files.readString(output));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
        Process legacy = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Xmx32m", "-cp", cp, CheckpointLowHeapWorker.class.getName(), temp.toString(), "legacy-whole-string")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(legacy.waitFor(45, TimeUnit.SECONDS));
            assertNotEquals(0, legacy.exitValue());
            assertTrue(Files.readString(output).contains("OutOfMemoryError"), Files.readString(output));
        } finally { if (legacy.isAlive()) legacy.destroyForcibly(); }
    }
}
