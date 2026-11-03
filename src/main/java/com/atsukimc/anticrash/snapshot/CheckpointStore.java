package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.atsukimc.anticrash.util.Hashing;
import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CheckpointStore {
    private static final Pattern ID_PATTERN = Pattern.compile("cp-(\\d{6,18})");
    private final Path directory;
    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;

    public enum CandidateState {
        DISCOVERED,
        VERIFYING,
        VALID,
        INVALID,
        QUARANTINED
    }

    private final Map<String, CandidateState> candidateStates =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> pins = new HashMap<>();
    private final Map<String, String> quarantined = new java.util.concurrent.ConcurrentHashMap<>();

    public CheckpointStore(Path directory, AntiCrashConfig config, AntiCrashLogger logger)
            throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.config = config;
        this.logger = logger;
        if (Files.exists(this.directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(this.directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(this.directory)) {
                throw new IOException("Unsafe checkpoint directory: " + this.directory);
            }
        } else {
            Files.createDirectories(this.directory);
        }
        discardAbandonedTemporaries();
        Path catalog = this.directory.resolve("quarantine.json");
        if (Files.exists(catalog, LinkOption.NOFOLLOW_LINKS)) {
            try {
                QuarantineData data =
                        AtomicFiles.readJson(catalog, QuarantineData.class, 1024L * 1024L);
                if (data != null && data.reasons != null) quarantined.putAll(data.reasons);
                quarantined
                        .keySet()
                        .forEach(id -> candidateStates.put(id, CandidateState.QUARANTINED));
            } catch (IOException | RuntimeException invalid) {
                throw new IOException("Unsafe quarantine catalog", invalid);
            }
        }
    }

    private static final class QuarantineData {
        Map<String, String> reasons = new HashMap<>();
    }

    public synchronized void pin(String id) {
        if (id != null) pins.merge(id, 1, Integer::sum);
    }

    public synchronized void unpin(String id) {
        Integer count = pins.get(id);
        if (count != null) {
            if (count <= 1) pins.remove(id);
            else pins.put(id, count - 1);
        }
    }

    public CandidateState candidateState(String id) {
        return candidateStates.getOrDefault(id, CandidateState.DISCOVERED);
    }

    public boolean isQuarantined(String id) {
        return quarantined.containsKey(id);
    }

    public synchronized void quarantine(String id, String reason) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return;
        quarantined.put(
                id,
                reason == null ? "invalid" : reason.substring(0, Math.min(reason.length(), 512)));
        candidateStates.put(id, CandidateState.QUARANTINED);
        QuarantineData data = new QuarantineData();
        data.reasons.putAll(quarantined);
        try {
            AtomicFiles.writeJson(directory.resolve("quarantine.json"), data);
        } catch (IOException failure) {
            logger.importantKey("checkpoint.previous_lookup_failed", failure.getMessage());
        }
    }

    /**
     * Removes {@code cp-XXXXXX.tmp} directories left by a JVM that died mid-capture.
     *
     * <p>They are never listed as complete checkpoints, so retention would keep them forever and
     * every abnormal exit would leak another partial world copy. The AntiCrash process lock is
     * already held here, so no other instance can own an in-flight temporary directory.
     */
    private void discardAbandonedTemporaries() {
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".tmp")
                        || !ID_PATTERN.matcher(name.substring(0, name.length() - 4)).matches()
                        || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(path)) {
                    continue;
                }
                SnapshotManager.deleteTree(path);
                logger.importantKey("checkpoint.abandoned_temp_removed", name);
            }
        } catch (IOException exception) {
            // A cleanup failure must never prevent the server from starting or checkpointing.
            logger.importantKey("checkpoint.abandoned_temp_failed", exception.getMessage());
        }
    }

    public synchronized Path createTempDirectory() throws IOException {
        long next = nextSequence();
        Path temporary = directory.resolve(String.format("cp-%06d.tmp", next));
        Files.createDirectory(temporary);
        return temporary;
    }

    public synchronized Path commit(Path temporary, CheckpointManifest manifest)
            throws IOException {
        PathSafety.requireChild(directory, temporary);
        if (!temporary.getFileName().toString().endsWith(".tmp"))
            throw new IOException("Not a checkpoint temp directory");
        if (!Files.isDirectory(temporary, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(temporary))
            throw new IOException("Unsafe checkpoint temp directory");
        Path manifestFile = temporary.resolve("manifest.json");
        // SnapshotResult already hashed every world file. Reuse those immutable hashes for
        // commit metadata; verification still performs a fresh hash of every file. The payload
        // lines are collected in a single tree walk and the manifest digest is appended to that
        // same list, so the manifest is written exactly once and the tree is scanned once.
        List<String> checksums = new ArrayList<>(checksumLines(temporary, manifest));
        manifest.checksum = Hashing.sha256JoinedLines(checksums);
        AtomicFiles.writeJson(manifestFile, manifest);
        checksums.add(Hashing.sha256(manifestFile) + "  manifest.json");
        checksums.sort(String::compareTo);

        Path checksumFile = temporary.resolve("checksums.sha256");
        AtomicFiles.writeLines(checksumFile, checksums);
        Path ready = temporary.resolve("READY");
        AtomicFiles.writeText(ready, Hashing.sha256(checksumFile) + System.lineSeparator());
        forceFiles(temporary, manifest);
        if (!"FAST".equals(durabilityMode())) {
            AtomicFiles.forceDirectory(temporary);
        }

        String name =
                temporary
                        .getFileName()
                        .toString()
                        .substring(0, temporary.getFileName().toString().length() - 4);
        Path committed = directory.resolve(name);
        AtomicFiles.moveAtomically(temporary, committed);
        if (!"FAST".equals(durabilityMode())) {
            AtomicFiles.forceDirectory(directory);
        }
        try {
            retain(committed, manifest);
        } catch (IOException retentionFailure) {
            // The checkpoint is already atomically committed. A cleanup failure must not turn
            // a usable checkpoint into a reported capture failure or delete its temp path.
            logger.importantKey(
                    "checkpoint.retention_failed",
                    committed.getFileName(),
                    retentionFailure.getMessage());
        }
        return committed;
    }

    public List<Path> listComplete() throws IOException {
        if (!Files.exists(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> ID_PATTERN.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingLong((Path path) -> sequence(path)).reversed())
                    .toList();
        }
    }

    /**
     * Discovers one candidate without authenticating or traversing its payload.
     *
     * <p>This is intentionally smaller than {@link #verify(Path)}. It only checks the directory,
     * READY marker and bounded manifest metadata needed to show an ASK prompt. The returned
     * candidate is explicitly unverified; callers must run the full verifier before replacing a
     * live world.
     */
    public CheckpointCandidate discoverLatestCandidate(String activeCheckpointId)
            throws IOException {
        List<Path> complete = listComplete();
        if (complete.isEmpty()) return null;
        Path preferred = null;
        if (activeCheckpointId != null) {
            for (Path path : complete) {
                if (activeCheckpointId.equals(path.getFileName().toString())) {
                    preferred = path;
                    break;
                }
            }
        }
        if (preferred != null) {
            CheckpointCandidate candidate = readCandidate(preferred);
            if (candidate != null) return candidate;
        }
        for (Path path : complete) {
            CheckpointCandidate candidate = readCandidate(path);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private CheckpointCandidate readCandidate(Path checkpoint) throws IOException {
        if (isQuarantined(checkpoint.getFileName().toString())) return null;
        Path normalized = PathSafety.requireChild(directory, checkpoint);
        Path ready = normalized.resolve("READY");
        Path manifestFile = normalized.resolve("manifest.json");
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(ready, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(ready)
                || !Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(manifestFile)) return null;
        // Real manifests include thousands of worldFiles. Read the same 16 MiB format bound as
        // verification, but skip payload arrays: startup only needs the identity and timestamp.
        if (Files.size(ready) > 1024L) return null;
        CheckpointManifest manifest = new CheckpointManifest();
        try (var json = AtomicFiles.boundedJsonReader(manifestFile, 16L * 1024L * 1024L)) {
            json.beginObject();
            while (json.hasNext()) {
                switch (json.nextName()) {
                    case "checkpointId" -> manifest.checkpointId = json.nextString();
                    case "utcTimestamp" -> manifest.utcTimestamp = json.nextString();
                    default -> json.skipValue();
                }
            }
            json.endObject();
        } catch (RuntimeException | IOException exception) {
            return null;
        }
        if (manifest == null
                || manifest.checkpointId == null
                || !manifest.checkpointId.equals(normalized.getFileName().toString())) return null;
        return new CheckpointCandidate(normalized, manifest);
    }

    public List<Path> listValid() throws IOException {
        List<Path> valid = new ArrayList<>();
        for (Path checkpoint : listComplete()) {
            try {
                verify(checkpoint);
                valid.add(checkpoint);
            } catch (IOException exception) {
                rethrowIfInterrupted(exception);
                logger.importantKey(
                        "checkpoint.invalid_ignored",
                        checkpoint.getFileName(),
                        exception.getMessage());
            }
        }
        return valid;
    }

    /** Returns the newest valid checkpoint without hashing every older retained checkpoint. */
    public Path newestValid() throws IOException {
        for (Path checkpoint : listComplete()) {
            try {
                verify(checkpoint);
                return checkpoint;
            } catch (IOException exception) {
                rethrowIfInterrupted(exception);
                logger.importantKey(
                        "checkpoint.invalid_ignored",
                        checkpoint.getFileName(),
                        exception.getMessage());
            }
        }
        return null;
    }

    /**
     * Returns only checkpoints belonging to the active recovery branch.
     *
     * <p>Version 2 manifests carry an exact parent ID. A missing or unreadable parent stops the
     * traversal rather than selecting a numerically newer checkpoint from an abandoned branch.
     * Version 1 checkpoints predate lineage metadata, so their only compatible fallback is an older
     * numeric generation; a higher ID is never selected for an older active checkpoint.
     */
    public List<Path> timelineCandidates(String activeCheckpointId) throws IOException {
        if (activeCheckpointId == null) return listComplete();
        if (!ID_PATTERN.matcher(activeCheckpointId).matches()) return List.of();

        List<Path> all = listComplete();
        Map<String, Path> byId = new HashMap<>();
        for (Path path : all) byId.put(path.getFileName().toString(), path);
        Path active = byId.get(activeCheckpointId);
        if (active == null) return List.of();

        List<Path> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentId = activeCheckpointId;
        while (currentId != null && visited.add(currentId)) {
            Path current = byId.get(currentId);
            if (current == null) break;
            result.add(current);

            CheckpointManifest manifest;
            try {
                manifest = readAuthenticatedLineageManifest(current);
            } catch (IOException | RuntimeException exception) {
                // Without authenticated lineage, choosing another branch is less safe than
                // stopping. The current path remains in the result so normal verification can
                // report the concrete corruption.
                break;
            }
            if (manifest == null || !currentId.equals(manifest.checkpointId)) break;
            if (manifest.formatVersion >= 2) {
                String parent =
                        manifest.timelineParentCheckpointId != null
                                ? manifest.timelineParentCheckpointId
                                : manifest.parentCheckpointId;
                if (parent == null) break;
                if (!ID_PATTERN.matcher(parent).matches()
                        || sequence(parent) >= sequence(currentId)) break;
                currentId = parent;
                continue;
            }
            if (manifest.formatVersion != 1) break;

            long currentSequence = sequence(currentId);
            for (Path candidate : all) {
                String candidateId = candidate.getFileName().toString();
                if (sequence(candidateId) < currentSequence && !visited.contains(candidateId)) {
                    result.add(candidate);
                }
            }
            break;
        }
        return List.copyOf(result);
    }

    public CheckpointManifest readManifest(Path checkpoint) throws IOException {
        Path normalized = PathSafety.requireChild(directory, checkpoint);
        Path manifest = normalized.resolve("manifest.json");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing checkpoint manifest");
        return AtomicFiles.readJson(manifest, CheckpointManifest.class, 16L * 1024L * 1024L);
    }

    /** Authenticates only the small control metadata needed to follow a recovery branch. */
    private CheckpointManifest readAuthenticatedLineageManifest(Path checkpoint)
            throws IOException {
        Path normalized = PathSafety.requireChild(directory, checkpoint);
        Path readyFile = normalized.resolve("READY");
        Path checksumFile = normalized.resolve("checksums.sha256");
        Path manifestFile = normalized.resolve("manifest.json");
        if (!Files.isRegularFile(readyFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(checksumFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Checkpoint lineage metadata is incomplete");
        }
        if (Files.size(readyFile) > 1024L
                || Files.size(checksumFile) > 64L * 1024L * 1024L
                || Files.size(manifestFile) > 16L * 1024L * 1024L) {
            throw new IOException("Checkpoint lineage metadata is too large");
        }
        String ready = AtomicFiles.readText(readyFile, 1024L).trim();
        if (!ready.equals(Hashing.sha256(checksumFile))) {
            throw new IOException("Checkpoint lineage READY checksum mismatch");
        }
        String manifestDigest = null;
        try (var reader = AtomicFiles.boundedTextReader(checksumFile, 64L * 1024L * 1024L)) {
            String line;
            while ((line = readChecksumLine(reader)) != null) {
                if (line.isBlank()) continue;
                int separator = line.indexOf("  ");
                if (separator <= 0) throw new IOException("Malformed checkpoint lineage checksum");
                String digest =
                        line.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
                String relative = line.substring(separator + 2).trim().replace('\\', '/');
                if (!digest.matches("[0-9a-f]{64}"))
                    throw new IOException("Malformed checkpoint lineage digest");
                if (relative.equals("manifest.json")) {
                    if (manifestDigest != null)
                        throw new IOException("Duplicate checkpoint manifest checksum");
                    manifestDigest = digest;
                }
            }
        }
        if (manifestDigest == null || !manifestDigest.equals(Hashing.sha256(manifestFile))) {
            throw new IOException("Checkpoint lineage manifest checksum mismatch");
        }
        return AtomicFiles.readJson(manifestFile, CheckpointManifest.class, 16L * 1024L * 1024L);
    }

    public void verify(Path checkpoint) throws IOException {
        verifyInternal(checkpoint, false);
    }

    /**
     * Verifies a checkpoint and hydrates destination metadata in the returned manifest for the
     * incremental snapshot fast path. Content is hashed before metadata is accepted, and files
     * whose metadata changes during verification remain on the conservative hash/copy path.
     */
    public CheckpointManifest verifyForIncrementalReuse(Path checkpoint) throws IOException {
        String id = checkpoint.getFileName().toString();
        // serialize same-process authentication and protect the candidate from retention.
        synchronized (verificationLock) {
            if (isQuarantined(id)) throw new IOException("Checkpoint quarantined: " + id);
            Authenticated cached = authenticated.get(id);
            if (cached != null && cached.unchanged(checkpoint)) return cached.manifest;
            authenticated.remove(id);
            candidateStates.put(id, CandidateState.VERIFYING);
            pin(id);
            try {
                CheckpointManifest result = verifyInternal(checkpoint, true);
                authenticated.put(id, Authenticated.capture(checkpoint, result));
                while (authenticated.size() > 2)
                    authenticated.remove(authenticated.keySet().iterator().next());
                candidateStates.put(id, CandidateState.VALID);
                return result;
            } catch (IOException failure) {
                if (failure instanceof InterruptedIOException) {
                    candidateStates.put(id, CandidateState.DISCOVERED);
                    throw failure;
                }
                candidateStates.put(id, CandidateState.INVALID);
                quarantine(id, failure.getMessage());
                throw failure;
            } finally {
                unpin(id);
            }
        }
    }

    private final Object verificationLock = new Object();
    private final Map<String, Authenticated> authenticated = new java.util.LinkedHashMap<>();

    private record Authenticated(CheckpointManifest manifest, Map<String, FileFacts> revisions) {
        static Authenticated capture(Path root, CheckpointManifest manifest) throws IOException {
            Map<String, FileFacts> facts = new HashMap<>();
            Map<String, SnapshotFile> world = SnapshotManager.index(manifest);
            Map<String, String> payload = new HashMap<>();
            try (var reader =
                    AtomicFiles.boundedTextReader(
                            root.resolve("checksums.sha256"), 64L * 1024 * 1024)) {
                String line;
                while ((line = readChecksumLine(reader)) != null) {
                    int separator = line.indexOf("  ");
                    if (separator > 0)
                        payload.put(
                                line.substring(separator + 2).replace('\\', '/'),
                                line.substring(0, separator));
                }
            }
            String listDigest = Hashing.sha256(root.resolve("checksums.sha256"));
            if (!AtomicFiles.readText(root.resolve("READY"), 1024).trim().equals(listDigest))
                throw new IOException("READY changed before verification publication");
            try (var paths = Files.walk(root)) {
                var iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        FileFacts revision = FileFacts.read(path);
                        SnapshotFile file =
                                relative.startsWith("world/")
                                        ? world.get(relative.substring(6))
                                        : null;
                        if (file != null) {
                            if (!JavaIncrementalBackend.previousFileMatches(path, revision, file))
                                throw new IOException(
                                        "World file changed before verification publication: "
                                                + relative);
                        } else if (!relative.equals("READY")
                                && !relative.equals("checksums.sha256")) {
                            String expected = payload.get(relative);
                            if (expected == null || !expected.equals(Hashing.sha256(path)))
                                throw new IOException(
                                        "Payload changed before verification publication: "
                                                + relative);
                        }
                        if (!FileFacts.read(path).sameRevision(revision))
                            throw new IOException("Verification publication raced: " + relative);
                        facts.put(root.relativize(path).toString(), revision);
                    }
                }
            }
            return new Authenticated(manifest, Map.copyOf(facts));
        }

        boolean unchanged(Path root) throws IOException {
            // No durable trust cache: restarting always authenticates content again. Within one
            // process only an identical complete revision may reuse the authenticated result.
            int count = 0;
            try (var paths = Files.walk(root)) {
                var iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    FileFacts facts = FileFacts.read(path);
                    if (facts.symbolicLink()) return false;
                    if (facts.directory()) continue;
                    FileFacts old = revisions.get(root.relativize(path).toString());
                    if (old == null || old.changeTime() == 0 || !facts.sameRevision(old))
                        return false;
                    count++;
                }
            }
            return count == revisions.size();
        }
    }

    private CheckpointManifest verifyInternal(Path checkpoint, boolean hydrateDestinationMetadata)
            throws IOException {
        Path normalized = PathSafety.requireChild(directory, checkpoint);
        logger.normalKey("checkpoint.validation_started", normalized.getFileName());
        PathSafety.rejectSymlink(normalized);
        Path readyFile = normalized.resolve("READY");
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(readyFile, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Checkpoint is incomplete");
        Path checksums = normalized.resolve("checksums.sha256");
        if (!Files.isRegularFile(checksums, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing checksum list");
        if (Files.size(checksums) > 64L * 1024L * 1024L)
            throw new IOException("Checksum list is too large");
        String ready = AtomicFiles.readText(readyFile, 1024L).trim();
        if (config.snapshot.verifyChecksums && !ready.equals(Hashing.sha256(checksums)))
            throw new IOException("READY marker checksum mismatch");
        Set<String> listed = new HashSet<>();
        Map<String, String> verifiedDigests = new HashMap<>();
        List<String> verifiedPayloadLines = new ArrayList<>();
        Map<String, DestinationMetadata> destinationMetadata = new HashMap<>();
        try (var reader = AtomicFiles.boundedTextReader(checksums, 64L * 1024L * 1024L)) {
            String line;
            while ((line = readChecksumLine(reader)) != null) {
                if (line.isBlank()) continue;
                int separator = line.indexOf("  ");
                if (separator <= 0) throw new IOException("Malformed checksum line");
                String digest = line.substring(0, separator).trim();
                if (!digest.matches("[0-9a-fA-F]{64}"))
                    throw new IOException("Malformed checksum digest");
                String relative = line.substring(separator + 2).trim().replace('\\', '/');
                Path file = safeRelative(normalized, relative);
                // safeRelative above already rejects READY and checksums.sha256 themselves.
                if (!listed.add(relative))
                    throw new IOException("Duplicate checksum path: " + relative);
                boolean collectDestinationMetadata =
                        hydrateDestinationMetadata
                                && config.snapshot.verifyChecksums
                                && relative.startsWith("world/");
                BasicFileAttributes before =
                        collectDestinationMetadata
                                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                ? Files.readAttributes(
                                        file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                                : null;
                long beforeChangeTime = before == null ? 0L : changeTime(file);
                long beforeLinkCount = before == null ? 0L : linkCount(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || (config.snapshot.verifyChecksums
                                && !digest.equals(Hashing.sha256(file)))) {
                    throw new IOException("Checksum mismatch: " + relative);
                }
                if (before != null) {
                    BasicFileAttributes after =
                            Files.readAttributes(
                                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (sameDestinationMetadata(
                            before, beforeChangeTime, beforeLinkCount, after, file)) {
                        destinationMetadata.put(
                                relative.substring("world/".length()),
                                new DestinationMetadata(
                                        after.lastModifiedTime().toMillis(),
                                        changeTime(file),
                                        linkCount(file)));
                    }
                }
                verifiedDigests.put(relative, digest.toLowerCase(java.util.Locale.ROOT));
                if (!relative.equals("manifest.json"))
                    verifiedPayloadLines.add(digest + "  " + relative);
            }
        }
        try (var stream = Files.walk(normalized)) {
            for (Path file : stream.toList()) {
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                PathSafety.rejectSymlink(file);
                String relative = normalized.relativize(file).toString().replace('\\', '/');
                if (!relative.equals("READY")
                        && !relative.equals("checksums.sha256")
                        && !listed.contains(relative))
                    throw new IOException("Unlisted checkpoint file: " + relative);
            }
        }
        CheckpointManifest manifest = readManifest(normalized);
        if (manifest == null
                || (manifest.formatVersion != 1
                        && manifest.formatVersion != 2
                        && manifest.formatVersion != 3
                        && manifest.formatVersion != 4)) {
            throw new IOException("Unsupported checkpoint manifest");
        }
        if (!normalized.getFileName().toString().equals(manifest.checkpointId))
            throw new IOException("Checkpoint manifest ID mismatch");
        validateManifest(normalized, manifest, verifiedDigests);
        // Every payload file was freshly hashed above. Reconstruct the manifest payload digest
        // from those already-verified checksum entries instead of reading the entire world a
        // second time.
        verifiedPayloadLines.sort(String::compareTo);
        if (config.snapshot.verifyChecksums
                && !Hashing.sha256JoinedLines(verifiedPayloadLines).equals(manifest.checksum)) {
            throw new IOException("Manifest payload checksum mismatch");
        }
        if (hydrateDestinationMetadata && !destinationMetadata.isEmpty()) {
            for (SnapshotFile snapshot : manifest.worldFiles) {
                DestinationMetadata metadata =
                        destinationMetadata.get(snapshot.path.replace('\\', '/'));
                if (metadata == null) continue;
                snapshot.destinationMetadataRecorded = true;
                snapshot.storedModifiedTime = metadata.modifiedTime();
                snapshot.storedChangeTime = metadata.changeTime();
                snapshot.storedLinkCount = metadata.linkCount();
            }
        }
        logger.normalKey("checkpoint.validation_passed", normalized.getFileName());
        return manifest;
    }

    /**
     * Re-authenticates the small runtime payloads after Minecraft has loaded the restored world.
     * The same process has already performed {@link #verify(Path)} before copying world data, so
     * hashing the complete world a second time only delays recovery and duplicates log output.
     */
    public CheckpointManifest verifyRecoveryPayloads(Path checkpoint) throws IOException {
        Path normalized = PathSafety.requireChild(directory, checkpoint);
        PathSafety.rejectSymlink(normalized);
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Checkpoint is incomplete");
        }
        Path readyFile = normalized.resolve("READY");
        Path checksumFile = normalized.resolve("checksums.sha256");
        if (!Files.isRegularFile(readyFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(checksumFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(readyFile)
                || Files.isSymbolicLink(checksumFile)) {
            throw new IOException("Checkpoint recovery metadata is incomplete");
        }
        if (Files.size(readyFile) > 1024L || Files.size(checksumFile) > 64L * 1024L * 1024L) {
            throw new IOException("Checkpoint recovery metadata is too large");
        }
        String ready = AtomicFiles.readText(readyFile, 1024L).trim();
        if (!ready.equals(Hashing.sha256(checksumFile))) {
            throw new IOException("Checkpoint recovery READY checksum mismatch");
        }

        Map<String, String> digests = new HashMap<>();
        List<String> payloadLines = new ArrayList<>();
        try (var reader = AtomicFiles.boundedTextReader(checksumFile, 64L * 1024L * 1024L)) {
            String line;
            while ((line = readChecksumLine(reader)) != null) {
                if (line.isBlank()) continue;
                int separator = line.indexOf("  ");
                if (separator <= 0) throw new IOException("Malformed checkpoint recovery checksum");
                String digest =
                        line.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
                String relative = line.substring(separator + 2).trim().replace('\\', '/');
                if (!digest.matches("[0-9a-f]{64}")
                        || relative.equals("READY")
                        || relative.equals("checksums.sha256")
                        || digests.putIfAbsent(relative, digest) != null) {
                    throw new IOException("Invalid checkpoint recovery checksum entry");
                }
                safeRelative(normalized, relative);
                if (!relative.equals("manifest.json")) payloadLines.add(digest + "  " + relative);
            }
        }

        for (String required :
                List.of(
                        "manifest.json",
                        "runtime-capsule.json",
                        "players.json",
                        "fakeplayers.json")) {
            String expected = digests.get(required);
            Path file = normalized.resolve(required);
            if (expected == null
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)
                    || !expected.equals(Hashing.sha256(file))) {
                throw new IOException("Checkpoint recovery payload checksum mismatch: " + required);
            }
        }
        // Recovery reads only the authenticated payload set. Reject files that were added after
        // commit even when they are unrelated to the four small recovery payloads; otherwise an
        // attacker or a stale partial copy could hide an unlisted file inside the checkpoint tree.
        try (var stream = Files.walk(normalized)) {
            for (Path file : stream.toList()) {
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                PathSafety.rejectSymlink(file);
                String relative = normalized.relativize(file).toString().replace('\\', '/');
                if (!relative.equals("READY")
                        && !relative.equals("checksums.sha256")
                        && !digests.containsKey(relative)) {
                    throw new IOException("Unlisted checkpoint recovery file: " + relative);
                }
            }
        }
        CheckpointManifest manifest = readManifest(normalized);
        if (manifest == null
                || (manifest.formatVersion != 1
                        && manifest.formatVersion != 2
                        && manifest.formatVersion != 3
                        && manifest.formatVersion != 4)
                || !normalized.getFileName().toString().equals(manifest.checkpointId)) {
            throw new IOException("Unsupported checkpoint recovery manifest");
        }
        validateManifest(normalized, manifest, digests);
        payloadLines.sort(String::compareTo);
        if (!Hashing.sha256JoinedLines(payloadLines).equals(manifest.checksum)) {
            throw new IOException("Checkpoint recovery manifest payload checksum mismatch");
        }
        return manifest;
    }

    public Path find(String id) throws IOException {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return null;
        Path candidate = PathSafety.requireChild(directory, directory.resolve(id));
        return Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(candidate)
                ? candidate
                : null;
    }

    /**
     * Unlink work one retention pass may add to a commit. Removing a generation unlinks every file
     * of a world; a backlog (for example after an upgrade from a build that kept everything) is
     * worked off over the following commits instead of stalling one of them for minutes.
     */
    private static final int MAX_REMOVALS_PER_PASS = 8;

    private static final long RETENTION_PASS_BUDGET_NANOS = 2_000_000_000L;

    public void retain() throws IOException {
        retain(null, null);
    }

    /**
     * Removes the generations the configured limits no longer allow, oldest first.
     *
     * <p>Every generation is a complete file set of its own: unchanged files are hard links, so
     * removing an older generation never takes a byte away from a newer one, and a restore reads
     * only the generation it restores. The parent is needed for one thing, as the link source of
     * the next generation, and that parent is the newest generation, which is never removed. An
     * earlier version kept every ancestor of a retained generation; with a chain that is never
     * interrupted by a BASE that meant nothing was ever removed.
     *
     * <p>Unlinking an old generation changes the ctime of every inode it shares with the newest
     * one. The next generation recognises an unchanged parent file by its recorded ctime and would
     * otherwise hash the whole world again after each pass, so the record of the newest generation
     * is brought up to date here: only for files that matched their record immediately before the
     * pass and still have the same size and mtime after it.
     *
     * @param newest the generation just committed, or null when not called from a commit
     * @param newestManifest its in-memory manifest, whose destination metadata is refreshed
     */
    private void retain(Path newest, CheckpointManifest newestManifest) throws IOException {
        // Integrity verification belongs to recovery/inspection. Re-hashing every retained
        // checkpoint after each commit made the live-world barrier grow with retention count.
        List<Path> checkpoints = listComplete();
        if (checkpoints.isEmpty()) return;
        Path first = checkpoints.get(0);
        long now = System.currentTimeMillis();
        long maxBytes =
                config.checkpoint.retentionMaxGb <= 0
                        ? 0
                        : config.checkpoint.retentionMaxGb * 1024L * 1024L * 1024L;
        // Sizing walks every file of every retained generation. Only the size axis needs it, and
        // it is disabled by default, so never pay for it when retention_max_gb is 0.
        long[] sizes = new long[checkpoints.size()];
        long total = 0;
        if (maxBytes > 0) {
            for (int i = 0; i < checkpoints.size(); i++) {
                sizes[i] = size(checkpoints.get(i));
                total += sizes[i];
            }
        }
        List<Integer> doomed = new ArrayList<>();
        long remaining = total;
        for (int i = checkpoints.size() - 1; i >= 1 && doomed.size() < MAX_REMOVALS_PER_PASS; i--) {
            Path candidate = checkpoints.get(i);
            boolean tooMany = i >= config.checkpoint.retentionCount;
            boolean tooOld =
                    config.checkpoint.retentionDays > 0
                            && now - Files.getLastModifiedTime(candidate).toMillis()
                                    > config.checkpoint.retentionDays * 86_400_000L;
            boolean tooLarge = maxBytes > 0 && remaining > maxBytes;
            String candidateId = candidate.getFileName().toString();
            if ((tooMany || tooOld || tooLarge)
                    && !isPinned(candidateId)
                    && !isQuarantined(candidateId)) {
                doomed.add(i);
                remaining -= sizes[i];
            }
        }
        if (doomed.isEmpty()) return;
        Map<String, FileFacts> before =
                newest != null && newestManifest != null && newest.equals(first)
                        ? trustedWorldFacts(newest, newestManifest)
                        : Map.of();
        long started = System.nanoTime();
        try {
            for (int index : doomed) {
                Path candidate = checkpoints.get(index);
                SnapshotManager.deleteTree(candidate);
                logger.verboseKey("checkpoint.retention_removed", candidate.getFileName());
                if (System.nanoTime() - started > RETENTION_PASS_BUDGET_NANOS) break;
            }
        } finally {
            if (!before.isEmpty()) refreshWorldFacts(newest, newestManifest, before);
        }
    }

    /**
     * The world files of {@code checkpoint} that currently match their recorded metadata exactly.
     */
    private static Map<String, FileFacts> trustedWorldFacts(
            Path checkpoint, CheckpointManifest manifest) {
        Map<String, FileFacts> trusted = new HashMap<>();
        Path world = checkpoint.resolve("world");
        for (SnapshotFile file : manifest.worldFiles) {
            if (file == null
                    || file.path == null
                    || !file.destinationMetadataRecorded
                    || file.storedChangeTime == 0L) continue;
            try {
                FileFacts facts = FileFacts.readOrNull(world.resolve(file.path));
                if (facts != null
                        && facts.regularFile()
                        && facts.size() == file.size
                        && facts.modifiedTime() == file.storedModifiedTime
                        && facts.changeTime() == file.storedChangeTime) {
                    trusted.put(file.path, facts);
                }
            } catch (IOException | RuntimeException unreadable) {
                // Left out: the next generation authenticates this file by content.
            }
        }
        return trusted;
    }

    /** Accepts the ctime and link count a retention pass of this process left behind. */
    private static void refreshWorldFacts(
            Path checkpoint, CheckpointManifest manifest, Map<String, FileFacts> before) {
        Path world = checkpoint.resolve("world");
        for (SnapshotFile file : manifest.worldFiles) {
            FileFacts old = file == null ? null : before.get(file.path);
            if (old == null) continue;
            try {
                FileFacts facts = FileFacts.readOrNull(world.resolve(file.path));
                if (facts != null
                        && facts.regularFile()
                        && facts.size() == old.size()
                        && facts.modifiedTime() == old.modifiedTime()
                        && facts.changeTime() != 0L) {
                    file.storedChangeTime = facts.changeTime();
                    file.storedLinkCount = facts.linkCount();
                }
            } catch (IOException | RuntimeException unreadable) {
                // The stale record stays; the file is then authenticated by content.
            }
        }
    }

    private synchronized boolean isPinned(String id) {
        return pins.containsKey(id);
    }

    private long nextSequence() throws IOException {
        long max = 0;
        if (Files.exists(directory)) {
            try (var stream = Files.list(directory)) {
                for (Path path : stream.toList()) {
                    Matcher matcher =
                            ID_PATTERN.matcher(
                                    path.getFileName().toString().replaceFirst("\\.tmp$", ""));
                    if (matcher.matches()) {
                        try {
                            max = Math.max(max, Long.parseLong(matcher.group(1)));
                        } catch (NumberFormatException exception) {
                            throw new IOException("Checkpoint sequence is too large", exception);
                        }
                    }
                }
            }
        }
        if (max >= 999_999_999_999_999_999L)
            throw new IOException("Checkpoint sequence is exhausted");
        return max + 1;
    }

    /** Sorted "digest relative" lines for every payload file except the manifest itself. */
    private List<String> checksumLines(Path root, CheckpointManifest manifest) throws IOException {
        List<String> lines = new ArrayList<>();
        Map<String, SnapshotFile> worldFiles = indexWorldFiles(manifest);
        try (var stream = Files.walk(root)) {
            for (Path file : stream.sorted().toList()) {
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                PathSafety.rejectSymlink(file);
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.equals("READY")
                        || relative.equals("checksums.sha256")
                        || relative.equals("manifest.json")) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String digest = trustedWorldDigest(file, relative, worldFiles);
                lines.add(digest + "  " + relative);
            }
        }
        lines.sort(String::compareTo);
        return lines;
    }

    private static Map<String, SnapshotFile> indexWorldFiles(CheckpointManifest manifest) {
        if (manifest == null || manifest.worldFiles == null || manifest.worldFiles.isEmpty())
            return Map.of();
        Map<String, SnapshotFile> result = new HashMap<>();
        for (SnapshotFile file : manifest.worldFiles) {
            if (file != null && file.path != null) result.put(file.path.replace('\\', '/'), file);
        }
        return result;
    }

    private static String trustedWorldDigest(
            Path file, String relative, Map<String, SnapshotFile> worldFiles) throws IOException {
        if (relative.startsWith("world/") && !worldFiles.isEmpty()) {
            SnapshotFile snapshot = worldFiles.get(relative.substring("world/".length()));
            if (snapshot != null && snapshot.sha256 != null && snapshot.size == Files.size(file)) {
                return snapshot.sha256;
            }
        }
        return Hashing.sha256(file);
    }

    private static String readChecksumLine(java.io.BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder(128);
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') break;
            if (character != '\r') line.append((char) character);
            if (line.length() > 8192) throw new IOException("Oversized checksum line");
        }
        return character == -1 && line.isEmpty() ? null : line.toString();
    }

    private static Path safeRelative(Path root, String relative) throws IOException {
        if (relative == null
                || relative.isBlank()
                || relative.equals("READY")
                || relative.equals("checksums.sha256")) {
            throw new IOException("Unsafe checkpoint path: " + relative);
        }
        final Path parsed;
        try {
            parsed = Path.of(relative.replace('\\', '/'));
        } catch (RuntimeException exception) {
            throw new IOException("Unsafe checkpoint path: " + relative, exception);
        }
        if (parsed.isAbsolute()) throw new IOException("Unsafe checkpoint path: " + relative);
        for (Path part : parsed) {
            String name = part.toString();
            if (name.equals(".") || name.equals(".."))
                throw new IOException("Unsafe checkpoint path: " + relative);
        }
        return PathSafety.requireChild(root, root.resolve(parsed));
    }

    private static void rethrowIfInterrupted(IOException exception) throws IOException {
        if (exception instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) {
            throw exception;
        }
    }

    private static boolean sameDestinationMetadata(
            BasicFileAttributes before,
            long beforeChangeTime,
            long beforeLinkCount,
            BasicFileAttributes after,
            Path file) {
        return before.isRegularFile()
                && after.isRegularFile()
                && before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && beforeChangeTime == changeTime(file)
                && beforeLinkCount == linkCount(file);
    }

    private static long changeTime(Path path) {
        try {
            Object value = Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS);
            return value instanceof FileTime time ? time.to(TimeUnit.NANOSECONDS) : 0L;
        } catch (UnsupportedOperationException | IOException ignored) {
            return 0L;
        }
    }

    private static long linkCount(Path path) {
        try {
            Object value = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            return value instanceof Number number ? number.longValue() : 0L;
        } catch (UnsupportedOperationException | IOException ignored) {
            return 0L;
        }
    }

    private record DestinationMetadata(long modifiedTime, long changeTime, long linkCount) {}

    private static void validateManifest(
            Path checkpoint, CheckpointManifest manifest, Map<String, String> digests)
            throws IOException {
        if (!CheckpointManifest.CURRENT_MINECRAFT_VERSION.equals(manifest.minecraftVersion)) {
            throw new IOException(
                    "Checkpoint Minecraft version mismatch: expected "
                            + CheckpointManifest.CURRENT_MINECRAFT_VERSION
                            + ", actual "
                            + manifest.minecraftVersion);
        }
        if (!RuntimeCapsule.supportsFormatVersion(manifest.runtimeCapsuleVersion)) {
            throw new IOException(
                    "Unsupported Runtime Capsule version: " + manifest.runtimeCapsuleVersion);
        }
        if (!manifest.clean
                || manifest.worldFiles == null
                || manifest.snapshotBackend == null
                || manifest.utcTimestamp == null) {
            throw new IOException("Checkpoint manifest is incomplete");
        }
        try {
            Instant.parse(manifest.utcTimestamp);
        } catch (Exception exception) {
            throw new IOException("Checkpoint timestamp is invalid", exception);
        }
        if (manifest.formatVersion >= 2 && manifest.parentCheckpointId != null) {
            if (!ID_PATTERN.matcher(manifest.parentCheckpointId).matches()
                    || sequence(manifest.parentCheckpointId) >= sequence(manifest.checkpointId)) {
                throw new IOException("Checkpoint parent ID is invalid");
            }
        }
        if (manifest.timelineParentCheckpointId != null
                && (!ID_PATTERN.matcher(manifest.timelineParentCheckpointId).matches()
                        || sequence(manifest.timelineParentCheckpointId)
                                >= sequence(manifest.checkpointId))) {
            throw new IOException("Checkpoint timeline parent ID is invalid");
        }
        if (manifest.snapshotDurationMs < 0
                || manifest.barrierDurationMs < 0
                || manifest.finalizationDurationMs < 0
                || manifest.totalBytes < 0
                || manifest.changedFiles < 0
                || manifest.reusedFiles < 0
                || manifest.playerSnapshotCount < 0
                || manifest.fakePlayerCount < 0
                || manifest.blockEventCount < 0
                || manifest.loadedChunkCount < 0
                || manifest.fullChunkCount < 0
                || manifest.blockTickingChunkCount < 0
                || manifest.entityTickingChunkCount < 0
                || manifest.forcedChunkCount < 0
                || manifest.blockEntityTickerCount < 0
                || manifest.entityOrderCount < 0
                || manifest.torchBurnoutCount < 0
                || manifest.ticketCount < 0
                || manifest.transientChunkCount < 0
                || manifest.transientChunksMadeDirty < 0
                || manifest.movingPistonCount < 0
                || manifest.scheduledTickCount < 0
                || manifest.orderedScheduledTickCount < 0
                || manifest.orderedMovingPistonCount < 0
                || manifest.orderedTransientEntityCount < 0
                || manifest.logicalDeltaFormatVersion < 0
                || manifest.logicalDeltaEpoch < 0
                || manifest.logicalDeltaChunkCount < 0
                || manifest.logicalDeltaEntityCount < 0
                || manifest.logicalDeltaEntityChunkCount < 0
                || manifest.logicalDeltaTombstoneCount < 0
                || manifest.logicalDeltaPayloadBytes < 0
                || manifest.generationChainDepth < 0
                || manifest.generationPresentFiles < 0
                || manifest.generationAbsentFiles < 0
                || manifest.generationInheritedFiles < 0
                || manifest.generationSealedFiles < 0
                || manifest.generationTombstoneFiles < 0
                || manifest.dimensions == null) {
            throw new IOException("Checkpoint manifest contains negative or missing counters");
        }
        if (manifest.runtimeCapsuleVersion >= 6) validateV6Manifest(manifest);
        Set<String> dimensions = new HashSet<>();
        for (String dimension : manifest.dimensions) {
            if (dimension == null
                    || dimension.isBlank()
                    || dimension.length() > 512
                    || !dimensions.add(dimension)) {
                throw new IOException("Checkpoint manifest contains an invalid dimension");
            }
        }
        for (String required :
                List.of(
                        "manifest.json",
                        "runtime-capsule.json",
                        "players.json",
                        "fakeplayers.json")) {
            if (!digests.containsKey(required))
                throw new IOException("Missing checkpoint payload: " + required);
        }
        if (manifest.formatVersion >= 4
                && "LOGICAL_DELTA".equalsIgnoreCase(manifest.checkpointEngine)
                && !digests.containsKey("logical-delta.json")) {
            throw new IOException("Logical delta checkpoint is missing logical-delta.json");
        }
        Set<String> manifestPaths = new HashSet<>();
        long totalBytes = 0;
        for (SnapshotFile snapshot : manifest.worldFiles) {
            if (snapshot == null
                    || snapshot.path == null
                    || snapshot.sha256 == null
                    || snapshot.size < 0
                    || SnapshotFilters.excluded(snapshot.path)) {
                throw new IOException("Invalid world file metadata in manifest");
            }
            String relative = snapshot.path.replace('\\', '/');
            safeRelative(checkpoint.resolve("world"), relative);
            if (!manifestPaths.add(relative))
                throw new IOException("Duplicate world file metadata: " + relative);
            String digest = digests.get("world/" + relative);
            if (digest == null || !digest.equalsIgnoreCase(snapshot.sha256)) {
                throw new IOException("World manifest/checksum mismatch: " + relative);
            }
            Path file = checkpoint.resolve("world").resolve(relative).normalize();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) != snapshot.size) {
                throw new IOException("World manifest size mismatch: " + relative);
            }
            try {
                totalBytes = Math.addExact(totalBytes, snapshot.size);
            } catch (ArithmeticException exception) {
                throw new IOException("World manifest byte count overflow", exception);
            }
        }
        Set<String> actualWorldPaths = new HashSet<>();
        for (String relative : digests.keySet())
            if (relative.startsWith("world/")) actualWorldPaths.add(relative.substring(6));
        if (!actualWorldPaths.equals(manifestPaths))
            throw new IOException("World manifest file set mismatch");
        if (manifest.totalBytes != totalBytes)
            throw new IOException("World manifest total byte count mismatch");
        if ((long) manifest.changedFiles + manifest.reusedFiles != manifest.worldFiles.size()) {
            throw new IOException("World manifest file counters are inconsistent");
        }
        validateGenerationFileSet(checkpoint, manifest, manifestPaths);
        validateGenerationSeal(checkpoint, manifest, digests);
    }

    private static void validateGenerationSeal(
            Path checkpoint, CheckpointManifest manifest, Map<String, String> digests)
            throws IOException {
        if (!"PORTABLE_GENERATION".equalsIgnoreCase(manifest.snapshotBackend)
                || manifest.generationSealFormatVersion <= 0) {
            return;
        }
        if (!digests.containsKey("generation-seal.json")) {
            throw new IOException("Portable generation is missing its seal journal");
        }
        Path sealPath = checkpoint.resolve("generation-seal.json");
        GenerationSeal seal =
                AtomicFiles.readJson(sealPath, GenerationSeal.class, 16L * 1024L * 1024L);
        if (seal == null
                || seal.formatVersion != manifest.generationSealFormatVersion
                || !safeEquals(seal.parentCheckpointId, manifest.parentCheckpointId)
                || seal.logicalCutServerTick != manifest.logicalCutServerTick
                || seal.logicalCutMonotonicNanos != manifest.logicalCutMonotonicNanos
                || seal.presentCount != manifest.generationPresentFiles
                || seal.absentCount != manifest.generationAbsentFiles
                || seal.inheritedCount != manifest.generationInheritedFiles
                || seal.sealedCount != manifest.generationSealedFiles
                || seal.tombstoneCount != manifest.generationTombstoneFiles
                || seal.fileSet == null
                || seal.fileSet.size() != manifest.fileSet.size()) {
            throw new IOException("Portable generation seal does not match manifest");
        }
        for (int index = 0; index < manifest.fileSet.size(); index++) {
            GenerationFileEntry expected = manifest.fileSet.get(index);
            GenerationFileEntry actual = seal.fileSet.get(index);
            if (expected == null
                    || actual == null
                    || !safeEquals(expected.path, actual.path)
                    || !safeEquals(expected.state, actual.state)
                    || !safeEquals(expected.captureKind, actual.captureKind)
                    || expected.size != actual.size
                    || expected.modifiedTime != actual.modifiedTime
                    || expected.changeTime != actual.changeTime
                    || !safeEquals(expected.sha256, actual.sha256)) {
                throw new IOException("Portable generation seal file set mismatch");
            }
        }
    }

    private static boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void validateGenerationFileSet(
            Path checkpoint, CheckpointManifest manifest, Set<String> worldPaths)
            throws IOException {
        if (manifest.fileSet == null || manifest.fileSet.isEmpty()) return;
        Set<String> seen = new HashSet<>();
        Set<String> present = new HashSet<>();
        int presentCount = 0;
        int absentCount = 0;
        int inheritedCount = 0;
        int sealedCount = 0;
        int tombstoneCount = 0;
        // Indexed once: a linear search per entry made verifying a 10,000-file generation compare
        // paths a hundred million times.
        Map<String, SnapshotFile> worldFilesByPath = new HashMap<>();
        for (SnapshotFile candidate : manifest.worldFiles) {
            if (candidate != null && candidate.path != null) {
                worldFilesByPath.putIfAbsent(candidate.path.replace('\\', '/'), candidate);
            }
        }
        for (GenerationFileEntry entry : manifest.fileSet) {
            if (entry == null
                    || entry.path == null
                    || entry.state == null
                    || !seen.add(entry.path.replace('\\', '/'))) {
                throw new IOException("Invalid checkpoint generation file set");
            }
            String path = entry.path.replace('\\', '/');
            safeRelative(checkpoint.resolve("world"), path);
            GenerationFileState state;
            try {
                state = GenerationFileState.valueOf(entry.state);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Unknown generation file state: " + entry.state, exception);
            }
            if (state == GenerationFileState.PRESENT) {
                if (entry.sha256 == null
                        || !entry.sha256.matches("[0-9a-fA-F]{64}")
                        || entry.size < 0
                        || entry.captureKind == null) {
                    throw new IOException("Invalid PRESENT generation file entry: " + path);
                }
                SnapshotFile worldFile = worldFilesByPath.get(path);
                if (worldFile == null
                        || worldFile.size != entry.size
                        || !entry.sha256.equalsIgnoreCase(worldFile.sha256)) {
                    throw new IOException(
                            "Generation file entry does not match world metadata: " + path);
                }
                try {
                    GenerationCaptureKind captureKind =
                            GenerationCaptureKind.valueOf(entry.captureKind);
                    if (captureKind == GenerationCaptureKind.INHERITED
                            && manifest.parentCheckpointId == null) {
                        throw new IOException("Inherited generation file has no parent: " + path);
                    }
                    if (captureKind == GenerationCaptureKind.INHERITED) inheritedCount++;
                    else sealedCount++;
                } catch (IllegalArgumentException exception) {
                    throw new IOException(
                            "Unknown generation capture kind: " + entry.captureKind, exception);
                }
                present.add(path);
                presentCount++;
            } else if (entry.captureKind != null
                    || entry.sha256 != null
                    || entry.size != 0L
                    || entry.modifiedTime != 0L
                    || entry.changeTime != 0L) {
                throw new IOException(
                        "Non-PRESENT generation entry contains payload metadata: " + path);
            } else if (state == GenerationFileState.TOMBSTONE) {
                tombstoneCount++;
            } else {
                absentCount++;
            }
        }
        if (!present.equals(worldPaths)) {
            throw new IOException("Generation file set does not match world files");
        }
        if (manifest.generationPresentFiles != presentCount
                || manifest.generationAbsentFiles != absentCount
                || manifest.generationInheritedFiles != inheritedCount
                || manifest.generationSealedFiles != sealedCount
                || manifest.generationTombstoneFiles != tombstoneCount) {
            throw new IOException("Generation file counters do not match file set");
        }
    }

    /**
     * v6 manifests carry the independently authenticated exact-domain and runtime evidence. Do not
     * let a syntactically valid but incomplete manifest reach the runtime payload validator.
     */
    private static void validateV6Manifest(CheckpointManifest manifest) throws IOException {
        if (manifest.fidelity == null
                || !Set.of("STRICT", "OPTIMIZED").contains(manifest.fidelity)
                || manifest.chunkDomainHash == null
                || !manifest.chunkDomainHash.matches("[0-9a-fA-F]{64}")
                || manifest.carpetCaptureStatus == null
                || !Set.of("CAPTURED", "NOT_APPLICABLE", "INCOMPLETE", "UNSUPPORTED")
                        .contains(manifest.carpetCaptureStatus)
                || manifest.unsupportedRuntimeState == null
                || manifest.unsupportedRuntimeState.size() > 10_000) {
            throw new IOException("Checkpoint v6 manifest runtime evidence is incomplete");
        }
        Set<String> markers = new HashSet<>();
        for (String marker : manifest.unsupportedRuntimeState) {
            if (marker == null
                    || marker.isBlank()
                    || marker.length() > 256
                    || !markers.add(marker)) {
                throw new IOException(
                        "Checkpoint v6 manifest contains an invalid unsupported-state marker");
            }
        }
        if (manifest.fullChunkCount > 1_000_000
                || manifest.blockTickingChunkCount > 1_000_000
                || manifest.entityTickingChunkCount > 1_000_000
                || manifest.forcedChunkCount > 1_000_000
                || manifest.blockEntityTickerCount > 1_000_000
                || manifest.entityOrderCount > 1_000_000
                || manifest.torchBurnoutCount > 1_000_000
                || manifest.ticketCount > 1_000_000
                || manifest.checkpointPlayerCount > 100_000
                || (manifest.checkpointPlayerHash != null
                        && !manifest.checkpointPlayerHash.matches("[0-9a-fA-F]{64}"))) {
            throw new IOException("Checkpoint v6 manifest runtime counters are too large");
        }
    }

    private static long size(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            return stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
    }

    private static long sequence(Path path) {
        return sequence(path.getFileName().toString());
    }

    private static long sequence(String id) {
        Matcher matcher = ID_PATTERN.matcher(id == null ? "" : id);
        if (!matcher.matches()) return -1;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Makes every newly written checkpoint file durable.
     *
     * <p>World entries that are hard links to the parent checkpoint are skipped: those inodes were
     * already fsynced when that parent was committed, and re-forcing them made an incremental
     * capture open and sync thousands of unchanged region files for nothing.
     */
    private void forceFiles(Path root, CheckpointManifest manifest) throws IOException {
        Map<String, SnapshotFile> worldFiles = indexWorldFiles(manifest);
        IoRateLimiter limiter = new IoRateLimiter(config.checkpoint.maxMbPerSecond);
        try (var stream = Files.walk(root)) {
            for (Path file : stream.toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if ("FAST".equals(durabilityMode())
                        && !relative.equals("manifest.json")
                        && !relative.equals("checksums.sha256")
                        && !relative.equals("READY")) {
                    continue;
                }
                if (relative.startsWith("world/")) {
                    SnapshotFile snapshot = worldFiles.get(relative.substring("world/".length()));
                    if (snapshot != null && snapshot.reusedFromParent) continue;
                }
                limiter.acquire(Files.size(file));
                AtomicFiles.force(file);
            }
        }
    }

    private String durabilityMode() {
        String mode = config.checkpoint.durabilityMode;
        return mode == null ? "SAFE" : mode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Throttles only background durability work; it is never used during the logical cut. */
    private static final class IoRateLimiter {
        private final long bytesPerSecond;
        private long windowStartedNanos = System.nanoTime();
        private long windowBytes;

        private IoRateLimiter(int megabytesPerSecond) {
            long megabytes = Math.max(0L, megabytesPerSecond);
            this.bytesPerSecond = megabytes == 0L ? 0L : megabytes * 1024L * 1024L;
        }

        private void acquire(long bytes) throws IOException {
            if (bytesPerSecond <= 0L || bytes <= 0L) return;
            long amount = Math.min(bytes, bytesPerSecond);
            while (true) {
                long now = System.nanoTime();
                long elapsed = now - windowStartedNanos;
                if (elapsed >= TimeUnit.SECONDS.toNanos(1L)) {
                    windowStartedNanos = now;
                    windowBytes = 0L;
                }
                if (windowBytes + amount <= bytesPerSecond) {
                    windowBytes += amount;
                    return;
                }
                long remaining = TimeUnit.SECONDS.toNanos(1L) - Math.max(0L, elapsed);
                try {
                    TimeUnit.NANOSECONDS.sleep(
                            Math.max(1L, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50L))));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Checkpoint I/O throttle interrupted");
                }
            }
        }
    }
}
