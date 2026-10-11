package com.atsukimc.anticrash.recovery;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRestorerTest {
    @TempDir
    Path temp;

    @Test
    void restoresByDirectoryExchangeAndKeepsExcludedLiveFiles() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);
        CheckpointStore store = new CheckpointStore(temp.resolve("checkpoints"), config, logger);
        Path checkpoint = createCheckpoint(store, "cp-000001", "checkpoint-world");
        Path live = temp.resolve("world");
        Files.createDirectories(live.resolve("playerdata"));
        Files.writeString(live.resolve("level.dat"), "live-world");
        Files.writeString(live.resolve("obsolete.dat"), "must-be-removed");
        Files.writeString(live.resolve("playerdata/player.dat"), "external-player-data");

        WorldRestorer restorer = new WorldRestorer(store, config, logger, temp.resolve("internal"));
        restorer.restoreCheckpoint(checkpoint, live);

        assertEquals("checkpoint-world", Files.readString(live.resolve("level.dat")));
        assertFalse(Files.exists(live.resolve("obsolete.dat")));
        assertEquals("external-player-data",
                Files.readString(live.resolve("playerdata/player.dat")));
        assertFalse(Files.exists(temp.resolve("internal/restore-work/restore-transaction.json")));
    }

    private static Path createCheckpoint(CheckpointStore store, String id, String payload)
            throws Exception {
        Path temporary = store.createTempDirectory();
        Path worldFile = temporary.resolve("world/level.dat");
        Files.createDirectories(worldFile.getParent());
        Files.writeString(worldFile, payload);
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");

        CheckpointManifest manifest = new CheckpointManifest();
        manifest.formatVersion = 2;
        manifest.runtimeCapsuleVersion = 1;
        manifest.checkpointId = id;
        manifest.snapshotBackend = "JAVA_INCREMENTAL";
        manifest.utcTimestamp = Instant.now().toString();
        manifest.totalBytes = Files.size(worldFile);
        manifest.changedFiles = 1;
        manifest.worldFiles.add(new SnapshotFile("level.dat", Files.size(worldFile),
                Files.getLastModifiedTime(worldFile).toMillis(), Hashing.sha256(worldFile)));
        return store.commit(temporary, manifest);
    }
}
