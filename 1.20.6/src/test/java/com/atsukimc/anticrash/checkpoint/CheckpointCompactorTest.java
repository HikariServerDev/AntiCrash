package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;
import com.atsukimc.anticrash.snapshot.CheckpointStore;
import com.atsukimc.anticrash.snapshot.SnapshotFile;
import com.atsukimc.anticrash.util.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointCompactorTest {
    @TempDir
    Path temp;

    @Test
    void createsAnAuthenticatedLogicalDeltaRootWithoutReadingLiveWorld() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        config.checkpoint.retentionDays = 0;
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config,
                new AntiCrashLogger(temp.resolve("logs"), config.logging));

        Path temporary = store.createTempDirectory();
        Path worldFile = temporary.resolve("world/level.dat");
        Files.createDirectories(worldFile.getParent());
        Files.writeString(worldFile, "authenticated-world");
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");

        CheckpointManifest sourceManifest = new CheckpointManifest();
        sourceManifest.checkpointId = temporary.getFileName().toString().replace(".tmp", "");
        sourceManifest.snapshotBackend = "JAVA_INCREMENTAL";
        sourceManifest.utcTimestamp = Instant.now().toString();
        SnapshotFile world = new SnapshotFile("level.dat", Files.size(worldFile),
                Files.getLastModifiedTime(worldFile).toMillis(), Hashing.sha256(worldFile));
        sourceManifest.worldFiles.add(world);
        sourceManifest.totalBytes = world.size;
        sourceManifest.changedFiles = 1;

        Path source = store.commit(temporary, sourceManifest);
        Path compacted = CheckpointCompactor.compact(store, source);
        store.verify(compacted);

        CheckpointManifest compactedManifest = store.readManifest(compacted);
        assertEquals("LOGICAL_DELTA", compactedManifest.checkpointEngine);
        assertEquals("LOGICAL_DELTA", compactedManifest.snapshotBackend);
        assertEquals("BASE", compactedManifest.generationType);
        assertNull(compactedManifest.parentCheckpointId);
        assertTrue(Files.isRegularFile(compacted.resolve("logical-delta.json")));
        assertEquals("authenticated-world",
                Files.readString(compacted.resolve("world/level.dat")));
    }
}
