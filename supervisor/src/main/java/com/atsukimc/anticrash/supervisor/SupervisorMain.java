package com.atsukimc.anticrash.supervisor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Foreground, process-level wrapper for the Minecraft server. The wrapper is intentionally
 * independent from the mod: it owns the child JVM, inherits the same console streams, and
 * turns a controlled restart marker or an abnormal child exit into an observable next launch.
 */
public final class SupervisorMain {
    private static final String SUPERVISED_ENV = "ANTICRASH_SUPERVISED";
    private static final String MARKER_ENV = "ANTICRASH_RESTART_MARKER";
    private static final String EXIT_ON_OOM = "-XX:+ExitOnOutOfMemoryError";
    private static final String DISABLE_EXIT_ON_OOM = "-XX:-ExitOnOutOfMemoryError";

    private SupervisorMain() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.command.isEmpty() || options.help) {
            usage(options.language);
            return;
        }
        if (options.dryRun) {
            System.out.println(options.text("AntiCrash Supervisor 確認のみ: ", "AntiCrash Supervisor dry-run: ")
                    + redactedCommand(options.command));
            return;
        }

        long supervisorPid = ProcessHandle.current().pid();
        System.out.println(options.text(
                "AntiCrash Supervisor: 前面監視を開始しました pid=" + supervisorPid,
                "AntiCrash Supervisor: foreground monitoring started pid=" + supervisorPid));
        int restarts = 0;
        int launchSequence = 0;
        Path workingDirectory = options.workingDirectory == null
                ? Path.of("").toAbsolutePath().normalize()
                : options.workingDirectory;
        if (Files.isSymbolicLink(workingDirectory)
                || !Files.isDirectory(workingDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workdir is not a safe directory: " + workingDirectory);
        }
        Path restartMarker = workingDirectory.resolve(".anticrash/state/restart-request");
        // A marker left by a previous direct/helper launch belongs to the first child startup:
        // that child will apply pending restore before opening the world. It must not trigger an
        // unrelated restart after the child later stops normally.
        if (claimRestartMarker(workingDirectory, restartMarker)) {
            System.out.println(options.text(
                    "AntiCrash Supervisor: 起動前に残っていた再起動markerを消費しました。pending restoreは最初の起動で処理されます。",
                    "AntiCrash Supervisor: consumed a pre-existing restart marker; pending restore will be handled by the first startup."));
        }
        // Management tools usually stop only the PID they launched. Without forwarding, a SIGTERM
        // to the Supervisor would leave the Minecraft child running detached from its console.
        AtomicReference<Process> currentChild = new AtomicReference<>();
        AtomicBoolean supervisorStopping = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            supervisorStopping.set(true);
            stopChild(currentChild.get(), options);
        }, "AntiCrash-Supervisor-Shutdown"));
        while (true) {
            if (supervisorStopping.get()) return;
            ProcessBuilder builder = new ProcessBuilder(hardenedCommand(options.command));
            builder.directory(workingDirectory.toFile());
            builder.environment().put(SUPERVISED_ENV, "1");
            builder.environment().put(MARKER_ENV, restartMarker.toString());
            builder.inheritIO();
            Instant started = Instant.now();
            Process process = builder.start();
            currentChild.set(process);
            if (supervisorStopping.get()) {
                // The shutdown hook may have run between the check above and this launch.
                stopChild(process, options);
                return;
            }
            launchSequence++;
            System.out.println(options.text(
                    "AntiCrash Supervisor: 子Serverを起動しました pid=" + process.pid()
                            + " 試行=" + launchSequence,
                    "AntiCrash Supervisor: started child pid=" + process.pid()
                            + " launch=" + launchSequence));
            int exitCode = process.waitFor();
            currentChild.set(null);
            if (supervisorStopping.get()) return;
            Duration lifetime = Duration.between(started, Instant.now());
            System.out.println(options.text(
                    "AntiCrash Supervisor: 子Serverが終了しました pid=" + process.pid()
                            + " code=" + exitCode + " 稼働時間=" + lifetime.toSeconds() + "秒",
                    "AntiCrash Supervisor: child exited pid=" + process.pid()
                            + " code=" + exitCode + " after " + lifetime.toSeconds() + "s"));

            if (claimRestartMarker(workingDirectory, restartMarker)) {
                System.out.println(options.text(
                        "AntiCrash Supervisor: 復元用の再起動要求を受理しました。pid="
                                + process.pid() + "を終了し、次の試行を開始します。",
                        "AntiCrash Supervisor: accepted a restore restart request from child pid="
                                + process.pid() + "; starting the next launch."));
                restarts = 0;
                if (options.restartDelayMillis > 0) Thread.sleep(options.restartDelayMillis);
                continue;
            }
            int nextRestarts = nextRestartCount(exitCode, restarts, lifetime.toMillis(),
                    options.maxRestarts, options.stableRuntimeMillis);
            if (nextRestarts < 0) {
                if (exitCode != 0) {
                    System.out.println(options.text(
                            "AntiCrash Supervisor: pid=" + process.pid() + " の異常終了(code="
                                    + exitCode + ")が再試行上限に達したため停止します。試行="
                                    + launchSequence,
                            "AntiCrash Supervisor: stopping after child pid=" + process.pid()
                                    + " exited abnormally with code=" + exitCode
                                    + "; short-failure retry limit reached at launch=" + launchSequence));
                }
                return;
            }
            restarts = nextRestarts;
            System.out.println(options.text(
                    "AntiCrash Supervisor: pid=" + process.pid() + " の異常終了(code=" + exitCode
                            + ")を検出しました。再起動します（短時間失敗 "
                            + restarts + "/" + options.maxRestarts + "、次の試行="
                            + (launchSequence + 1) + "）。",
                    "AntiCrash Supervisor: abnormal exit detected for child pid=" + process.pid()
                            + " code=" + exitCode + "; restarting (short failures "
                            + restarts + "/" + options.maxRestarts + ", next launch="
                            + (launchSequence + 1) + ")."));
            if (options.restartDelayMillis > 0) Thread.sleep(options.restartDelayMillis);
        }
    }

    /**
     * Forwards the Supervisor's own termination to the child. SIGTERM lets Minecraft run its
     * shutdown hook and save; AntiCrash still classifies that stop as abnormal (it was not a
     * /stop from the server thread), exactly as if the child had been sent SIGTERM directly.
     */
    static void stopChild(Process child, Options options) {
        if (child == null || !child.isAlive()) return;
        System.out.println(options.text(
                "AntiCrash Supervisor: 終了要求を受けたため子Server pid=" + child.pid() + " を停止します。",
                "AntiCrash Supervisor: stopping child pid=" + child.pid() + " because the supervisor is terminating."));
        child.destroy();
        try {
            if (!child.waitFor(options.shutdownGraceMillis, TimeUnit.MILLISECONDS)) {
                System.out.println(options.text(
                        "AntiCrash Supervisor: 子Serverが時間内に終了しないため強制終了します。",
                        "AntiCrash Supervisor: child did not exit in time; terminating it forcibly."));
                child.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            child.destroyForcibly();
        }
    }

    /**
     * Limits only a burst of short-lived failures. A server that had reached the configured
     * stable lifetime receives a fresh retry budget, so an OOM or JVM failure after normal
     * long-running operation cannot leave the server permanently stopped.
     */
    static int nextRestartCount(int exitCode, int restarts, long lifetimeMillis,
                                int maxRestarts, long stableRuntimeMillis) {
        if (exitCode == 0) return -1;
        int failuresInBurst = stableRuntimeMillis > 0 && lifetimeMillis >= stableRuntimeMillis
                ? 0 : restarts;
        if (failuresInBurst >= maxRestarts) return -1;
        return failuresInBurst + 1;
    }

    /**
     * Turns an OutOfMemoryError into a process exit so the supervisor can recover. Otherwise a
     * dead server thread may leave worker threads alive indefinitely and make the child appear
     * healthy to a process-only wait loop.
     */
    static List<String> hardenedCommand(List<String> command) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("server command is empty");
        String executableName = Path.of(command.get(0)).getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!(executableName.equals("java") || executableName.equals("java.exe"))) return List.copyOf(command);
        ArrayList<String> hardened = new ArrayList<>(command);
        for (int index = 1; index < hardened.size(); index++) {
            if (DISABLE_EXIT_ON_OOM.equals(hardened.get(index))) {
                hardened.set(index, EXIT_ON_OOM);
                return List.copyOf(hardened);
            }
            if (EXIT_ON_OOM.equals(hardened.get(index))) return List.copyOf(hardened);
        }
        hardened.add(1, EXIT_ON_OOM);
        return List.copyOf(hardened);
    }

    /** Prevents dry-run diagnostics from disclosing common command-line credentials. */
    static List<String> redactedCommand(List<String> command) {
        ArrayList<String> redacted = new ArrayList<>(command.size());
        boolean redactNext = false;
        for (String argument : command) {
            if (redactNext) {
                redacted.add("<redacted>");
                redactNext = false;
                continue;
            }
            String lower = argument.toLowerCase(java.util.Locale.ROOT);
            if (isSensitiveOption(lower)) {
                redacted.add(argument);
                redactNext = true;
                continue;
            }
            int equals = argument.indexOf('=');
            if (equals > 0 && containsSensitiveName(lower.substring(0, equals))) {
                redacted.add(argument.substring(0, equals + 1) + "<redacted>");
            } else {
                redacted.add(argument);
            }
        }
        return List.copyOf(redacted);
    }

    private static boolean isSensitiveOption(String argument) {
        return argument.equals("--password") || argument.equals("--token")
                || argument.equals("--secret") || argument.equals("--api-key")
                || argument.equals("--apikey") || argument.equals("--access-token");
    }

    private static boolean containsSensitiveName(String name) {
        return name.contains("password") || name.contains("passwd") || name.contains("token")
                || name.contains("secret") || name.contains("api-key") || name.contains("apikey");
    }

    /** Claims only the fixed regular marker below the supplied server work directory. */
    static boolean claimRestartMarker(Path workingDirectory, Path restartMarker) throws IOException {
        Path root = workingDirectory.toAbsolutePath().normalize();
        Path expected = root.resolve(".anticrash").resolve("state").resolve("restart-request");
        Path marker = restartMarker.toAbsolutePath().normalize();
        if (!marker.equals(expected)) throw new IOException("Unsafe AntiCrash restart marker path: " + marker);

        Path internal = root.resolve(".anticrash");
        Path state = internal.resolve("state");
        for (Path directory : List.of(internal, state)) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return false;
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unsafe AntiCrash restart marker directory: " + directory);
            }
        }
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return false;
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe AntiCrash restart marker: " + marker);
        }
        validateRestartMarker(marker);
        return Files.deleteIfExists(marker);
    }

    private static void validateRestartMarker(Path marker) throws IOException {
        String request = null;
        String createdAt = null;
        for (String rawLine : readBoundedMarker(marker).split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("Malformed AntiCrash restart marker");
            String key = line.substring(0, equals);
            String value = line.substring(equals + 1);
            if (key.equals("request") && request == null) request = value;
            else if (key.equals("created_at") && createdAt == null) createdAt = value;
            else throw new IOException("Malformed AntiCrash restart marker");
        }
        try {
            if (request == null || createdAt == null) throw new IllegalArgumentException("missing field");
            java.util.UUID.fromString(request);
            Instant.parse(createdAt);
        } catch (RuntimeException exception) {
            throw new IOException("Malformed AntiCrash restart marker", exception);
        }
    }

    private static String readBoundedMarker(Path marker) throws IOException {
        try (InputStream input = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(1025);
            if (bytes.length > 1024) throw new IOException("AntiCrash restart marker is too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void usage(String language) {
        boolean english = "en".equalsIgnoreCase(language);
        System.out.println(english
                ? "Usage: java -jar AntiCrash-Supervisor-1.0.0.jar [options] -- <server command>"
                : "使用方法: java -jar AntiCrash-Supervisor-1.0.0.jar [options] -- <Server起動command>");
        System.out.println(english ? "  --workdir <path>       child working directory"
                : "  --workdir <path>       Serverの作業directory");
        System.out.println(english ? "  --max-restarts <n>     retries in one short-failure burst (default 3)"
                : "  --max-restarts <n>     短時間の連続失敗を再試行する回数（既定3）");
        System.out.println(english ? "  --restart-delay-ms <n> delay between attempts (default 5000)"
                : "  --restart-delay-ms <n> 再試行間隔ms（既定5000）");
        System.out.println(english ? "  --stable-runtime-ms <n> reset retry budget after this lifetime; 0 disables reset (default 60000)"
                : "  --stable-runtime-ms <n> この稼働時間後に再試行回数をreset。0は無効（既定60000）");
        System.out.println(english ? "  --shutdown-grace-ms <n> wait for the child after the supervisor is terminated (default 120000)"
                : "  --shutdown-grace-ms <n> Supervisor終了時に子Serverの終了を待つms（既定120000）");
        System.out.println(english ? "  --language <ja|en>     supervisor message language (default ja)"
                : "  --language <ja|en>     Supervisorの表示言語（既定ja）");
        System.out.println(english ? "  --dry-run              print a redacted command without starting it"
                : "  --dry-run              起動せず、機密値を伏せたcommandを表示");
        System.out.println(english ? "  --help                 show this help"
                : "  --help                 このヘルプを表示");
    }

    private static final class Options {
        private Path workingDirectory;
        private int maxRestarts = 3;
        private long restartDelayMillis = 5000;
        private long stableRuntimeMillis = 60_000;
        private long shutdownGraceMillis = 120_000;
        private boolean dryRun;
        private boolean help;
        private String language = "ja";
        private final List<String> command = new ArrayList<>();

        private static Options parse(String[] args) {
            Options options = new Options();
            boolean commandPart = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (commandPart) {
                    options.command.add(arg);
                    continue;
                }
                if ("--".equals(arg)) {
                    commandPart = true;
                } else if ("--help".equals(arg)) {
                    options.help = true;
                } else if ("--dry-run".equals(arg)) {
                    options.dryRun = true;
                } else if ("--workdir".equals(arg)) {
                    options.workingDirectory = Path.of(requireValue(args, ++i, arg)).toAbsolutePath().normalize();
                    if (Files.isSymbolicLink(options.workingDirectory)
                            || !Files.isDirectory(options.workingDirectory, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IllegalArgumentException("workdir is not a safe directory: " + options.workingDirectory);
                    }
                } else if ("--max-restarts".equals(arg)) {
                    options.maxRestarts = nonNegativeInt(requireValue(args, ++i, arg), arg);
                } else if ("--restart-delay-ms".equals(arg)) {
                    options.restartDelayMillis = nonNegativeLong(requireValue(args, ++i, arg), arg);
                } else if ("--stable-runtime-ms".equals(arg)) {
                    options.stableRuntimeMillis = nonNegativeLong(requireValue(args, ++i, arg), arg);
                } else if ("--shutdown-grace-ms".equals(arg)) {
                    options.shutdownGraceMillis = nonNegativeLong(requireValue(args, ++i, arg), arg);
                } else if ("--language".equals(arg)) {
                    options.language = requireValue(args, ++i, arg).toLowerCase(java.util.Locale.ROOT);
                    if (!("ja".equals(options.language) || "en".equals(options.language))) {
                        throw new IllegalArgumentException("--language must be ja or en");
                    }
                } else {
                    throw new IllegalArgumentException("Unknown option: " + arg + " (use -- before the child command)");
                }
            }
            return options;
        }

        private String text(String japanese, String english) {
            return "en".equals(language) ? english : japanese;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            return args[index];
        }

        private static int nonNegativeInt(String value, String option) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0) throw new IllegalArgumentException(option + " must be non-negative");
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid integer for " + option + ": " + value, exception);
            }
        }

        private static long nonNegativeLong(String value, String option) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed < 0) throw new IllegalArgumentException(option + " must be non-negative");
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid integer for " + option + ": " + value, exception);
            }
        }
    }
}
