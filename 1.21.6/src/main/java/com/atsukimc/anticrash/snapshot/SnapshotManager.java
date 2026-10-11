package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.nio.file.LinkOption;

public final class SnapshotManager {
    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;
    private final SnapshotBackend java;
    private final SnapshotBackend portable;
    private final SnapshotBackend reflink;
    private final SnapshotBackend btrfs = new BtrfsBackend();
    private final SnapshotBackend criu;
    private volatile boolean reflinkUnavailableForSession;

    public SnapshotManager(AntiCrashConfig config, AntiCrashLogger logger) {
        this.config = config;
        this.logger = logger;
        this.java = new JavaIncrementalBackend(() -> this.config.snapshot.ioThreads);
        this.portable = new PortableGenerationBackend(() ->
                this.config.snapshot.ioThreads > 0 ? this.config.snapshot.ioThreads : 1);
        this.reflink = new ReflinkBackend(() -> this.config.snapshot.ioThreads);
        this.criu = new CriuBackend(() -> this.config.snapshot.ioThreads);
    }

    public SnapshotResult snapshot(Path liveWorld, Path destination, Path previousWorld,
                                   Map<String, SnapshotFile> previousFiles,
                                   Map<String, SnapshotFile> liveHashes) throws IOException {
        String requested = config.snapshot.backend.toUpperCase(Locale.ROOT);
        SnapshotBackend selected = switch (requested) {
            case "BTRFS" -> btrfs;
            case "REFLINK" -> reflink;
            case "CRIU_EXPERIMENTAL" -> criu;
            case "PORTABLE_GENERATION", "GENERATION" -> portable;
            default -> java;
        };
        // AUTO already probes each candidate. Support probing can spawn an external process
        // (btrfs subvolume show), so never repeat it for a backend it just accepted.
        boolean supportConfirmed = false;
        if (requested.equals("AUTO")) {
            // AUTO's correctness path is filesystem-portable generation.  Reflink/Btrfs are
            // optional accelerators, but their ordinary SnapshotBackend API cannot detach the
            // live tree before the server resumes; selecting them here would reintroduce an
            // epoch race. Explicit BTRFS/REFLINK remains available as LEGACY_SAFE.
            selected = portable;
            supportConfirmed = true;
        }
        if (selected == reflink && reflinkUnavailableForSession) {
            logger.importantKey("snapshot.backend_unavailable", requested);
            selected = java;
        }
        if (!supportConfirmed && !selected.isSupported(liveWorld)) {
            if (selected != java) logger.importantKey("snapshot.backend_unavailable", requested);
            selected = java;
        }
        try {
            return selected.snapshot(liveWorld, destination, previousWorld, previousFiles, liveHashes);
        } catch (IOException firstFailure) {
            if (selected == java) throw firstFailure;
            if (selected == reflink) reflinkUnavailableForSession = true;
            logger.importantKey("snapshot.backend_failed", selected.name(), firstFailure.getMessage());
            deleteTree(destination);
            return java.snapshot(liveWorld, destination, previousWorld, previousFiles, liveHashes);
        }
    }

    public static Map<String, SnapshotFile> index(CheckpointManifest manifest) {
        Map<String, SnapshotFile> result = new LinkedHashMap<>();
        if (manifest != null && manifest.worldFiles != null) {
            for (SnapshotFile file : manifest.worldFiles) result.put(file.path, file);
        }
        return result;
    }

    public static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(root)) { Files.delete(root); return; }
        if (BtrfsBackend.deleteSubvolumeIfPresent(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) Files.deleteIfExists(path);
                else if (!BtrfsBackend.deleteSubvolumeIfPresent(path)) Files.deleteIfExists(path);
            }
        }
    }
}
