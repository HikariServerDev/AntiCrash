package com.atsukimc.anticrash.snapshot.logical;

import com.atsukimc.anticrash.checkpoint.CheckpointTiming;
import com.atsukimc.anticrash.player.NbtCodec;
import com.atsukimc.anticrash.util.Hashing;

import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.level.LevelProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-thread side of the Exact-Epoch semantic delta engine.
 *
 * <p>Only immutable Base64 NBT bytes cross the cut. Background code never receives a Minecraft
 * object. The engine is intentionally conservative: a missing candidate, an unsupported persistent
 * component, a lifecycle uncertainty, or a mutation during capture returns a blocked result so the
 * caller can use the existing safe backend.
 */
public final class LogicalDeltaEngine {
    public record PreparationReport(
            int captured, int inspected, long bytes, long evictions, String error) {}

    public record CaptureResult(LogicalDelta delta, boolean exact, String reason) {
        public static CaptureResult blocked(long epoch, String reason) {
            return new CaptureResult(LogicalDelta.blocked(epoch, reason), false, reason);
        }
    }

    private final ChunkMutationTracker mutations;
    private final ChunkCandidateCache candidates;

    /** Last successfully committed semantic index in this JVM. Missing means full first delta. */
    private final Map<String, Long> committedChunkGenerations = new LinkedHashMap<>();

    private final Map<String, String> committedChunkHashes = new LinkedHashMap<>();

    /** Accessible domain at the last committed epoch, separate from stale generation keys. */
    private final java.util.Set<String> committedAccessibleChunks = new java.util.HashSet<>();

    private final Map<String, String> committedEntityChunkHashes = new LinkedHashMap<>();
    private boolean committedBaseline;
    private int roundRobin;
    private String preparationError;

    public LogicalDeltaEngine(long candidateCacheBytes) {
        mutations = ChunkMutationTracker.global();
        candidates = new ChunkCandidateCache(candidateCacheBytes);
    }

    public ChunkCandidateCache candidates() {
        return candidates;
    }

    public synchronized String candidateReport() {
        return "size="
                + candidates.size()
                + "; bytes="
                + candidates.bytes()
                + "; maxBytes="
                + candidates.maxBytes()
                + "; evictions="
                + candidates.evictions()
                + "; committedBaseline="
                + committedBaseline
                + "; committedChunks="
                + committedChunkGenerations.size()
                + "; committedAccessibleChunks="
                + committedAccessibleChunks.size()
                + "; committedEntityChunks="
                + committedEntityChunkHashes.size()
                + "; preparationError="
                + (preparationError == null ? "none" : preparationError);
    }

    /**
     * Seeds the semantic generation ledger after a complete BASE/fallback view has committed at the
     * current server boundary. No chunk NBT is copied here; unchanged chunks inherit the
     * authenticated parent and only a later mutation requires a fresh candidate.
     */
    public synchronized void establishGenerationBaseline(MinecraftServer server) {
        committedChunkGenerations.clear();
        committedChunkHashes.clear();
        committedAccessibleChunks.clear();
        if (server != null) {
            registerSchedulers(server);
            // Keep generations for chunks that are no longer accessible as well. If one of those
            // chunks is mutated and then unloaded, the next logical cut must fail closed instead
            // of silently inheriting a stale parent region.
            committedChunkGenerations.putAll(mutations.snapshot());
            for (WorldChunkRef ref : accessibleChunks(server)) {
                String key = ref.key();
                committedAccessibleChunks.add(key);
                committedChunkGenerations.put(
                        key, mutations.generation(ref.dimension(), ref.chunk().getPos().toLong()));
                committedChunkHashes.put(key, "INHERITED");
            }
        }
        committedEntityChunkHashes.clear();
        committedBaseline = true;
        EntityLifecycleTracker.global().clearAll();
        mutations.clearUncertain();
    }

    /**
     * Captures a bounded number of detached ChunkSerializer payloads while the world is running.
     * This method must be called by the server thread; it never waits for a future or runs a
     * lifecycle/task pump.
     */
    public PreparationReport prepareCandidates(MinecraftServer server, long budgetNanos) {
        if (server == null || budgetNanos <= 0L)
            return new PreparationReport(0, 0, candidates.bytes(), candidates.evictions(), null);
        long started = System.nanoTime();
        registerSchedulers(server);
        List<WorldChunkRef> refs = accessibleChunks(server);
        if (refs.isEmpty())
            return new PreparationReport(
                    0, 0, candidates.bytes(), candidates.evictions(), preparationError);
        int captured = 0;
        int inspected = 0;
        int start = Math.floorMod(roundRobin, refs.size());
        try {
            for (int offset = 0; offset < refs.size(); offset++) {
                if (System.nanoTime() - started >= budgetNanos) break;
                WorldChunkRef ref = refs.get((start + offset) % refs.size());
                inspected++;
                long generation =
                        mutations.generation(ref.dimension(), ref.chunk().getPos().toLong());
                if (candidates.getIfValid(ref.key(), server.getTicks(), generation) != null)
                    continue;
                String encoded = encodeChunk(ref.world(), ref.chunk());
                byte[] payload = Base64.getDecoder().decode(encoded);
                candidates.put(
                        ref.key(), server.getTicks(), generation, payload, Hashing.sha256(payload));
                captured++;
                roundRobin = (start + offset + 1) % refs.size();
            }
        } catch (Exception exception) {
            preparationError = safeMessage(exception);
        }
        return new PreparationReport(
                captured, inspected, candidates.bytes(), candidates.evictions(), preparationError);
    }

    /**
     * Captures the current semantic state at one server tick. No filesystem operation is done here.
     * A candidate must be from this epoch or have a provably unchanged mutation generation.
     */
    public CaptureResult captureAtCut(
            MinecraftServer server, long epoch, String parentCheckpointId) {
        return captureAtCut(server, epoch, parentCheckpointId, false);
    }

    public CaptureResult captureAtCut(
            MinecraftServer server,
            long epoch,
            String parentCheckpointId,
            boolean allowUnsupportedPersistentState) {
        boolean unsupportedPersistentState;
        try {
            unsupportedPersistentState = hasUnsupportedPersistentFiles(server);
        } catch (IOException exception) {
            return CaptureResult.blocked(
                    epoch, "persistent_state_probe_failed:" + safeMessage(exception));
        }
        return captureAtCut(
                server,
                epoch,
                parentCheckpointId,
                allowUnsupportedPersistentState,
                unsupportedPersistentState,
                null);
    }

    /**
     * Cut variant used by CheckpointManager after its preflight. The boolean is deliberately
     * supplied by the barrier owner so the logical cut never walks the live data/poi trees twice.
     */
    public CaptureResult captureAtCut(
            MinecraftServer server,
            long epoch,
            String parentCheckpointId,
            boolean allowUnsupportedPersistentState,
            boolean unsupportedPersistentState) {
        return captureAtCut(
                server,
                epoch,
                parentCheckpointId,
                allowUnsupportedPersistentState,
                unsupportedPersistentState,
                null);
    }

    /**
     * Cut variant with an optional phase sink. The timing sink is diagnostic-only and does not
     * change the read-only capture or the epoch validation rules.
     */
    public CaptureResult captureAtCut(
            MinecraftServer server,
            long epoch,
            String parentCheckpointId,
            boolean allowUnsupportedPersistentState,
            boolean unsupportedPersistentState,
            CheckpointTiming timing) {
        if (server == null) return CaptureResult.blocked(epoch, "server_missing");
        registerSchedulers(server);
        if (!committedBaseline) {
            // A process restart does not have the mutation ledger that relates the live World to
            // the previous checkpoint. Capturing every accessible Chunk just to guess that delta
            // would defeat the low-pause contract, so force the configured full/fallback path.
            return CaptureResult.blocked(epoch, "generation_baseline_missing");
        }
        if (mutations.isUncertain())
            return CaptureResult.blocked(epoch, "chunk_mutation_hook_uncertain");
        long version = mutations.version();
        long phaseStart = System.nanoTime();
        List<WorldChunkRef> refs = accessibleChunks(server);
        java.util.Set<String> accessibleKeys = new java.util.HashSet<>();
        for (WorldChunkRef ref : refs) accessibleKeys.add(ref.key());
        recordPhase(timing, "delta.chunk_scan", phaseStart);
        Map<String, Long> observedGenerations = mutations.snapshot();
        for (Map.Entry<String, Long> observed : observedGenerations.entrySet()) {
            Long committed = committedChunkGenerations.get(observed.getKey());
            if (!Objects.equals(committed, observed.getValue())
                    && !accessibleKeys.contains(observed.getKey())) {
                return CaptureResult.blocked(
                        epoch, "changed_chunk_not_accessible:" + observed.getKey());
            }
        }
        EntityLifecycleTracker.Capture lifecycle = EntityLifecycleTracker.global().snapshot();
        if (lifecycle.overflow())
            return CaptureResult.blocked(epoch, "entity_lifecycle_observer_overflow");
        LogicalDelta delta = new LogicalDelta();
        delta.captureEpoch = epoch;
        delta.logicalCutMonotonicNanos = System.nanoTime();
        delta.lifecycleSequence = lifecycle.sequence();
        delta.parentCheckpointId = parentCheckpointId;
        delta.componentStatus.put("CHUNK_NBT", "CAPTURED");
        delta.componentStatus.put("ENTITIES", "CAPTURED");
        delta.componentStatus.put("WORLD_METADATA", "CAPTURED");
        delta.componentStatus.put("SCHEDULED_TICKS", "RUNTIME_CAPSULE_AUTHORITY");
        delta.componentStatus.put("BLOCK_EVENTS", "RUNTIME_CAPSULE_AUTHORITY");
        delta.componentStatus.put("POI", "UNSUPPORTED_IF_PRESENT");
        delta.componentStatus.put("PERSISTENT_STATE", "UNSUPPORTED_IF_PRESENT");
        // Preserve all observable generations for the next epoch. The accessible domain itself is
        // represented by chunkIndex; retaining old keys lets the next cut detect a mutation that
        // happened after a chunk was unloaded.
        delta.chunkGenerationIndex.putAll(observedGenerations);

        try {
            if (unsupportedPersistentState && !allowUnsupportedPersistentState) {
                return CaptureResult.blocked(epoch, "unsupported_persistent_files_present");
            }
            if (unsupportedPersistentState) {
                delta.componentStatus.put("POI", "UNSUPPORTED_ALLOWED_BY_CONFIG");
                delta.componentStatus.put("PERSISTENT_STATE", "UNSUPPORTED_ALLOWED_BY_CONFIG");
            }
            phaseStart = System.nanoTime();
            for (WorldChunkRef ref : refs) {
                long packed = ref.chunk().getPos().toLong();
                long generation = mutations.generation(ref.dimension(), packed);
                String key = ref.key();
                boolean changed =
                        !committedAccessibleChunks.contains(key)
                                || !Objects.equals(committedChunkGenerations.get(key), generation);
                if (!changed) {
                    // The full materialized parent is the authority for this unchanged chunk.
                    // Do not require a detached candidate or allocate a duplicate NBT payload.
                    delta.indexChunk(
                            key, committedChunkHashes.getOrDefault(key, "INHERITED"), generation);
                    continue;
                }
                ChunkCandidateCache.Candidate candidate =
                        candidates.getIfValid(ref.key(), epoch, generation);
                if (candidate == null) {
                    return CaptureResult.blocked(epoch, "candidate_missing_or_stale:" + ref.key());
                }
                byte[] payload = candidate.payload();
                delta.indexChunk(key, candidate.sha256(), generation);
                LogicalChunkDelta chunk = new LogicalChunkDelta();
                chunk.dimension = ref.dimension();
                chunk.packedPosition = packed;
                chunk.captureEpoch = candidate.captureEpoch();
                chunk.mutationGeneration = generation;
                chunk.payloadBase64 = Base64.getEncoder().encodeToString(payload);
                chunk.sha256 = Hashing.sha256(payload);
                delta.addChunk(chunk);
            }
            recordPhase(timing, "delta.chunk_candidate_validation", phaseStart);
            phaseStart = System.nanoTime();
            captureEntities(server, epoch, delta);
            recordPhase(timing, "delta.entity_capture", phaseStart);
            phaseStart = System.nanoTime();
            for (EntityLifecycleTracker.Event event : lifecycle.events()) {
                String chunkKey = ChunkMutationTracker.key(event.dimension(), event.chunk());
                // Every entity-loaded chunk is indexed above and is therefore authoritative: its
                // complete current entity list either replaces the parent region or provably
                // equals it. A tombstone for such a chunk is redundant and harmful, because an
                // entity that left and later re-entered the same chunk would be deleted again.
                if (delta.entityChunkIndex.containsKey(chunkKey)) continue;
                if ("ENTITY_UNLOADED".equals(event.kind())) {
                    // The entity section was written by Vanilla and unloaded after the parent
                    // epoch. Only the barrier save can capture that newer on-disk state.
                    return CaptureResult.blocked(epoch, "entity_unload_requires_barrier_fallback");
                }
                delta.addTombstone(
                        new LogicalTombstone(
                                event.kind(),
                                event.dimension(),
                                event.uuid(),
                                epoch,
                                event.detail(),
                                event.chunk()));
            }
            recordPhase(timing, "delta.tombstones", phaseStart);
            phaseStart = System.nanoTime();
            captureWorldMetadata(server, delta);
            recordPhase(timing, "delta.level_data", phaseStart);
            if (mutations.version() != version) {
                return CaptureResult.blocked(epoch, "chunk_mutated_during_logical_cut");
            }
            delta.exact = true;
            return new CaptureResult(delta, true, null);
        } catch (Exception exception) {
            return CaptureResult.blocked(epoch, safeMessage(exception));
        }
    }

    private static void recordPhase(CheckpointTiming timing, String phase, long startedNanos) {
        if (timing != null) timing.record(phase, Math.max(0L, System.nanoTime() - startedNanos));
    }

    /** Called only after the enclosing checkpoint has been atomically committed. */
    public synchronized void markCommitted(LogicalDelta delta) {
        if (delta == null || !delta.exact) return;
        committedChunkGenerations.clear();
        committedChunkGenerations.putAll(delta.chunkGenerationIndex);
        committedChunkHashes.clear();
        committedChunkHashes.putAll(delta.chunkIndex);
        committedAccessibleChunks.clear();
        committedAccessibleChunks.addAll(delta.chunkIndex.keySet());
        committedEntityChunkHashes.clear();
        committedEntityChunkHashes.putAll(delta.entityChunkIndex);
        committedBaseline = true;
        EntityLifecycleTracker.global().clearThrough(delta.lifecycleSequence);
    }

    public synchronized void resetCommittedBaseline() {
        candidates.clear();
        committedChunkGenerations.clear();
        committedChunkHashes.clear();
        committedAccessibleChunks.clear();
        committedEntityChunkHashes.clear();
        committedBaseline = false;
        EntityLifecycleTracker.global().clearAll();
    }

    public void releaseCandidates() {
        candidates.clear();
    }

    /**
     * The semantic path is safe only when these mutable stores are absent or explicitly handled.
     */
    public static boolean hasUnsupportedPersistentFiles(MinecraftServer server) throws IOException {
        if (server == null || server.getOverworld() == null) return true;
        Path root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
        // POI and arbitrary PersistentState files have no safe generic in-memory capture API in
        // 1.18.2.  Refuse semantic materialization when they exist instead of carrying an old
        // parent file into a newer epoch. Every dimension keeps its own poi/ and data/.
        for (ServerWorld world : server.getWorlds()) {
            Path dimensionRoot =
                    net.minecraft.world.dimension.DimensionType.getSaveDirectory(
                            world.getRegistryKey(), root);
            if (hasRegularFiles(dimensionRoot.resolve("poi"))
                    || hasRegularFiles(dimensionRoot.resolve("data"))) return true;
        }
        return hasRegularFiles(root.resolve("poi")) || hasRegularFiles(root.resolve("data"));
    }

    private static boolean hasRegularFiles(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            return false;
        try (var stream = Files.walk(path)) {
            return stream.anyMatch(file ->
                    Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(file));
        }
    }

    private void captureEntities(MinecraftServer server, long epoch, LogicalDelta delta)
            throws IOException {
        Map<String, List<Entity>> grouped = new LinkedHashMap<>();
        Map<String, ServerWorld> owners = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            String dimension = world.getRegistryKey().getValue().toString();
            for (Entity entity : world.iterateEntities()) {
                // Mirror ServerEntityManager#trySave: players, passengers (saved inside their
                // vehicle) and vehicles ridden by a player (saved with the player) never belong to
                // an entity region.
                if (entity == null || entity.isRemoved() || !entity.shouldSave()) continue;
                String key = ChunkMutationTracker.key(dimension, entity.getChunkPos().toLong());
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
                owners.putIfAbsent(key, world);
            }
        }
        // An empty captured chunk is meaningful: it removes an entity that existed in the parent.
        // We include every currently accessible chunk, not just chunks containing an entity.
        for (WorldChunkRef ref : accessibleChunks(server)) {
            String key = ChunkMutationTracker.key(ref.dimension(), ref.chunk().getPos().toLong());
            grouped.putIfAbsent(key, List.of());
            owners.putIfAbsent(key, ref.world());
        }
        for (Map.Entry<String, List<Entity>> entry : grouped.entrySet()) {
            // Dimension identifiers themselves contain a colon (minecraft:overworld).  Split at
            // the final separator added by ChunkMutationTracker rather than truncating the
            // namespace and treating the rest as a malformed long.
            int separator = entry.getKey().lastIndexOf(':');
            if (separator <= 0 || separator == entry.getKey().length() - 1) {
                throw new IOException("invalid entity chunk key: " + entry.getKey());
            }
            String dimension = entry.getKey().substring(0, separator);
            long packed = Long.parseLong(entry.getKey().substring(separator + 1));
            ServerWorld world = owners.get(entry.getKey());
            if (world == null || !world.isChunkLoaded(packed)) {
                // The entity section has not been read from disk yet (FRESH/PENDING). Vanilla
                // refuses to write such a section, and so must we: an empty list here would erase
                // entities that simply have not finished loading.
                if (!entry.getValue().isEmpty()) {
                    throw new IOException("entity_section_not_loaded:" + entry.getKey());
                }
                continue;
            }
            NbtCompound root = new NbtCompound();
            root.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
            NbtList entities = new NbtList();
            List<Entity> saved = new ArrayList<>();
            List<NbtCompound> savedNbt = new ArrayList<>();
            for (Entity entity : entry.getValue()) {
                // saveNbt writes the "id" key; writeNbt alone produces an entity that Minecraft
                // silently discards on load.
                NbtCompound nbt = new NbtCompound();
                if (!entity.saveNbt(nbt)) continue;
                entities.add(nbt);
                saved.add(entity);
                savedNbt.add(nbt);
            }
            root.put("Entities", entities);
            ChunkPos pos = new ChunkPos(packed);
            root.putIntArray("Position", new int[] {pos.x, pos.z});
            String encoded = NbtCodec.encode(root);
            String hash = Hashing.sha256(Base64.getDecoder().decode(encoded));
            String key = ChunkMutationTracker.key(dimension, packed);
            delta.indexEntityChunk(key, hash);
            boolean changed =
                    !committedBaseline
                            || !Objects.equals(committedEntityChunkHashes.get(key), hash);
            if (!changed) continue;
            for (int index = 0; index < saved.size(); index++) {
                Entity entity = saved.get(index);
                LogicalEntityDelta snapshot = new LogicalEntityDelta();
                snapshot.dimension = dimension;
                snapshot.uuid = entity.getUuidAsString();
                snapshot.entityType = String.valueOf(entity.getType());
                snapshot.networkId = entity.getId();
                snapshot.packedChunkPosition = entity.getChunkPos().toLong();
                snapshot.captureEpoch = epoch;
                String encodedEntity = NbtCodec.encode(savedNbt.get(index));
                snapshot.payloadBase64 = encodedEntity;
                snapshot.sha256 = Hashing.sha256(Base64.getDecoder().decode(encodedEntity));
                delta.addEntity(snapshot);
            }
            LogicalEntityChunkDelta chunk = new LogicalEntityChunkDelta();
            chunk.dimension = dimension;
            chunk.packedPosition = packed;
            chunk.captureEpoch = epoch;
            chunk.payloadBase64 = encoded;
            chunk.sha256 = hash;
            delta.addEntityChunk(chunk);
        }
    }

    private static void captureWorldMetadata(MinecraftServer server, LogicalDelta delta)
            throws IOException {
        if (!(server.getSaveProperties() instanceof LevelProperties properties)) {
            throw new IOException("save_properties_not_level_properties");
        }
        // Refresh the values MinecraftServer#save and ServerWorld#saveLevel copy into the save
        // properties right before writing level.dat. Without this, the world border, custom
        // boss bars and the dragon fight would be restored from the last full save instead.
        ServerWorld overworld = server.getOverworld();
        if (overworld != null) {
            properties.getMainWorldProperties().setWorldBorder(overworld.getWorldBorder().write());
        }
        properties.setCustomBossEvents(server.getBossBarManager().toNbt());
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEnderDragonFight() != null) {
                properties.setDragonFight(world.getEnderDragonFight().toData());
            }
        }
        NbtCompound player = server.getPlayerManager().getUserData();
        NbtCompound data =
                properties.cloneWorldNbt(
                        server.getRegistryManager(), player == null ? null : player.copy());
        // LevelStorage.Session#backupLevelDataFile nests everything under "Data". A bare
        // compound is unreadable to LevelStorage and falls back to level.dat_old or defaults.
        NbtCompound root = new NbtCompound();
        root.put("Data", data);
        String encoded = NbtCodec.encode(root);
        delta.componentStatus.put("LEVEL_DAT", "CAPTURED");
        delta.componentStatus.put(
                "LEVEL_DAT_SHA256", Hashing.sha256(Base64.getDecoder().decode(encoded)));
        delta.componentStatus.put("LEVEL_DAT_PAYLOAD", encoded);
    }

    private static String encodeChunk(ServerWorld world, WorldChunk chunk) throws IOException {
        return NbtCodec.encode(ChunkSerializer.serialize(world, chunk));
    }

    private static List<WorldChunkRef> accessibleChunks(MinecraftServer server) {
        List<WorldChunkRef> result = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            String dimension = world.getRegistryKey().getValue().toString();
            var storage =
                    (com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin)
                            world.getChunkManager().threadedAnvilChunkStorage;
            for (ChunkHolder holder : storage.anticrash$getCurrentChunkHolders().values()) {
                if (holder == null) continue;
                var future = holder.getAccessibleFuture();
                if (future == null || !future.isDone() || future.isCompletedExceptionally())
                    continue;
                try {
                    var ready = future.getNow(null);
                    WorldChunk chunk = ready == null ? null : ready.left().orElse(null);
                    if (chunk != null) {
                        result.add(
                                new WorldChunkRef(
                                        world,
                                        dimension,
                                        chunk,
                                        ChunkMutationTracker.key(
                                                dimension, chunk.getPos().toLong())));
                    }
                } catch (RuntimeException ignored) {
                    // A retiring holder is not an exact candidate for this epoch.
                }
            }
        }
        // The key is built once per chunk; building it inside the comparator allocated two strings
        // per comparison, every tick, for thousands of chunks.
        result.sort(Comparator.comparing(WorldChunkRef::key));
        return result;
    }

    private static void registerSchedulers(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            String dimension = world.getRegistryKey().getValue().toString();
            ChunkMutationTracker.registerScheduler(world.getBlockTickScheduler(), dimension);
            ChunkMutationTracker.registerScheduler(world.getFluidTickScheduler(), dimension);
        }
    }

    private static String safeMessage(Throwable exception) {
        String message = exception == null ? "unknown" : exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record WorldChunkRef(
            ServerWorld world, String dimension, WorldChunk chunk, String key) {}
}
