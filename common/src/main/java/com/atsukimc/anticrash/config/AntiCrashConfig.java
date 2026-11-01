package com.atsukimc.anticrash.config;

import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.atsukimc.anticrash.util.FileSecurity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** User-editable configuration. Runtime state never lives in this object/file. */
public final class AntiCrashConfig {
    public static final int CURRENT_VERSION = 1;

    public int configVersion = CURRENT_VERSION;
    public String language = MessageCatalog.DEFAULT_LANGUAGE;
    public final Checkpoint checkpoint = new Checkpoint();
    public final CommandPermissions commands = new CommandPermissions();
    public final Snapshot snapshot = new Snapshot();
    public final Recovery recovery = new Recovery();
    public final PlayerSnapshot playerSnapshot = new PlayerSnapshot();
    public final FakePlayer fakePlayer = new FakePlayer();
    public final Consistency consistency = new Consistency();
    public final Login login = new Login();
    public final Notifications notifications = new Notifications();
    public final Logging logging = new Logging();
    private String loadError;

    public static AntiCrashConfig load(Path file, Consumer<String> warning) {
        AntiCrashConfig config = new AntiCrashConfig();
        if (!Files.exists(file)) {
            try {
                config.save(file);
            } catch (Exception exception) {
                config.loadError = exception.toString();
                warning.accept(
                        MessageCatalog.text(
                                config.language,
                                "config.write_default_failed",
                                exception.getMessage()));
            }
            return config;
        }
        try {
            if (Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Configuration is not a regular file: " + file);
            }
            Map<String, String> values = Toml.parse(AtomicFiles.readText(file, 4L * 1024L * 1024L));
            boolean templateIncomplete =
                    !values.containsKey("language")
                            || !values.containsKey("checkpoint.shutdown_timeout_seconds")
                            || !values.containsKey("commands.start_permission_level");
            config.apply(values, warning);
            if (config.configVersion > CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported future config_version: " + config.configVersion);
            }
            config.validate(warning);
            warnUnknownKeys(values, warning, config.language);
            warnUnsafeConfigPermissions(file, warning, config.language);
            // Never rewrite a current, valid user file merely because it is intentionally
            // minimal. Doing so would discard comments and extension keys. Only an actual older
            // schema migration may replace it, and only after successful parsing/validation.
            if (config.configVersion < CURRENT_VERSION) {
                config.configVersion = CURRENT_VERSION;
                try {
                    config.save(file);
                } catch (IOException exception) {
                    warning.accept(
                            MessageCatalog.text(
                                    config.language,
                                    "config.write_default_failed",
                                    exception.getMessage()));
                }
            } else if (templateIncomplete) {
                warning.accept(MessageCatalog.text(config.language, "config.incomplete_template"));
            }
        } catch (Exception exception) {
            config.loadError = exception.toString();
            warning.accept(
                    MessageCatalog.text(
                            config.language, "config.read_failed", exception.getMessage()));
            // Never replace an existing, unreadable user configuration with defaults.  Keeping
            // the original file is essential both for diagnosis and to avoid silently changing
            // recovery/permission policy after one malformed line.
        }
        return config;
    }

    public boolean loadedSafely() {
        return loadError == null;
    }

    public String loadError() {
        return loadError;
    }

    public void reloadFrom(Path file, Consumer<String> warning) throws IOException {
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file))
            throw new IOException("Configuration file does not exist or is unsafe: " + file);
        AntiCrashConfig loaded = new AntiCrashConfig();
        try {
            Map<String, String> values = Toml.parse(AtomicFiles.readText(file, 4L * 1024L * 1024L));
            loaded.apply(values, warning);
            if (loaded.configVersion > CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported future config_version: " + loaded.configVersion);
            }
            loaded.validate(warning);
            warnUnknownKeys(values, warning, loaded.language);
            warnUnsafeConfigPermissions(file, warning, loaded.language);
            loaded.configVersion = CURRENT_VERSION;
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException(
                    "Invalid AntiCrash configuration: " + exception.getMessage(), exception);
        }
        configVersion = loaded.configVersion;
        copyFrom(loaded);
    }

    private void copyFrom(AntiCrashConfig other) {
        language = other.language;
        checkpoint.copyFrom(other.checkpoint);
        commands.copyFrom(other.commands);
        snapshot.copyFrom(other.snapshot);
        recovery.copyFrom(other.recovery);
        playerSnapshot.copyFrom(other.playerSnapshot);
        fakePlayer.copyFrom(other.fakePlayer);
        consistency.copyFrom(other.consistency);
        login.copyFrom(other.login);
        notifications.copyFrom(other.notifications);
        logging.copyFrom(other.logging);
    }

    public void save(Path file) throws IOException {
        AtomicFiles.writeText(file, toToml());
    }

    public void apply(Map<String, String> values, Consumer<String> warning) {
        configVersion = integer(values, "config_version", configVersion, warning);
        language = string(values, "language", language);

        checkpoint.enabled = bool(values, "checkpoint.enabled", checkpoint.enabled, warning);
        checkpoint.intervalSeconds =
                integer(values, "checkpoint.interval_seconds", checkpoint.intervalSeconds, warning);
        checkpoint.createOnManualCommand =
                bool(
                        values,
                        "checkpoint.create_on_manual_command",
                        checkpoint.createOnManualCommand,
                        warning);
        checkpoint.createOnCleanShutdown =
                bool(
                        values,
                        "checkpoint.create_on_clean_shutdown",
                        checkpoint.createOnCleanShutdown,
                        warning);
        checkpoint.shutdownTimeoutSeconds =
                integer(
                        values,
                        "checkpoint.shutdown_timeout_seconds",
                        checkpoint.shutdownTimeoutSeconds,
                        warning);
        checkpoint.retentionCount =
                integer(values, "checkpoint.retention_count", checkpoint.retentionCount, warning);
        checkpoint.retentionDays =
                integer(values, "checkpoint.retention_days", checkpoint.retentionDays, warning);
        checkpoint.retentionMaxGb =
                integer(values, "checkpoint.retention_max_gb", checkpoint.retentionMaxGb, warning);
        checkpoint.saveBatchChunks =
                integer(
                        values,
                        "checkpoint.save_batch_chunks",
                        checkpoint.saveBatchChunks,
                        warning);
        checkpoint.warnBarrierMs =
                integer(
                        values,
                        "checkpoint.performance.warn_barrier_ms",
                        checkpoint.warnBarrierMs,
                        warning);
        checkpoint.warnCheckpointMs =
                integer(
                        values,
                        "checkpoint.performance.warn_checkpoint_ms",
                        checkpoint.warnCheckpointMs,
                        warning);
        checkpoint.targetBarrierMs =
                integer(
                        values,
                        "checkpoint.performance.target_barrier_ms",
                        checkpoint.targetBarrierMs,
                        warning);
        checkpoint.criticalBarrierMs =
                integer(
                        values,
                        "checkpoint.performance.critical_barrier_ms",
                        checkpoint.criticalBarrierMs,
                        warning);
        checkpoint.rollingEnabled =
                bool(
                        values,
                        "checkpoint.performance.rolling_enabled",
                        checkpoint.rollingEnabled,
                        warning);
        checkpoint.rollingBudgetMsPerTick =
                decimal(
                        values,
                        "checkpoint.performance.rolling_budget_ms_per_tick",
                        checkpoint.rollingBudgetMsPerTick,
                        warning);
        checkpoint.rollingMaxBudgetMsPerTick =
                decimal(
                        values,
                        "checkpoint.performance.rolling_max_budget_ms_per_tick",
                        checkpoint.rollingMaxBudgetMsPerTick,
                        warning);
        checkpoint.adaptiveBudget =
                bool(
                        values,
                        "checkpoint.performance.adaptive_budget",
                        checkpoint.adaptiveBudget,
                        warning);
        checkpoint.warnFinalizeMs =
                integer(
                        values,
                        "checkpoint.performance.warn_finalize_ms",
                        checkpoint.warnFinalizeMs,
                        warning);
        checkpoint.maxInflightGenerations =
                integer(
                        values,
                        "checkpoint.pipeline.max_inflight_generations",
                        checkpoint.maxInflightGenerations,
                        warning);
        checkpoint.baseInterval =
                integer(
                        values,
                        "checkpoint.incremental.base_interval",
                        checkpoint.baseInterval,
                        warning);
        checkpoint.maxChainDepth =
                integer(
                        values,
                        "checkpoint.incremental.max_chain_depth",
                        checkpoint.maxChainDepth,
                        warning);
        checkpoint.backgroundThreads =
                integer(
                        values,
                        "checkpoint.io.background_threads",
                        checkpoint.backgroundThreads,
                        warning);
        checkpoint.maxMbPerSecond =
                integer(
                        values,
                        "checkpoint.io.max_mb_per_second",
                        checkpoint.maxMbPerSecond,
                        warning);
        checkpoint.minimumFreeMb =
                integer(
                        values,
                        "checkpoint.storage.minimum_free_mb",
                        checkpoint.minimumFreeMb,
                        warning);
        checkpoint.durabilityMode =
                string(values, "checkpoint.durability.mode", checkpoint.durabilityMode);
        checkpoint.engineMode = string(values, "checkpoint.engine.mode", checkpoint.engineMode);
        checkpoint.engineFallback =
                string(values, "checkpoint.engine.fallback", checkpoint.engineFallback);
        checkpoint.singleInflight =
                bool(
                        values,
                        "checkpoint.engine.single_inflight",
                        checkpoint.singleInflight,
                        warning);
        checkpoint.candidateCacheMaxMb =
                integer(values, "checkpoint.cache.max_mb", checkpoint.candidateCacheMaxMb, warning);
        checkpoint.candidateBudgetMsPerTick =
                decimal(
                        values,
                        "checkpoint.cache.budget_ms_per_tick",
                        checkpoint.candidateBudgetMsPerTick,
                        warning);
        checkpoint.mirrorEnabled =
                bool(values, "checkpoint.mirror.enabled", checkpoint.mirrorEnabled, warning);
        checkpoint.mirrorAuditIntervalSeconds =
                integer(
                        values,
                        "checkpoint.mirror.audit_interval_seconds",
                        checkpoint.mirrorAuditIntervalSeconds,
                        warning);
        checkpoint.mirrorTraceLimit =
                integer(
                        values,
                        "checkpoint.mirror.trace_limit",
                        checkpoint.mirrorTraceLimit,
                        warning);
        checkpoint.allowUnsupportedPersistentState =
                bool(
                        values,
                        "checkpoint.engine.allow_unsupported_persistent_state",
                        checkpoint.allowUnsupportedPersistentState,
                        warning);

        commands.help = integer(values, "commands.help_permission_level", commands.help, warning);
        commands.status =
                integer(values, "commands.status_permission_level", commands.status, warning);
        commands.start =
                integer(values, "commands.start_permission_level", commands.start, warning);
        commands.startForce =
                integer(
                        values,
                        "commands.start_force_permission_level",
                        commands.startForce,
                        warning);
        commands.freeze =
                integer(values, "commands.freeze_permission_level", commands.freeze, warning);
        commands.checkpoint =
                integer(
                        values,
                        "commands.checkpoint_permission_level",
                        commands.checkpoint,
                        warning);
        commands.recovery =
                integer(values, "commands.recovery_permission_level", commands.recovery, warning);
        commands.fakeplayers =
                integer(
                        values,
                        "commands.fakeplayers_permission_level",
                        commands.fakeplayers,
                        warning);
        commands.journal =
                integer(values, "commands.journal_permission_level", commands.journal, warning);
        commands.config =
                integer(values, "commands.config_permission_level", commands.config, warning);
        commands.debug =
                integer(values, "commands.debug_permission_level", commands.debug, warning);

        snapshot.backend = string(values, "snapshot.backend", snapshot.backend);
        snapshot.verifyChecksums =
                bool(values, "snapshot.verify_checksums", snapshot.verifyChecksums, warning);
        snapshot.keepCrashRawWorld =
                bool(values, "snapshot.keep_crash_raw_world", snapshot.keepCrashRawWorld, warning);
        snapshot.crashRawRetentionCount =
                integer(
                        values,
                        "snapshot.crash_raw_retention_count",
                        snapshot.crashRawRetentionCount,
                        warning);
        snapshot.ioThreads = integer(values, "snapshot.io_threads", snapshot.ioThreads, warning);

        recovery.policy = string(values, "recovery.policy", recovery.policy);
        recovery.fidelity = string(values, "recovery.fidelity", recovery.fidelity);
        recovery.freezeAfterRestore =
                bool(values, "recovery.freeze_after_restore", recovery.freezeAfterRestore, warning);
        recovery.requireManualStart =
                bool(values, "recovery.require_manual_start", recovery.requireManualStart, warning);
        recovery.startCommand = string(values, "recovery.start_command", recovery.startCommand);
        recovery.restoreLatestValidCheckpoint =
                bool(
                        values,
                        "recovery.restore_latest_valid_checkpoint",
                        recovery.restoreLatestValidCheckpoint,
                        warning);
        recovery.allowRestoreOlderCheckpoint =
                bool(
                        values,
                        "recovery.allow_restore_older_checkpoint",
                        recovery.allowRestoreOlderCheckpoint,
                        warning);
        recovery.fallbackToPreviousCheckpoint =
                bool(
                        values,
                        "recovery.fallback_to_previous_checkpoint",
                        recovery.fallbackToPreviousCheckpoint,
                        warning);
        recovery.askTimeoutSeconds =
                integer(
                        values,
                        "recovery.ask_timeout_seconds",
                        recovery.askTimeoutSeconds,
                        warning);
        recovery.askDefaultAction =
                string(values, "recovery.ask_default_action", recovery.askDefaultAction);
        recovery.allowStartWithFailedValidation =
                bool(
                        values,
                        "recovery.allow_start_with_failed_validation",
                        recovery.allowStartWithFailedValidation,
                        warning);
        recovery.spawnRestartHelper =
                bool(values, "recovery.spawn_restart_helper", recovery.spawnRestartHelper, warning);
        recovery.temporaryTicketReleaseTicks =
                integer(
                        values,
                        "recovery.temporary_ticket_release_ticks",
                        recovery.temporaryTicketReleaseTicks,
                        warning);
        recovery.preloadMode = string(values, "recovery.preload_mode", recovery.preloadMode);
        recovery.maxPreloadChunks =
                integer(values, "recovery.max_preload_chunks", recovery.maxPreloadChunks, warning);
        recovery.chunkReadyTimeoutSeconds =
                integer(
                        values,
                        "recovery.chunk_ready_timeout_seconds",
                        recovery.chunkReadyTimeoutSeconds,
                        warning);
        recovery.chunks.maxConcurrentLoads =
                integer(
                        values,
                        "recovery.chunks.max_concurrent_loads",
                        recovery.chunks.maxConcurrentLoads,
                        warning);
        recovery.strict.allowUnsupportedRuntimeState =
                bool(
                        values,
                        "recovery.strict.allow_unsupported_runtime_state",
                        recovery.strict.allowUnsupportedRuntimeState,
                        warning);
        recovery.strict.allowUnsupportedCarpetState =
                bool(
                        values,
                        "recovery.strict.allow_unsupported_carpet_state",
                        recovery.strict.allowUnsupportedCarpetState,
                        warning);
        recovery.strict.requireEntityOrder =
                bool(
                        values,
                        "recovery.strict.require_entity_order",
                        recovery.strict.requireEntityOrder,
                        warning);
        recovery.strict.requireBlockEntityTickerOrder =
                bool(
                        values,
                        "recovery.strict.require_block_entity_ticker_order",
                        recovery.strict.requireBlockEntityTickerOrder,
                        warning);
        recovery.strict.requireTicketLedger =
                bool(
                        values,
                        "recovery.strict.require_ticket_ledger",
                        recovery.strict.requireTicketLedger,
                        warning);
        recovery.strict.players.requireCheckpointPlayers =
                bool(
                        values,
                        "recovery.strict.players.require_checkpoint_players",
                        recovery.strict.players.requireCheckpointPlayers,
                        warning);

        recovery.lock.allowLogin =
                bool(values, "recovery.lock.allow_login", recovery.lock.allowLogin, warning);
        recovery.lock.allowChat =
                bool(values, "recovery.lock.allow_chat", recovery.lock.allowChat, warning);
        recovery.lock.allowCommands =
                bool(values, "recovery.lock.allow_commands", recovery.lock.allowCommands, warning);
        recovery.lock.allowPlayerMovement =
                bool(
                        values,
                        "recovery.lock.allow_player_movement",
                        recovery.lock.allowPlayerMovement,
                        warning);
        recovery.lock.allowPlayerLook =
                bool(
                        values,
                        "recovery.lock.allow_player_look",
                        recovery.lock.allowPlayerLook,
                        warning);
        recovery.lock.allowSneak =
                bool(values, "recovery.lock.allow_sneak", recovery.lock.allowSneak, warning);
        recovery.lock.allowBlockBreak =
                bool(
                        values,
                        "recovery.lock.allow_block_break",
                        recovery.lock.allowBlockBreak,
                        warning);
        recovery.lock.allowBlockPlace =
                bool(
                        values,
                        "recovery.lock.allow_block_place",
                        recovery.lock.allowBlockPlace,
                        warning);
        recovery.lock.allowItemUse =
                bool(values, "recovery.lock.allow_item_use", recovery.lock.allowItemUse, warning);
        recovery.lock.allowEntityAttack =
                bool(
                        values,
                        "recovery.lock.allow_entity_attack",
                        recovery.lock.allowEntityAttack,
                        warning);
        recovery.lock.allowEntityInteract =
                bool(
                        values,
                        "recovery.lock.allow_entity_interact",
                        recovery.lock.allowEntityInteract,
                        warning);
        recovery.lock.allowContainerInteraction =
                bool(
                        values,
                        "recovery.lock.allow_container_interaction",
                        recovery.lock.allowContainerInteraction,
                        warning);
        recovery.lock.allowItemDrop =
                bool(values, "recovery.lock.allow_item_drop", recovery.lock.allowItemDrop, warning);
        recovery.lock.allowItemPickup =
                bool(
                        values,
                        "recovery.lock.allow_item_pickup",
                        recovery.lock.allowItemPickup,
                        warning);

        playerSnapshot.enabled =
                bool(values, "player_snapshot.enabled", playerSnapshot.enabled, warning);
        playerSnapshot.dimension =
                bool(values, "player_snapshot.dimension", playerSnapshot.dimension, warning);
        playerSnapshot.position =
                bool(values, "player_snapshot.position", playerSnapshot.position, warning);
        playerSnapshot.rotation =
                bool(values, "player_snapshot.rotation", playerSnapshot.rotation, warning);
        playerSnapshot.gamemode =
                bool(values, "player_snapshot.gamemode", playerSnapshot.gamemode, warning);
        playerSnapshot.abilities =
                bool(values, "player_snapshot.abilities", playerSnapshot.abilities, warning);
        playerSnapshot.inventoryMain =
                bool(
                        values,
                        "player_snapshot.inventory_main",
                        playerSnapshot.inventoryMain,
                        warning);
        playerSnapshot.inventoryArmor =
                bool(
                        values,
                        "player_snapshot.inventory_armor",
                        playerSnapshot.inventoryArmor,
                        warning);
        playerSnapshot.inventoryOffhand =
                bool(
                        values,
                        "player_snapshot.inventory_offhand",
                        playerSnapshot.inventoryOffhand,
                        warning);
        playerSnapshot.selectedHotbarSlot =
                bool(
                        values,
                        "player_snapshot.selected_hotbar_slot",
                        playerSnapshot.selectedHotbarSlot,
                        warning);
        playerSnapshot.experienceLevel =
                bool(
                        values,
                        "player_snapshot.experience_level",
                        playerSnapshot.experienceLevel,
                        warning);
        playerSnapshot.experienceProgress =
                bool(
                        values,
                        "player_snapshot.experience_progress",
                        playerSnapshot.experienceProgress,
                        warning);
        playerSnapshot.experienceTotal =
                bool(
                        values,
                        "player_snapshot.experience_total",
                        playerSnapshot.experienceTotal,
                        warning);
        playerSnapshot.enderChest =
                bool(values, "player_snapshot.ender_chest", playerSnapshot.enderChest, warning);
        playerSnapshot.health =
                bool(values, "player_snapshot.health", playerSnapshot.health, warning);
        playerSnapshot.absorption =
                bool(values, "player_snapshot.absorption", playerSnapshot.absorption, warning);
        playerSnapshot.foodLevel =
                bool(values, "player_snapshot.food_level", playerSnapshot.foodLevel, warning);
        playerSnapshot.foodSaturation =
                bool(
                        values,
                        "player_snapshot.food_saturation",
                        playerSnapshot.foodSaturation,
                        warning);
        playerSnapshot.foodExhaustion =
                bool(
                        values,
                        "player_snapshot.food_exhaustion",
                        playerSnapshot.foodExhaustion,
                        warning);
        playerSnapshot.potionEffects =
                bool(
                        values,
                        "player_snapshot.potion_effects",
                        playerSnapshot.potionEffects,
                        warning);
        playerSnapshot.velocity =
                bool(values, "player_snapshot.velocity", playerSnapshot.velocity, warning);
        playerSnapshot.fallDistance =
                bool(values, "player_snapshot.fall_distance", playerSnapshot.fallDistance, warning);
        playerSnapshot.fireTicks =
                bool(values, "player_snapshot.fire_ticks", playerSnapshot.fireTicks, warning);
        playerSnapshot.air = bool(values, "player_snapshot.air", playerSnapshot.air, warning);
        playerSnapshot.spawnPoint =
                bool(values, "player_snapshot.spawn_point", playerSnapshot.spawnPoint, warning);
        playerSnapshot.vanillaFiles.restorePlayerdata =
                bool(
                        values,
                        "player_snapshot.vanilla_files.restore_playerdata",
                        playerSnapshot.vanillaFiles.restorePlayerdata,
                        warning);
        playerSnapshot.vanillaFiles.restoreStats =
                bool(
                        values,
                        "player_snapshot.vanilla_files.restore_stats",
                        playerSnapshot.vanillaFiles.restoreStats,
                        warning);
        playerSnapshot.vanillaFiles.restoreAdvancements =
                bool(
                        values,
                        "player_snapshot.vanilla_files.restore_advancements",
                        playerSnapshot.vanillaFiles.restoreAdvancements,
                        warning);

        fakePlayer.enabled = bool(values, "fakeplayer.enabled", fakePlayer.enabled, warning);
        fakePlayer.autoRejoin =
                bool(values, "fakeplayer.auto_rejoin", fakePlayer.autoRejoin, warning);
        fakePlayer.restoreBeforeUnfreeze =
                bool(
                        values,
                        "fakeplayer.restore_before_unfreeze",
                        fakePlayer.restoreBeforeUnfreeze,
                        warning);
        fakePlayer.requireAllReadyBeforeStart =
                bool(
                        values,
                        "fakeplayer.require_all_ready_before_start",
                        fakePlayer.requireAllReadyBeforeStart,
                        warning);
        fakePlayer.snapshot.dimension =
                bool(
                        values,
                        "fakeplayer.snapshot.dimension",
                        fakePlayer.snapshot.dimension,
                        warning);
        fakePlayer.snapshot.position =
                bool(values, "fakeplayer.snapshot.position", fakePlayer.snapshot.position, warning);
        fakePlayer.snapshot.rotation =
                bool(values, "fakeplayer.snapshot.rotation", fakePlayer.snapshot.rotation, warning);
        fakePlayer.snapshot.gamemode =
                bool(values, "fakeplayer.snapshot.gamemode", fakePlayer.snapshot.gamemode, warning);
        fakePlayer.snapshot.flying =
                bool(values, "fakeplayer.snapshot.flying", fakePlayer.snapshot.flying, warning);
        fakePlayer.snapshot.inventory =
                bool(
                        values,
                        "fakeplayer.snapshot.inventory",
                        fakePlayer.snapshot.inventory,
                        warning);
        fakePlayer.snapshot.selectedSlot =
                bool(
                        values,
                        "fakeplayer.snapshot.selected_slot",
                        fakePlayer.snapshot.selectedSlot,
                        warning);
        fakePlayer.snapshot.health =
                bool(values, "fakeplayer.snapshot.health", fakePlayer.snapshot.health, warning);
        fakePlayer.snapshot.hunger =
                bool(values, "fakeplayer.snapshot.hunger", fakePlayer.snapshot.hunger, warning);
        fakePlayer.snapshot.effects =
                bool(values, "fakeplayer.snapshot.effects", fakePlayer.snapshot.effects, warning);
        fakePlayer.snapshot.sneaking =
                bool(values, "fakeplayer.snapshot.sneaking", fakePlayer.snapshot.sneaking, warning);
        fakePlayer.snapshot.sprinting =
                bool(
                        values,
                        "fakeplayer.snapshot.sprinting",
                        fakePlayer.snapshot.sprinting,
                        warning);
        fakePlayer.snapshot.movement =
                bool(values, "fakeplayer.snapshot.movement", fakePlayer.snapshot.movement, warning);
        fakePlayer.snapshot.actions =
                bool(values, "fakeplayer.snapshot.actions", fakePlayer.snapshot.actions, warning);
        fakePlayer.snapshot.actionInternalTiming =
                bool(
                        values,
                        "fakeplayer.snapshot.action_internal_timing",
                        fakePlayer.snapshot.actionInternalTiming,
                        warning);
        fakePlayer.snapshot.itemUseCooldown =
                bool(
                        values,
                        "fakeplayer.snapshot.item_use_cooldown",
                        fakePlayer.snapshot.itemUseCooldown,
                        warning);
        fakePlayer.snapshot.blockBreakProgress =
                bool(
                        values,
                        "fakeplayer.snapshot.block_break_progress",
                        fakePlayer.snapshot.blockBreakProgress,
                        warning);

        consistency.mode = string(values, "consistency.mode", consistency.mode);

        login.retryTimeoutSeconds =
                integer(values, "login.retry_timeout_seconds", login.retryTimeoutSeconds, warning);

        notifications.frozenActionBar =
                bool(
                        values,
                        "notifications.frozen_action_bar",
                        notifications.frozenActionBar,
                        warning);
        notifications.checkpointActionBarDelayTicks =
                integer(
                        values,
                        "notifications.checkpoint_action_bar_delay_ticks",
                        notifications.checkpointActionBarDelayTicks,
                        warning);
        notifications.askPromptInGame =
                bool(
                        values,
                        "notifications.ask_prompt_in_game",
                        notifications.askPromptInGame,
                        warning);

        logging.level = string(values, "logging.level", logging.level);
        logging.console = bool(values, "logging.console", logging.console, warning);
        logging.consoleColor = string(values, "logging.console_color", logging.consoleColor);
        if (!oneOf(logging.consoleColor, "AUTO", "ON", "OFF")) logging.consoleColor = "AUTO";
        logging.chatColor = bool(values, "logging.chat_color", logging.chatColor, warning);
        logging.file = bool(values, "logging.file", logging.file, warning);
        logging.maxFileMb = integer(values, "logging.max_file_mb", logging.maxFileMb, warning);
        logging.retainedFiles =
                integer(values, "logging.retained_files", logging.retainedFiles, warning);
        logging.showCheckpointContents =
                bool(
                        values,
                        "logging.show_checkpoint_contents",
                        logging.showCheckpointContents,
                        warning);
        logging.showPlayerDetails =
                bool(values, "logging.show_player_details", logging.showPlayerDetails, warning);
        logging.showFakeplayerDetails =
                bool(
                        values,
                        "logging.show_fakeplayer_details",
                        logging.showFakeplayerDetails,
                        warning);
        logging.showBlockEvents =
                bool(values, "logging.show_block_events", logging.showBlockEvents, warning);
        logging.showFileOperations =
                bool(values, "logging.show_file_operations", logging.showFileOperations, warning);
        logging.showTransactionDetails =
                bool(
                        values,
                        "logging.show_transaction_details",
                        logging.showTransactionDetails,
                        warning);
    }

    public void validate(Consumer<String> warning) {
        if (!MessageCatalog.isSupported(language)) {
            warning.accept(
                    MessageCatalog.text(
                            MessageCatalog.DEFAULT_LANGUAGE, "config.invalid_language", language));
            language = MessageCatalog.DEFAULT_LANGUAGE;
        } else {
            language = MessageCatalog.normalize(language);
        }
        if (checkpoint.intervalSeconds <= 0 || checkpoint.intervalSeconds > 86_400) {
            warning.accept(MessageCatalog.text(language, "config.interval_range"));
            checkpoint.intervalSeconds = 30;
        }
        if (checkpoint.shutdownTimeoutSeconds < 0) {
            warning.accept(MessageCatalog.text(language, "config.shutdown_timeout"));
            checkpoint.shutdownTimeoutSeconds = 5;
        }
        if (checkpoint.retentionCount < 1) {
            warning.accept(MessageCatalog.text(language, "config.retention_count"));
            checkpoint.retentionCount = 1;
        }
        if (checkpoint.retentionDays < 0) {
            warning.accept(MessageCatalog.text(language, "config.retention_days"));
            checkpoint.retentionDays = 0;
        }
        if (checkpoint.retentionMaxGb < 0) {
            warning.accept(MessageCatalog.text(language, "config.retention_gb"));
            checkpoint.retentionMaxGb = 0;
        }
        if (checkpoint.saveBatchChunks < 1 || checkpoint.saveBatchChunks > 4_096) {
            warning.accept(MessageCatalog.text(language, "config.save_batch_range"));
            checkpoint.saveBatchChunks = 256;
        }
        if (checkpoint.warnBarrierMs < 0) checkpoint.warnBarrierMs = 0;
        if (checkpoint.warnCheckpointMs < 0) checkpoint.warnCheckpointMs = 0;
        if (checkpoint.targetBarrierMs < 1 || checkpoint.targetBarrierMs > 60_000) {
            warning.accept(MessageCatalog.text(language, "config.checkpoint_performance_range"));
            checkpoint.targetBarrierMs = 20;
        }
        if (checkpoint.warnBarrierMs < checkpoint.targetBarrierMs) {
            warning.accept(
                    MessageCatalog.text(
                            language,
                            "config.checkpoint_warning_order",
                            checkpoint.warnBarrierMs,
                            checkpoint.targetBarrierMs));
            checkpoint.warnBarrierMs = checkpoint.targetBarrierMs;
        }
        if (checkpoint.criticalBarrierMs < checkpoint.warnBarrierMs
                || checkpoint.criticalBarrierMs > 300_000) {
            warning.accept(MessageCatalog.text(language, "config.checkpoint_critical_range"));
            checkpoint.criticalBarrierMs = Math.max(50, checkpoint.warnBarrierMs);
        }
        if (!Double.isFinite(checkpoint.rollingBudgetMsPerTick)
                || checkpoint.rollingBudgetMsPerTick < 0.0
                || checkpoint.rollingBudgetMsPerTick > 50.0)
            checkpoint.rollingBudgetMsPerTick = 1.0;
        if (!Double.isFinite(checkpoint.rollingMaxBudgetMsPerTick)
                || checkpoint.rollingMaxBudgetMsPerTick < checkpoint.rollingBudgetMsPerTick
                || checkpoint.rollingMaxBudgetMsPerTick > 100.0) {
            checkpoint.rollingMaxBudgetMsPerTick = Math.max(3.0, checkpoint.rollingBudgetMsPerTick);
        }
        if (checkpoint.warnFinalizeMs < 0) checkpoint.warnFinalizeMs = 0;
        if (checkpoint.maxInflightGenerations < 1 || checkpoint.maxInflightGenerations > 8)
            checkpoint.maxInflightGenerations = 2;
        if (checkpoint.baseInterval < 0 || checkpoint.baseInterval > 10_000)
            checkpoint.baseInterval = 0;
        if (checkpoint.maxChainDepth < 0 || checkpoint.maxChainDepth > 10_000)
            checkpoint.maxChainDepth = 0;
        if (checkpoint.backgroundThreads < 1 || checkpoint.backgroundThreads > 16)
            checkpoint.backgroundThreads = 1;
        if (checkpoint.maxMbPerSecond < 0 || checkpoint.maxMbPerSecond > 1_000_000)
            checkpoint.maxMbPerSecond = 0;
        if (checkpoint.minimumFreeMb < 0 || checkpoint.minimumFreeMb > 1_048_576)
            checkpoint.minimumFreeMb = 256;
        if (!oneOf(checkpoint.durabilityMode, "SAFE", "BALANCED", "FAST"))
            checkpoint.durabilityMode = "SAFE";
        checkpoint.durabilityMode = checkpoint.durabilityMode.toUpperCase(Locale.ROOT);
        if (!oneOf(checkpoint.engineMode, "LOGICAL_DELTA", "PORTABLE_GENERATION", "LEGACY_SAFE")) {
            warning.accept(MessageCatalog.text(language, "config.checkpoint_engine_mode"));
            checkpoint.engineMode = "LOGICAL_DELTA";
        } else checkpoint.engineMode = checkpoint.engineMode.toUpperCase(Locale.ROOT);
        if (!oneOf(checkpoint.engineFallback, "PORTABLE_GENERATION", "LEGACY_SAFE")) {
            warning.accept(MessageCatalog.text(language, "config.checkpoint_engine_fallback"));
            checkpoint.engineFallback = "PORTABLE_GENERATION";
        } else checkpoint.engineFallback = checkpoint.engineFallback.toUpperCase(Locale.ROOT);
        if (checkpoint.candidateCacheMaxMb < 16 || checkpoint.candidateCacheMaxMb > 4096) {
            warning.accept(MessageCatalog.text(language, "config.checkpoint_cache_range"));
            checkpoint.candidateCacheMaxMb = 256;
        }
        if (!Double.isFinite(checkpoint.candidateBudgetMsPerTick)
                || checkpoint.candidateBudgetMsPerTick < 0.0
                || checkpoint.candidateBudgetMsPerTick > 20.0) {
            warning.accept(
                    MessageCatalog.text(language, "config.checkpoint_candidate_budget_range"));
            checkpoint.candidateBudgetMsPerTick = 1.0;
        }
        if (checkpoint.mirrorAuditIntervalSeconds < 0
                || checkpoint.mirrorAuditIntervalSeconds > 86_400) {
            warning.accept(
                    MessageCatalog.text(language, "config.checkpoint_mirror_interval_range"));
            checkpoint.mirrorAuditIntervalSeconds = 300;
        }
        if (checkpoint.mirrorTraceLimit < 0 || checkpoint.mirrorTraceLimit > 1024) {
            warning.accept(MessageCatalog.text(language, "config.checkpoint_mirror_trace_range"));
            checkpoint.mirrorTraceLimit = 64;
        }
        commands.validate(language, warning);
        String publicCommands = publicMutatingCommands();
        if (!publicCommands.isEmpty()) {
            warning.accept(
                    MessageCatalog.text(
                            language, "security.public_mutating_commands", publicCommands));
        }
        if (!oneOf(
                snapshot.backend,
                "AUTO",
                "PORTABLE_GENERATION",
                "GENERATION",
                "JAVA_INCREMENTAL",
                "LEGACY_SAFE",
                "REFLINK",
                "BTRFS",
                "CRIU_EXPERIMENTAL")) {
            warning.accept(MessageCatalog.text(language, "config.snapshot_backend"));
            snapshot.backend = "AUTO";
        }
        if (!snapshot.verifyChecksums)
            warning.accept(MessageCatalog.text(language, "security.checksums_disabled"));
        if (!oneOf(recovery.policy, "AUTO", "ASK", "MANUAL")) {
            warning.accept(MessageCatalog.text(language, "config.recovery_policy"));
            recovery.policy = "AUTO";
        }
        if (!oneOf(recovery.fidelity, "STRICT", "OPTIMIZED")) {
            warning.accept(MessageCatalog.text(language, "config.recovery_fidelity"));
            recovery.fidelity = "OPTIMIZED";
        } else {
            recovery.fidelity = recovery.fidelity.toUpperCase(Locale.ROOT);
        }
        if (recovery.askTimeoutSeconds < 0 || recovery.askTimeoutSeconds > 86_400) {
            warning.accept(MessageCatalog.text(language, "config.ask_timeout_range"));
            recovery.askTimeoutSeconds = 60;
        }
        if (!oneOf(recovery.askDefaultAction, "RESTORE", "CONTINUE", "ABORT")) {
            warning.accept(MessageCatalog.text(language, "config.ask_action"));
            recovery.askDefaultAction = "ABORT";
        }
        if (!oneOf(recovery.preloadMode, "CRITICAL", "ENTITY_TICKING", "ALL")) {
            warning.accept(MessageCatalog.text(language, "config.chunk_preload_mode"));
            recovery.preloadMode = "ALL";
        }
        if (recovery.maxPreloadChunks < 1 || recovery.maxPreloadChunks > 100_000) {
            warning.accept(MessageCatalog.text(language, "config.chunk_preload_limit"));
            recovery.maxPreloadChunks = 100_000;
        }
        if (recovery.chunkReadyTimeoutSeconds < 0 || recovery.chunkReadyTimeoutSeconds > 86_400) {
            warning.accept(MessageCatalog.text(language, "config.chunk_ready_timeout_range"));
            recovery.chunkReadyTimeoutSeconds = 120;
        }
        if (recovery.chunks.maxConcurrentLoads < 1 || recovery.chunks.maxConcurrentLoads > 64) {
            warning.accept(MessageCatalog.text(language, "config.max_concurrent_loads_range"));
            recovery.chunks.maxConcurrentLoads = 4;
        }
        if (recovery.startCommand == null
                || recovery.startCommand.isBlank()
                || recovery.startCommand.length() > 256
                || recovery.startCommand.indexOf('\n') >= 0
                || recovery.startCommand.indexOf('\r') >= 0) {
            recovery.startCommand = "/ac start";
        }
        if (recovery.temporaryTicketReleaseTicks < 1
                || recovery.temporaryTicketReleaseTicks > 1_200_000) {
            warning.accept(MessageCatalog.text(language, "config.ticket_release_range"));
            recovery.temporaryTicketReleaseTicks = 100;
        }
        String recoveryMutations = enabledRecoveryMutations();
        if (!recoveryMutations.isEmpty()) {
            warning.accept(
                    MessageCatalog.text(
                            language, "security.recovery_mutations_enabled", recoveryMutations));
        }
        if (!fakePlayer.snapshot.actions && fakePlayer.snapshot.actionInternalTiming) {
            warning.accept(
                    MessageCatalog.text(language, "config.fakeplayer_timing_requires_actions"));
            fakePlayer.snapshot.actionInternalTiming = false;
        }
        if (login.retryTimeoutSeconds < 0 || login.retryTimeoutSeconds > 20) {
            warning.accept(MessageCatalog.text(language, "config.login_retry_range"));
            login.retryTimeoutSeconds = 5;
        }
        if (notifications.checkpointActionBarDelayTicks < 0
                || notifications.checkpointActionBarDelayTicks > 1_200) {
            warning.accept(MessageCatalog.text(language, "config.action_bar_delay_range"));
            notifications.checkpointActionBarDelayTicks = 10;
        }
        if (!oneOf(consistency.mode, "IGNORE", "WARN", "JOURNAL")) {
            warning.accept(MessageCatalog.text(language, "config.consistency_mode"));
            consistency.mode = "IGNORE";
        }
        if (!oneOf(logging.level, "OFF", "IMPORTANT", "NORMAL", "VERBOSE", "TRACE")) {
            warning.accept(MessageCatalog.text(language, "config.logging_level"));
            logging.level = "NORMAL";
        }
        if (logging.maxFileMb < 1 || logging.maxFileMb > 10_240) {
            warning.accept(MessageCatalog.text(language, "config.log_size_range"));
            logging.maxFileMb = 50;
        }
        if (logging.retainedFiles < 1 || logging.retainedFiles > 1_000) {
            warning.accept(MessageCatalog.text(language, "config.log_retention_range"));
            logging.retainedFiles = 10;
        }
        if (snapshot.crashRawRetentionCount < 1) snapshot.crashRawRetentionCount = 1;
        if (snapshot.ioThreads < 0 || snapshot.ioThreads > 64) {
            warning.accept(MessageCatalog.text(language, "config.io_threads_range"));
            snapshot.ioThreads = 0;
        }
    }

    private String publicMutatingCommands() {
        StringBuilder result = new StringBuilder();
        appendPublic(result, commands.startForce == 0, "/ac start --force");
        appendPublic(result, commands.freeze == 0, "/ac freeze");
        appendPublic(result, commands.checkpoint == 0, "/ac checkpoint");
        appendPublic(result, commands.recovery == 0, "/ac recovery");
        appendPublic(result, commands.fakeplayers == 0, "/ac fakeplayers restore");
        appendPublic(result, commands.config == 0, "/ac config reload");
        appendPublic(result, commands.debug == 0, "/ac debug dump");
        return result.toString();
    }

    private String enabledRecoveryMutations() {
        StringBuilder result = new StringBuilder();
        appendPublic(result, recovery.lock.allowBlockBreak, "allow_block_break");
        appendPublic(result, recovery.lock.allowBlockPlace, "allow_block_place");
        appendPublic(result, recovery.lock.allowItemUse, "allow_item_use");
        appendPublic(result, recovery.lock.allowEntityAttack, "allow_entity_attack");
        appendPublic(result, recovery.lock.allowEntityInteract, "allow_entity_interact");
        appendPublic(
                result, recovery.lock.allowContainerInteraction, "allow_container_interaction");
        appendPublic(result, recovery.lock.allowItemDrop, "allow_item_drop");
        appendPublic(result, recovery.lock.allowItemPickup, "allow_item_pickup");
        return result.toString();
    }

    private static void appendPublic(StringBuilder result, boolean enabled, String value) {
        if (!enabled) return;
        if (!result.isEmpty()) result.append(", ");
        result.append(value);
    }

    private static boolean oneOf(String value, String... allowed) {
        for (String option : allowed) if (option.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static void warnUnknownKeys(
            Map<String, String> values, Consumer<String> warning, String language) {
        Set<String> known = Toml.parse(new AntiCrashConfig().toToml()).keySet();
        for (String key : values.keySet()) {
            if (!known.contains(key))
                warning.accept(MessageCatalog.text(language, "config.unknown_key", key));
        }
    }

    private static void warnUnsafeConfigPermissions(
            Path file, Consumer<String> warning, String language) {
        try {
            if (FileSecurity.isWritableByGroupOrOthers(file)) {
                warning.accept(MessageCatalog.text(language, "security.config_permissions", file));
            }
        } catch (IOException exception) {
            warning.accept(
                    MessageCatalog.text(
                            language,
                            "security.permission_check_failed",
                            file,
                            exception.getMessage()));
        }
    }

    private static String string(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static boolean bool(
            Map<String, String> values, String key, boolean fallback, Consumer<String> warning) {
        String value = values.get(key);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
            return Boolean.parseBoolean(value);
        warning.accept(
                MessageCatalog.text(
                        values.get("language"), "config.invalid_boolean", key, fallback));
        return fallback;
    }

    private static int integer(
            Map<String, String> values, String key, int fallback, Consumer<String> warning) {
        String value = values.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            warning.accept(
                    MessageCatalog.text(
                            values.get("language"), "config.invalid_integer", key, fallback));
            return fallback;
        }
    }

    private static double decimal(
            Map<String, String> values, String key, double fallback, Consumer<String> warning) {
        String value = values.get(key);
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            warning.accept(
                    MessageCatalog.text(
                            values.get("language"), "config.invalid_decimal", key, fallback));
            return fallback;
        }
    }

    public String toToml() {
        return """
        # AntiCrash 1.0.1 - complete user configuration / 完全設定
        # User settings only. Run markers, recovery state and checkpoints are stored
        # under .anticrash/ and must never be copied into this file.
        # ユーザー設定専用です。実行・復旧状態は .anticrash/ に分離して保存されます。
        # language: "ja" or "en". This changes AntiCrash messages only, not Minecraft/Fabric logs.
        config_version = 1
        language = "%s"

        [checkpoint]
        # interval_seconds uses monotonic wall-clock time (1..86400; default 30 seconds), not 600 game ticks.
        # A request enters the barrier only after the current server tick finishes.
        enabled = %s
        interval_seconds = %d
        create_on_manual_command = %s
        create_on_clean_shutdown = %s
        # Maximum time a normal shutdown waits for a final checkpoint. 0 skips that wait.
        shutdown_timeout_seconds = %d
        # At least one newest checkpoint is retained. 0 days/GB means no limit for that axis.
        retention_count = %d
        retention_days = %d
        retention_max_gb = %d
        # Normally leave this alone. A checkpoint cut takes queued chunk saves from the IO
        # queue and waits for the disk only to protect the heap; that limit follows the
        # heap size. Only a value below 16 makes the cut wait after that many chunks.
        save_batch_chunks = %d

        [checkpoint.performance]
        # Logical-cut target/warn/critical thresholds are measured with monotonic nanoTime.
        target_barrier_ms = %d
        warn_barrier_ms = %d
        critical_barrier_ms = %d
        warn_checkpoint_ms = %d
        rolling_enabled = %s
        rolling_budget_ms_per_tick = %s
        rolling_max_budget_ms_per_tick = %s
        adaptive_budget = %s
        warn_finalize_ms = %d

        [checkpoint.pipeline]
        # A new generation is not requested while this many finalizers are in flight.
        max_inflight_generations = %d

        [checkpoint.incremental]
        # Every generation is a complete immutable view. A BASE copies the whole world into
        # a lineage that shares no file with the previous one; 0 (default) never starts one
        # periodically. Otherwise a BASE follows after this many generations (0..10000).
        base_interval = %d
        max_chain_depth = %d

        [checkpoint.io]
        # Background finalization is bounded independently of server-thread capture.
        background_threads = %d
        # 0 means unlimited. This is a finalizer throttle, not a live-world read budget.
        max_mb_per_second = %d

        [checkpoint.storage]
        # Refuse to seal when less than this amount of free space is available.
        minimum_free_mb = %d

        [checkpoint.durability]
        # SAFE forces payload files, manifest, checksums and READY before publish.
        mode = "%s"

        [checkpoint.engine]
        # LOGICAL_DELTA captures immutable semantic NBT at an exact epoch and materializes
        # it in the background. Unsupported stores never become READY silently.
        mode = "%s"
        fallback = "%s"
        single_inflight = %s
        allow_unsupported_persistent_state = %s

        [checkpoint.cache]
        # Bounded server-thread candidate cache. Eviction only causes a safe recapture or
        # fallback; it is never treated as proof that a chunk is unchanged.
        max_mb = %d
        budget_ms_per_tick = %s

        [checkpoint.mirror]
        # Audit-only mutation observations. Vanilla queues are never replaced or drained.
        enabled = %s
        audit_interval_seconds = %d
        trace_limit = %d

        [commands]
        # Minecraft permission levels: 0=all players, 4=OP level 4.
        # /ac start is intentionally 0 by default; every other category is 4.
        # --force has a separate level because it may bypass semantic recovery validation.
        help_permission_level = %d
        status_permission_level = %d
        start_permission_level = %d
        start_force_permission_level = %d
        freeze_permission_level = %d
        checkpoint_permission_level = %d
        recovery_permission_level = %d
        fakeplayers_permission_level = %d
        journal_permission_level = %d
        config_permission_level = %d
        debug_permission_level = %d

        [snapshot]
        # AUTO/PORTABLE_GENERATION use the filesystem-independent generation seal.
        # BTRFS/REFLINK/CRIU_EXPERIMENTAL are explicit LEGACY_SAFE fallbacks.
        # Disabling verify_checksums weakens corruption detection and is not recommended.
        backend = "%s"
        verify_checksums = %s
        # crash-raw is the pre-rollback world used by /ac recovery abort.
        keep_crash_raw_world = %s
        crash_raw_retention_count = %d
        # Threads used for the frozen point-in-time copy. The work is per-file stat and
        # hard-link latency, not CPU, so a few threads shorten the freeze noticeably on an
        # SSD. 0 picks min(4, cores); 1 restores the old single-threaded copy.
        io_threads = %d

        [recovery]
        # AUTO: restore after a crash without asking; a normal stop starts normally.
        # ASK: ask on the server console after every exit, before the world is loaded.
        #   After a normal stop, Y resumes from the stop-time checkpoint (running machinery
        #   continues; the start takes longer) and N starts the saved world as Vanilla does
        #   (machinery caught mid-cycle may stop).
        # MANUAL: remain frozen for an explicit recovery command.
        policy = "%s"
        # STRICT verifies the checkpoint tick domain and captured runtime categories
        # before the first resumed tick. OPTIMIZED keeps the lightweight legacy planner.
        fidelity = "%s"
        # Both defaults keep the first restored world tick stopped until /ac start.
        freeze_after_restore = %s
        require_manual_start = %s
        # Display text shown to joining players; the actual registered command remains /ac start.
        start_command = "%s"
        restore_latest_valid_checkpoint = %s
        allow_restore_older_checkpoint = %s
        fallback_to_previous_checkpoint = %s
        # ASK starts this timer only after server startup completes. 0 means no timeout; maximum 86400.
        # No answer within the timeout: Y (restore) after a crash, N after a normal stop.
        ask_timeout_seconds = %d
        # Compatibility key only; current ASK timeout logic does not use this value.
        ask_default_action = "%s"
        # Even when true, --force cannot bypass corrupt state files or persistence failures.
        allow_start_with_failed_validation = %s
        # A restore is applied before the next world load, so the server has to come back
        # up for it to take effect. With the Supervisor in front this is automatic and this
        # setting is ignored. Without it, AntiCrash can launch a detached helper that
        # relaunches the JVM, but the helper rebuilds the command from the running process
        # and cannot reproduce a start script's environment. Leave this false to stop after
        # the restore is scheduled and start the server yourself.
        spawn_restart_helper = %s
        # 1..1200000 ticks.
        temporary_ticket_release_ticks = %d
        # ALL (default) brings every chunk loaded at the checkpoint back to its level there
        # (entity ticking, block ticking or loaded) before the first tick, so machines that
        # span chunks resume together. ENTITY_TICKING adds only the entity-ticking set;
        # CRITICAL only forced, moving-piston/scheduled-tick/transient-state and
        # Player/FakePlayer/Block Event anchor chunks, and lets the rest load after the
        # world resumes. The limit (1..100000) always applies; exceeding it fails recovery.
        preload_mode = "%s"
        max_preload_chunks = %d
        # Recovery keeps the world frozen until every required Chunk is ready. If no Chunk
        # makes progress for this many seconds, recovery fails closed instead of freezing
        # forever. Only a total lack of progress counts, so a large plan that is still
        # advancing is never interrupted. 0 disables the check; maximum 86400.
        chunk_ready_timeout_seconds = %d

        [recovery.chunks]
        # STRICT requests every expected chunk, but limits asynchronous loads in flight.
        # This controls peak memory; it never reduces the expected domain.
        max_concurrent_loads = %d

        [recovery.strict]
        # These are explicit escape hatches for runtime state that Minecraft 1.18.2
        # does not expose with a safe public restore operation. Default is fail closed.
        allow_unsupported_runtime_state = %s
        allow_unsupported_carpet_state = %s
        require_entity_order = %s
        require_block_entity_ticker_order = %s
        require_ticket_ledger = %s

        [recovery.strict.players]
        # If true, every Real Player present at checkpoint time must be present before
        # /ac start. The identity list is stored in Runtime Capsule independently of
        # player_snapshot.enabled; Inventory policy remains controlled by that section.
        require_checkpoint_players = %s

        [recovery.lock]
        # These switches apply only while recovery is frozen. KeepAlive remains enabled.
        # allow_commands permits a conservative read-only command set; AntiCrash recovery
        # controls and /stop stay available subject to their normal permission levels.
        allow_login = %s
        allow_chat = %s
        allow_commands = %s
        allow_player_movement = %s
        allow_player_look = %s
        allow_sneak = %s
        allow_block_break = %s
        allow_block_place = %s
        allow_item_use = %s
        allow_entity_attack = %s
        allow_entity_interact = %s
        allow_container_interaction = %s
        allow_item_drop = %s
        allow_item_pickup = %s

        [player_snapshot]
        # Real Player defaults intentionally restore only dimension, position and rotation.
        # Inventory/XP/Ender Chest/health/hunger remain owned by external Player-data systems.
        # Enabling any optional field may overwrite data loaded by another mod at login.
        enabled = %s
        dimension = %s
        position = %s
        rotation = %s
        gamemode = %s
        abilities = %s
        inventory_main = %s
        inventory_armor = %s
        inventory_offhand = %s
        selected_hotbar_slot = %s
        experience_level = %s
        experience_progress = %s
        experience_total = %s
        ender_chest = %s
        health = %s
        absorption = %s
        food_level = %s
        food_saturation = %s
        food_exhaustion = %s
        potion_effects = %s
        velocity = %s
        fall_distance = %s
        fire_ticks = %s
        air = %s
        spawn_point = %s

        [player_snapshot.vanilla_files]
        # High-risk opt-in: copies/restores whole Vanilla Player files. Leave false when
        # Inventory, XP or advancements are synchronized through MySQL/another server.
        restore_playerdata = %s
        restore_stats = %s
        restore_advancements = %s

        [fakeplayer]
        # Carpet is optional. If snapshots contain FakePlayers but Carpet cannot restore
        # all required players, start validation fails closed by default.
        enabled = %s
        auto_rejoin = %s
        restore_before_unfreeze = %s
        require_all_ready_before_start = %s

        [fakeplayer.snapshot]
        # FakePlayers are TMC machine parts, so these fields default to true.
        # actions + action_internal_timing preserve interval/count/next/done phase.
        dimension = %s
        position = %s
        rotation = %s
        gamemode = %s
        flying = %s
        inventory = %s
        selected_slot = %s
        health = %s
        hunger = %s
        effects = %s
        sneaking = %s
        sprinting = %s
        movement = %s
        actions = %s
        action_internal_timing = %s
        item_use_cooldown = %s
        block_break_progress = %s

        [login]
        # AntiCrash refuses logins during a checkpoint barrier and during a recovery freeze
        # with allow_login = false. A barrier is far shorter than a login, so the connection
        # is held and the accept is retried every tick for this long instead of being
        # disconnected; a login that gets in before the freeze clears simply joins.
        # Only AntiCrash's own refusal is retried: ban, whitelist, capacity and every other
        # Vanilla reason is applied immediately and unchanged. 0 restores the old immediate
        # disconnect. The maximum is 20 because a client drops a connection that has been
        # silent for 30 seconds.
        retry_timeout_seconds = %d

        [notifications]
        # In-game feedback for states in which AntiCrash deliberately stops the world.
        # The action bar tells players why the server is not responding instead of
        # leaving them with an unexplained freeze; it never changes recovery behaviour.
        frozen_action_bar = %s
        # Ticks a checkpoint freeze must last before the action bar appears (0..1200).
        # Keeps a fast checkpoint from flashing a message every interval.
        # Kept for backward-compatible config parsing. Checkpoint creation never uses
        # the action bar; recovery status messages may still use it.
        checkpoint_action_bar_delay_ticks = %d
        # Also show the ASK prompt in-game to players who may answer it, with /ac y|n.
        ask_prompt_in_game = %s

        [consistency]
        # IGNORE: no tracking. WARN: durable conservative warnings. JOURNAL: detailed JSONL.
        # No mode automatically edits MySQL or reconciles items by guessing.
        mode = "%s"

        [logging]
        # OFF, IMPORTANT, NORMAL, VERBOSE, TRACE. Fatal messages still reach the console.
        # Files rotate at max_file_mb (1..10240) and retain retained_files (1..1000) generations.
        level = "%s"
        console = %s
        # Colours on the server console: AUTO (only when the output is a terminal), ON, OFF.
        # latest.log and .anticrash/logs/anticrash.log are always written without colour codes.
        console_color = "%s"
        # Colours in command replies and notices shown in Minecraft chat.
        chat_color = %s
        file = %s
        max_file_mb = %d
        retained_files = %d
        show_checkpoint_contents = %s
        show_player_details = %s
        show_fakeplayer_details = %s
        show_block_events = %s
        show_file_operations = %s
        show_transaction_details = %s
        """
                .formatted(
                        escapeTomlString(language),
                        checkpoint.enabled,
                        checkpoint.intervalSeconds,
                        checkpoint.createOnManualCommand,
                        checkpoint.createOnCleanShutdown,
                        checkpoint.shutdownTimeoutSeconds,
                        checkpoint.retentionCount,
                        checkpoint.retentionDays,
                        checkpoint.retentionMaxGb,
                        checkpoint.saveBatchChunks,
                        checkpoint.targetBarrierMs,
                        checkpoint.warnBarrierMs,
                        checkpoint.criticalBarrierMs,
                        checkpoint.warnCheckpointMs,
                        checkpoint.rollingEnabled,
                        Double.toString(checkpoint.rollingBudgetMsPerTick),
                        Double.toString(checkpoint.rollingMaxBudgetMsPerTick),
                        checkpoint.adaptiveBudget,
                        checkpoint.warnFinalizeMs,
                        checkpoint.maxInflightGenerations,
                        checkpoint.baseInterval,
                        checkpoint.maxChainDepth,
                        checkpoint.backgroundThreads,
                        checkpoint.maxMbPerSecond,
                        checkpoint.minimumFreeMb,
                        escapeTomlString(checkpoint.durabilityMode),
                        escapeTomlString(checkpoint.engineMode),
                        escapeTomlString(checkpoint.engineFallback),
                        checkpoint.singleInflight,
                        checkpoint.allowUnsupportedPersistentState,
                        checkpoint.candidateCacheMaxMb,
                        Double.toString(checkpoint.candidateBudgetMsPerTick),
                        checkpoint.mirrorEnabled,
                        checkpoint.mirrorAuditIntervalSeconds,
                        checkpoint.mirrorTraceLimit,
                        commands.help,
                        commands.status,
                        commands.start,
                        commands.startForce,
                        commands.freeze,
                        commands.checkpoint,
                        commands.recovery,
                        commands.fakeplayers,
                        commands.journal,
                        commands.config,
                        commands.debug,
                        escapeTomlString(snapshot.backend),
                        snapshot.verifyChecksums,
                        snapshot.keepCrashRawWorld,
                        snapshot.crashRawRetentionCount,
                        snapshot.ioThreads,
                        escapeTomlString(recovery.policy),
                        escapeTomlString(recovery.fidelity),
                        recovery.freezeAfterRestore,
                        recovery.requireManualStart,
                        escapeTomlString(recovery.startCommand),
                        recovery.restoreLatestValidCheckpoint,
                        recovery.allowRestoreOlderCheckpoint,
                        recovery.fallbackToPreviousCheckpoint,
                        recovery.askTimeoutSeconds,
                        escapeTomlString(recovery.askDefaultAction),
                        recovery.allowStartWithFailedValidation,
                        recovery.spawnRestartHelper,
                        recovery.temporaryTicketReleaseTicks,
                        escapeTomlString(recovery.preloadMode),
                        recovery.maxPreloadChunks,
                        recovery.chunkReadyTimeoutSeconds,
                        recovery.chunks.maxConcurrentLoads,
                        recovery.strict.allowUnsupportedRuntimeState,
                        recovery.strict.allowUnsupportedCarpetState,
                        recovery.strict.requireEntityOrder,
                        recovery.strict.requireBlockEntityTickerOrder,
                        recovery.strict.requireTicketLedger,
                        recovery.strict.players.requireCheckpointPlayers,
                        recovery.lock.allowLogin,
                        recovery.lock.allowChat,
                        recovery.lock.allowCommands,
                        recovery.lock.allowPlayerMovement,
                        recovery.lock.allowPlayerLook,
                        recovery.lock.allowSneak,
                        recovery.lock.allowBlockBreak,
                        recovery.lock.allowBlockPlace,
                        recovery.lock.allowItemUse,
                        recovery.lock.allowEntityAttack,
                        recovery.lock.allowEntityInteract,
                        recovery.lock.allowContainerInteraction,
                        recovery.lock.allowItemDrop,
                        recovery.lock.allowItemPickup,
                        playerSnapshot.enabled,
                        playerSnapshot.dimension,
                        playerSnapshot.position,
                        playerSnapshot.rotation,
                        playerSnapshot.gamemode,
                        playerSnapshot.abilities,
                        playerSnapshot.inventoryMain,
                        playerSnapshot.inventoryArmor,
                        playerSnapshot.inventoryOffhand,
                        playerSnapshot.selectedHotbarSlot,
                        playerSnapshot.experienceLevel,
                        playerSnapshot.experienceProgress,
                        playerSnapshot.experienceTotal,
                        playerSnapshot.enderChest,
                        playerSnapshot.health,
                        playerSnapshot.absorption,
                        playerSnapshot.foodLevel,
                        playerSnapshot.foodSaturation,
                        playerSnapshot.foodExhaustion,
                        playerSnapshot.potionEffects,
                        playerSnapshot.velocity,
                        playerSnapshot.fallDistance,
                        playerSnapshot.fireTicks,
                        playerSnapshot.air,
                        playerSnapshot.spawnPoint,
                        playerSnapshot.vanillaFiles.restorePlayerdata,
                        playerSnapshot.vanillaFiles.restoreStats,
                        playerSnapshot.vanillaFiles.restoreAdvancements,
                        fakePlayer.enabled,
                        fakePlayer.autoRejoin,
                        fakePlayer.restoreBeforeUnfreeze,
                        fakePlayer.requireAllReadyBeforeStart,
                        fakePlayer.snapshot.dimension,
                        fakePlayer.snapshot.position,
                        fakePlayer.snapshot.rotation,
                        fakePlayer.snapshot.gamemode,
                        fakePlayer.snapshot.flying,
                        fakePlayer.snapshot.inventory,
                        fakePlayer.snapshot.selectedSlot,
                        fakePlayer.snapshot.health,
                        fakePlayer.snapshot.hunger,
                        fakePlayer.snapshot.effects,
                        fakePlayer.snapshot.sneaking,
                        fakePlayer.snapshot.sprinting,
                        fakePlayer.snapshot.movement,
                        fakePlayer.snapshot.actions,
                        fakePlayer.snapshot.actionInternalTiming,
                        fakePlayer.snapshot.itemUseCooldown,
                        fakePlayer.snapshot.blockBreakProgress,
                        login.retryTimeoutSeconds,
                        notifications.frozenActionBar,
                        notifications.checkpointActionBarDelayTicks,
                        notifications.askPromptInGame,
                        escapeTomlString(consistency.mode),
                        escapeTomlString(logging.level),
                        logging.console,
                        escapeTomlString(logging.consoleColor),
                        logging.chatColor,
                        logging.file,
                        logging.maxFileMb,
                        logging.retainedFiles,
                        logging.showCheckpointContents,
                        logging.showPlayerDetails,
                        logging.showFakeplayerDetails,
                        logging.showBlockEvents,
                        logging.showFileOperations,
                        logging.showTransactionDetails);
    }

    private static String escapeTomlString(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private static final class Toml {
        static Map<String, String> parse(String input) {
            Map<String, String> values = new LinkedHashMap<>();
            Set<String> declaredSections = new HashSet<>();
            String section = "";
            for (String rawLine : input.split("\\R", -1)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    if (section.isEmpty())
                        throw new IllegalArgumentException("Empty TOML section: " + rawLine);
                    if (!declaredSections.add(section))
                        throw new IllegalArgumentException("Duplicate TOML section: " + section);
                    continue;
                }
                if (line.startsWith("[") || line.endsWith("]"))
                    throw new IllegalArgumentException("Invalid TOML section: " + rawLine);
                int equals = line.indexOf('=');
                if (equals <= 0)
                    throw new IllegalArgumentException("Invalid TOML line: " + rawLine);
                String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = stripComment(line.substring(equals + 1).trim());
                if (key.isEmpty() || value.isEmpty())
                    throw new IllegalArgumentException("Invalid TOML key/value: " + rawLine);
                value = parseValue(value, rawLine);
                String qualified = section.isEmpty() ? key : section + "." + key;
                if (values.putIfAbsent(qualified, value) != null) {
                    throw new IllegalArgumentException("Duplicate TOML key: " + qualified);
                }
            }
            return values;
        }

        private static String stripComment(String value) {
            boolean quoted = false;
            boolean escaped = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (quoted && escaped) {
                    escaped = false;
                    continue;
                }
                if (quoted && c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') quoted = !quoted;
                if (c == '#' && !quoted) return value.substring(0, i).trim();
            }
            if (quoted || escaped) throw new IllegalArgumentException("Unterminated TOML string");
            return value;
        }

        private static String parseValue(String value, String rawLine) {
            if (!value.startsWith("\"")) {
                if (value.indexOf('"') >= 0)
                    throw new IllegalArgumentException("Invalid TOML string: " + rawLine);
                return value;
            }
            if (value.length() < 2 || !value.endsWith("\""))
                throw new IllegalArgumentException("Invalid TOML string: " + rawLine);
            String input = value.substring(1, value.length() - 1);
            StringBuilder decoded = new StringBuilder(input.length());
            for (int index = 0; index < input.length(); index++) {
                char character = input.charAt(index);
                if (character != '\\') {
                    decoded.append(character);
                    continue;
                }
                if (++index >= input.length())
                    throw new IllegalArgumentException("Invalid TOML escape: " + rawLine);
                char escape = input.charAt(index);
                switch (escape) {
                    case '\\' -> decoded.append('\\');
                    case '"' -> decoded.append('"');
                    case 'n' -> decoded.append('\n');
                    case 'r' -> decoded.append('\r');
                    case 't' -> decoded.append('\t');
                    case 'u' -> {
                        if (index + 4 >= input.length())
                            throw new IllegalArgumentException(
                                    "Invalid TOML unicode escape: " + rawLine);
                        String hex = input.substring(index + 1, index + 5);
                        if (!hex.matches("[0-9a-fA-F]{4}"))
                            throw new IllegalArgumentException(
                                    "Invalid TOML unicode escape: " + rawLine);
                        decoded.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported TOML escape \\" + escape + ": " + rawLine);
                }
            }
            return decoded.toString();
        }
    }

    public static final class Checkpoint {
        public boolean enabled = true;
        public int intervalSeconds = 30;
        public boolean createOnManualCommand = true;
        public boolean createOnCleanShutdown = true;
        public int shutdownTimeoutSeconds = 5;
        public int retentionCount = 20;
        public int retentionDays = 7;
        public int retentionMaxGb = 0;
        public int saveBatchChunks = 256;
        public int warnBarrierMs = 50;
        public int warnCheckpointMs = 5000;
        public int targetBarrierMs = 20;
        public int criticalBarrierMs = 100;
        public boolean rollingEnabled = true;
        public double rollingBudgetMsPerTick = 1.0;
        public double rollingMaxBudgetMsPerTick = 3.0;
        public boolean adaptiveBudget = true;
        public int warnFinalizeMs = 1000;
        public int maxInflightGenerations = 2;
        public int baseInterval = 0;
        public int maxChainDepth = 0;
        public int backgroundThreads = 1;
        public int maxMbPerSecond = 0;
        public int minimumFreeMb = 256;
        public String durabilityMode = "SAFE";
        public String engineMode = "LOGICAL_DELTA";
        public String engineFallback = "PORTABLE_GENERATION";
        public boolean singleInflight = true;
        public int candidateCacheMaxMb = 256;
        public double candidateBudgetMsPerTick = 1.0;
        public boolean mirrorEnabled = true;
        public int mirrorAuditIntervalSeconds = 300;
        public int mirrorTraceLimit = 64;
        public boolean allowUnsupportedPersistentState = false;

        void copyFrom(Checkpoint o) {
            enabled = o.enabled;
            intervalSeconds = o.intervalSeconds;
            createOnManualCommand = o.createOnManualCommand;
            createOnCleanShutdown = o.createOnCleanShutdown;
            shutdownTimeoutSeconds = o.shutdownTimeoutSeconds;
            retentionCount = o.retentionCount;
            retentionDays = o.retentionDays;
            retentionMaxGb = o.retentionMaxGb;
            saveBatchChunks = o.saveBatchChunks;
            warnBarrierMs = o.warnBarrierMs;
            warnCheckpointMs = o.warnCheckpointMs;
            targetBarrierMs = o.targetBarrierMs;
            criticalBarrierMs = o.criticalBarrierMs;
            rollingEnabled = o.rollingEnabled;
            rollingBudgetMsPerTick = o.rollingBudgetMsPerTick;
            rollingMaxBudgetMsPerTick = o.rollingMaxBudgetMsPerTick;
            adaptiveBudget = o.adaptiveBudget;
            warnFinalizeMs = o.warnFinalizeMs;
            maxInflightGenerations = o.maxInflightGenerations;
            baseInterval = o.baseInterval;
            maxChainDepth = o.maxChainDepth;
            backgroundThreads = o.backgroundThreads;
            maxMbPerSecond = o.maxMbPerSecond;
            minimumFreeMb = o.minimumFreeMb;
            durabilityMode = o.durabilityMode;
            engineMode = o.engineMode;
            engineFallback = o.engineFallback;
            singleInflight = o.singleInflight;
            candidateCacheMaxMb = o.candidateCacheMaxMb;
            candidateBudgetMsPerTick = o.candidateBudgetMsPerTick;
            mirrorEnabled = o.mirrorEnabled;
            mirrorAuditIntervalSeconds = o.mirrorAuditIntervalSeconds;
            mirrorTraceLimit = o.mirrorTraceLimit;
            allowUnsupportedPersistentState = o.allowUnsupportedPersistentState;
        }
    }

    public static final class CommandPermissions {
        public int help = 4;
        public int status = 4;
        public int start = 0;
        public int startForce = 4;
        public int freeze = 4;
        public int checkpoint = 4;
        public int recovery = 4;
        public int fakeplayers = 4;
        public int journal = 4;
        public int config = 4;
        public int debug = 4;

        void copyFrom(CommandPermissions o) {
            help = o.help;
            status = o.status;
            start = o.start;
            startForce = o.startForce;
            freeze = o.freeze;
            checkpoint = o.checkpoint;
            recovery = o.recovery;
            fakeplayers = o.fakeplayers;
            journal = o.journal;
            config = o.config;
            debug = o.debug;
        }

        void validate(String language, Consumer<String> warning) {
            help = level("commands.help_permission_level", help, 4, language, warning);
            status = level("commands.status_permission_level", status, 4, language, warning);
            start = level("commands.start_permission_level", start, 0, language, warning);
            startForce =
                    level(
                            "commands.start_force_permission_level",
                            startForce,
                            4,
                            language,
                            warning);
            freeze = level("commands.freeze_permission_level", freeze, 4, language, warning);
            checkpoint =
                    level("commands.checkpoint_permission_level", checkpoint, 4, language, warning);
            recovery = level("commands.recovery_permission_level", recovery, 4, language, warning);
            fakeplayers =
                    level(
                            "commands.fakeplayers_permission_level",
                            fakeplayers,
                            4,
                            language,
                            warning);
            journal = level("commands.journal_permission_level", journal, 4, language, warning);
            config = level("commands.config_permission_level", config, 4, language, warning);
            debug = level("commands.debug_permission_level", debug, 4, language, warning);
        }

        private static int level(
                String key, int value, int fallback, String language, Consumer<String> warning) {
            if (value >= 0 && value <= 4) return value;
            warning.accept(MessageCatalog.text(language, "config.permission_level", key, fallback));
            return fallback;
        }
    }

    public static final class Snapshot {
        public String backend = "AUTO";
        public boolean verifyChecksums = true;
        public boolean keepCrashRawWorld = true;
        public int crashRawRetentionCount = 10;
        public int ioThreads = 0;

        void copyFrom(Snapshot o) {
            backend = o.backend;
            verifyChecksums = o.verifyChecksums;
            keepCrashRawWorld = o.keepCrashRawWorld;
            crashRawRetentionCount = o.crashRawRetentionCount;
            ioThreads = o.ioThreads;
        }
    }

    public static final class Recovery {
        public String policy = "AUTO";

        /** OPTIMIZED preserves the existing low-memory behavior; STRICT is explicit. */
        public String fidelity = "OPTIMIZED";

        public boolean freezeAfterRestore = true;
        public boolean requireManualStart = true;
        public String startCommand = "/ac start";
        public boolean restoreLatestValidCheckpoint = true;
        public boolean allowRestoreOlderCheckpoint = true;
        public boolean fallbackToPreviousCheckpoint = true;
        public int askTimeoutSeconds = 60;
        public String askDefaultAction = "ABORT";
        public boolean allowStartWithFailedValidation = false;
        public boolean spawnRestartHelper = false;
        public int temporaryTicketReleaseTicks = 100;
        public String preloadMode = "ALL";
        public int maxPreloadChunks = 100_000;

        /** Seconds without any chunk-readiness progress before recovery fails closed. */
        public int chunkReadyTimeoutSeconds = 120;

        public final Chunks chunks = new Chunks();
        public final Strict strict = new Strict();
        public final Lock lock = new Lock();

        void copyFrom(Recovery o) {
            policy = o.policy;
            fidelity = o.fidelity;
            freezeAfterRestore = o.freezeAfterRestore;
            requireManualStart = o.requireManualStart;
            startCommand = o.startCommand;
            restoreLatestValidCheckpoint = o.restoreLatestValidCheckpoint;
            allowRestoreOlderCheckpoint = o.allowRestoreOlderCheckpoint;
            fallbackToPreviousCheckpoint = o.fallbackToPreviousCheckpoint;
            askTimeoutSeconds = o.askTimeoutSeconds;
            askDefaultAction = o.askDefaultAction;
            allowStartWithFailedValidation = o.allowStartWithFailedValidation;
            spawnRestartHelper = o.spawnRestartHelper;
            temporaryTicketReleaseTicks = o.temporaryTicketReleaseTicks;
            preloadMode = o.preloadMode;
            maxPreloadChunks = o.maxPreloadChunks;
            chunkReadyTimeoutSeconds = o.chunkReadyTimeoutSeconds;
            chunks.copyFrom(o.chunks);
            strict.copyFrom(o.strict);
            lock.copyFrom(o.lock);
        }
    }

    public static final class Chunks {
        public int maxConcurrentLoads = 4;

        void copyFrom(Chunks o) {
            maxConcurrentLoads = o.maxConcurrentLoads;
        }
    }

    public static final class Strict {
        public boolean allowUnsupportedRuntimeState = false;
        public boolean allowUnsupportedCarpetState = false;
        public boolean requireEntityOrder = true;
        public boolean requireBlockEntityTickerOrder = true;
        public boolean requireTicketLedger = true;
        public final StrictPlayers players = new StrictPlayers();

        void copyFrom(Strict o) {
            allowUnsupportedRuntimeState = o.allowUnsupportedRuntimeState;
            allowUnsupportedCarpetState = o.allowUnsupportedCarpetState;
            requireEntityOrder = o.requireEntityOrder;
            requireBlockEntityTickerOrder = o.requireBlockEntityTickerOrder;
            requireTicketLedger = o.requireTicketLedger;
            players.copyFrom(o.players);
        }
    }

    public static final class StrictPlayers {
        public boolean requireCheckpointPlayers = false;

        void copyFrom(StrictPlayers o) {
            requireCheckpointPlayers = o.requireCheckpointPlayers;
        }
    }

    public static final class Lock {
        public boolean allowLogin = true,
                allowChat = true,
                allowCommands = true,
                allowPlayerMovement = true,
                allowPlayerLook = true,
                allowSneak = true;
        public boolean allowBlockBreak = false,
                allowBlockPlace = false,
                allowItemUse = false,
                allowEntityAttack = false,
                allowEntityInteract = false,
                allowContainerInteraction = false,
                allowItemDrop = false,
                allowItemPickup = false;

        void copyFrom(Lock o) {
            allowLogin = o.allowLogin;
            allowChat = o.allowChat;
            allowCommands = o.allowCommands;
            allowPlayerMovement = o.allowPlayerMovement;
            allowPlayerLook = o.allowPlayerLook;
            allowSneak = o.allowSneak;
            allowBlockBreak = o.allowBlockBreak;
            allowBlockPlace = o.allowBlockPlace;
            allowItemUse = o.allowItemUse;
            allowEntityAttack = o.allowEntityAttack;
            allowEntityInteract = o.allowEntityInteract;
            allowContainerInteraction = o.allowContainerInteraction;
            allowItemDrop = o.allowItemDrop;
            allowItemPickup = o.allowItemPickup;
        }
    }

    public static final class PlayerSnapshot {
        public boolean enabled = true,
                dimension = true,
                position = true,
                rotation = true,
                gamemode = false,
                abilities = false;
        public boolean inventoryMain = false,
                inventoryArmor = false,
                inventoryOffhand = false,
                selectedHotbarSlot = false;
        public boolean experienceLevel = false,
                experienceProgress = false,
                experienceTotal = false,
                enderChest = false;
        public boolean health = false,
                absorption = false,
                foodLevel = false,
                foodSaturation = false,
                foodExhaustion = false;
        public boolean potionEffects = false,
                velocity = false,
                fallDistance = false,
                fireTicks = false,
                air = false,
                spawnPoint = false;
        public final VanillaFiles vanillaFiles = new VanillaFiles();

        void copyFrom(PlayerSnapshot o) {
            dimension = o.dimension;
            position = o.position;
            rotation = o.rotation;
            gamemode = o.gamemode;
            abilities = o.abilities;
            enabled = o.enabled;
            inventoryMain = o.inventoryMain;
            inventoryArmor = o.inventoryArmor;
            inventoryOffhand = o.inventoryOffhand;
            selectedHotbarSlot = o.selectedHotbarSlot;
            experienceLevel = o.experienceLevel;
            experienceProgress = o.experienceProgress;
            experienceTotal = o.experienceTotal;
            enderChest = o.enderChest;
            health = o.health;
            absorption = o.absorption;
            foodLevel = o.foodLevel;
            foodSaturation = o.foodSaturation;
            foodExhaustion = o.foodExhaustion;
            potionEffects = o.potionEffects;
            velocity = o.velocity;
            fallDistance = o.fallDistance;
            fireTicks = o.fireTicks;
            air = o.air;
            spawnPoint = o.spawnPoint;
            vanillaFiles.copyFrom(o.vanillaFiles);
        }
    }

    public static final class VanillaFiles {
        public boolean restorePlayerdata = false, restoreStats = false, restoreAdvancements = false;

        void copyFrom(VanillaFiles o) {
            restorePlayerdata = o.restorePlayerdata;
            restoreStats = o.restoreStats;
            restoreAdvancements = o.restoreAdvancements;
        }
    }

    public static final class FakePlayer {
        public boolean enabled = true,
                autoRejoin = true,
                restoreBeforeUnfreeze = true,
                requireAllReadyBeforeStart = true;
        public final FakeSnapshot snapshot = new FakeSnapshot();

        void copyFrom(FakePlayer o) {
            enabled = o.enabled;
            autoRejoin = o.autoRejoin;
            restoreBeforeUnfreeze = o.restoreBeforeUnfreeze;
            requireAllReadyBeforeStart = o.requireAllReadyBeforeStart;
            snapshot.copyFrom(o.snapshot);
        }
    }

    public static final class FakeSnapshot {
        public boolean dimension = true,
                position = true,
                rotation = true,
                gamemode = true,
                flying = true,
                inventory = true,
                selectedSlot = true,
                health = true,
                hunger = true,
                effects = true,
                sneaking = true,
                sprinting = true,
                movement = true,
                actions = true,
                actionInternalTiming = true,
                itemUseCooldown = true,
                blockBreakProgress = true;

        void copyFrom(FakeSnapshot o) {
            dimension = o.dimension;
            position = o.position;
            rotation = o.rotation;
            gamemode = o.gamemode;
            flying = o.flying;
            inventory = o.inventory;
            selectedSlot = o.selectedSlot;
            health = o.health;
            hunger = o.hunger;
            effects = o.effects;
            sneaking = o.sneaking;
            sprinting = o.sprinting;
            movement = o.movement;
            actions = o.actions;
            actionInternalTiming = o.actionInternalTiming;
            itemUseCooldown = o.itemUseCooldown;
            blockBreakProgress = o.blockBreakProgress;
        }
    }

    public static final class Consistency {
        public String mode = "IGNORE";

        void copyFrom(Consistency o) {
            mode = o.mode;
        }
    }

    public static final class Login {
        public int retryTimeoutSeconds = 5;

        void copyFrom(Login o) {
            retryTimeoutSeconds = o.retryTimeoutSeconds;
        }
    }

    public static final class Notifications {
        public boolean frozenActionBar = true;
        public int checkpointActionBarDelayTicks = 10;
        public boolean askPromptInGame = true;

        void copyFrom(Notifications o) {
            frozenActionBar = o.frozenActionBar;
            checkpointActionBarDelayTicks = o.checkpointActionBarDelayTicks;
            askPromptInGame = o.askPromptInGame;
        }
    }

    public static final class Logging {
        public String level = "NORMAL";
        public boolean console = true, file = true;
        public int maxFileMb = 50, retainedFiles = 10;
        public String consoleColor = "AUTO";
        public boolean chatColor = true;
        public boolean showCheckpointContents = false,
                showPlayerDetails = false,
                showFakeplayerDetails = true,
                showBlockEvents = false,
                showFileOperations = false,
                showTransactionDetails = false;

        void copyFrom(Logging o) {
            level = o.level;
            console = o.console;
            consoleColor = o.consoleColor;
            chatColor = o.chatColor;
            file = o.file;
            maxFileMb = o.maxFileMb;
            retainedFiles = o.retainedFiles;
            showCheckpointContents = o.showCheckpointContents;
            showPlayerDetails = o.showPlayerDetails;
            showFakeplayerDetails = o.showFakeplayerDetails;
            showBlockEvents = o.showBlockEvents;
            showFileOperations = o.showFileOperations;
            showTransactionDetails = o.showTransactionDetails;
        }
    }
}
