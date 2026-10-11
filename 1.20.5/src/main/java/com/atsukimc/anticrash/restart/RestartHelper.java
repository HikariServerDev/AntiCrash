package com.atsukimc.anticrash.restart;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Standalone, dependency-free process that waits for the old JVM and relaunches it in the same
 * console. It also keeps watching the replacement: a non-zero exit (for example an OOM during
 * recovery startup) is retried instead of silently leaving the server stopped.
 */
public final class RestartHelper {
    private static final int START_ATTEMPTS = 3;
    private static final String EXIT_ON_OOM = "-XX:+ExitOnOutOfMemoryError";
    private static final String DISABLE_EXIT_ON_OOM = "-XX:-ExitOnOutOfMemoryError";
    private static final long CRASH_TERMINATION_WAIT_MILLIS = 5_000L;

    private RestartHelper() {
    }

    public static void main(String[] arguments) {
        try {
            Options options = Options.parse(arguments);
            ProcessHandle.of(options.waitPid).ifPresent(handle -> handle.onExit().join());
            if (!RestartRequest.claim(options.marker)) return;

            print(options.language,
                    "旧Serverプロセスの終了を確認しました。復元処理のため再起動します。",
                    "The old server process has exited; restarting for the pending restore.");
            Exception lastFailure = null;
            for (int attempt = 1; attempt <= START_ATTEMPTS; attempt++) {
                try {
                    Map<Path, Long> crashReportsBeforeStart = crashReportSnapshot(options.workingDirectory);
                    ProcessBuilder builder = new ProcessBuilder(hardenedCommand(options.command))
                            .directory(options.workingDirectory.toFile())
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectError(ProcessBuilder.Redirect.INHERIT);
                    // Once the old foreground JVM exits, an interactive shell may reclaim the
                    // terminal process group. A replacement that inherits that terminal's stdin
                    // can then be stopped by SIGTTIN before logging anything. Detached helper
                    // mode therefore uses a null stdin; use Supervisor when console input must be
                    // preserved across the controlled restart.
                    builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
                    long startedAtMillis = System.currentTimeMillis();
                    Process replacement = builder.start();
                    ReplacementResult result = waitForReplacement(replacement,
                            options.workingDirectory, crashReportsBeforeStart, startedAtMillis);
                    if (!result.terminated()) {
                        print(options.language,
                                "新しいServerの終了を確認できないため、重複起動を防止して停止します。",
                                "The replacement server did not terminate cleanly; stopping to avoid a duplicate server.");
                        return;
                    }
                    int exitCode = result.exitCode();
                    if (exitCode == 0 && !result.crashReportDetected()) return;
                    boolean retry = shouldRetry(exitCode, result.crashReportDetected(), attempt);
                    if (result.crashReportDetected()) {
                        lastFailure = new IllegalStateException("a new crash report was created by the replacement");
                        print(options.language,
                                retry ? "再起動したServerの新しいクラッシュレポートを検出しました。再試行します。"
                                        : "再起動したServerの新しいクラッシュレポートを検出しました。再試行上限に達しました。",
                                retry ? "A new crash report was detected from the replacement server; retrying."
                                        : "A new crash report was detected from the replacement server; the retry limit was reached.");
                    } else {
                        lastFailure = new IllegalStateException("replacement exited with code " + exitCode);
                        print(options.language,
                                retry ? "再起動したServerが異常終了しました（code=" + exitCode + "）。再試行します。"
                                        : "再起動したServerが異常終了しました（code=" + exitCode + "）。再試行上限に達しました。",
                                retry ? "The replacement server exited abnormally (code=" + exitCode + "); retrying."
                                        : "The replacement server exited abnormally (code=" + exitCode + "); the retry limit was reached.");
                    }
                    if (!retry) break;
                    Thread.sleep(1_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception exception) {
                    lastFailure = exception;
                    if (attempt < START_ATTEMPTS) Thread.sleep(1_000L);
                }
            }
            print(options.language,
                    "Serverの再起動または起動維持に3回失敗しました: " + lastFailure,
                    "The server could not be restarted or kept running after three attempts: " + lastFailure);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("AntiCrash Restart Helper: interrupted while waiting for the server");
        } catch (Exception exception) {
            System.err.println("AntiCrash Restart Helper: " + exception);
        }
    }

    /**
     * Makes an OOM a process-level failure for replacement JVMs. Without this flag Java can leave
     * the Server thread dead while worker/watchdog threads keep the process alive, which leaves a
     * detached helper waiting forever and makes the server look permanently stopped.
     */
    static List<String> hardenedCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("server command is empty");
        }
        String executable = command.get(0);
        String executableName = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(executableName.equals("java") || executableName.equals("java.exe"))) {
            return List.copyOf(command);
        }
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

    static boolean shouldRetry(int exitCode, boolean crashReportDetected, int attempt) {
        return attempt < START_ATTEMPTS && (exitCode != 0 || crashReportDetected);
    }

    private static ReplacementResult waitForReplacement(Process replacement, Path workingDirectory,
                                                        Map<Path, Long> crashReportsBeforeStart,
                                                        long startedAtMillis) throws InterruptedException {
        while (true) {
            if (replacement.waitFor(1, TimeUnit.SECONDS)) {
                return new ReplacementResult(replacement.exitValue(),
                        hasNewCrashReport(workingDirectory, crashReportsBeforeStart, startedAtMillis), true);
            }
            if (!hasNewCrashReport(workingDirectory, crashReportsBeforeStart, startedAtMillis)) continue;

            replacement.destroy();
            if (!replacement.waitFor(CRASH_TERMINATION_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                replacement.destroyForcibly();
                if (!replacement.waitFor(CRASH_TERMINATION_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    return new ReplacementResult(-1, true, false);
                }
            }
            return new ReplacementResult(replacement.exitValue(), true, true);
        }
    }

    private static Map<Path, Long> crashReportSnapshot(Path workingDirectory) {
        Path directory = workingDirectory.resolve("crash-reports");
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        Map<Path, Long> result = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "crash-*.txt")) {
            for (Path report : stream) {
                if (Files.isSymbolicLink(report) || !Files.isRegularFile(report, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    result.put(report.toAbsolutePath().normalize(),
                            Files.getLastModifiedTime(report, LinkOption.NOFOLLOW_LINKS).toMillis());
                } catch (IOException ignored) {
                    // A concurrently removed report is not evidence that can safely trigger a kill.
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return Map.copyOf(result);
    }

    private static boolean hasNewCrashReport(Path workingDirectory, Map<Path, Long> before,
                                             long startedAtMillis) {
        Map<Path, Long> after = crashReportSnapshot(workingDirectory);
        for (Map.Entry<Path, Long> entry : after.entrySet()) {
            Long previous = before.get(entry.getKey());
            if ((previous == null && entry.getValue() >= startedAtMillis)
                    || (previous != null && entry.getValue() > previous)) {
                return true;
            }
        }
        return false;
    }

    private record ReplacementResult(int exitCode, boolean crashReportDetected, boolean terminated) {
    }

    private static void print(String language, String japanese, String english) {
        System.out.println("AntiCrash Restart Helper: " + ("en".equalsIgnoreCase(language) ? english : japanese));
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win") ? "NUL" : "/dev/null");
    }

    static final class Options {
        long waitPid;
        Path marker;
        Path workingDirectory;
        String language = "ja";
        final List<String> command = new ArrayList<>();

        static Options parse(String[] arguments) {
            Options options = new Options();
            boolean commandPart = false;
            for (int index = 0; index < arguments.length; index++) {
                String argument = arguments[index];
                if (commandPart) {
                    options.command.add(argument);
                } else if ("--".equals(argument)) {
                    commandPart = true;
                } else if ("--wait-pid".equals(argument)) {
                    options.waitPid = Long.parseLong(requireValue(arguments, ++index, argument));
                } else if ("--marker".equals(argument)) {
                    options.marker = Path.of(requireValue(arguments, ++index, argument)).toAbsolutePath().normalize();
                } else if ("--workdir".equals(argument)) {
                    options.workingDirectory = Path.of(requireValue(arguments, ++index, argument)).toAbsolutePath().normalize();
                } else if ("--language".equals(argument)) {
                    options.language = requireValue(arguments, ++index, argument);
                } else {
                    throw new IllegalArgumentException("Unknown option: " + argument);
                }
            }
            if (options.waitPid <= 0) throw new IllegalArgumentException("--wait-pid is required");
            if (options.marker == null) throw new IllegalArgumentException("--marker is required");
            if (options.workingDirectory == null) throw new IllegalArgumentException("--workdir is required");
            if (Files.isSymbolicLink(options.workingDirectory)
                    || !Files.isDirectory(options.workingDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("--workdir must be a safe directory");
            }
            if (options.command.isEmpty()) throw new IllegalArgumentException("server command is required after --");
            Path expectedMarker = RestartRequest.markerPath(options.workingDirectory);
            if (!options.marker.equals(expectedMarker)) {
                throw new IllegalArgumentException("--marker must be the AntiCrash marker under --workdir");
            }
            return options;
        }

        private static String requireValue(String[] arguments, int index, String option) {
            if (index >= arguments.length) throw new IllegalArgumentException("Missing value for " + option);
            return arguments[index];
        }
    }
}
