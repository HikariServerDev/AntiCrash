package com.atsukimc.anticrash.restart;

import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Arms one controlled replacement monitor for a restore that requires pre-world-load work. */
public final class RestartCoordinator {
    private final Path gameDirectory;
    private final Path marker;
    private final AntiCrashLogger logger;

    public RestartCoordinator(Path gameDirectory, AntiCrashLogger logger) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.marker = RestartRequest.markerPath(this.gameDirectory);
        this.logger = logger;
    }

    /**
     * @param spawnHelper whether to launch the detached restart helper when no Supervisor is
     *                    handling the handoff. The helper rebuilds the launch command from
     *                    {@link ProcessHandle.Info}, which cannot reproduce a start script's own
     *                    environment, so an installation that starts the server from a wrapper
     *                    turns it off and restarts by hand instead.
     */
    public void arm(String language, boolean spawnHelper) throws IOException {
        if (isSupervisorHandshake(marker, System.getenv(RestartRequest.SUPERVISED_ENV),
                System.getenv(RestartRequest.MARKER_ENV))) {
            RestartRequest.create(marker);
            logger.importantKey("restart.supervisor_armed");
            return;
        }
        if (!spawnHelper) {
            // Nothing would consume a marker, so none is written and no process is started. The
            // pending restore itself lives in recovery-state.json and is applied by the next
            // startup whenever that happens.
            logger.normalKey("restart.helper_disabled");
            return;
        }

        RestartRequest.create(marker);
        try {
            List<String> helperCommand = new ArrayList<>();
            helperCommand.add(javaExecutable().toString());
            helperCommand.add("-cp");
            helperCommand.add(helperClasspath().toString());
            helperCommand.add(RestartHelper.class.getName());
            helperCommand.add("--wait-pid");
            helperCommand.add(Long.toString(ProcessHandle.current().pid()));
            helperCommand.add("--marker");
            helperCommand.add(marker.toString());
            helperCommand.add("--workdir");
            helperCommand.add(gameDirectory.toString());
            helperCommand.add("--language");
            helperCommand.add(MessageCatalog.normalize(language));
            helperCommand.add("--");
            helperCommand.addAll(currentProcessCommand(ProcessHandle.current().info()));

            new ProcessBuilder(helperCommand)
                    .directory(gameDirectory.toFile())
                    .inheritIO()
                    .start();
            logger.importantKey("restart.helper_armed");
        } catch (Exception exception) {
            RestartRequest.cancel(marker);
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Could not start the controlled restart helper", exception);
        }
    }

    public void cancel() {
        RestartRequest.cancel(marker);
    }

    /** Removes a stale handoff marker only after the replacement applied its pending restore. */
    public void completeHandoff() throws IOException {
        RestartRequest.claim(marker);
    }

    static List<String> currentProcessCommand(ProcessHandle.Info info) throws IOException {
        String executable = info.command().orElseThrow(
                () -> new IOException("The current Java executable is not available"));
        String[] arguments = info.arguments().orElseThrow(
                () -> new IOException("The current Java process arguments are not available"));
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add(executable);
        command.addAll(Arrays.asList(arguments));
        return List.copyOf(command);
    }

    /** A stale flag alone must never suppress the controlled helper and strand a pending restore. */
    static boolean isSupervisorHandshake(Path expectedMarker, String supervised, String suppliedMarker) {
        if (!"1".equals(supervised) || suppliedMarker == null || suppliedMarker.isBlank()) return false;
        try {
            return Path.of(suppliedMarker).toAbsolutePath().normalize()
                    .equals(expectedMarker.toAbsolutePath().normalize());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Path javaExecutable() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path executable = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable)) throw new IOException("Java executable does not exist: " + executable);
        return executable;
    }

    private static Path helperClasspath() throws IOException {
        String helperClass = RestartHelper.class.getName().replace('.', '/') + ".class";
        var container = FabricLoader.getInstance().getModContainer(AntiCrashMod.MOD_ID)
                .orElseThrow(() -> new IOException("AntiCrash mod container is unavailable"));
        for (Path path : container.getOrigin().getPaths()) {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) return normalized;
            if (Files.isDirectory(normalized) && Files.isRegularFile(normalized.resolve(helperClass))) return normalized;
        }
        throw new IOException("Could not locate the AntiCrash jar for the restart helper");
    }
}
