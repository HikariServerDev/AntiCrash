package com.atsukimc.anticrash.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Optional Linux/coreutils fast path. It remains safe because the world stays frozen during I/O.
 */
public final class ReflinkBackend extends JavaIncrementalBackend {
    public ReflinkBackend(java.util.function.IntSupplier configuredThreads) {
        super(configuredThreads);
    }

    private static final Path CP = Path.of("/bin/cp");

    @Override
    public String name() {
        return "REFLINK";
    }

    @Override
    public boolean isSupported(Path liveWorld) {
        if (!super.isSupported(liveWorld)
                || !System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("linux")
                || !Files.isExecutable(CP)) return false;
        try {
            return supportsFileStoreType(Files.getFileStore(liveWorld).type());
        } catch (IOException ignored) {
            return false;
        }
    }

    static boolean supportsFileStoreType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("btrfs")
                || normalized.contains("xfs")
                || normalized.contains("bcachefs");
    }

    @Override
    protected void copyFresh(Path source, Path target) throws IOException {
        ExternalCommand.Result result =
                ExternalCommand.run(
                        Duration.ofSeconds(30),
                        CP.toString(),
                        "--reflink=always",
                        "--preserve=mode,timestamps",
                        source.toString(),
                        target.toString());
        if (result.exitCode() != 0)
            throw new IOException("cp --reflink failed: " + result.output().trim());
    }
}
