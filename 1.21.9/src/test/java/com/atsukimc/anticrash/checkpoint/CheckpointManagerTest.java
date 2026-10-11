package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.core.AntiCrashStateMachine;
import com.atsukimc.anticrash.core.InternalStateManager;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.player.PlayerSnapshotManager;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.RuntimeCapsuleManager;
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

class CheckpointManagerTest {
    @TempDir
    Path temp;

    @Test
    void bootVerifiedCheckpointIsReusedWithoutStartingAnotherLookup() throws Exception {
        Path game = temp.resolve("game");
        InternalStateManager internal = new InternalStateManager(game, message -> { });
        internal.initializeDirectories();
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(internal.logsDir(), config.logging);
        CheckpointStore store = new CheckpointStore(internal.checkpointsDir(), config, logger);
        Path checkpoint = commitCurrentCheckpoint(store);
        CheckpointManager manager = new CheckpointManager(config, internal,
                new AntiCrashStateMachine(internal), logger,
                new RuntimeCapsuleManager(logger), new PlayerSnapshotManager(config, logger));
        try {
            manager.verifyAndUseAsIncrementalBase(checkpoint);

            assertEquals("cp-000001", manager.lastCheckpointId());
            assertEquals("cp-000001", manager.transactionCheckpointId());
            assertEquals(RuntimeCapsule.CURRENT_FORMAT_VERSION,
                    manager.lastManifest().runtimeCapsuleVersion);
        } finally {
            manager.close();
        }
    }

    private static Path commitCurrentCheckpoint(CheckpointStore store) throws Exception {
        Path temporary = store.createTempDirectory();
        Path worldFile = temporary.resolve("world/level.dat");
        Files.createDirectories(worldFile.getParent());
        Files.writeString(worldFile, "world");
        Files.writeString(temporary.resolve("runtime-capsule.json"), "{}");
        Files.writeString(temporary.resolve("players.json"), "[]");
        Files.writeString(temporary.resolve("fakeplayers.json"), "[]");

        CheckpointManifest manifest = new CheckpointManifest();
        manifest.checkpointId = "cp-000001";
        manifest.runtimeCapsuleVersion = RuntimeCapsule.CURRENT_FORMAT_VERSION;
        manifest.snapshotBackend = "JAVA_INCREMENTAL";
        manifest.utcTimestamp = Instant.now().toString();
        manifest.fidelity = "OPTIMIZED";
        manifest.chunkDomainHash = "0".repeat(64);
        manifest.carpetCaptureStatus = "NOT_APPLICABLE";
        manifest.totalBytes = Files.size(worldFile);
        manifest.changedFiles = 1;
        manifest.worldFiles.add(new SnapshotFile("level.dat", manifest.totalBytes,
                Files.getLastModifiedTime(worldFile).toMillis(), Hashing.sha256(worldFile)));
        return store.commit(temporary, manifest);
    }
}
