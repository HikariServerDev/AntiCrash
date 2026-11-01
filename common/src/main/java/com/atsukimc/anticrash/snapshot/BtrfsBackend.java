package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Optional Btrfs subvolume backend with exact executable paths and bounded commands. */
public final class BtrfsBackend implements SnapshotBackend {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final List<Path> COMMAND_CANDIDATES =
            List.of(
                    Path.of("/usr/bin/btrfs"), Path.of("/usr/sbin/btrfs"),
                    Path.of("/bin/btrfs"), Path.of("/sbin/btrfs"));

    @Override
    public String name() {
        return "BTRFS";
    }

    @Override
    public boolean isSupported(Path liveWorld) {
        if (!Files.isDirectory(liveWorld, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(liveWorld)
                || !System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("linux")) return false;
        Path command = commandPath();
        if (command == null) return false;
        try {
            if (!Files.getFileStore(liveWorld)
                    .type()
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("btrfs")) return false;
            ExternalCommand.Result result =
                    ExternalCommand.run(
                            Duration.ofSeconds(10),
                            command.toString(),
                            "subvolume",
                            "show",
                            liveWorld.toAbsolutePath().normalize().toString());
            return result.exitCode() == 0;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public SnapshotResult snapshot(
            Path liveWorld,
            Path destination,
            Path previousWorld,
            Map<String, SnapshotFile> previousFiles,
            Map<String, SnapshotFile> liveHashes)
            throws IOException {
        if (!isSupported(liveWorld))
            throw new IOException("Live world is not a supported Btrfs subvolume");
        Path command = commandPath();
        if (command == null) throw new IOException("The btrfs executable is unavailable");
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Btrfs destination already exists: " + destination);
        ExternalCommand.Result snapshot =
                ExternalCommand.run(
                        COMMAND_TIMEOUT,
                        command.toString(),
                        "subvolume",
                        "snapshot",
                        liveWorld.toString(),
                        destination.toString());
        if (snapshot.exitCode() != 0)
            throw new IOException("btrfs snapshot failed: " + snapshot.output().trim());

        // A writable snapshot is intentional: excluded Vanilla player files must be removed and
        // CheckpointStore must fsync its payload before READY is committed.
        for (String excluded : List.of("playerdata", "stats", "advancements", "session.lock")) {
            SnapshotManager.deleteTree(destination.resolve(excluded));
        }

        SnapshotResult result = new SnapshotResult(name());
        try (Stream<Path> stream = Files.walk(destination)) {
            for (Path file : stream.sorted().toList()) {
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                PathSafety.rejectSymlink(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = destination.relativize(file).toString().replace('\\', '/');
                if (SnapshotFilters.excluded(relative))
                    throw new IOException("Excluded file remained in Btrfs snapshot: " + relative);
                long size = Files.size(file);
                result.totalBytes += size;
                result.changedFiles++;
                result.files.add(
                        new SnapshotFile(
                                relative,
                                size,
                                Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS)
                                        .toMillis(),
                                Hashing.sha256(file)));
            }
        }
        return result;
    }

    static boolean deleteSubvolumeIfPresent(Path path) throws IOException {
        if (path == null
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) return false;
        try {
            if (!Files.getFileStore(path)
                    .type()
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("btrfs")) return false;
            Object inode = Files.getAttribute(path, "unix:ino", LinkOption.NOFOLLOW_LINKS);
            if (!(inode instanceof Number number) || number.longValue() != 256L) return false;
        } catch (UnsupportedOperationException exception) {
            return false;
        }
        Path command = commandPath();
        if (command == null) return false;
        ExternalCommand.Result show =
                ExternalCommand.run(
                        Duration.ofSeconds(10),
                        command.toString(),
                        "subvolume",
                        "show",
                        path.toAbsolutePath().normalize().toString());
        if (show.exitCode() != 0) return false;
        ExternalCommand.Result delete =
                ExternalCommand.run(
                        COMMAND_TIMEOUT,
                        command.toString(),
                        "subvolume",
                        "delete",
                        path.toAbsolutePath().normalize().toString());
        if (delete.exitCode() != 0)
            throw new IOException("btrfs subvolume delete failed: " + delete.output().trim());
        return true;
    }

    private static Path commandPath() {
        for (Path candidate : COMMAND_CANDIDATES)
            if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    && Files.isExecutable(candidate)) return candidate;
        return null;
    }
}
