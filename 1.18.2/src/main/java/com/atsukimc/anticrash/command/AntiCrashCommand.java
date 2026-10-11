package com.atsukimc.anticrash.command;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.integration.carpet.CarpetIntegration;
import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Administrative commands. Every mutating operation is explicit and reports a safe failure. */
public final class AntiCrashCommand {
    private AntiCrashCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, AntiCrashController controller) {
        registerAnswer(dispatcher, controller, "y", true);
        registerAnswer(dispatcher, controller, "Y", true);
        registerAnswer(dispatcher, controller, "n", false);
        registerAnswer(dispatcher, controller, "N", false);
        dispatcher.register(literal("ac")
                .requires(source -> hasAnyPermission(source, controller))
                .executes(context -> overview(context.getSource(), controller))
                .then(literal("help").requires(source -> permitted(source, controller.config().commands.help))
                        .executes(context -> help(context.getSource(), controller)))
                .then(literal("status").requires(source -> permitted(source, controller.config().commands.status))
                        .executes(context -> status(context.getSource(), controller)))
                .then(literal("freeze").requires(source -> permitted(source, controller.config().commands.freeze))
                        .executes(context -> freeze(context.getSource(), controller)))
                .then(literal("checkpoint").requires(source -> permitted(source, controller.config().commands.checkpoint))
                        .executes(context -> checkpoint(context.getSource(), controller))
                        .then(literal("now").executes(context -> checkpoint(context.getSource(), controller)))
                        .then(literal("base").executes(context -> checkpointBase(context.getSource(), controller)))
                        .then(literal("compact").executes(context -> checkpointCompact(context.getSource(), controller)))
                        .then(literal("list").executes(context -> list(context.getSource(), controller)))
                        .then(literal("info").then(argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> suggestCheckpoints(builder, controller, false))
                                .executes(context -> inspect(context.getSource(), controller, StringArgumentType.getString(context, "id")))))
                        .then(literal("restore").requires(source -> permitted(source, controller.config().commands.recovery)).then(argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> suggestCheckpoints(builder, controller, true))
                                .executes(context -> restore(context.getSource(), controller, StringArgumentType.getString(context, "id")))))
                )
                .then(literal("recovery").requires(source -> permitted(source, controller.config().commands.recovery))
                        .then(literal("status").executes(context -> status(context.getSource(), controller)))
                        .then(literal("verify")
                                .executes(context -> recoveryVerify(context.getSource(), controller, false))
                                .then(literal("--verbose")
                                        .executes(context -> recoveryVerify(context.getSource(), controller, true))))
                        .then(literal("restore").then(argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> suggestCheckpoints(builder, controller, true))
                                .executes(context -> restore(context.getSource(), controller, StringArgumentType.getString(context, "id")))))
                        .then(literal("start")
                                .executes(context -> start(context.getSource(), controller, false))
                                .then(literal("--force")
                                        .requires(source -> permitted(source, controller.config().commands.startForce))
                                        .executes(context -> start(context.getSource(), controller, true))))
                        .then(literal("abort").executes(context -> abort(context.getSource(), controller)))
                        .then(literal("continue").executes(context -> continueRecovery(context.getSource(), controller)))
                        .then(literal("y").executes(context -> answer(context.getSource(), controller, true)))
                        .then(literal("n").executes(context -> answer(context.getSource(), controller, false))))
                .then(literal("start").requires(source -> permitted(source, controller.config().commands.start))
                        .executes(context -> start(context.getSource(), controller, false))
                        .then(literal("--force").requires(source -> permitted(source, controller.config().commands.startForce))
                                .executes(context -> start(context.getSource(), controller, true))))
                .then(literal("reload").requires(source -> permitted(source, controller.config().commands.config))
                        .executes(context -> reload(context.getSource(), controller)))
                .then(literal("config").requires(source -> permitted(source, controller.config().commands.config))
                        .then(literal("reload").executes(context -> reload(context.getSource(), controller))))
                .then(literal("list").requires(source -> permitted(source, controller.config().commands.checkpoint))
                        .executes(context -> list(context.getSource(), controller)))
                .then(literal("inspect").requires(source -> permitted(source, controller.config().commands.checkpoint))
                        .then(argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> suggestCheckpoints(builder, controller, false))
                        .executes(context -> inspect(context.getSource(), controller, StringArgumentType.getString(context, "id")))))
                .then(literal("fakeplayers").requires(source -> permitted(source, controller.config().commands.fakeplayers))
                        .executes(context -> fakePlayers(context.getSource(), controller))
                        .then(literal("restore").executes(context -> restoreFakePlayers(context.getSource(), controller)))
                        .then(literal("info").then(argument("name", StringArgumentType.word())
                                .executes(context -> fakePlayerInfo(context.getSource(), controller, StringArgumentType.getString(context, "name"))))))
                .then(literal("journal").requires(source -> permitted(source, controller.config().commands.journal))
                        .executes(context -> journalStatus(context.getSource(), controller))
                        .then(literal("status").executes(context -> journalStatus(context.getSource(), controller)))
                        .then(literal("list").executes(context -> journalList(context.getSource(), controller))))
                .then(literal("debug").requires(source -> permitted(source, controller.config().commands.debug))
                        .then(literal("dump")
                        .executes(context -> debugDump(context.getSource(), controller)))
                        .then(literal("equivalence")
                                .executes(context -> debugEquivalence(context.getSource(), controller, false))
                                .then(literal("--verbose")
                                        .executes(context -> debugEquivalence(context.getSource(), controller, true))))
                        .then(literal("checkpoint-performance")
                                .executes(context -> checkpointPerformance(context.getSource(), controller, false))
                                .then(literal("verbose")
                                        .executes(context -> checkpointPerformance(context.getSource(), controller, true)))
                                .then(literal("last")
                                        .executes(context -> checkpointPerformance(context.getSource(), controller, false))
                                        .then(literal("verbose")
                                                .executes(context -> checkpointPerformance(context.getSource(), controller, true))))
                                .then(literal("reset")
                                        .executes(context -> resetCheckpointPerformance(context.getSource(), controller))) )
                        .then(literal("mirror")
                                .executes(context -> debugMirror(context.getSource(), controller, false))
                                .then(literal("audit")
                                        .executes(context -> debugMirror(context.getSource(), controller, true))))));
    }

    private static void registerAnswer(CommandDispatcher<ServerCommandSource> dispatcher,
                                       AntiCrashController controller, String name, boolean restore) {
        dispatcher.register(literal(name)
                .requires(source -> permitted(source, controller.config().commands.recovery))
                .executes(context -> answer(context.getSource(), controller, restore)));
    }

    private static boolean permitted(ServerCommandSource source, int permissionLevel) {
        return source.hasPermissionLevel(permissionLevel);
    }

    private static boolean hasAnyPermission(ServerCommandSource source,
                                            AntiCrashController controller) {
        var commands = controller.config().commands;
        return permitted(source, commands.help) || permitted(source, commands.status)
                || permitted(source, commands.start) || permitted(source, commands.startForce)
                || permitted(source, commands.freeze) || permitted(source, commands.checkpoint)
                || permitted(source, commands.recovery) || permitted(source, commands.fakeplayers)
                || permitted(source, commands.journal) || permitted(source, commands.config)
                || permitted(source, commands.debug);
    }

    private static int overview(ServerCommandSource source, AntiCrashController controller) {
        return help(source, controller);
    }

    private static int help(ServerCommandSource source, AntiCrashController controller) {
        feedback(source, message(controller, "command.help.header"), false);
        if (permitted(source, controller.config().commands.start))
            feedback(source, message(controller, "command.help.start"), false);
        if (permitted(source, controller.config().commands.status))
            feedback(source, message(controller, "command.help.status"), false);
        if (permitted(source, controller.config().commands.freeze))
            feedback(source, message(controller, "command.help.freeze"), false);
        if (permitted(source, controller.config().commands.checkpoint))
            feedback(source, message(controller, "command.help.checkpoint"), false);
        if (permitted(source, controller.config().commands.recovery))
            feedback(source, message(controller, "command.help.recovery"), false);
        if (permitted(source, controller.config().commands.fakeplayers))
            feedback(source, message(controller, "command.help.fakeplayers"), false);
        if (permitted(source, controller.config().commands.journal))
            feedback(source, message(controller, "command.help.journal"), false);
        if (permitted(source, controller.config().commands.config))
            feedback(source, message(controller, "command.help.config"), false);
        if (permitted(source, controller.config().commands.debug))
            feedback(source, message(controller, "command.help.debug"), false);
        return 1;
    }

    private static int status(ServerCommandSource source, AntiCrashController controller) {
        AntiCrashController.StatusSnapshot status=controller.statusSnapshot();
        feedback(source, message(controller, "command.status", status.state(),
                status.selectedCheckpoint(),status.activeCheckpoint(),status.worldFrozen(),
                status.startValidation(),status.backend(),status.integrity(),status.chunksReady(),
                status.chunksExpected(),status.chunksFailed(),status.blockEventsRestored(),
                status.blockEventsFailed(),status.fakePlayersReady(),status.fakePlayersExpected(),
                status.fakePlayersFailed(),summarize(status.realPlayers()),status.consistencyMode(),
                safeText(status.world()),safeText(status.error()),
                seconds(controller, status.nextCheckpointSeconds()),
                seconds(controller, status.askRemainingSeconds())), false);
        // Surfacing the concrete blocker here saves a round trip through the server log.
        if (status.startRejectionReason() != null) {
            feedback(source, message(controller, "command.status.start_blocked",
                    safeText(status.startRejectionReason())), false);
        }
        feedback(source, message(controller, "command.status.equivalence",
                status.fidelity(), status.expectedChunks(), status.readyChunks(),
                status.pendingFutureCount(), status.currentConcurrentLoads(),
                status.equivalenceStatus(), status.blockingCategory(), status.carpetCompleteness(),
                summarize(status.unsupportedRuntimeState())), false);
        String verification = controller.runtimeVerificationSummary();
        if (!verification.isEmpty()) feedback(source, message(controller, "command.status.verification", verification), false);
        String degraded = controller.recoveryDegradedSummary();
        if (!degraded.isEmpty()) feedback(source, message(controller, "command.status.degraded", degraded), false);
        feedback(source, controller.checkpointPerformanceReport(), false);
        return 1;
    }

    private static int checkpoint(ServerCommandSource source, AntiCrashController controller) {
        if (controller.requestCheckpoint()) {
            feedback(source, message(controller, "command.checkpoint.requested"), true);
            return 1;
        }
        error(source, message(controller, "command.checkpoint.rejected"));
        return 0;
    }

    private static int checkpointBase(ServerCommandSource source, AntiCrashController controller) {
        if (controller.requestBaseCheckpoint()) {
            feedback(source, message(controller, "command.checkpoint.base_requested"), true);
            return 1;
        }
        error(source, message(controller, "command.checkpoint.rejected"));
        return 0;
    }

    private static int checkpointCompact(ServerCommandSource source, AntiCrashController controller) {
        if (controller.requestCheckpointCompaction()) {
            feedback(source, message(controller, "command.checkpoint.compaction_requested"), true);
            return 1;
        }
        error(source, message(controller, "command.checkpoint.compaction_rejected"));
        return 0;
    }

    private static int freeze(ServerCommandSource source, AntiCrashController controller) {
        if (controller.freeze()) {
            feedback(source, message(controller, "command.freeze", controller.state()), true);
            return 1;
        }
        error(source, message(controller, "command.freeze.rejected", controller.state()));
        return 0;
    }

    private static int restore(ServerCommandSource source, AntiCrashController controller, String id) {
        if (controller.scheduleCheckpointRestore(id)) {
            feedback(source, message(controller, "command.restore.scheduled", id), true);
            return 1;
        }
        error(source, message(controller, "command.restore.rejected", id));
        return 0;
    }

    private static int start(ServerCommandSource source, AntiCrashController controller, boolean force) {
        // Ask for the reason before the attempt: a successful request changes the state that the
        // explanation is derived from.
        String reason = controller.startRejectionReason(force);
        if (reason == null && controller.requestStart(force)) {
            feedback(source, message(controller, "command.start.scheduled"), true);
            return 1;
        }
        error(source, message(controller, "command.start.rejected",
                reason == null ? message(controller, "start.reason.state_persist_failed") : reason));
        return 0;
    }

    private static int abort(ServerCommandSource source, AntiCrashController controller) {
        if (controller.abortRecovery()) {
            feedback(source, message(controller, "command.abort.scheduled"), true);
            return 1;
        }
        error(source, message(controller, "command.abort.rejected"));
        return 0;
    }

    private static int continueRecovery(ServerCommandSource source, AntiCrashController controller) {
        if (controller.continueRecovery()) {
            feedback(source, message(controller, "command.continue.success"), true);
            return 1;
        }
        error(source, message(controller, "command.continue.rejected"));
        return 0;
    }

    private static int answer(ServerCommandSource source, AntiCrashController controller, boolean restore) {
        String checkpointId = controller.selectedCheckpointId();
        if (controller.answerRecovery(restore)) {
            feedback(source, restore
                    ? message(controller, "command.recovery.answer_restore", checkpointId)
                    : message(controller, "command.recovery.answer_continue"), true);
            return 1;
        }
        error(source, message(controller, "command.recovery.answer_rejected"));
        return 0;
    }

    private static int reload(ServerCommandSource source, AntiCrashController controller) {
        try {
            controller.reloadConfig();
            feedback(source, message(controller, "command.reload.success"), true);
            return 1;
        } catch (Exception exception) {
            error(source, message(controller, "command.reload.failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int list(ServerCommandSource source, AntiCrashController controller) {
        try {
            List<Path> checkpoints = controller.availableCheckpoints();
            feedback(source, checkpoints.isEmpty() ? message(controller, "command.list.empty")
                    : message(controller, "command.list.result",
                    summarize(describeCheckpoints(checkpoints))), false);
            return 1;
        } catch (Exception exception) {
            error(source, message(controller, "command.list.failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int inspect(ServerCommandSource source, AntiCrashController controller, String id) {
        try {
            CheckpointManifest manifest = controller.manifest(id);
            feedback(source, message(controller, "command.inspect.result", manifest.checkpointId,
                    manifest.snapshotBackend, manifest.serverTick, manifest.dimensions,
                    manifest.totalBytes, manifest.changedFiles, manifest.reusedFiles,
                    manifest.playerSnapshotCount, manifest.fakePlayerCount, manifest.blockEventCount), false);
            return 1;
        } catch (Exception exception) {
            error(source, message(controller, "command.inspect.failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int journalStatus(ServerCommandSource source, AntiCrashController controller) {
        try {
            feedback(source, message(controller, "command.journal.status", controller.journalCount(),
                    controller.transactionWarningCount(), summarizePaths(controller.journalFiles())), false);
            return 1;
        } catch (Exception exception) {
            error(source, message(controller, "command.journal.status_failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int journalList(ServerCommandSource source, AntiCrashController controller) {
        try {
            feedback(source, message(controller, "command.journal.list", summarizePaths(controller.journalFiles())), false);
            return 1;
        } catch (Exception exception) {
            error(source, message(controller, "command.journal.list_failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int fakePlayers(ServerCommandSource source, AntiCrashController controller) {
        List<FakePlayerSnapshot> players = controller.currentFakePlayers();
        feedback(source, message(controller, "command.fakeplayers.list",
                summarize(players.stream().map(player -> player.name).toList())), false);
        return 1;
    }

    private static int restoreFakePlayers(ServerCommandSource source, AntiCrashController controller) {
        try {
            CarpetIntegration.RestoreSummary summary = controller.restoreFakePlayersNow();
            feedback(source, message(controller, "command.fakeplayer.restore", summary.ready, summary.expected, summary.failed), true);
            return summary.failed == 0 ? 1 : 0;
        } catch (Exception exception) {
            error(source, message(controller, "command.fakeplayer.restore_failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int fakePlayerInfo(ServerCommandSource source, AntiCrashController controller, String name) {
        FakePlayerSnapshot found = controller.currentFakePlayers().stream()
                .filter(player -> name.equalsIgnoreCase(player.name)).findFirst().orElse(null);
        if (found == null) {
            error(source, message(controller, "command.fakeplayer.not_found", name));
            return 0;
        }
        feedback(source, message(controller, "command.fakeplayer.info", found.name, found.uuid,
                found.dimension, found.x, found.y, found.z, found.actionPackState != null), false);
        return 1;
    }

    private static int debugDump(ServerCommandSource source, AntiCrashController controller) {
        try {
            controller.debugDump();
            feedback(source, message(controller, "command.debug.success"), true);
            return 1;
        } catch (Exception exception) {
            error(source, message(controller, "command.debug.failed", safeDetail(exception)));
            return 0;
        }
    }

    private static int recoveryVerify(ServerCommandSource source, AntiCrashController controller,
                                      boolean verbose) {
        feedback(source, controller.renderEquivalence(verbose), false);
        return 1;
    }

    private static int debugEquivalence(ServerCommandSource source, AntiCrashController controller,
                                        boolean verbose) {
        feedback(source, controller.renderEquivalence(verbose), false);
        return 1;
    }

    private static int checkpointPerformance(ServerCommandSource source,
                                             AntiCrashController controller, boolean verbose) {
        feedback(source, controller.checkpointPerformanceReport(verbose), false);
        return 1;
    }

    private static int debugMirror(ServerCommandSource source, AntiCrashController controller,
                                   boolean audit) {
        feedback(source, controller.checkpointMirrorReport(audit), false);
        return 1;
    }

    private static int resetCheckpointPerformance(ServerCommandSource source,
                                                  AntiCrashController controller) {
        controller.resetCheckpointPerformance();
        feedback(source, message(controller, "command.debug.performance_reset"), true);
        return 1;
    }

    private static String message(AntiCrashController controller, String key, Object... arguments) {
        return MessageCatalog.text(controller.config().language, key, arguments);
    }

    private static CompletableFuture<Suggestions> suggestCheckpoints(SuggestionsBuilder builder,
                                                                      AntiCrashController controller,
                                                                      boolean includeLatest) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (includeLatest && "latest".startsWith(remaining)) builder.suggest("latest");
        try {
            for (Path checkpoint : controller.availableCheckpoints()) {
                String id = checkpoint.getFileName().toString();
                if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(id);
            }
        } catch (Exception ignored) {
            // Suggestions are optional; command execution still reports the actionable error.
        }
        return builder.buildFuture();
    }

    private static final DateTimeFormatter CHECKPOINT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Adds the local commit time to each ID. Choosing which checkpoint to restore is impossible
     * from bare sequence numbers, and the directory timestamp is available without reading and
     * parsing every manifest.
     */
    private static List<String> describeCheckpoints(List<Path> checkpoints) {
        List<String> described = new ArrayList<>(checkpoints.size());
        for (Path checkpoint : checkpoints) {
            String id = checkpoint.getFileName().toString();
            try {
                described.add(id + " (" + CHECKPOINT_TIME.format(
                        Files.getLastModifiedTime(checkpoint).toInstant()) + ")");
            } catch (Exception exception) {
                described.add(id);
            }
        }
        return described;
    }

    /** Renders a countdown, or a placeholder when no timer is running. */
    private static String seconds(AntiCrashController controller, long value) {
        return value < 0 ? message(controller, "command.status.none")
                : message(controller, "command.status.seconds", value);
    }

    private static String summarizePaths(List<Path> paths) {
        return summarize(paths.stream().map(path -> path.getFileName().toString()).toList());
    }

    static String summarize(List<?> values) {
        if (values == null || values.isEmpty()) return "[]";
        int shown = Math.min(20, values.size());
        String result = values.subList(0, shown).toString();
        return shown == values.size() ? result : result + " … (+" + (values.size() - shown) + ")";
    }

    static String safeDetail(Throwable exception) {
        String detail = exception == null ? null : exception.getMessage();
        if (detail == null || detail.isBlank()) detail = exception == null ? "unknown" : exception.getClass().getSimpleName();
        return safeText(detail);
    }

    static String safeText(String detail) {
        if (detail == null || detail.isBlank()) return "unknown";
        StringBuilder safe = new StringBuilder(Math.min(detail.length(), 512));
        for (int index = 0; index < detail.length() && safe.length() < 512; index++) {
            char character = detail.charAt(index);
            safe.append(character == '\n' || character == '\r' || Character.isISOControl(character) ? ' ' : character);
        }
        if (detail.length() > 512) safe.append(" …");
        return safe.toString();
    }

    private static void feedback(ServerCommandSource source, String message, boolean broadcast) {
        AntiCrashController controller = com.atsukimc.anticrash.AntiCrashMod.controller();
        if (controller != null && fromConsole(source)) {
            // Vanilla would log the reply as plain text; print it coloured like other AntiCrash lines.
            controller.logger().commandReply(message, false);
            if (broadcast) ((com.atsukimc.anticrash.mixin.ServerCommandSourceAccessorMixin) source)
                    .anticrash$sendToOps(text(message));
            return;
        }
        source.sendFeedback(text(message), broadcast);
    }

    private static void error(ServerCommandSource source, String message) {
        AntiCrashController controller = com.atsukimc.anticrash.AntiCrashMod.controller();
        if (controller != null && fromConsole(source)) {
            controller.logger().commandReply(message, true);
            return;
        }
        source.sendError(text(message));
    }

    private static boolean fromConsole(ServerCommandSource source) {
        com.atsukimc.anticrash.mixin.ServerCommandSourceAccessorMixin accessor =
                (com.atsukimc.anticrash.mixin.ServerCommandSourceAccessorMixin) source;
        return accessor.anticrash$getOutput() == source.getServer() && !accessor.anticrash$isSilent();
    }

    private static Text text(String message) {
        AntiCrashController controller = com.atsukimc.anticrash.AntiCrashMod.controller();
        return com.atsukimc.anticrash.logging.ChatStyle.text(message, null,
                controller == null || controller.config().logging.chatColor);
    }
}
