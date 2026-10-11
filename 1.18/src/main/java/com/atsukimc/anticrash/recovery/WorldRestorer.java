package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;
import com.atsukimc.anticrash.snapshot.CheckpointStore;
import com.atsukimc.anticrash.snapshot.SnapshotFile;
import com.atsukimc.anticrash.snapshot.SnapshotFilters;
import com.atsukimc.anticrash.snapshot.SnapshotManager;
import com.atsukimc.anticrash.snapshot.TreeCopy;
import com.atsukimc.anticrash.snapshot.VanillaFileSnapshot;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/** Pre-world-load restore. It never writes a world that Minecraft has opened. */
public final class WorldRestorer {
    private static final long MAX_TRANSACTION_BYTES = 64L * 1024L;
    private final CheckpointStore store;
    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;
    private final Path workRoot;
    /** The only live-world path a persisted transaction is allowed to name. */
    private final Path expectedLiveWorld;

    public WorldRestorer(CheckpointStore store, AntiCrashConfig config, AntiCrashLogger logger, Path internalRoot) throws IOException {
        this(store, config, logger, internalRoot, null);
    }

    public WorldRestorer(CheckpointStore store, AntiCrashConfig config, AntiCrashLogger logger,
                         Path internalRoot, Path expectedLiveWorld) throws IOException {
        this.store=store;this.config=config;this.logger=logger;this.workRoot=internalRoot.resolve("restore-work");
        this.expectedLiveWorld = expectedLiveWorld == null ? null
                : expectedLiveWorld.toAbsolutePath().normalize();
        if(Files.exists(workRoot,LinkOption.NOFOLLOW_LINKS)){if(!Files.isDirectory(workRoot,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(workRoot))throw new IOException("Unsafe restore-work directory: "+workRoot);}else Files.createDirectories(workRoot);
        recoverInterruptedTransaction();
        discardAbandonedStages();
    }

    /**
     * Drops staging trees left by a JVM that died mid-restore. A stage is only ever valid inside
     * one restoreCheckpoint call, so anything present at construction time is dead weight.
     */
    private void discardAbandonedStages() {
        try(var stream=Files.list(workRoot)){
            for(Path stage:stream.toList()){
                if(!Files.isDirectory(stage,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(stage))continue;
                SnapshotManager.deleteTree(stage);
                logger.importantKey("restore.abandoned_stage_removed",stage.getFileName());
            }
        }catch(IOException exception){
            logger.importantKey("restore.abandoned_stage_failed",exception.getMessage());
        }
    }

    public CheckpointManifest restoreCheckpoint(Path checkpoint, Path liveWorld) throws IOException {
        validateLiveWorldPath(liveWorld);
        CheckpointManifest manifest=store.verifyForIncrementalReuse(checkpoint);
        if(Files.exists(liveWorld))validateTree(liveWorld);
        Path stage=workRoot.resolve(checkpoint.getFileName().toString()+"-"+java.util.UUID.randomUUID()).normalize();PathSafety.requireChild(workRoot,stage);Files.createDirectory(stage);Files.createDirectory(stage.resolve("world"));
        try{
            Set<String> expected=new HashSet<>();
            for(SnapshotFile snapshotFile:manifest.worldFiles){
                String relative=snapshotFile.path.replace('\\','/');
                if(!expected.add(relative)||SnapshotFilters.excluded(relative))throw new IOException("Invalid world file in manifest: "+relative);
                Path source=PathSafety.requireChild(checkpoint.resolve("world"),checkpoint.resolve("world").resolve(relative));
                Path target=PathSafety.requireChild(stage.resolve("world"),stage.resolve("world").resolve(relative));
                if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing snapshot file: "+relative);
                PathSafety.rejectSymlink(source);
                Files.createDirectories(target.getParent());
                Files.copy(source,target,StandardCopyOption.COPY_ATTRIBUTES);
                // Verification and staging are separate I/O passes. Re-hash the staged bytes so
                // media errors or a checkpoint changed during the copy can never reach the live
                // world merely because the earlier store verification succeeded.
                if(Files.size(target)!=snapshotFile.size
                        || !Hashing.sha256(target).equalsIgnoreCase(snapshotFile.sha256)){
                    throw new IOException("Staged snapshot checksum mismatch: "+relative);
                }
            }
            copyExcludedLiveFiles(liveWorld, stage.resolve("world"));
            VanillaFileSnapshot.restore(checkpoint,stage.resolve("world"),config);
            validateTree(stage.resolve("world"));
            commitStagedWorld(stage, stage.resolve("world"), liveWorld, checkpoint.getFileName().toString());
            logger.importantKey("restore.world_files",checkpoint.getFileName());
        }finally{try{SnapshotManager.deleteTree(stage);}catch(Exception cleanup){logger.verboseKey("restore.stage_cleanup_failed",cleanup.getMessage());}}
        return manifest;
    }

    public void restoreCrashRaw(Path raw,Path liveWorld)throws IOException{
        validateLiveWorldPath(liveWorld);
        Path source=PathSafety.requireChild(raw,raw.resolve("world"));if(!Files.isDirectory(source))throw new IOException("Crash-raw world is missing");if(Files.exists(liveWorld))validateTree(liveWorld);
        Path stage=workRoot.resolve("raw-"+java.util.UUID.randomUUID()).normalize();
        PathSafety.requireChild(workRoot,stage);Files.createDirectory(stage);Files.createDirectory(stage.resolve("world"));
        try {
            TreeCopy.copy(source,stage.resolve("world"));
            validateTree(stage.resolve("world"));
            commitStagedWorld(stage,stage.resolve("world"),liveWorld,raw.getFileName().toString());
            logger.importantKey("restore.crashraw",raw.getFileName());
        } finally {
            try { SnapshotManager.deleteTree(stage); }
            catch (Exception cleanup) { logger.verboseKey("restore.stage_cleanup_failed",cleanup.getMessage()); }
        }
    }

    /** Copies player data and the session lock that are intentionally outside a normal checkpoint. */
    private static void copyExcludedLiveFiles(Path live, Path staged) throws IOException {
        if (!Files.exists(live, LinkOption.NOFOLLOW_LINKS)) return;
        String[] names = {"playerdata", "stats", "advancements", "session.lock"};
        for (String name : names) {
            Path source = live.resolve(name);
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            PathSafety.rejectSymlink(source);
            Path target = PathSafety.requireChild(staged, staged.resolve(name));
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                TreeCopy.copy(source, target);
            } else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    /**
     * Commits a fully prepared world with a recoverable directory exchange.  If the JVM dies
     * between the two renames, the next AntiCrash initialization restores the old directory or
     * keeps the completely installed new directory; it never treats a half-copied tree as a
     * normal world.
     */
    private void commitStagedWorld(Path stage, Path stagedWorld, Path liveWorld, String sourceId)
            throws IOException {
        validateLiveWorldPath(liveWorld);
        Path backup = workRoot.resolve(".live-backup-" + java.util.UUID.randomUUID()).normalize();
        PathSafety.requireChild(workRoot, backup);
        RestoreTransaction transaction = new RestoreTransaction(
                liveWorld.toAbsolutePath().normalize().toString(),
                backup.toAbsolutePath().normalize().toString(),
                stage.toAbsolutePath().normalize().toString(), "PREPARED", sourceId);
        writeTransaction(transaction);
        boolean hadLive = Files.exists(liveWorld, LinkOption.NOFOLLOW_LINKS);
        try {
            if (hadLive) moveDirectoryAtomically(liveWorld, backup);
            writeTransaction(transaction.withStatus("COMMITTING"));
            Files.createDirectories(liveWorld.getParent());
            moveDirectoryAtomically(stagedWorld, liveWorld);
            writeTransaction(transaction.withStatus("COMPLETE"));
            if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) SnapshotManager.deleteTree(backup);
            Files.deleteIfExists(transactionFile());
            AtomicFiles.forceDirectory(liveWorld.getParent());
        } catch (IOException exception) {
            // Leave the marker and backup in place.  Constructor recovery is the only safe place
            // to decide whether the old or the new directory was installed.
            throw exception;
        }
    }

    private void recoverInterruptedTransaction() throws IOException {
        Path file = transactionFile();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_TRANSACTION_BYTES) {
            throw new IOException("Unsafe or oversized restore transaction marker");
        }
        RestoreTransaction transaction;
        try {
            transaction = AtomicFiles.readJson(file, RestoreTransaction.class, MAX_TRANSACTION_BYTES);
        } catch (RuntimeException exception) {
            throw new IOException("Malformed restore transaction marker", exception);
        }
        if (transaction == null || transaction.livePath == null || transaction.backupPath == null
                || transaction.stagePath == null) throw new IOException("Incomplete restore transaction marker");
        Path backup = PathSafety.requireChild(workRoot, Path.of(transaction.backupPath));
        Path stage = PathSafety.requireChild(workRoot, Path.of(transaction.stagePath));
        Path live = Path.of(transaction.livePath).toAbsolutePath().normalize();
        if (!live.isAbsolute()) throw new IOException("Restore transaction live path is not absolute");
        validateLiveWorldPath(live);
        if (Files.exists(live, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Restore transaction live path is not a directory");
        }
        if (Files.exists(live, LinkOption.NOFOLLOW_LINKS)) PathSafety.rejectSymlink(live);
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            PathSafety.rejectSymlink(backup);
            if (!Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Restore transaction backup is not a directory");
            }
        }
        if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) {
            PathSafety.rejectSymlink(stage);
            if (!Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Restore transaction staging root is not a directory");
            }
        }
        Path stagedWorld = stage.resolve("world");
        if (Files.exists(stagedWorld, LinkOption.NOFOLLOW_LINKS)) {
            PathSafety.rejectSymlink(stagedWorld);
        }

        boolean liveExists = Files.exists(live, LinkOption.NOFOLLOW_LINKS);
        boolean backupExists = Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
        boolean stagedWorldExists = Files.isDirectory(stagedWorld, LinkOption.NOFOLLOW_LINKS);
        if (!liveExists && backupExists) {
            Files.createDirectories(live.getParent());
            moveDirectoryAtomically(backup, live);
            liveExists = true;
            backupExists = false;
        } else if (!liveExists && !backupExists && stagedWorldExists) {
            // The destination did not exist before the exchange.  Complete the second rename
            // from the fully staged tree instead of treating an otherwise recoverable restore as
            // a mixed-world failure.
            Files.createDirectories(live.getParent());
            moveDirectoryAtomically(stagedWorld, live);
            liveExists = true;
            stagedWorldExists = false;
        } else if (liveExists && backupExists) {
            // The second rename completed; the new live directory is complete, and the backup is
            // now only an old immutable copy left by a crash before cleanup.
            SnapshotManager.deleteTree(backup);
            backupExists = false;
        }
        if (!liveExists) throw new IOException("Restore transaction has neither live nor backup world");
        if (stagedWorldExists) SnapshotManager.deleteTree(stage);
        if (!backupExists && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) SnapshotManager.deleteTree(backup);
        Files.deleteIfExists(file);
    }

    private Path transactionFile() {
        return workRoot.resolve("restore-transaction.json");
    }

    private void validateLiveWorldPath(Path liveWorld) throws IOException {
        Path normalized = liveWorld.toAbsolutePath().normalize();
        if (expectedLiveWorld != null && !expectedLiveWorld.equals(normalized)) {
            throw new IOException("Restore transaction names an unexpected live World: " + normalized);
        }
        if (normalized.equals(workRoot) || normalized.startsWith(workRoot)) {
            throw new IOException("Live World may not be inside restore-work: " + normalized);
        }
    }

    private void writeTransaction(RestoreTransaction transaction) throws IOException {
        AtomicFiles.writeJson(transactionFile(), transaction);
        AtomicFiles.force(transactionFile());
        AtomicFiles.forceDirectory(workRoot);
    }

    private static void moveDirectoryAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic world directory exchange is unavailable", exception);
        }
    }

    private record RestoreTransaction(String livePath, String backupPath, String stagePath,
                                      String status, String sourceId) {
        private RestoreTransaction withStatus(String nextStatus) {
            return new RestoreTransaction(livePath, backupPath, stagePath, nextStatus, sourceId);
        }
    }

    private static void validateTree(Path root)throws IOException{PathSafety.rejectSymlink(root);try(var stream=Files.walk(root)){for(Path path:stream.toList())PathSafety.rejectSymlink(path);}}
}
