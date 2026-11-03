package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class CrashRawManagerTest {
    @TempDir Path temp;

    @Test
    void captureIsCommittedWithIntegrityMetadataAndVerifiedBeforeUse() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CrashRawManager manager = new CrashRawManager(temp.resolve("crash-raw"), config, logger);
        Path world = temp.resolve("world");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "level");
        Files.writeString(world.resolve("region/r.0.0.mca"), "region");

        Path raw = manager.capture(world, "a57e99da-e16b-48f4-814f-f47c56644ba5");

        assertTrue(Files.isRegularFile(raw.resolve("READY")));
        assertTrue(Files.isRegularFile(raw.resolve("checksums.sha256")));
        assertTrue(manager.find(raw.getFileName().toString()).equals(raw));

        Files.writeString(raw.resolve("world/level.dat"), "corrupt");
        assertThrows(java.io.IOException.class, () -> manager.find(raw.getFileName().toString()));
    }

    @Test
    void incompleteLegacyRawIsNotUsedForDestructiveRestore() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        CrashRawManager manager =
                new CrashRawManager(
                        temp.resolve("crash-raw"),
                        config,
                        new AntiCrashLogger(temp.resolve("logs"), config.logging));
        Path raw = temp.resolve("crash-raw/a57e99da-e16b-48f4-814f-f47c56644ba5/world");
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("level.dat"), "unverified");

        assertThrows(
                java.io.IOException.class,
                () -> manager.find("a57e99da-e16b-48f4-814f-f47c56644ba5"));
    }

    private CrashRawManager manager() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        return new CrashRawManager(
                temp.resolve("crash-raw"),
                config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));
    }

    private Path world() throws Exception {
        Path world = temp.resolve("world");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "level-1");
        Files.writeString(world.resolve("region/r.0.0.mca"), "region-unchanged");
        return world;
    }

    private static void age(Path raw, long minutes) throws Exception {
        Files.setLastModifiedTime(
                raw,
                java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis() - minutes * 60_000L));
    }

    @Test
    void laterCaptureLinksUnchangedFilesAndCopiesChangedOnes() throws Exception {
        CrashRawManager manager = manager();
        Path world = world();
        Path first = manager.capture(world, "a57e99da-e16b-48f4-814f-f47c56644ba5");
        age(first, 10);
        Files.writeString(world.resolve("level.dat"), "level-2");

        Path second = manager.capture(world, "b57e99da-e16b-48f4-814f-f47c56644ba5");

        assertTrue(
                Files.isSameFile(
                        first.resolve("world/region/r.0.0.mca"),
                        second.resolve("world/region/r.0.0.mca")));
        assertFalse(
                Files.isSameFile(
                        first.resolve("world/level.dat"), second.resolve("world/level.dat")));
        assertEquals("level-2", Files.readString(second.resolve("world/level.dat")));
        assertEquals("level-1", Files.readString(first.resolve("world/level.dat")));
        manager.verify(first);
        manager.verify(second);
    }

    @Test
    void corruptedEarlierFileIsCopiedNotLinked() throws Exception {
        CrashRawManager manager = manager();
        Path world = world();
        Path first = manager.capture(world, "a57e99da-e16b-48f4-814f-f47c56644ba5");
        age(first, 10);
        // Same size as the live file, but no longer the bytes its checksum list promises.
        Files.writeString(first.resolve("world/region/r.0.0.mca"), "region-XXXXXXXXX");

        Path second = manager.capture(world, "b57e99da-e16b-48f4-814f-f47c56644ba5");

        assertFalse(
                Files.isSameFile(
                        first.resolve("world/region/r.0.0.mca"),
                        second.resolve("world/region/r.0.0.mca")));
        manager.verify(second);
    }

    @Test
    void compactionLinksIdenticalFilesOfOlderCopiesToTheNewest() throws Exception {
        CrashRawManager manager = manager();
        Path world = world();
        Path older = manager.capture(world, "a57e99da-e16b-48f4-814f-f47c56644ba5");
        // Hide the older copy so the newer one is a full copy, as before deduplication existed.
        Path ready = older.resolve("READY");
        Path hidden = older.resolve("READY.hidden");
        Files.move(ready, hidden);
        Path newer = manager.capture(world, "b57e99da-e16b-48f4-814f-f47c56644ba5");
        Files.move(hidden, ready);
        age(older, 10);
        assertFalse(
                Files.isSameFile(
                        older.resolve("world/region/r.0.0.mca"),
                        newer.resolve("world/region/r.0.0.mca")));

        manager.compactExisting(0L).join();

        assertTrue(
                Files.isSameFile(
                        older.resolve("world/region/r.0.0.mca"),
                        newer.resolve("world/region/r.0.0.mca")));
        manager.verify(older);
        manager.verify(newer);
    }
}
