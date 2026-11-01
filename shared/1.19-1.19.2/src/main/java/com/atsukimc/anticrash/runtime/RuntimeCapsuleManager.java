package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.checkpoint.CheckpointTiming;
import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.integration.carpet.CarpetIntegration;
import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import net.minecraft.world.tick.ChunkTickScheduler;
import net.minecraft.world.tick.OrderedTick;

import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.UUID;

public final class RuntimeCapsuleManager {
    private final AntiCrashLogger logger;
    private final AntiCrashConfig config;
    private boolean carpetRestoreSucceeded = true;
    private String carpetRestoreDetail;

    public RuntimeCapsuleManager(AntiCrashLogger logger) {
        this(null, logger);
    }

    public RuntimeCapsuleManager(AntiCrashConfig config, AntiCrashLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    public RuntimeCapsule capture(MinecraftServer server,
                                  TransientChunkStateMarker.MarkResult transientState) {
        return capture(server, transientState, null);
    }

    /**
     * Checkpoint capture with an optional phase sink.  The sink is diagnostic-only: all values
     * are recorded after the same read-only operation that the original capture performed.
     */
    public RuntimeCapsule capture(MinecraftServer server,
                                  TransientChunkStateMarker.MarkResult transientState,
                                  CheckpointTiming timing) {
        RuntimeCapsule capsule = captureObservableState(server, transientState, true, timing);
        // Capturing an epoch must be observational.  Mutating World#nextTickOrder here changes
        // the live simulation merely because a checkpoint was requested and made the previous
        // implementation timing-dependent.  Restore/legacy migration may opt into repair, but
        // the checkpoint capture path never does.
        normalizeNextTickOrder(server, capsule, false);
        populateFingerprints(capsule, null);
        validate(capsule);
        return capsule;
    }

    /** Captures only observable Minecraft state for a post-restore comparison. */
    public RuntimeCapsule captureCurrentForEquivalence(MinecraftServer server) {
        RuntimeCapsule capsule = captureObservableState(server, null, false, null);
        populateFingerprints(capsule, null);
        return capsule;
    }

    /** Adds the separately persisted FakePlayer state to the capsule fingerprint set. */
    public static void populateFakePlayerFingerprint(RuntimeCapsule capsule,
                                                     List<FakePlayerSnapshot> snapshots) {
        if (capsule == null) return;
        List<String> lines = RuntimeFingerprint.fakePlayerLines(snapshots);
        capsule.expectedFingerprints.put(EquivalenceCategory.FAKEPLAYERS,
                RuntimeFingerprint.hashLines(lines));
        capsule.fakePlayerFingerprintVersion = RuntimeCapsule.CURRENT_FAKE_PLAYER_FINGERPRINT_VERSION;
    }

    /** Adds the optional STRICT Real Player identity payload and its authenticated fingerprint. */
    public static void populateCheckpointPlayerIdentities(
            RuntimeCapsule capsule, List<CheckpointPlayerSnapshot> snapshots) {
        if (capsule == null) return;
        capsule.checkpointPlayersCaptured = true;
        capsule.checkpointPlayers.clear();
        if (snapshots != null) capsule.checkpointPlayers.addAll(snapshots);
        capsule.expectedFingerprints.put(EquivalenceCategory.PLAYERS,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.checkpointPlayerLines(capsule.checkpointPlayers)));
    }

    private RuntimeCapsule captureObservableState(MinecraftServer server,
                                                  TransientChunkStateMarker.MarkResult transientState,
                                                  boolean checkpointCapture,
                                                  CheckpointTiming timing) {
        RuntimeCapsule capsule = new RuntimeCapsule();
        capsule.fidelity = config == null ? "OPTIMIZED" : config.recovery.fidelity;
        capsule.serverTick = server.getTicks();
        capsule.capturedAtEpochMs = System.currentTimeMillis();
        for (ServerWorld world : server.getWorlds()) {
            String dimension = world.getRegistryKey().getValue().toString();
            long phaseStart = System.nanoTime();
            ChunkStateSnapshot chunks = ChunkStateSnapshot.capture(world,
                    transientState == null ? Set.of() : transientState.chunkPositions(dimension));
            recordPhase(timing, "runtime.chunk_domain", phaseStart);
            capsule.chunks.add(chunks);
            phaseStart = System.nanoTime();
            RuntimeCapsule.WorldRuntime worldRuntime = new RuntimeCapsule.WorldRuntime();
            worldRuntime.dimension = chunks.dimension;
            worldRuntime.gameTime = world.getTime();
            worldRuntime.timeOfDay = world.getTimeOfDay();
            com.atsukimc.anticrash.mixin.WorldAccessorMixin accessor =
                    (com.atsukimc.anticrash.mixin.WorldAccessorMixin) world;
            worldRuntime.nextTickOrder = accessor.anticrash$getNextTickOrder();
            worldRuntime.lcgBlockSeed = accessor.anticrash$getLcgBlockSeed();
            worldRuntime.randomStateSupported = false;
            RuntimeCapsule.InhabitedTimes inhabited = new RuntimeCapsule.InhabitedTimes();
            inhabited.dimension = chunks.dimension;
            for (Long packed : chunks.loadedChunks) {
                net.minecraft.world.chunk.WorldChunk loaded = world.getChunkManager().getWorldChunk(
                        ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed));
                if (loaded == null) continue;
                inhabited.chunks.add(packed);
                inhabited.times.add(loaded.getInhabitedTime());
            }
            capsule.inhabitedTimes.add(inhabited);
            worldRuntime.idleTimeout = ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                    .anticrash$getIdleTimeout();
            worldRuntime.rainGradient = accessor.anticrash$getRainGradient();
            worldRuntime.thunderGradient = accessor.anticrash$getThunderGradient();
            worldRuntime.loadedChunkCount = chunks.loadedCount();
            worldRuntime.fullChunkCount = chunks.fullCount();
            worldRuntime.blockTickingChunkCount = chunks.blockTickingCount();
            worldRuntime.entityTickingChunkCount = chunks.entityTickingCount();
            worldRuntime.forcedChunkCount = chunks.forcedCount();
            capsule.worlds.add(worldRuntime);
            recordPhase(timing, "runtime.world_runtime", phaseStart);

            phaseStart = System.nanoTime();
            capsule.entityOrder.addAll(EntityRuntimeSnapshot.capture(world));
            recordPhase(timing, "runtime.entities", phaseStart);
            phaseStart = System.nanoTime();
            capsule.blockEntityTickerOrder.addAll(BlockEntityTickerSnapshot.capture(world));
            recordPhase(timing, "runtime.block_entity_tickers", phaseStart);
            phaseStart = System.nanoTime();
            capsule.redstoneTorchBurnout.add(RedstoneTorchBurnoutSnapshot.capture(world));
            recordPhase(timing, "runtime.torch_burnout", phaseStart);

            // Piston block entities are part of the block-entity ticking domain, not the entity
            // ticking domain. Observing only entity-ticking chunks creates the timing-dependent
            // hole that made a multi-chunk Trencher stop after recovery. Use the actual
            // block-ticking set in both modes; this only inspects existing ticker objects and
            // does not load an additional chunk. The fallback keeps legacy-shaped in-memory
            // snapshots safe for diagnostics.
            Set<Long> pistonChunks = new HashSet<>();
            if (chunks.blockTickingChunks != null) pistonChunks.addAll(chunks.blockTickingChunks);
            else if (chunks.entityTickingChunks != null) pistonChunks.addAll(chunks.entityTickingChunks);
            if (checkpointCapture && transientState != null) {
                pistonChunks.addAll(transientState.chunkPositions(dimension));
            }
            phaseStart = System.nanoTime();
            capsule.movingPistonTickOrder.addAll(
                    MovingPistonTickSnapshot.capture(world, pistonChunks));
            recordPhase(timing, "runtime.moving_pistons", phaseStart);
            phaseStart = System.nanoTime();
            capsule.transientEntityTickOrder.addAll(TransientEntityTickSnapshot.capture(world));
            recordPhase(timing, "runtime.transient_entities", phaseStart);
        }
        long phaseStart = System.nanoTime();
        capsule.ticketLedger.addAll(TicketLedgerSnapshot.capture(server.getWorlds()));
        recordPhase(timing, "runtime.ticket_ledger", phaseStart);
        phaseStart = System.nanoTime();
        capsule.blockEvents = BlockEventSnapshot.capture(server.getWorlds());
        recordPhase(timing, "runtime.block_events", phaseStart);
        phaseStart = System.nanoTime();
        capsule.carpetTickSpeed = CarpetIntegration.captureTickSpeed();
        capsule.carpetRules = com.atsukimc.anticrash.integration.carpet.CarpetRules.capture();
        if (CarpetIntegration.isInstalled()) {
            capsule.carpetCaptureStatus = capsule.carpetTickSpeed == null ? "INCOMPLETE" : "CAPTURED";
            if (capsule.carpetTickSpeed == null) capsule.unsupportedRuntimeState.add(
                    "CARPET_TICKSPEED_CAPTURE");
            else if (capsule.carpetTickSpeed.timeWarpActive) capsule.unsupportedRuntimeState.add(
                    "CARPET_TIME_WARP");
        } else {
            capsule.carpetCaptureStatus = "NOT_APPLICABLE";
        }
        recordPhase(timing, "runtime.carpet", phaseStart);
        phaseStart = System.nanoTime();
        if (transientState != null) capsule.scheduledTicks.addAll(transientState.orderedScheduledTicks());
        // The marker is the authoritative checkpoint-boundary ordering. An empty marker result is
        // still a real checkpoint state: rescanning after the marker would mix ticks created by
        // post-marker chunk work into the capsule and reintroduce the timing-dependent corruption
        // this snapshot is meant to prevent. The fallback scan is used only by verification and
        // by callers that did not run the marker; it never forces a new chunk.
        if (!checkpointCapture || transientState == null) {
            capsule.scheduledTicks.addAll(captureActiveScheduledTicks(server));
        }
        capsule.scheduledTicks.sort(java.util.Comparator
                .comparing((ScheduledTickSnapshot tick) -> tick.dimension)
                .thenComparingLong(tick -> tick.triggerTick)
                .thenComparingInt(tick -> tick.priority)
                .thenComparingLong(tick -> tick.subTickOrder)
                .thenComparing(tick -> tick.kind)
                .thenComparing(tick -> tick.type)
                .thenComparingInt(tick -> tick.x)
                .thenComparingInt(tick -> tick.y)
                .thenComparingInt(tick -> tick.z));
        recordPhase(timing, "runtime.scheduled_ticks", phaseStart);
        capsule.unsupportedRuntimeState.add("WORLD_RNG");
        capsule.entityIdCounter = com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin.anticrash$currentId().get();
        for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.currentScreenHandler != null && player.currentScreenHandler != player.playerScreenHandler) {
                capsule.containerSessions.add(player.getGameProfile().getName() + "|"
                        + player.currentScreenHandler.getClass().getSimpleName());
            }
        }
        if (!capsule.containerSessions.isEmpty()) capsule.unsupportedRuntimeState.add("CONTAINER_SESSION");
        phaseStart = System.nanoTime();
        for (Map.Entry<String, List<String>> category : RuntimeExtensionSnapshot.capture(server, capsule).entrySet()) {
            capsule.runtimeExtensionCounts.put(category.getKey(), category.getValue().size());
            capsule.expectedFingerprints.put(category.getKey(), RuntimeFingerprint.hashLines(category.getValue()));
            // CHUNK_RUNTIME is the inhabitedTimes list itself and is not stored twice.
            if (!RuntimeExtensionSnapshot.CHUNK.equals(category.getKey())
                    && category.getValue().size() <= RuntimeCapsule.MAX_DIAGNOSTIC_LINES) {
                capsule.runtimeExtensionLines.put(category.getKey(), List.copyOf(category.getValue()));
            }
        }
        recordPhase(timing, "runtime.extensions", phaseStart);
        return capsule;
    }

    private static void recordPhase(CheckpointTiming timing, String phase, long startedNanos) {
        if (timing != null) timing.record(phase, Math.max(0L, System.nanoTime() - startedNanos));
    }

    private static List<ScheduledTickSnapshot> captureActiveScheduledTicks(MinecraftServer server) {
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        List<ScheduledTickSnapshot> result = new ArrayList<>();
        for (ServerWorld world : worlds.values()) {
            ThreadedAnvilChunkStorageAccessorMixin storage =
                    (ThreadedAnvilChunkStorageAccessorMixin)
                            world.getChunkManager().threadedAnvilChunkStorage;
            for (net.minecraft.server.world.ChunkHolder holder :
                    storage.anticrash$getCurrentChunkHolders().values()) {
                if (holder == null) continue;
                // WorldTickScheduler uses block-ticking readiness. ENTITY_TICKING would omit
                // valid FULL/TICKING chunks and could leave a multi-chunk machine's scheduler
                // state out of the capsule.
                var future = holder.getAccessibleFuture();
                if (future == null || !future.isDone() || future.isCompletedExceptionally()) continue;
                try {
                    var value = future.getNow(null);
                    if (value != null && value.left().isPresent()) {
                        result.addAll(ScheduledTickSnapshot.capture(
                                world.getRegistryKey().getValue().toString(), value.left().get()));
                    }
                } catch (RuntimeException ignored) {
                    // A concurrently retiring holder is excluded and reported by the domain
                    // fingerprint rather than being force-loaded here.
                }
            }
        }
        result.sort(java.util.Comparator.comparing((ScheduledTickSnapshot tick) -> tick.dimension)
                .thenComparingLong(tick -> tick.triggerTick)
                .thenComparingLong(tick -> tick.subTickOrder)
                .thenComparing(tick -> tick.kind)
                .thenComparing(tick -> tick.type)
                .thenComparingInt(tick -> tick.x)
                .thenComparingInt(tick -> tick.y)
                .thenComparingInt(tick -> tick.z));
        return result;
    }

    private static void populateFingerprints(RuntimeCapsule capsule,
                                             List<FakePlayerSnapshot> fakePlayers) {
        capsule.expectedFingerprints.put(EquivalenceCategory.CHUNK_DOMAIN,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.chunkDomainLines(capsule.chunks)));
        capsule.expectedFingerprints.put(EquivalenceCategory.BLOCK_EVENTS,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.blockEventLines(capsule.blockEvents)));
        capsule.expectedFingerprints.put(EquivalenceCategory.SCHEDULED_TICKS,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.scheduledTickLines(capsule.scheduledTicks)));
        capsule.expectedFingerprints.put(EquivalenceCategory.MOVING_PISTONS,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.movingPistonLines(capsule.movingPistonTickOrder)));
        capsule.expectedFingerprints.put(EquivalenceCategory.BLOCK_ENTITY_TICKERS,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.tickerLines(capsule.blockEntityTickerOrder)));
        capsule.expectedFingerprints.put(EquivalenceCategory.ENTITIES,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.entityLines(capsule.entityOrder)));
        capsule.expectedFingerprints.put(EquivalenceCategory.REDSTONE_RUNTIME,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.burnoutLines(capsule.redstoneTorchBurnout)));
        capsule.expectedFingerprints.put(EquivalenceCategory.TICKET_LEDGER,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.ticketLines(capsule.ticketLedger)));
        capsule.expectedFingerprints.put(EquivalenceCategory.WORLD_RUNTIME,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldLines(capsule.worlds)));
        capsule.expectedFingerprints.put(EquivalenceCategory.WORLD_RNG,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldRngLines()));
        capsule.expectedFingerprints.put(EquivalenceCategory.CARPET,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.carpetLines(capsule.carpetTickSpeed)));
        if (fakePlayers != null) populateFakePlayerFingerprint(capsule, fakePlayers);
    }

    private void normalizeNextTickOrder(MinecraftServer server, RuntimeCapsule capsule,
                                        boolean applyLiveState) {
        Map<String, Long> maximumRestoredOrder = new HashMap<>();
        for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
            maximumRestoredOrder.merge(tick.dimension, tick.subTickOrder, Math::max);
        }
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        for (RuntimeCapsule.WorldRuntime snapshot : capsule.worlds) {
            Long maximum = maximumRestoredOrder.get(snapshot.dimension);
            long normalized = normalizeNextTickOrderValue(snapshot.nextTickOrder, maximum);
            if (normalized == snapshot.nextTickOrder) continue;
            snapshot.nextTickOrder = normalized;
            ServerWorld world = worlds.get(snapshot.dimension);
            if (world == null) {
                throw new IllegalStateException("Scheduled-tick order dimension is unavailable: "
                        + snapshot.dimension);
            }
            if (applyLiveState) {
                // Legacy restore can repair a live process whose counter reset.  Checkpoint
                // capture deliberately passes false and remains side-effect free.
                ((com.atsukimc.anticrash.mixin.WorldAccessorMixin) world)
                        .anticrash$setNextTickOrder(snapshot.nextTickOrder);
            }
        }
    }

    static long normalizeNextTickOrderValue(long current, Long maximumExisting) {
        if (maximumExisting == null || current > maximumExisting) return current;
        if (maximumExisting == Long.MAX_VALUE) {
            throw new IllegalStateException("Scheduled-tick order is exhausted");
        }
        return maximumExisting + 1L;
    }

    public BlockEventSnapshot.RestoreResult restore(MinecraftServer server, RuntimeCapsule capsule) {
        validate(capsule);
        restoreWorldTickOrder(server, capsule);
        restoreWorldLcgBlockSeed(server, capsule);
        carpetRestoreDetail = null;
        if (capsule.carpetTickSpeed == null) {
            carpetRestoreSucceeded = !CarpetIntegration.isInstalled()
                    || "NOT_APPLICABLE".equals(capsule.carpetCaptureStatus);
            if (!carpetRestoreSucceeded) carpetRestoreDetail = "Carpet tick-speed state is missing";
        } else {
            carpetRestoreSucceeded = CarpetIntegration.restoreTickSpeed(capsule.carpetTickSpeed, logger);
            if (!carpetRestoreSucceeded) carpetRestoreDetail = "Carpet tick-speed restore failed";
        }
        if (capsule.carpetRules != null) {
            // Rules changed at run time are not in carpet.conf and would otherwise be back at
            // whatever a fresh start gives them.
            com.atsukimc.anticrash.integration.carpet.CarpetRules.Outcome rules =
                    com.atsukimc.anticrash.integration.carpet.CarpetRules.restore(capsule.carpetRules, server);
            if (rules.changed() > 0 || rules.failed() > 0 || rules.unknown() > 0) {
                logger.normalKey("carpet.rules_restored", rules.changed(), rules.failed(), rules.unknown());
            }
        }
        BlockEventSnapshot.RestoreResult result = capsule.blockEvents.restore(server.getWorlds());
        logger.normalKey("runtime.block_events", result.restored, result.failed);
        return result;
    }

    public boolean carpetRestoreSucceeded() { return carpetRestoreSucceeded; }
    public String carpetRestoreDetail() { return carpetRestoreDetail; }

    private void restoreWorldLcgBlockSeed(MinecraftServer server, RuntimeCapsule capsule) {
        if (capsule.formatVersion < 6) return;
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        for (RuntimeCapsule.WorldRuntime snapshot : capsule.worlds) {
            ServerWorld world = worlds.get(snapshot.dimension);
            if (world == null) throw new IllegalStateException(
                    "World runtime dimension is unavailable: " + snapshot.dimension);
            com.atsukimc.anticrash.mixin.WorldAccessorMixin accessor =
                    (com.atsukimc.anticrash.mixin.WorldAccessorMixin) world;
            accessor.anticrash$setLcgBlockSeed(snapshot.lcgBlockSeed);
            if (snapshot.idleTimeout != null) {
                ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                        .anticrash$setIdleTimeout(Math.max(0, snapshot.idleTimeout));
            }
            if (snapshot.rainGradient != null && snapshot.thunderGradient != null) {
                float rain = Math.max(0.0f, Math.min(1.0f, snapshot.rainGradient));
                float thunder = Math.max(0.0f, Math.min(1.0f, snapshot.thunderGradient));
                accessor.anticrash$setRainGradientValue(rain);
                accessor.anticrash$setRainGradientPrev(rain);
                accessor.anticrash$setThunderGradientValue(thunder);
                accessor.anticrash$setThunderGradientPrev(thunder);
            }
            // ServerChunkManager adds (world time - lastMobSpawningTime) to every ticking chunk's
            // InhabitedTime. The field starts at 0 in a new JVM, so the first tick after a restart
            // would add the whole age of the world. At a tick boundary it equals the world time.
            ((com.atsukimc.anticrash.mixin.ServerChunkManagerAccessorMixin) (Object) world.getChunkManager())
                    .anticrash$setLastMobSpawningTime(world.getTime());
        }
    }

    /**
     * Restores the global sequence used when new block/fluid ticks are scheduled. Without this,
     * a restarted world starts again at zero and newly-created ticks can sort ahead of restored
     * ticks that have the same trigger time and priority.
     */
    private void restoreWorldTickOrder(MinecraftServer server, RuntimeCapsule capsule) {
        if (capsule.formatVersion < 4) {
            logger.normalKey("runtime.tick_order_legacy");
            return;
        }
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        int restored = 0;
        for (RuntimeCapsule.WorldRuntime snapshot : capsule.worlds) {
            ServerWorld world = worlds.get(snapshot.dimension);
            if (world == null) {
                throw new IllegalStateException("Scheduled-tick order dimension is unavailable: "
                        + snapshot.dimension);
            }
            ((com.atsukimc.anticrash.mixin.WorldAccessorMixin) world)
                    .anticrash$setNextTickOrder(snapshot.nextTickOrder);
            restored++;
        }
        logger.normalKey("runtime.tick_order", restored);
    }

    /** Restores exact scheduled-tick queues after required chunks reach FULL accessibility. */
    public ScheduledTickRestoreResult restoreScheduledTicks(MinecraftServer server,
                                                            RuntimeCapsule capsule) {
        validate(capsule);
        ScheduledTickRestoreResult result = new ScheduledTickRestoreResult();
        if (capsule.formatVersion < 3) {
            logger.normalKey("runtime.scheduled_ticks_legacy");
            return result;
        }

        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        // Registry resolution belongs to runtime preflight (Minecraft is bootstrapped here), not
        // the format-only validator used by offline tests and checkpoint inspection.
        for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
            Identifier id = tick.typeIdentifier();
            boolean registered = ScheduledTickSnapshot.BLOCK.equals(tick.kind)
                    ? Registry.BLOCK.containsId(id) : Registry.FLUID.containsId(id);
            if (!registered) {
                throw new IllegalStateException("Unknown scheduled-tick type: " + tick.type);
            }
        }
        Map<QueueKey, List<ScheduledTickSnapshot>> groups = new LinkedHashMap<>();
        Set<QueueChunkKey> queueChunks = new java.util.LinkedHashSet<>();
        // Chunks that own an exact scheduled tick. Recovery prepares every one of them, so each
        // must be accessible; the other checkpoint-FULL chunks are only cleared when loaded.
        Set<QueueChunkKey> tickChunks = new java.util.HashSet<>();
        for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
            long packed = new ChunkPos(tick.x >> 4, tick.z >> 4).toLong();
            queueChunks.add(new QueueChunkKey(tick.dimension, packed));
            tickChunks.add(new QueueChunkKey(tick.dimension, packed));
            groups.computeIfAbsent(new QueueKey(tick.dimension, tick.kind, packed),
                            ignored -> new ArrayList<>())
                    .add(tick);
        }
        if (capsule.formatVersion >= 6) {
            // A zero-length queue is still state. Chunk post-processing can create a scheduled
            // tick between FULL load and /ac start; include every checkpoint-FULL chunk so both
            // schedulers are cleared/replaced even when the expected queue was empty.
            for (ChunkStateSnapshot snapshot : capsule.chunks) {
                for (Long packed : snapshot.fullChunks) {
                    queueChunks.add(new QueueChunkKey(snapshot.dimension, packed));
                }
            }
        }
        // Replace both schedulers for every protected scheduled-tick chunk. The opposite queue
        // may intentionally have been empty at the checkpoint and must remain empty even if
        // chunk post-processing recreated a tick while the restored world was loading.
        for (QueueChunkKey chunk : queueChunks) {
            groups.computeIfAbsent(new QueueKey(chunk.dimension, ScheduledTickSnapshot.BLOCK,
                    chunk.packedChunkPosition), ignored -> new ArrayList<>());
            groups.computeIfAbsent(new QueueKey(chunk.dimension, ScheduledTickSnapshot.FLUID,
                    chunk.packedChunkPosition), ignored -> new ArrayList<>());
        }

        // Resolve every target before mutating any scheduler, keeping restore fail-closed.
        List<PreparedQueue> prepared = new ArrayList<>();
        for (Map.Entry<QueueKey, List<ScheduledTickSnapshot>> entry : groups.entrySet()) {
            QueueKey key = entry.getKey();
            ServerWorld world = worlds.get(key.dimension);
            ChunkPos pos = new ChunkPos(key.packedChunkPosition);
            if (world == null) {
                throw new IllegalStateException("Scheduled-tick recovery dimension is not ready: "
                        + key.dimension);
            }
            // getWorldChunk() is backed by the ticking future and returns null for a legal
            // FULL-only holder. Scheduled ticks live in the chunk-local scheduler and only need
            // the accessible WorldChunk here. Resolve it without joining a future.
            WorldChunk chunk = resolveAccessibleChunk(world, pos);
            if (chunk == null && !tickChunks.contains(new QueueChunkKey(key.dimension, key.packedChunkPosition))) {
                // A checkpoint-FULL chunk without ticks that the preload mode left unloaded (for
                // example CRITICAL). When it loads, its scheduler comes from the restored chunk
                // NBT, which is exactly the cut's queue; nothing was recreated to clear.
                continue;
            }
            if (chunk == null) {
                throw new IllegalStateException("Scheduled-tick recovery chunk is not accessible: "
                        + key.dimension + " " + pos);
            }
            prepared.add(new PreparedQueue(world, chunk, key.kind, entry.getValue()));
        }

        for (PreparedQueue queue : prepared) {
            if (ScheduledTickSnapshot.BLOCK.equals(queue.kind)) {
                restoreBlockQueue(queue.world, queue.chunk, queue.ticks);
            } else {
                restoreFluidQueue(queue.world, queue.chunk, queue.ticks);
            }
            result.restored += queue.ticks.size();
            result.queues++;
        }
        logger.normalKey("runtime.scheduled_ticks", result.restored, result.queues);
        return result;
    }

    private static WorldChunk resolveAccessibleChunk(ServerWorld world, ChunkPos position) {
        ThreadedAnvilChunkStorageAccessorMixin storage =
                (ThreadedAnvilChunkStorageAccessorMixin)
                        world.getChunkManager().threadedAnvilChunkStorage;
        net.minecraft.server.world.ChunkHolder holder =
                storage.anticrash$getCurrentChunkHolders().get(position.toLong());
        if (holder == null) return null;
        var future = holder.getAccessibleFuture();
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return null;
        try {
            var value = future.getNow(null);
            return value == null ? null : value.left().orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Restores the relative tick order of moving pistons before the first recovered world tick. */
    /**
     * Puts each loaded chunk's InhabitedTime back to its value at the cut.
     *
     * @return {restored, chunks of the capsule that are not loaded}
     */
    public int[] restoreInhabitedTimes(MinecraftServer server, RuntimeCapsule capsule) {
        int restored = 0, missing = 0;
        if (capsule.inhabitedTimes == null) return new int[]{0, 0};
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        for (RuntimeCapsule.InhabitedTimes inhabited : capsule.inhabitedTimes) {
            ServerWorld world = inhabited == null ? null : worlds.get(inhabited.dimension);
            if (world == null || inhabited.chunks.size() != inhabited.times.size()) continue;
            for (int index = 0; index < inhabited.chunks.size(); index++) {
                long packed = inhabited.chunks.get(index);
                net.minecraft.world.chunk.WorldChunk chunk = world.getChunkManager().getWorldChunk(
                        ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed));
                if (chunk == null) {
                    missing++;
                    continue;
                }
                chunk.setInhabitedTime(Math.max(0L, inhabited.times.get(index)));
                restored++;
            }
        }
        return new int[]{restored, missing};
    }

    public int restoreMovingPistonTickOrder(MinecraftServer server, RuntimeCapsule capsule) {
        validate(capsule);
        if (capsule.formatVersion < 3) return 0;
        if (capsule.formatVersion >= 6) {
            // STRICT follows this with the complete current/pending ticker restore. OPTIMIZED
            // still needs the v5 moving-piston guarantee, but cannot require every unrelated
            // ticker because its preload plan intentionally loads only a best-effort subset.
            if (config != null && "STRICT".equalsIgnoreCase(config.recovery.fidelity)) {
                verifyMovingPistonsPresent(server, capsule);
            } else {
                restoreOptimizedMovingPistonTickOrder(server, capsule);
            }
            logger.normalKey("runtime.moving_piston_order", capsule.movingPistonTickOrder.size());
            return capsule.movingPistonTickOrder.size();
        }
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        Map<String, List<MovingPistonTickSnapshot>> byDimension = new LinkedHashMap<>();
        for (MovingPistonTickSnapshot snapshot : capsule.movingPistonTickOrder) {
            byDimension.computeIfAbsent(snapshot.dimension, ignored -> new ArrayList<>())
                    .add(snapshot);
        }

        // Resolve every expected piston and ticker before changing list order.
        List<PreparedPistonOrder> prepared = new ArrayList<>();
        for (Map.Entry<String, List<MovingPistonTickSnapshot>> entry : byDimension.entrySet()) {
            ServerWorld world = worlds.get(entry.getKey());
            if (world == null) {
                throw new IllegalStateException("Moving-piston dimension is not available: "
                        + entry.getKey());
            }
            List<BlockEntityTickInvoker> tickers =
                    ((com.atsukimc.anticrash.mixin.WorldAccessorMixin) world)
                            .anticrash$getBlockEntityTickers();
            Map<BlockPos, BlockEntityTickInvoker> targetTickers = new HashMap<>();
            Set<BlockPos> expected = new HashSet<>();
            for (MovingPistonTickSnapshot snapshot : entry.getValue()) {
                expected.add(snapshot.position());
            }
            for (BlockEntityTickInvoker pending :
                    ((com.atsukimc.anticrash.mixin.WorldAccessorMixin) world)
                            .anticrash$getPendingBlockEntityTickers()) {
                if (!BlockEntityTickerSnapshot.removed(pending) && expected.contains(pending.getPos())) {
                    throw new IllegalStateException("Moving-piston ticker is still pending: "
                            + pending.getPos());
                }
            }
            List<Integer> slots = new ArrayList<>();
            for (int index = 0; index < tickers.size(); index++) {
                BlockEntityTickInvoker ticker = tickers.get(index);
                if (BlockEntityTickerSnapshot.removed(ticker)) continue;
                BlockPos position = ticker.getPos();
                if (!expected.contains(position)) continue;
                if (!(BlockEntityTickerSnapshot.loadedBlockEntity(world, position) instanceof PistonBlockEntity)
                        || targetTickers.put(position, ticker) != null) {
                    throw new IllegalStateException("Moving-piston ticker is invalid: " + position);
                }
                slots.add(index);
            }
            if (targetTickers.size() != expected.size() || slots.size() != expected.size()) {
                throw new IllegalStateException("Not all moving-piston tickers are ready in "
                        + entry.getKey());
            }
            List<BlockEntityTickInvoker> ordered = new ArrayList<>();
            for (MovingPistonTickSnapshot snapshot : entry.getValue()) {
                BlockEntityTickInvoker ticker = targetTickers.get(snapshot.position());
                if (ticker == null) {
                    throw new IllegalStateException("Moving-piston ticker disappeared: "
                            + snapshot.position());
                }
                ordered.add(ticker);
            }
            prepared.add(new PreparedPistonOrder(tickers, slots, ordered));
        }

        for (PreparedPistonOrder order : prepared) {
            for (int index = 0; index < order.slots.size(); index++) {
                order.tickers.set(order.slots.get(index), order.ordered.get(index));
            }
        }
        logger.normalKey("runtime.moving_piston_order", capsule.movingPistonTickOrder.size());
        return capsule.movingPistonTickOrder.size();
    }

    /**
     * Reorders only the moving-piston entries for OPTIMIZED recovery. The current/pending queue
     * boundary is taken from the v6 semantic ticker snapshot; no piston is moved between Vanilla
     * lists and unrelated block entities are left untouched. Every expected piston must resolve
     * to a live PistonBlockEntity ticker before any list is changed.
     */
    private static void restoreOptimizedMovingPistonTickOrder(MinecraftServer server,
                                                                RuntimeCapsule capsule) {
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        Map<PistonPositionKey, String> expectedQueues = new HashMap<>();
        for (BlockEntityTickerSnapshot ticker : capsule.blockEntityTickerOrder) {
            if (ticker == null) continue;
            PistonPositionKey key = new PistonPositionKey(ticker.dimension, ticker.x, ticker.y, ticker.z);
            String previous = expectedQueues.putIfAbsent(key, ticker.queue);
            if (previous != null && !previous.equals(ticker.queue)) {
                throw new IllegalStateException("Moving-piston queue is ambiguous: " + key);
            }
        }
        Map<String, List<MovingPistonTickSnapshot>> byDimension = new LinkedHashMap<>();
        for (MovingPistonTickSnapshot snapshot : capsule.movingPistonTickOrder) {
            if (snapshot == null) throw new IllegalStateException("Moving-piston snapshot is null");
            byDimension.computeIfAbsent(snapshot.dimension, ignored -> new ArrayList<>()).add(snapshot);
        }

        List<PreparedPistonOrder> prepared = new ArrayList<>();
        for (Map.Entry<String, List<MovingPistonTickSnapshot>> dimension : byDimension.entrySet()) {
            ServerWorld world = worlds.get(dimension.getKey());
            if (world == null) throw new IllegalStateException(
                    "Moving-piston dimension is not available: " + dimension.getKey());
            com.atsukimc.anticrash.mixin.WorldAccessorMixin accessor =
                    (com.atsukimc.anticrash.mixin.WorldAccessorMixin) world;
            prepareOptimizedPistonQueue(prepared, world, dimension.getKey(),
                    BlockEntityTickerSnapshot.CURRENT, dimension.getValue(), expectedQueues,
                    accessor.anticrash$getBlockEntityTickers());
            prepareOptimizedPistonQueue(prepared, world, dimension.getKey(),
                    BlockEntityTickerSnapshot.PENDING, dimension.getValue(), expectedQueues,
                    accessor.anticrash$getPendingBlockEntityTickers());
        }
        for (PreparedPistonOrder order : prepared) {
            for (int index = 0; index < order.slots.size(); index++) {
                order.tickers.set(order.slots.get(index), order.ordered.get(index));
            }
        }
    }

    private static void prepareOptimizedPistonQueue(List<PreparedPistonOrder> output,
                                                     ServerWorld world, String dimension,
                                                     String queue,
                                                     List<MovingPistonTickSnapshot> snapshots,
                                                     Map<PistonPositionKey, String> expectedQueues,
                                                     List<BlockEntityTickInvoker> live) {
        List<MovingPistonTickSnapshot> expected = new ArrayList<>();
        Set<BlockPos> positions = new HashSet<>();
        for (MovingPistonTickSnapshot snapshot : snapshots) {
            BlockPos position = snapshot.position();
            String expectedQueue = expectedQueues.get(new PistonPositionKey(dimension,
                    position.getX(), position.getY(), position.getZ()));
            if (expectedQueue == null) {
                throw new IllegalStateException("Moving-piston ticker metadata is missing: "
                        + dimension + " " + position);
            }
            if (queue.equals(expectedQueue)) {
                if (!positions.add(position)) throw new IllegalStateException(
                        "Duplicate moving-piston position: " + dimension + " " + position);
                expected.add(snapshot);
            }
        }
        if (expected.isEmpty()) return;

        Map<BlockPos, BlockEntityTickInvoker> targets = new HashMap<>();
        List<Integer> slots = new ArrayList<>();
        for (int index = 0; index < live.size(); index++) {
            BlockEntityTickInvoker ticker = live.get(index);
            if (BlockEntityTickerSnapshot.removed(ticker) || !positions.contains(ticker.getPos())) continue;
            BlockPos position = ticker.getPos();
            if (!(BlockEntityTickerSnapshot.loadedBlockEntity(world, position) instanceof PistonBlockEntity)
                    || targets.put(position, ticker) != null) {
                throw new IllegalStateException("Moving-piston ticker is invalid: "
                        + dimension + " " + position);
            }
            slots.add(index);
        }
        if (targets.size() != expected.size()) {
            throw new IllegalStateException("Moving-piston ticker is unavailable: "
                    + dimension + " queue=" + queue + " expected=" + expected.size()
                    + " actual=" + targets.size());
        }
        List<BlockEntityTickInvoker> ordered = new ArrayList<>(expected.size());
        for (MovingPistonTickSnapshot snapshot : expected) {
            BlockEntityTickInvoker ticker = targets.get(snapshot.position());
            if (ticker == null) throw new IllegalStateException(
                    "Moving-piston ticker disappeared: " + dimension + " " + snapshot.position());
            ordered.add(ticker);
        }
        output.add(new PreparedPistonOrder(live, slots, ordered));
    }

    private static void verifyMovingPistonsPresent(MinecraftServer server, RuntimeCapsule capsule) {
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        for (MovingPistonTickSnapshot snapshot : capsule.movingPistonTickOrder) {
            ServerWorld world = worlds.get(snapshot.dimension);
            if (world == null || !(BlockEntityTickerSnapshot.loadedBlockEntity(world, snapshot.position()) instanceof PistonBlockEntity)) {
                throw new IllegalStateException("Moving-piston block entity is unavailable: "
                        + snapshot.dimension + " " + snapshot.position());
            }
            com.atsukimc.anticrash.mixin.WorldAccessorMixin accessor =
                    (com.atsukimc.anticrash.mixin.WorldAccessorMixin) world;
            boolean present = containsTicker(accessor.anticrash$getBlockEntityTickers(), snapshot.position())
                    || containsTicker(accessor.anticrash$getPendingBlockEntityTickers(), snapshot.position());
            if (!present) throw new IllegalStateException("Moving-piston ticker is unavailable: "
                    + snapshot.dimension + " " + snapshot.position());
        }
    }

    private static boolean containsTicker(List<BlockEntityTickInvoker> tickers, BlockPos position) {
        for (BlockEntityTickInvoker ticker : tickers) {
            if (!BlockEntityTickerSnapshot.removed(ticker) && position.equals(ticker.getPos())) return true;
        }
        return false;
    }

    /**
     * EntityList ordering is captured observationally but is not rewritten. EntityList is an
     * active Vanilla data structure and its insertion/removal invariants are not exposed through
     * a safe restore API in 1.18.2. Replacing its internal map (the old implementation) could
     * detach entities from ServerEntityManager and is therefore forbidden in both fidelity modes.
     */
    public int restoreTransientEntityTickOrder(MinecraftServer server, RuntimeCapsule capsule) {
        validate(capsule);
        if (capsule.formatVersion < 4) return 0;
        logger.normalKey("runtime.entity_order_observed_only", capsule.transientEntityTickOrder.size());
        return 0;
    }

    /**
     * Reorders only the existing semantic ticker lists. No BlockEntityTickInvoker is serialized or
     * manufactured: every expected entry must resolve to the live position/type/name, and the
     * two Vanilla-owned lists are then reordered in place. A pending list mismatch is a hard
     * failure because moving it into the current list at the wrong boundary changes the next tick.
     */
    public int restoreBlockEntityTickerOrder(MinecraftServer server, RuntimeCapsule capsule) {
        validate(capsule);
        if (capsule.formatVersion < 6) return 0;
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        Map<String, Map<String, List<BlockEntityTickerSnapshot>>> expected = new LinkedHashMap<>();
        for (RuntimeCapsule.WorldRuntime world : capsule.worlds) {
            expected.put(world.dimension, new LinkedHashMap<>());
        }
        for (BlockEntityTickerSnapshot snapshot : capsule.blockEntityTickerOrder) {
            if (snapshot == null || snapshot.dimension == null || snapshot.queue == null) {
                throw new IllegalStateException("BlockEntity ticker snapshot is invalid");
            }
            expected.computeIfAbsent(snapshot.dimension, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(snapshot.queue, ignored -> new ArrayList<>()).add(snapshot);
        }

        List<PreparedTickerOrder> prepared = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<BlockEntityTickerSnapshot>>> dimension : expected.entrySet()) {
            ServerWorld world = worlds.get(dimension.getKey());
            if (world == null) throw new IllegalStateException(
                    "BlockEntity ticker dimension is unavailable: " + dimension.getKey());
            com.atsukimc.anticrash.mixin.WorldAccessorMixin accessor =
                    (com.atsukimc.anticrash.mixin.WorldAccessorMixin) world;
            prepareTickerQueue(prepared, world, BlockEntityTickerSnapshot.CURRENT,
                    dimension.getValue().getOrDefault(BlockEntityTickerSnapshot.CURRENT, List.of()),
                    accessor.anticrash$getBlockEntityTickers());
            prepareTickerQueue(prepared, world, BlockEntityTickerSnapshot.PENDING,
                    dimension.getValue().getOrDefault(BlockEntityTickerSnapshot.PENDING, List.of()),
                    accessor.anticrash$getPendingBlockEntityTickers());
        }
        for (PreparedTickerOrder order : prepared) {
            order.live.clear();
            order.live.addAll(order.ordered);
        }
        logger.normalKey("runtime.block_entity_ticker_order", capsule.blockEntityTickerOrder.size());
        return capsule.blockEntityTickerOrder.size();
    }

    private static void prepareTickerQueue(List<PreparedTickerOrder> output, ServerWorld world,
                                           String queue, List<BlockEntityTickerSnapshot> expected,
                                           List<BlockEntityTickInvoker> live) {
        Map<String, BlockEntityTickInvoker> byKey = new HashMap<>();
        int liveCount = 0;
        for (BlockEntityTickInvoker ticker : live) {
            // Removed tickers are never captured (Vanilla drops them unrun), so they are neither
            // matched here nor kept: leaving them out of the reordered list is what the next
            // World#tickBlockEntities pass would do anyway.
            if (BlockEntityTickerSnapshot.removed(ticker)) continue;
            liveCount++;
            String key = BlockEntityTickerSnapshot.keyFor(world, queue, ticker);
            if (key == null || byKey.put(key, ticker) != null) {
                throw new IllegalStateException("Duplicate or invalid " + queue
                        + " BlockEntity ticker in " + world.getRegistryKey().getValue());
            }
        }
        List<BlockEntityTickInvoker> ordered = new ArrayList<>(expected.size());
        Set<String> seen = new HashSet<>();
        for (BlockEntityTickerSnapshot snapshot : expected) {
            String key = snapshot.key();
            BlockEntityTickInvoker ticker = byKey.get(key);
            if (ticker == null || !seen.add(key)) {
                throw new IllegalStateException("BlockEntity ticker is missing or duplicated: "
                        + world.getRegistryKey().getValue() + " " + key);
            }
            ordered.add(ticker);
        }
        if (ordered.size() != liveCount) {
            throw new IllegalStateException("BlockEntity ticker set changed in "
                    + world.getRegistryKey().getValue() + " " + queue
                    + " expected=" + ordered.size() + " actual=" + liveCount);
        }
        output.add(new PreparedTickerOrder(live, ordered));
    }

    /** Restores the per-world Redstone Torch burnout history without retaining old World keys. */
    public int restoreRedstoneTorchBurnout(MinecraftServer server, RuntimeCapsule capsule) {
        validate(capsule);
        if (capsule.formatVersion < 6) return 0;
        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        for (RedstoneTorchBurnoutSnapshot snapshot : capsule.redstoneTorchBurnout) {
            ServerWorld world = worlds.get(snapshot.dimension);
            if (world == null) throw new IllegalStateException(
                    "Redstone burnout dimension is unavailable: " + snapshot.dimension);
            snapshot.restore(world);
        }
        logger.normalKey("runtime.torch_burnout", capsule.redstoneTorchBurnoutCount());
        return capsule.redstoneTorchBurnoutCount();
    }

    /**
     * Compares the observable post-restore state. This is deliberately a diagnostic operation:
     * it never mutates tickets, EntityList maps, or worker queues.
     */
    public RecoveryEquivalenceReport compareEquivalence(MinecraftServer server,
                                                         RuntimeCapsule expected,
                                                         List<FakePlayerSnapshot> expectedFakePlayers,
                                                         AntiCrashConfig.Strict strict,
                                                         String checkpointId) {
        RecoveryEquivalenceReport report = new RecoveryEquivalenceReport(checkpointId,
                config == null ? "STRICT" : config.recovery.fidelity);
        if (expected == null) {
            report.add(RecoveryEquivalenceReport.missing(EquivalenceCategory.CHECKPOINT_INTEGRITY,
                    true, "Runtime Capsule is missing"));
            report.finalizeResult(false);
            return report;
        }
        if (expected.formatVersion < 6) {
            for (String category : List.of(EquivalenceCategory.CHUNK_DOMAIN,
                    EquivalenceCategory.BLOCK_ENTITY_TICKERS, EquivalenceCategory.ENTITIES,
                    EquivalenceCategory.REDSTONE_RUNTIME, EquivalenceCategory.WORLD_RNG,
                    EquivalenceCategory.CARPET, EquivalenceCategory.FAKEPLAYERS,
                    EquivalenceCategory.TICKET_LEDGER)) {
                report.add(RecoveryEquivalenceReport.missing(category, true,
                        "Runtime Capsule v" + expected.formatVersion + " has no v6 state"));
            }
            report.finalizeResult(false);
            return report;
        }

        RuntimeCapsule actual = captureCurrentForEquivalence(server);
        addComparison(report, EquivalenceCategory.CHUNK_DOMAIN,
                RuntimeFingerprint.chunkDomainLines(expected.chunks),
                RuntimeFingerprint.chunkDomainLines(actual.chunks), true);
        addComparison(report, EquivalenceCategory.BLOCK_EVENTS,
                RuntimeFingerprint.blockEventLines(expected.blockEvents),
                RuntimeFingerprint.blockEventLines(actual.blockEvents), true);
        addComparison(report, EquivalenceCategory.SCHEDULED_TICKS,
                RuntimeFingerprint.scheduledTickLines(expected.scheduledTicks),
                RuntimeFingerprint.scheduledTickLines(actual.scheduledTicks), true);
        addComparison(report, EquivalenceCategory.MOVING_PISTONS,
                RuntimeFingerprint.movingPistonLines(expected.movingPistonTickOrder),
                RuntimeFingerprint.movingPistonLines(actual.movingPistonTickOrder), true);
        addComparison(report, EquivalenceCategory.BLOCK_ENTITY_TICKERS,
                RuntimeFingerprint.tickerLines(expected.blockEntityTickerOrder),
                RuntimeFingerprint.tickerLines(actual.blockEntityTickerOrder),
                strict == null || strict.requireBlockEntityTickerOrder);
        Set<String> fakePlayerUuids = new HashSet<>();
        if (expectedFakePlayers != null) {
            for (FakePlayerSnapshot player : expectedFakePlayers) {
                if (player != null && player.uuid != null) fakePlayerUuids.add(player.uuid);
            }
        }
        boolean includeRealPlayers = strict != null && strict.players != null
                && strict.players.requireCheckpointPlayers;
        addComparison(report, EquivalenceCategory.ENTITIES,
                RuntimeFingerprint.entityLines(expected.entityOrder, fakePlayerUuids, includeRealPlayers),
                RuntimeFingerprint.entityLines(actual.entityOrder, fakePlayerUuids, includeRealPlayers),
                strict == null || strict.requireEntityOrder);
        addComparison(report, EquivalenceCategory.REDSTONE_RUNTIME,
                RuntimeFingerprint.burnoutLines(expected.redstoneTorchBurnout),
                RuntimeFingerprint.burnoutLines(actual.redstoneTorchBurnout), true);
        if (strict != null && strict.players != null && strict.players.requireCheckpointPlayers) {
            if (!expected.checkpointPlayersCaptured || expected.checkpointPlayers == null) {
                report.add(RecoveryEquivalenceReport.missing(EquivalenceCategory.PLAYERS, true,
                        "STRICT checkpoint player identities are missing"));
            } else {
                addComparison(report, EquivalenceCategory.PLAYERS,
                        RuntimeFingerprint.checkpointPlayerLines(expected.checkpointPlayers),
                        RuntimeFingerprint.liveRealPlayerLines(server), true);
            }
        } else {
            report.add(RecoveryEquivalenceReport.notApplicable(EquivalenceCategory.PLAYERS));
        }
        List<String> expectedTickets = RuntimeFingerprint.ticketLines(expected.ticketLedger);
        List<String> actualTickets = RuntimeFingerprint.ticketLines(actual.ticketLedger);
        if (expectedTickets.isEmpty() && actualTickets.isEmpty()) {
            addComparison(report, EquivalenceCategory.TICKET_LEDGER,
                    expectedTickets, actualTickets, strict == null || strict.requireTicketLedger);
        } else {
            // The mapped 1.18.2 ticket manager has no safe public restoration API. Tickets are
            // deliberately regenerated by Vanilla from players/forced chunks instead of copying
            // its private map, so even an identical-looking observation is not proof that the
            // old expiry/identity graph was restored. Preserve hashes and diffs as diagnostics,
            // but classify the category honestly as UNSUPPORTED. Operators who accept this
            // limitation can set require_ticket_ledger=false explicitly.
            report.add(RecoveryEquivalenceReport.unsupportedComparison(
                    EquivalenceCategory.TICKET_LEDGER, expectedTickets, actualTickets,
                    strict == null || strict.requireTicketLedger,
                    "Chunk Ticket ledger is observe-only; Vanilla tickets are regenerated, not restored"));
        }
        addComparison(report, EquivalenceCategory.WORLD_RUNTIME,
                RuntimeFingerprint.worldLines(expected.worlds),
                RuntimeFingerprint.worldLines(actual.worlds), true);
        report.add(RecoveryEquivalenceReport.unsupportedComparison(
                EquivalenceCategory.WORLD_RNG,
                RuntimeFingerprint.worldRngLines(), RuntimeFingerprint.worldRngLines(), true,
                "Minecraft Random implementation state is not restored by this Minecraft version adapter"));

        compareCarpet(report, expected, actual, strict == null || strict.allowUnsupportedCarpetState);
        compareFakePlayers(report, server, expectedFakePlayers,
                strict == null || strict.allowUnsupportedCarpetState);
        report.add(RecoveryEquivalenceReport.checkpointIntegrity(expected));
        Set<String> allowedUnsupported = new LinkedHashSet<>();
        if (strict != null && strict.allowUnsupportedRuntimeState) {
            // Only opaque runtime observations are covered by this switch. A mismatch, a
            // missing old-format field, or a failed restore remains blocking.
            allowedUnsupported.add(EquivalenceCategory.WORLD_RNG);
            if (!strict.requireEntityOrder) allowedUnsupported.add(EquivalenceCategory.ENTITIES);
            if (!strict.requireBlockEntityTickerOrder) {
                allowedUnsupported.add(EquivalenceCategory.BLOCK_ENTITY_TICKERS);
            }
            if (!strict.requireTicketLedger) allowedUnsupported.add(EquivalenceCategory.TICKET_LEDGER);
        }
        if (strict != null && strict.allowUnsupportedCarpetState) {
            allowedUnsupported.add(EquivalenceCategory.CARPET);
            allowedUnsupported.add(EquivalenceCategory.FAKEPLAYERS);
        }
        report.finalizeResult(allowedUnsupported);
        return report;
    }

    private static void addComparison(RecoveryEquivalenceReport report, String category,
                                      List<String> expected, List<String> actual, boolean blocking) {
        report.add(RecoveryEquivalenceReport.compare(category, expected, actual, blocking));
    }

    private static void compareCarpet(RecoveryEquivalenceReport report, RuntimeCapsule expected,
                                      RuntimeCapsule actual, boolean allowUnsupported) {
        String expectedStatus = expected.carpetCaptureStatus;
        if ("NOT_APPLICABLE".equals(expectedStatus)) {
            if ("NOT_APPLICABLE".equals(actual.carpetCaptureStatus)) {
                report.add(RecoveryEquivalenceReport.notApplicable(EquivalenceCategory.CARPET));
            } else {
                report.add(RecoveryEquivalenceReport.compare(EquivalenceCategory.CARPET,
                        List.of("NOT_APPLICABLE"), List.of(actual.carpetCaptureStatus), true));
            }
            return;
        }
        if (!"CAPTURED".equals(expectedStatus) || expected.carpetTickSpeed == null) {
            report.add(RecoveryEquivalenceReport.unsupported(EquivalenceCategory.CARPET, !allowUnsupported,
                    "Carpet state capture was " + expectedStatus));
            return;
        }
        if (!"CAPTURED".equals(actual.carpetCaptureStatus) || actual.carpetTickSpeed == null) {
            report.add(RecoveryEquivalenceReport.unsupported(EquivalenceCategory.CARPET, !allowUnsupported,
                    "Carpet state is unavailable after restore"));
            return;
        }
        if (expected.carpetTickSpeed.timeWarpActive || actual.carpetTickSpeed.timeWarpActive) {
            report.add(RecoveryEquivalenceReport.unsupported(EquivalenceCategory.CARPET,
                    !allowUnsupported,
                    "Carpet time-warp references/callbacks cannot be safely rebound to a new server"));
            return;
        }
        addComparison(report, EquivalenceCategory.CARPET,
                RuntimeFingerprint.carpetLines(expected.carpetTickSpeed),
                RuntimeFingerprint.carpetLines(actual.carpetTickSpeed), true);
    }

    private void compareFakePlayers(RecoveryEquivalenceReport report, MinecraftServer server,
                                    List<FakePlayerSnapshot> expected, boolean allowUnsupported) {
        List<FakePlayerSnapshot> actual;
        try {
            AntiCrashConfig comparisonConfig = config == null ? new AntiCrashConfig() : config;
            actual = CarpetIntegration.captureForVerification(server, comparisonConfig, logger);
        } catch (IOException | RuntimeException exception) {
            report.add(RecoveryEquivalenceReport.unsupported(EquivalenceCategory.FAKEPLAYERS,
                    !allowUnsupported, "FakePlayer capture failed: " + exception.getMessage()));
            return;
        }
        addComparison(report, EquivalenceCategory.FAKEPLAYERS,
                RuntimeFingerprint.fakePlayerLines(expected), RuntimeFingerprint.fakePlayerLines(actual), true);
    }

    /** Strict preflight used before recovery starts; absent v6 fields are never MATCH. */
    public static List<String> strictMissingFields(RuntimeCapsule capsule) {
        return strictMissingFields(capsule, null);
    }

    /** Strict missing-state check with policy-dependent optional requirements. */
    public static List<String> strictMissingFields(RuntimeCapsule capsule,
                                                   AntiCrashConfig.Strict strict) {
        List<String> missing = new ArrayList<>();
        if (capsule == null) {
            missing.add("RUNTIME_CAPSULE");
            return missing;
        }
        if (capsule.formatVersion < 6) {
            missing.add("RUNTIME_CAPSULE_V6");
            return missing;
        }
        if (capsule.expectedFingerprints == null) {
            missing.add("EXPECTED_FINGERPRINTS");
        } else {
            for (String category : List.of(EquivalenceCategory.CHUNK_DOMAIN,
                    EquivalenceCategory.BLOCK_EVENTS, EquivalenceCategory.SCHEDULED_TICKS,
                    EquivalenceCategory.MOVING_PISTONS, EquivalenceCategory.BLOCK_ENTITY_TICKERS,
                    EquivalenceCategory.ENTITIES, EquivalenceCategory.REDSTONE_RUNTIME,
                    EquivalenceCategory.WORLD_RUNTIME, EquivalenceCategory.WORLD_RNG,
                    EquivalenceCategory.CARPET,
                    EquivalenceCategory.TICKET_LEDGER, EquivalenceCategory.FAKEPLAYERS)) {
                String fingerprint = capsule.expectedFingerprints.get(category);
                if (fingerprint == null || fingerprint.isBlank()) {
                    missing.add("FINGERPRINT_" + category);
                }
            }
        }
        if (capsule.carpetCaptureStatus == null) missing.add("CARPET_CAPTURE_STATUS");
        if ("CAPTURED".equals(capsule.carpetCaptureStatus)
                && capsule.carpetTickSpeed == null) missing.add("CARPET_TICKSPEED");
        if (capsule.unsupportedRuntimeState == null) missing.add("UNSUPPORTED_RUNTIME_STATE");
        if (capsule.entityOrder == null) missing.add("ENTITY_ORDER");
        if (capsule.blockEntityTickerOrder == null) missing.add("BLOCK_ENTITY_TICKER_ORDER");
        if (capsule.redstoneTorchBurnout == null) missing.add("REDSTONE_BURNOUT");
        if (capsule.ticketLedger == null) missing.add("TICKET_LEDGER");
        if (strict != null && strict.players != null && strict.players.requireCheckpointPlayers) {
            if (!capsule.checkpointPlayersCaptured || capsule.checkpointPlayers == null) {
                missing.add("CHECKPOINT_PLAYERS");
            } else if (capsule.expectedFingerprints == null
                    || capsule.expectedFingerprints.get(EquivalenceCategory.PLAYERS) == null) {
                missing.add("FINGERPRINT_" + EquivalenceCategory.PLAYERS);
            }
        }
        if (capsule.scheduledTicks == null) missing.add("SCHEDULED_TICKS");
        if (capsule.movingPistonTickOrder == null) missing.add("MOVING_PISTONS");
        if (capsule.worlds == null) missing.add("WORLD_RUNTIME");
        if (capsule.chunks == null) {
            missing.add("CHUNK_DOMAIN");
        } else {
            for (ChunkStateSnapshot snapshot : capsule.chunks) {
                if (snapshot == null || snapshot.formatVersion < 3 || snapshot.domain == null) {
                    missing.add("CHUNK_DOMAIN");
                    break;
                }
            }
        }
        return List.copyOf(missing);
    }

    /**
     * Returns state that would make a STRICT checkpoint incomplete under the active policy.
     * Capturing an incomplete capsule and labeling it COMPLETE would only postpone the failure
     * until the next JVM, so CheckpointManager rejects it before publishing the checkpoint.
     */
    public static List<String> strictCaptureBlockers(RuntimeCapsule capsule,
                                                      AntiCrashConfig.Strict strict) {
        List<String> blockers = new ArrayList<>(strictMissingFields(capsule, strict));
        if (capsule == null || strict == null) return List.copyOf(blockers);
        if (capsule.unsupportedRuntimeState != null) {
            for (String unsupported : capsule.unsupportedRuntimeState) {
                if (unsupported == null || unsupported.isBlank()) continue;
                boolean carpetState = unsupported != null && unsupported.startsWith("CARPET_");
                boolean allowed = carpetState
                        ? strict.allowUnsupportedCarpetState
                        : strict.allowUnsupportedRuntimeState;
                if (!allowed && !blockers.contains(unsupported)) blockers.add(unsupported);
            }
        }
        if (!strict.allowUnsupportedCarpetState
                && capsule.carpetCaptureStatus != null
                && !Set.of("CAPTURED", "NOT_APPLICABLE").contains(capsule.carpetCaptureStatus)) {
            blockers.add("CARPET_" + capsule.carpetCaptureStatus);
        }
        return List.copyOf(blockers);
    }

    private static void validateDomain(ChunkStateSnapshot snapshot) {
        Set<Long> unique = new HashSet<>();
        Set<Long> loaded = new HashSet<>();
        Set<Long> full = new HashSet<>();
        Set<Long> block = new HashSet<>();
        Set<Long> entity = new HashSet<>();
        Set<Long> forced = new HashSet<>();
        for (ChunkStateSnapshot.ChunkReadiness row : snapshot.domain) {
            if (row == null || !unique.add(row.packedPosition) || row.level < -1 || row.level > 64
                    || row.completedLevel < -1 || row.completedLevel > 64
                    || row.levelType == null || row.source == null || row.source.length() > 128
                    || !Set.of("INACCESSIBLE", "BORDER", "TICKING", "ENTITY_TICKING")
                    .contains(row.levelType)) {
                throw new IllegalStateException("Runtime capsule contains an invalid chunk-domain row");
            }
            if (row.loaded) loaded.add(row.packedPosition);
            if (row.full) full.add(row.packedPosition);
            if (row.blockTicking) block.add(row.packedPosition);
            if (row.entityTicking) entity.add(row.packedPosition);
            if (row.forced) forced.add(row.packedPosition);
            // Entity-ticking without block-ticking is a real, if short, Vanilla state: the two
            // readiness futures of a chunk that is being promoted complete independently, and the
            // server-side ticking check can lag one of them. Rejecting it failed the whole
            // checkpoint. Recovery prepares such a chunk at the entity-ticking level, which makes
            // it both.
            if (row.blockTicking && !row.full) {
                throw new IllegalStateException("Block-ticking chunk is not FULL");
            }
        }
        if (!loaded.equals(new HashSet<>(snapshot.loadedChunks))
                || !full.equals(new HashSet<>(snapshot.fullChunks))
                || !block.equals(new HashSet<>(snapshot.blockTickingChunks))
                || !entity.equals(new HashSet<>(snapshot.entityTickingChunks))
                || !forced.equals(new HashSet<>(snapshot.forcedChunks))) {
            throw new IllegalStateException("Runtime capsule chunk-domain sets do not match their rows");
        }
    }

    private static void validateV6State(RuntimeCapsule capsule, Set<String> dimensions) {
        if (capsule.fidelity == null
                || !Set.of("STRICT", "OPTIMIZED").contains(capsule.fidelity)
                || capsule.carpetCaptureStatus == null
                || !Set.of("CAPTURED", "NOT_APPLICABLE", "INCOMPLETE", "UNSUPPORTED")
                .contains(capsule.carpetCaptureStatus)
                || ("CAPTURED".equals(capsule.carpetCaptureStatus)
                && capsule.carpetTickSpeed == null)
                || ("NOT_APPLICABLE".equals(capsule.carpetCaptureStatus)
                && capsule.carpetTickSpeed != null)
                || capsule.entityOrder == null || capsule.entityOrder.size() > 1_000_000
                || capsule.blockEntityTickerOrder == null
                || capsule.blockEntityTickerOrder.size() > 1_000_000
                || capsule.redstoneTorchBurnout == null
                || capsule.redstoneTorchBurnout.size() > 10_000
                || capsule.ticketLedger == null || capsule.ticketLedger.size() > 1_000_000
                || capsule.checkpointPlayers == null
                || capsule.checkpointPlayers.size() > 100_000
                || (!capsule.checkpointPlayersCaptured && !capsule.checkpointPlayers.isEmpty())
                || capsule.unsupportedRuntimeState == null
                || capsule.unsupportedRuntimeState.size() > 10_000
                || capsule.expectedFingerprints == null
                || capsule.expectedFingerprints.size() > 64) {
            throw new IllegalStateException("Runtime capsule v6 state is incomplete");
        }
        for (String unsupported : capsule.unsupportedRuntimeState) {
            if (unsupported == null || unsupported.isBlank() || unsupported.length() > 256) {
                throw new IllegalStateException("Runtime capsule contains an invalid unsupported-state marker");
            }
        }
        for (Map.Entry<String, String> fingerprint : capsule.expectedFingerprints.entrySet()) {
            if (fingerprint.getKey() == null || fingerprint.getKey().length() > 128
                    || fingerprint.getValue() == null
                    || !fingerprint.getValue().matches("[0-9a-fA-F]{64}")) {
                throw new IllegalStateException("Runtime capsule contains an invalid fingerprint");
            }
        }
        String checkpointPlayerFingerprint = capsule.expectedFingerprints.get(
                EquivalenceCategory.PLAYERS);
        if (capsule.checkpointPlayersCaptured) {
            if (checkpointPlayerFingerprint == null
                    || !checkpointPlayerFingerprint.equals(RuntimeFingerprint.hashLines(
                    RuntimeFingerprint.checkpointPlayerLines(capsule.checkpointPlayers)))) {
                throw new IllegalStateException(
                        "Runtime capsule checkpoint player fingerprint is missing or invalid");
            }
        } else if (checkpointPlayerFingerprint != null) {
            throw new IllegalStateException(
                    "Runtime capsule has an unmarked checkpoint player fingerprint");
        }
        Set<UUID> entities = new HashSet<>();
        Map<String, Integer> nextEntityOrder = new HashMap<>();
        for (EntityRuntimeSnapshot entity : capsule.entityOrder) {
            if (entity == null || entity.dimension == null || !dimensions.contains(entity.dimension)
                    || entity.uuid == null || entity.type == null || entity.type.length() > 512
                    || entity.order < 0
                    || entity.order != nextEntityOrder.getOrDefault(entity.dimension, 0)) {
                throw new IllegalStateException("Runtime capsule contains an invalid entity order row");
            }
            nextEntityOrder.merge(entity.dimension, 1, Integer::sum);
            try {
                if (!entities.add(UUID.fromString(entity.uuid))) {
                    throw new IllegalStateException("Runtime capsule contains duplicate entity order rows");
                }
            } catch (RuntimeException exception) {
                if (exception instanceof IllegalStateException state) throw state;
                throw new IllegalStateException("Runtime capsule contains an invalid entity UUID");
            }
        }
        Set<String> tickers = new HashSet<>();
        Map<String, Integer> nextTickerOrder = new HashMap<>();
        for (BlockEntityTickerSnapshot ticker : capsule.blockEntityTickerOrder) {
            if (ticker == null || ticker.dimension == null || !dimensions.contains(ticker.dimension)
                    || ticker.queue == null || (!BlockEntityTickerSnapshot.CURRENT.equals(ticker.queue)
                    && !BlockEntityTickerSnapshot.PENDING.equals(ticker.queue))
                    || ticker.type == null || ticker.type.length() > 512
                    || ticker.name == null || ticker.name.length() > 512 || ticker.order < 0
                    || ticker.order != nextTickerOrder.getOrDefault(
                    ticker.dimension + "|" + ticker.queue, 0)
                    || !tickers.add(ticker.dimension + "|" + ticker.key())) {
                throw new IllegalStateException("Runtime capsule contains an invalid ticker order row");
            }
            nextTickerOrder.merge(ticker.dimension + "|" + ticker.queue, 1, Integer::sum);
        }
        Set<String> burnoutDimensions = new HashSet<>();
        for (RedstoneTorchBurnoutSnapshot burnout : capsule.redstoneTorchBurnout) {
            if (burnout == null || burnout.dimension == null || !dimensions.contains(burnout.dimension)
                    || !burnoutDimensions.add(burnout.dimension) || burnout.entries == null
                    || burnout.entries.size() > 100_000) {
                throw new IllegalStateException("Runtime capsule contains an invalid torch burnout state");
            }
            for (RedstoneTorchBurnoutSnapshot.Entry entry : burnout.entries) {
                if (entry == null) throw new IllegalStateException(
                        "Runtime capsule contains an empty torch burnout entry");
            }
        }
        Set<String> tickets = new HashSet<>();
        for (TicketLedgerSnapshot.Entry ticket : capsule.ticketLedger) {
            if (ticket == null || ticket.dimension == null || !dimensions.contains(ticket.dimension)
                    || ticket.type == null || ticket.type.length() > 512
                    || ticket.argument == null || ticket.argument.length() > 2048
                    || ticket.level < 0 || ticket.level > 64 || ticket.expiryTicks < -1
                    || !tickets.add(ticket.canonical())) {
                throw new IllegalStateException("Runtime capsule contains an invalid ticket ledger row");
            }
        }
        if (capsule.checkpointPlayersCaptured) {
            Set<UUID> players = new HashSet<>();
            for (CheckpointPlayerSnapshot player : capsule.checkpointPlayers) {
                if (player == null || player.formatVersion != 1 || player.uuid == null
                        || player.name == null || player.name.isBlank() || player.name.length() > 256
                        || player.dimension == null || player.dimension.isBlank()
                        || player.dimension.length() > 512) {
                    throw new IllegalStateException(
                            "Runtime capsule contains an invalid checkpoint player identity");
                }
                try {
                    if (!players.add(UUID.fromString(player.uuid))) {
                        throw new IllegalStateException(
                                "Runtime capsule contains duplicate checkpoint player identities");
                    }
                } catch (RuntimeException exception) {
                    if (exception instanceof IllegalStateException state) throw state;
                    throw new IllegalStateException(
                            "Runtime capsule contains an invalid checkpoint player UUID");
                }
            }
        }
    }

    public static void validate(RuntimeCapsule capsule) {
        if (capsule == null || !RuntimeCapsule.supportsFormatVersion(capsule.formatVersion)) {
            throw new IllegalStateException("Runtime capsule has an unsupported format");
        }
        if (capsule.blockEvents == null || capsule.blockEvents.formatVersion != 1
                || capsule.blockEvents.entries == null || capsule.blockEvents.entries.size() > 1_000_000) {
            throw new IllegalStateException("Runtime capsule is missing valid block events");
        }
        if (capsule.chunks == null || capsule.worlds == null) throw new IllegalStateException("Runtime capsule is incomplete");
        Set<String> dimensions = new HashSet<>();
        Map<String, ChunkStateSnapshot> chunksByDimension = new HashMap<>();
        long listedChunkPositions = 0;
        for (com.atsukimc.anticrash.runtime.ChunkStateSnapshot chunks : capsule.chunks) {
            int expectedChunkVersion = capsule.formatVersion == 1 ? 1
                    : capsule.formatVersion >= 6 ? 3 : 2;
            if (chunks == null || chunks.formatVersion != expectedChunkVersion
                    || chunks.dimension == null
                    || chunks.dimension.isBlank() || chunks.dimension.length() > 512
                    || chunks.loadedChunks == null || chunks.entityTickingChunks == null
                    || chunks.forcedChunks == null
                    || (capsule.formatVersion >= 2 && chunks.transientStateChunks == null)
                    || (capsule.formatVersion >= 6 && (chunks.fullChunks == null
                    || chunks.blockTickingChunks == null || chunks.domain == null))) {
                throw new IllegalStateException("Runtime capsule contains an invalid chunk snapshot");
            }
            if (!dimensions.add(chunks.dimension)) throw new IllegalStateException("Runtime capsule contains duplicate dimension chunks: " + chunks.dimension);
            rejectNullOrDuplicate(chunks.loadedChunks, "loaded", chunks.dimension);
            rejectNullOrDuplicate(chunks.entityTickingChunks, "entity-ticking", chunks.dimension);
            rejectNullOrDuplicate(chunks.forcedChunks, "forced", chunks.dimension);
            if (capsule.formatVersion >= 2) {
                rejectNullOrDuplicate(chunks.transientStateChunks, "transient-state",
                        chunks.dimension);
            }
            if (capsule.formatVersion >= 6) {
                rejectNullOrDuplicate(chunks.fullChunks, "full", chunks.dimension);
                rejectNullOrDuplicate(chunks.blockTickingChunks, "block-ticking", chunks.dimension);
                validateDomain(chunks);
            }
            listedChunkPositions += (long) chunks.loadedChunks.size()
                    + chunks.entityTickingChunks.size() + chunks.forcedChunks.size()
                    + (capsule.formatVersion >= 2 ? chunks.transientStateChunks.size() : 0)
                    + (capsule.formatVersion >= 6 ? chunks.fullChunks.size()
                    + chunks.blockTickingChunks.size() + chunks.domain.size() : 0);
            if (listedChunkPositions > 300_000L) {
                throw new IllegalStateException("Runtime capsule contains too many chunk positions");
            }
            chunksByDimension.put(chunks.dimension, chunks);
        }
        Set<String> worldDimensions = new HashSet<>();
        for (RuntimeCapsule.WorldRuntime world : capsule.worlds) {
            if (world == null || world.dimension == null || world.dimension.isBlank()
                    || world.dimension.length() > 512 || world.loadedChunkCount < 0
                    || world.entityTickingChunkCount < 0 || !worldDimensions.add(world.dimension)) {
                throw new IllegalStateException("Runtime capsule contains invalid world metadata");
            }
            ChunkStateSnapshot chunks = chunksByDimension.get(world.dimension);
            if (chunks == null || world.loadedChunkCount != chunks.loadedCount()
                    || world.entityTickingChunkCount != chunks.entityTickingCount()) {
                throw new IllegalStateException("Runtime capsule world/chunk counters do not match: " + world.dimension);
            }
            if (capsule.formatVersion >= 6 && (world.fullChunkCount != chunks.fullCount()
                    || world.blockTickingChunkCount != chunks.blockTickingCount()
                    || world.forcedChunkCount != chunks.forcedCount())) {
                throw new IllegalStateException("Runtime capsule exact chunk counters do not match: "
                        + world.dimension);
            }
        }
        if (worldDimensions.isEmpty() || !worldDimensions.equals(dimensions)) {
            throw new IllegalStateException("Runtime capsule dimensions do not match");
        }
        if (capsule.formatVersion >= 3) {
            validateScheduledTicks(capsule, dimensions, chunksByDimension);
            validateMovingPistonOrder(capsule, dimensions, chunksByDimension);
        }
        if (capsule.formatVersion >= 4) validateNextTickOrder(capsule);
        if (capsule.carpetTickSpeed != null
                && (capsule.carpetTickSpeed.formatVersion < 1
                || capsule.carpetTickSpeed.formatVersion > 2
                || !Float.isFinite(capsule.carpetTickSpeed.tickrate)
                || capsule.carpetTickSpeed.tickrate <= 0.0F
                || (capsule.carpetTickSpeed.formatVersion >= 2
                && (!Float.isFinite(capsule.carpetTickSpeed.mspt)
                || capsule.carpetTickSpeed.mspt <= 0.0F
                || capsule.carpetTickSpeed.playerActiveTimeout < 0)))) {
            throw new IllegalStateException("Runtime capsule contains an invalid Carpet tick speed");
        }
        if (capsule.formatVersion >= 4) {
            validateTransientEntityOrder(capsule, dimensions, chunksByDimension);
        }
        if (capsule.formatVersion >= 6) validateV6State(capsule, dimensions);
    }

    private static void validateTransientEntityOrder(RuntimeCapsule capsule, Set<String> dimensions,
                                                     Map<String, ChunkStateSnapshot> chunksByDimension) {
        if (capsule.transientEntityTickOrder == null
                || capsule.transientEntityTickOrder.size() > 100_000) {
            throw new IllegalStateException("Runtime capsule contains too many transient entities");
        }
        Set<UUID> unique = new HashSet<>();
        for (TransientEntityTickSnapshot entity : capsule.transientEntityTickOrder) {
            UUID uuid;
            if (entity == null || entity.dimension == null || !dimensions.contains(entity.dimension)
                    || (!TransientEntityTickSnapshot.TNT.equals(entity.kind)
                    && !TransientEntityTickSnapshot.FALLING_BLOCK.equals(entity.kind))) {
                throw new IllegalStateException("Runtime capsule contains an invalid transient entity");
            }
            try {
                uuid = UUID.fromString(entity.uuid);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Runtime capsule contains an invalid transient entity UUID");
            }
            long packed = new ChunkPos(entity.chunkX, entity.chunkZ).toLong();
            ChunkStateSnapshot chunks = chunksByDimension.get(entity.dimension);
            if (!unique.add(uuid) || chunks == null
                    || !chunks.entityTickingChunks.contains(packed)
                    || (capsule.formatVersion < 6
                    && !chunks.transientStateChunks.contains(packed))) {
                throw new IllegalStateException("Runtime capsule contains a duplicate or unready transient entity");
            }
        }
    }

    private static void validateNextTickOrder(RuntimeCapsule capsule) {
        Map<String, Long> nextByDimension = new HashMap<>();
        for (RuntimeCapsule.WorldRuntime world : capsule.worlds) {
            nextByDimension.put(world.dimension, world.nextTickOrder);
        }
        for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
            Long next = nextByDimension.get(tick.dimension);
            if (next == null || next <= tick.subTickOrder) {
                throw new IllegalStateException("Runtime capsule scheduled-tick counter overlaps "
                        + "an existing tick in " + tick.dimension);
            }
        }
    }

    private static void validateMovingPistonOrder(RuntimeCapsule capsule, Set<String> dimensions,
                                                  Map<String, ChunkStateSnapshot> chunksByDimension) {
        if (capsule.movingPistonTickOrder == null
                || capsule.movingPistonTickOrder.size() > 100_000) {
            throw new IllegalStateException("Runtime capsule contains too many moving pistons");
        }
        Set<PistonPositionKey> unique = new HashSet<>();
        for (MovingPistonTickSnapshot piston : capsule.movingPistonTickOrder) {
            if (piston == null || piston.dimension == null
                    || !dimensions.contains(piston.dimension)) {
                throw new IllegalStateException("Runtime capsule contains an invalid moving piston");
            }
            long packed = new ChunkPos(piston.x >> 4, piston.z >> 4).toLong();
            ChunkStateSnapshot chunks = chunksByDimension.get(piston.dimension);
            // A piston can be caught mid-move in a chunk that is loaded but no longer ticking
            // (the player moved away). Vanilla keeps it frozen there and finishes the move when
            // the chunk ticks again, so that is a state to record, not an invalid capsule.
            boolean chunkReady = chunks != null && (capsule.formatVersion >= 6
                    ? chunks.blockTickingChunks.contains(packed) || chunks.loadedChunks.contains(packed)
                    : chunks.entityTickingChunks.contains(packed));
            if (!chunkReady
                    || (capsule.formatVersion < 6 && !chunks.transientStateChunks.contains(packed))
                    || !unique.add(new PistonPositionKey(
                    piston.dimension, piston.x, piston.y, piston.z))) {
                throw new IllegalStateException("Runtime capsule contains a duplicate or unready moving piston");
            }
        }
    }

    private static void validateScheduledTicks(RuntimeCapsule capsule, Set<String> dimensions,
                                               Map<String, ChunkStateSnapshot> chunksByDimension) {
        if (capsule.scheduledTicks == null || capsule.scheduledTicks.size() > 100_000) {
            throw new IllegalStateException("Runtime capsule contains too many scheduled ticks");
        }
        Set<ScheduledTickKey> unique = new HashSet<>();
        for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
            Identifier id;
            if (tick == null || tick.dimension == null || !dimensions.contains(tick.dimension)
                    || (!ScheduledTickSnapshot.BLOCK.equals(tick.kind)
                    && !ScheduledTickSnapshot.FLUID.equals(tick.kind))
                    || tick.type == null || tick.type.length() > 512
                    || (id = Identifier.tryParse(tick.type)) == null
                    || tick.priority < TickPriorityBounds.MIN
                    || tick.priority > TickPriorityBounds.MAX) {
                throw new IllegalStateException("Runtime capsule contains an invalid scheduled tick");
            }
            ChunkStateSnapshot chunks = chunksByDimension.get(tick.dimension);
            long packed = new ChunkPos(tick.x >> 4, tick.z >> 4).toLong();
            boolean recoveryReady = chunks != null
                    && (capsule.formatVersion >= 6
                    ? chunks.fullChunks.contains(packed)
                    : chunks.entityTickingChunks.contains(packed))
                    && (capsule.formatVersion >= 6
                    || chunks.transientStateChunks.contains(packed));
            if (!unique.add(new ScheduledTickKey(tick.dimension, tick.kind,
                    tick.type, tick.x, tick.y, tick.z)) || !recoveryReady) {
                throw new IllegalStateException("Runtime capsule contains an unknown or duplicate scheduled tick");
            }
        }
    }

    private static void restoreBlockQueue(ServerWorld world, WorldChunk chunk,
                                          List<ScheduledTickSnapshot> ticks) {
        ChunkPos pos = chunk.getPos();
        ChunkTickScheduler<Block> scheduler = ScheduledTickSnapshot.blockScheduler(chunk);
        world.getBlockTickScheduler().removeChunkTickScheduler(pos);
        scheduler.removeTicksIf(ignored -> true);
        for (ScheduledTickSnapshot tick : ticks) {
            Block type = Registry.BLOCK.get(tick.typeIdentifier());
            scheduler.scheduleTick(new OrderedTick<>(type, tick.position(), tick.triggerTick,
                    tick.tickPriority(), tick.subTickOrder));
        }
        world.getBlockTickScheduler().addChunkTickScheduler(pos, scheduler);
    }

    private static void restoreFluidQueue(ServerWorld world, WorldChunk chunk,
                                          List<ScheduledTickSnapshot> ticks) {
        ChunkPos pos = chunk.getPos();
        ChunkTickScheduler<Fluid> scheduler = ScheduledTickSnapshot.fluidScheduler(chunk);
        world.getFluidTickScheduler().removeChunkTickScheduler(pos);
        scheduler.removeTicksIf(ignored -> true);
        for (ScheduledTickSnapshot tick : ticks) {
            Fluid type = Registry.FLUID.get(tick.typeIdentifier());
            scheduler.scheduleTick(new OrderedTick<>(type, tick.position(), tick.triggerTick,
                    tick.tickPriority(), tick.subTickOrder));
        }
        world.getFluidTickScheduler().addChunkTickScheduler(pos, scheduler);
    }

    private record QueueKey(String dimension, String kind, long packedChunkPosition) { }
    private record QueueChunkKey(String dimension, long packedChunkPosition) { }
    private record PreparedQueue(ServerWorld world, WorldChunk chunk, String kind,
                                 List<ScheduledTickSnapshot> ticks) { }
    private record ScheduledTickKey(String dimension, String kind, String type,
                                    int x, int y, int z) { }
    private record PistonPositionKey(String dimension, int x, int y, int z) { }
    private record PreparedPistonOrder(List<BlockEntityTickInvoker> tickers, List<Integer> slots,
                                       List<BlockEntityTickInvoker> ordered) { }
    private record PreparedTickerOrder(List<BlockEntityTickInvoker> live,
                                       List<BlockEntityTickInvoker> ordered) { }
    private static final class TickPriorityBounds {
        private static final int MIN = -3;
        private static final int MAX = 3;
    }

    public static final class ScheduledTickRestoreResult {
        public int restored;
        public int queues;
    }

    private static void rejectNullOrDuplicate(List<Long> positions, String kind, String dimension) {
        Set<Long> unique = new HashSet<>();
        for (Long position : positions) {
            if (position == null || !unique.add(position)) {
                throw new IllegalStateException("Runtime capsule contains invalid " + kind
                        + " chunk positions: " + dimension);
            }
        }
    }
}
