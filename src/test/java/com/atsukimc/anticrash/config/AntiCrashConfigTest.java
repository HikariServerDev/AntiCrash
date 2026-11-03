package com.atsukimc.anticrash.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.core.InternalStateManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

class AntiCrashConfigTest {
    @TempDir Path temp;

    @Test
    void createsSafeDefaultsAndCompleteTemplate() throws Exception {
        Path configFile = temp.resolve("config/anticrash.toml");
        AntiCrashConfig config = AntiCrashConfig.load(configFile, message -> {});

        assertTrue(Files.isRegularFile(configFile));
        assertEquals("ja", config.language);
        assertEquals(30, config.checkpoint.intervalSeconds);
        assertEquals(5, config.checkpoint.shutdownTimeoutSeconds);
        assertEquals(20, config.checkpoint.targetBarrierMs);
        assertEquals(50, config.checkpoint.warnBarrierMs);
        assertEquals(100, config.checkpoint.criticalBarrierMs);
        assertEquals(0, config.checkpoint.maxChainDepth);
        assertEquals(0, config.checkpoint.baseInterval);
        assertEquals("SAFE", config.checkpoint.durabilityMode);
        assertEquals(60, config.recovery.askTimeoutSeconds);
        assertEquals("OPTIMIZED", config.recovery.fidelity);
        assertEquals("ALL", config.recovery.preloadMode);
        assertEquals(100_000, config.recovery.maxPreloadChunks);
        assertEquals(4, config.recovery.chunks.maxConcurrentLoads);
        assertFalse(config.recovery.strict.allowUnsupportedRuntimeState);
        assertFalse(config.recovery.strict.allowUnsupportedCarpetState);
        assertTrue(config.recovery.strict.requireEntityOrder);
        assertTrue(config.recovery.strict.requireBlockEntityTickerOrder);
        assertTrue(config.recovery.strict.requireTicketLedger);
        assertFalse(config.recovery.strict.players.requireCheckpointPlayers);
        assertEquals(0, config.commands.start);
        assertEquals(4, config.commands.startForce);
        assertEquals(4, config.commands.checkpoint);
        assertTrue(config.playerSnapshot.dimension);
        assertTrue(config.playerSnapshot.position);
        assertTrue(config.playerSnapshot.rotation);
        assertFalse(config.playerSnapshot.health);
        assertFalse(config.playerSnapshot.foodLevel);
        assertTrue(config.fakePlayer.snapshot.actions);
        assertTrue(config.toToml().contains("[player_snapshot.vanilla_files]"));
        assertTrue(config.toToml().contains("action_internal_timing"));
        assertTrue(config.toToml().contains("language = \"ja\""));
        assertTrue(config.toToml().contains("shutdown_timeout_seconds = 5"));
        assertTrue(config.toToml().contains("preload_mode = \"ALL\""));
        assertTrue(config.toToml().contains("max_preload_chunks = 100000"));
        assertTrue(config.toToml().contains("[recovery.chunks]"));
        assertTrue(config.toToml().contains("max_concurrent_loads = 4"));
        assertTrue(config.toToml().contains("[recovery.strict.players]"));
        assertTrue(config.toToml().contains("start_permission_level = 0"));
        assertTrue(config.toToml().contains("Y (restore) after a crash, N after a normal stop"));
        assertTrue(config.toToml().contains("ASK: ask on the server console after every exit"));
        assertTrue(config.toToml().contains("Inventory/XP/Ender Chest"));
        if (Files.getFileStore(configFile).supportsFileAttributeView("posix")) {
            assertEquals(
                    PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(configFile));
        }
    }

    @Test
    void parsesNestedSectionsAndNormalizesInvalidValues() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(
                configFile,
                """
                language = "en"
                [checkpoint]
                interval_seconds = -10
                shutdown_timeout_seconds = -1
                retention_count = 0
                [commands]
                start_permission_level = 2
                status_permission_level = 8
                [snapshot]
                backend = "not-a-backend"
                [recovery]
                policy = "not-a-policy"
                fidelity = "STRICT"
                ask_timeout_seconds = 60
                preload_mode = "ENTITY_TICKING"
                max_preload_chunks = 512
                [recovery.chunks]
                max_concurrent_loads = 8
                [recovery.strict]
                allow_unsupported_runtime_state = true
                allow_unsupported_carpet_state = true
                require_entity_order = false
                require_block_entity_ticker_order = false
                require_ticket_ledger = false
                [recovery.strict.players]
                require_checkpoint_players = true
                [player_snapshot]
                health = true
                food_level = true
                [fakeplayer.snapshot]
                action_internal_timing = false
                [logging]
                level = "TRACE"
                """);
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertEquals(30, config.checkpoint.intervalSeconds);
        assertEquals(5, config.checkpoint.shutdownTimeoutSeconds);
        assertEquals(2, config.commands.start);
        assertEquals(4, config.commands.status);
        assertEquals(1, config.checkpoint.retentionCount);
        assertEquals("AUTO", config.snapshot.backend);
        assertEquals("AUTO", config.recovery.policy);
        assertEquals("STRICT", config.recovery.fidelity);
        assertEquals(60, config.recovery.askTimeoutSeconds);
        assertEquals("ENTITY_TICKING", config.recovery.preloadMode);
        assertEquals(512, config.recovery.maxPreloadChunks);
        assertEquals(8, config.recovery.chunks.maxConcurrentLoads);
        assertTrue(config.recovery.strict.allowUnsupportedRuntimeState);
        assertTrue(config.recovery.strict.allowUnsupportedCarpetState);
        assertFalse(config.recovery.strict.requireEntityOrder);
        assertFalse(config.recovery.strict.requireBlockEntityTickerOrder);
        assertFalse(config.recovery.strict.requireTicketLedger);
        assertTrue(config.recovery.strict.players.requireCheckpointPlayers);
        assertTrue(config.playerSnapshot.health);
        assertTrue(config.playerSnapshot.foodLevel);
        assertFalse(config.fakePlayer.snapshot.actionInternalTiming);
        assertEquals("TRACE", config.logging.level);
        assertEquals("en", config.language);
        assertFalse(warnings.isEmpty());
    }

    @Test
    void parsesCheckpointPipelineAndDurabilitySettings() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(
                configFile,
                """
                [checkpoint.performance]
                target_barrier_ms = 20
                warn_barrier_ms = 30
                critical_barrier_ms = 80
                rolling_budget_ms_per_tick = 1.5
                rolling_max_budget_ms_per_tick = 2.5
                adaptive_budget = false
                [checkpoint.pipeline]
                max_inflight_generations = 3
                [checkpoint.incremental]
                base_interval = 7
                max_chain_depth = 9
                [checkpoint.io]
                background_threads = 2
                max_mb_per_second = 128
                [checkpoint.storage]
                minimum_free_mb = 512
                [checkpoint.durability]
                mode = "BALANCED"
                """);

        AntiCrashConfig config = AntiCrashConfig.load(configFile, message -> {});

        assertEquals(20, config.checkpoint.targetBarrierMs);
        assertEquals(30, config.checkpoint.warnBarrierMs);
        assertEquals(80, config.checkpoint.criticalBarrierMs);
        assertEquals(1.5, config.checkpoint.rollingBudgetMsPerTick);
        assertEquals(2.5, config.checkpoint.rollingMaxBudgetMsPerTick);
        assertFalse(config.checkpoint.adaptiveBudget);
        assertEquals(3, config.checkpoint.maxInflightGenerations);
        assertEquals(7, config.checkpoint.baseInterval);
        assertEquals(9, config.checkpoint.maxChainDepth);
        assertEquals(2, config.checkpoint.backgroundThreads);
        assertEquals(128, config.checkpoint.maxMbPerSecond);
        assertEquals(512, config.checkpoint.minimumFreeMb);
        assertEquals("BALANCED", config.checkpoint.durabilityMode);
    }

    @Test
    void fallsBackToJapaneseForUnsupportedLanguage() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(configFile, "language = \"fr\"\n");
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertEquals("ja", config.language);
        assertTrue(warnings.stream().anyMatch(message -> message.contains("ja または en")));
    }

    @Test
    void rejectsAnUnsafeChunkPreloadConfiguration() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(
                configFile,
                """
                language = "ja"
                [recovery]
                preload_mode = "EVERYTHING"
                max_preload_chunks = 100001
                """);
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertEquals("ALL", config.recovery.preloadMode);
        assertEquals(100_000, config.recovery.maxPreloadChunks);
        assertTrue(warnings.stream().anyMatch(message -> message.contains("preload_mode")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("max_preload_chunks")));
    }

    @Test
    void preservesAValidMinimalCurrentVersionFile() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        String minimal = "config_version = 1\n";
        Files.writeString(configFile, minimal);
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertEquals("ja", config.language);
        assertEquals(minimal, Files.readString(configFile));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("上書きしません")));
    }

    @Test
    void internalStateLivesSeparatelyFromUserConfig() throws Exception {
        Path gameDir = temp.resolve("game");
        InternalStateManager internal = new InternalStateManager(gameDir, message -> {});
        internal.initializeDirectories();
        internal.beginRun(null);

        assertTrue(Files.isDirectory(gameDir.resolve(".anticrash/checkpoints")));
        assertTrue(Files.isRegularFile(gameDir.resolve(".anticrash/state/run-state.json")));
        assertFalse(Files.exists(gameDir.resolve("config/anticrash.toml")));
    }

    @Test
    void malformedExistingConfigIsNeverReplacedWithDefaults() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        String malformed = "language = \"ja\"\n[recovery\npolicy = \"ASK\"\n";
        Files.writeString(configFile, malformed);

        AntiCrashConfig config = AntiCrashConfig.load(configFile, message -> {});

        assertFalse(config.loadedSafely());
        assertTrue(config.loadError() != null && !config.loadError().isBlank());
        assertEquals(malformed, Files.readString(configFile));
    }

    @Test
    void duplicateKeysFailClosedWithoutChangingTheFile() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        String duplicate = "language = \"ja\"\nlanguage = \"en\"\n";
        Files.writeString(configFile, duplicate);

        AntiCrashConfig config = AntiCrashConfig.load(configFile, message -> {});

        assertFalse(config.loadedSafely());
        assertEquals(duplicate, Files.readString(configFile));
    }

    @Test
    void failedReloadLeavesTheActiveConfigurationUntouched() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        AntiCrashConfig config = AntiCrashConfig.load(configFile, message -> {});
        config.language = "en";
        config.commands.start = 2;
        Files.writeString(configFile, "language = \"unterminated\n");

        assertThrows(java.io.IOException.class, () -> config.reloadFrom(configFile, message -> {}));
        assertEquals("en", config.language);
        assertEquals(2, config.commands.start);
    }

    @Test
    void generatedTomlEscapesAndReloadsStringValuesExactly() throws Exception {
        Path configFile = temp.resolve("config/anticrash.toml");
        AntiCrashConfig written = new AntiCrashConfig();
        written.recovery.startCommand = "/ac start \\\"safe\\\"";
        written.save(configFile);

        AntiCrashConfig loaded = AntiCrashConfig.load(configFile, message -> {});

        assertTrue(loaded.loadedSafely());
        assertEquals(written.recovery.startCommand, loaded.recovery.startCommand);
    }

    @Test
    void futureConfigVersionIsRejectedWithoutDowngrade() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        String future = "config_version = 2\nlanguage = \"en\"\n";
        Files.writeString(configFile, future);

        AntiCrashConfig config = AntiCrashConfig.load(configFile, message -> {});

        assertFalse(config.loadedSafely());
        assertEquals(future, Files.readString(configFile));
    }

    @Test
    void warnsAboutUnknownKeysInsteadOfSilentlyAcceptingTypos() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(
                configFile,
                "config_version = 1\nlanguage = \"ja\"\n"
                        + "[recovery.lock]\nallow_block_breakk = true\n");
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertTrue(config.loadedSafely());
        assertFalse(config.recovery.lock.allowBlockBreak);
        assertTrue(warnings.stream().anyMatch(message -> message.contains("allow_block_breakk")));
    }

    @Test
    void boundsValuesThatCouldOverflowTimersOrCauseUnboundedLogRotation() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(
                configFile,
                """
                [checkpoint]
                interval_seconds = 2147483647
                [recovery]
                ask_timeout_seconds = 2147483647
                temporary_ticket_release_ticks = 2147483647
                [logging]
                max_file_mb = 2147483647
                retained_files = 2147483647
                """);
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertEquals(30, config.checkpoint.intervalSeconds);
        assertEquals(60, config.recovery.askTimeoutSeconds);
        assertEquals(100, config.recovery.temporaryTicketReleaseTicks);
        assertEquals(50, config.logging.maxFileMb);
        assertEquals(10, config.logging.retainedFiles);
        assertTrue(warnings.size() >= 5);
    }

    @Test
    void warnsWhenExplicitSettingsWeakenRecoverySecurity() throws Exception {
        Path configFile = temp.resolve("anticrash.toml");
        Files.writeString(
                configFile,
                """
                [commands]
                recovery_permission_level = 0
                [snapshot]
                verify_checksums = false
                [recovery.lock]
                allow_block_place = true
                """);
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertTrue(config.loadedSafely());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("/ac recovery")));
        assertTrue(
                warnings.stream().anyMatch(message -> message.contains("verify_checksums=false")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("allow_block_place")));
    }

    @Test
    void warnsWhenExistingConfigIsWritableByOtherUsers() throws Exception {
        Path configFile = temp.resolve("shared-anticrash.toml");
        Files.writeString(configFile, "language = \"ja\"\n");
        if (!Files.getFileStore(configFile).supportsFileAttributeView("posix")) return;
        Files.setPosixFilePermissions(configFile, PosixFilePermissions.fromString("rw-rw-r--"));
        List<String> warnings = new ArrayList<>();

        AntiCrashConfig config = AntiCrashConfig.load(configFile, warnings::add);

        assertTrue(config.loadedSafely());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("所有者以外")));
    }
}
