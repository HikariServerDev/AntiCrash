package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.mixin.ServerChunkManagerAccessorMixin;
import com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Prepares only a bounded set of recovery chunks while the server remains frozen.
 *
 * <p>World files contain the persistent chunk data. A checkpoint's loaded and entity-ticking
 * lists describe the old JVM's cache and ticket graph; keeping the whole graph resident during
 * startup duplicates Vanilla's normal loading and caused the observed 1 GiB heap exhaustion.
 * The plan is therefore selected before any ticket is added, and the default selects only
 * critical anchors.</p>
 */
public final class ChunkRecoveryManager {
    // An ephemeral, non-persisted type, distinct from Vanilla's finite spawn-search tickets;
    // only public ticket APIs are used, and every abort/completion explicitly releases it.
    public static final ChunkTicketType RECOVERY_TICKET = new ChunkTicketType(
            Long.MAX_VALUE, ChunkTicketType.FOR_LOADING | ChunkTicketType.FOR_SIMULATION);
    private static final long MAX_POLL_NANOS = 20_000_000L;
    private static final long PROGRESS_LOG_NANOS = 5_000_000_000L;
    private static final long MIN_HEAP_HEADROOM_BYTES = 64L * 1024L * 1024L;
    private static final int RECOVERY_OOM_EXIT_CODE = 137;
    private static final OutOfMemoryError MEMORY_FAILURE = new OutOfMemoryError(
            "Insufficient heap while preparing recovery chunks");
    private static final OutOfMemoryError TRACKING_MEMORY_FAILURE = new OutOfMemoryError(
            "Insufficient heap for recovery ticket tracking");

    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;
    private HeapGuard heapGuard = new HeapGuard(MIN_HEAP_HEADROOM_BYTES);
    private boolean lowMemoryReported;
    private final ArrayList<Ticket> tickets = new ArrayList<>();
    private final ArrayDeque<PendingChunk> pending = new ArrayDeque<>();
    private final Map<PendingChunk, InFlight> inFlight = new LinkedHashMap<>();
    private final Set<ServerWorld> recoveryWorlds = new LinkedHashSet<>();
    private int expected;
    private int ready;
    private int failed;
    private int deferredLoaded;
    private int deferredEntityTicking;
    private int releaseCountdown;
    private boolean preparing;
    private long lastProgressLogNanos;
    private long lastProgressNanos;
    private long progressSignature;
    private boolean limitExceeded;
    private String blockingReason;
    private boolean strict;

    public ChunkRecoveryManager(AntiCrashConfig config, AntiCrashLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Creates the recovery queue. Chunk loading itself is deliberately spread across server
     * ticks: loading every retained chunk synchronously during loadWorld can exhaust a small JVM
     * heap before Minecraft has a chance to release startup allocations.
     */
    public void begin(MinecraftServer server, RuntimeCapsule capsule,
                      Set<ChunkRecoveryPlan.Anchor> anchors) throws IOException {
        if (!tickets.isEmpty()) throw new IOException("Recovery chunk tickets are already active");
        pending.clear();
        inFlight.clear();
        recoveryWorlds.clear();
        expected = 0;
        ready = 0;
        failed = 0;
        deferredLoaded = 0;
        deferredEntityTicking = 0;
        releaseCountdown = 0;
        limitExceeded = false;
        blockingReason = null;
        heapGuard = new HeapGuard(MIN_HEAP_HEADROOM_BYTES);
        lowMemoryReported = false;
        strict = "STRICT".equalsIgnoreCase(config.recovery.fidelity);

        ChunkRecoveryPlan.Selection selection;
        try {
            selection = strict
                    ? ChunkRecoveryPlan.selectStrict(capsule, config.recovery.maxPreloadChunks)
                    : ChunkRecoveryPlan.select(capsule,
                    ChunkRecoveryPlan.PreloadMode.parse(config.recovery.preloadMode),
                    anchors, config.recovery.maxPreloadChunks);
        } catch (OutOfMemoryError error) {
            // Selection is performed before any ticket is installed. Still terminate explicitly so
            // a recovery OOM is visible to a foreground Supervisor as a failed child process.
            terminateAfterOutOfMemory(error);
            throw error;
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        if (strict && selection.limitExceeded()) {
            limitExceeded = true;
            expected = selection.requiredCount();
            blockingReason = "STRICT recovery requires " + selection.requiredCount()
                    + " chunks but max_preload_chunks is " + config.recovery.maxPreloadChunks;
            logger.importantKey("chunk.strict_limit_exceeded", selection.requiredCount(),
                    config.recovery.maxPreloadChunks);
            throw new IOException(blockingReason);
        }
        expected = selection.entries().size();
        // Reserve the tracking array before adding any Minecraft ticket. If the heap is already
        // exhausted, begin() fails before an untracked ticket can be installed.
        try {
            tickets.ensureCapacity(expected);
        } catch (OutOfMemoryError error) {
            terminateAfterOutOfMemory(TRACKING_MEMORY_FAILURE);
            throw TRACKING_MEMORY_FAILURE; // Runtime.halt() does not return on a normal JVM.
        }
        deferredLoaded = selection.deferredLoaded();
        deferredEntityTicking = selection.deferredEntityTicking();

        Map<String, ServerWorld> worlds = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.put(world.getRegistryKey().getValue().toString(), world);
        }
        for (ChunkRecoveryPlan.Entry entry : selection.entries()) {
            ServerWorld world = worlds.get(entry.dimension());
            if (world == null) {
                failed++;
            } else {
                pending.addLast(new PendingChunk(world, entry));
                recoveryWorlds.add(world);
            }
        }
        preparing = !pending.isEmpty();
        lastProgressLogNanos = System.nanoTime();
        lastProgressNanos = lastProgressLogNanos;
        progressSignature = progressSignature();
        logger.normalKey("chunk.preparing", expected, deferredLoaded, deferredEntityTicking, failed);
        if (!preparing) logReady();
    }

    /**
     * Requests a bounded number of asynchronous FULL futures while the world stays frozen.
     * Neither this method nor any helper waits on a chunk future; completion is observed on a
     * later server-loop pass after Vanilla's chunk/storage lifecycle has advanced.
     */
    public void poll() throws IOException {
        try {
            pollInternal();
        } catch (OutOfMemoryError error) {
            // OOM can be raised by any part of the asynchronous lifecycle (holder updates,
            // readiness inspection or diagnostic allocation), not only by the initial request.
            // Release the recovery-only graph before making the child process fail visibly to a
            // foreground Supervisor.
            try {
                abort();
            } catch (Throwable ignored) {
                // A second allocation failure must not prevent the fail-fast exit below.
            }
            terminateAfterOutOfMemory(error);
            throw error; // Runtime.halt() does not return on a normal JVM.
        }
    }

    private void pollInternal() throws IOException {
        if (!preparing) return;
        long started = System.nanoTime();
        int requested = 0;
        // Once memory has been short, chunks are prepared one at a time for the rest of this
        // recovery: the same chunks end up loaded, only fewer are being read and built at once.
        int maxConcurrent = heapGuard.lowMemoryMode() ? 1
                : Math.max(1, Math.min(64, config.recovery.chunks.maxConcurrentLoads));
        while (!pending.isEmpty() && inFlight.size() < maxConcurrent
                && requested < maxConcurrent
                && (requested == 0 || System.nanoTime() - started < MAX_POLL_NANOS)) {
            HeapGuard.Decision decision = heapGuard.check(!inFlight.isEmpty());
            if (decision != HeapGuard.Decision.PROCEED && !lowMemoryReported) {
                lowMemoryReported = true;
                logger.importantKey("chunk.low_memory_mode", ready + failed, expected,
                        Runtime.getRuntime().maxMemory() / (1024L * 1024L));
            }
            if (decision == HeapGuard.Decision.EXHAUSTED) {
                long live = HeapGuard.liveBytesAfterCollection();
                logger.fatalKey("chunk.heap_exhausted", ready + failed, expected,
                        live < 0 ? "?" : Long.toString(live / (1024L * 1024L)),
                        Runtime.getRuntime().maxMemory() / (1024L * 1024L));
                abort();
                terminateAfterOutOfMemory(MEMORY_FAILURE);
                throw MEMORY_FAILURE; // Runtime.halt() does not return on a normal JVM.
            }
            if (decision == HeapGuard.Decision.WAIT) break;
            maxConcurrent = heapGuard.lowMemoryMode() ? 1 : maxConcurrent;
            try {
                PendingChunk next = pending.removeFirst();
                request(next);
            } catch (OutOfMemoryError error) {
                // The normal guard prevents this in practice. If a single chunk allocation still
                // exhausts the heap, release every ticket before failing closed so the next
                // process can start from the checkpoint instead of retaining a half-plan.
                abort();
                terminateAfterOutOfMemory(MEMORY_FAILURE);
                throw MEMORY_FAILURE; // Runtime.halt() does not return on a normal JVM.
            }
            requested++;
        }
        advanceChunkLifecycle();
        refreshTicketReadiness();
        long now = System.nanoTime();
        long signature = progressSignature();
        if (signature != progressSignature) {
            progressSignature = signature;
            lastProgressNanos = now;
        }
        if (pending.isEmpty() && inFlight.isEmpty() && ready + failed == expected) {
            preparing = false;
            logReady();
            return;
        }
        if (now - lastProgressLogNanos >= PROGRESS_LOG_NANOS) {
            lastProgressLogNanos = now;
            logger.normalKey("chunk.progress", ready + failed, expected, ready, failed);
        }
        failIfStalled(now);
    }

    /** Changes whenever a chunk is dequeued or reaches a ready/failed conclusion. */
    private long progressSignature() {
        return ((long) (ready + failed) << 32)
                ^ ((long) pending.size() << 16) ^ (inFlight.size() & 0xFFFFL);
    }

    /**
     * Recovery keeps the world frozen until every required chunk is ready, so a chunk that never
     * reaches entity-ticking readiness would otherwise freeze the server indefinitely with no
     * operator-visible conclusion. Only a complete lack of progress is treated as a stall, so a
     * large but genuinely advancing preload plan is never interrupted.
     */
    private void failIfStalled(long now) throws IOException {
        int timeoutSeconds = config.recovery.chunkReadyTimeoutSeconds;
        if (timeoutSeconds <= 0) return;
        if (now - (lastProgressNanos + timeoutSeconds * 1_000_000_000L) < 0) return;
        logger.fatalKey("chunk.ready_timeout", timeoutSeconds, ready + failed, expected,
                ready, failed, pending.size(), inFlight.size());
        throw new IOException("Recovery chunks made no progress for " + timeoutSeconds
                + " seconds: " + (ready + failed) + "/" + expected
                + ", ready=" + ready + ", failed=" + failed + ", pending=" + pending.size());
    }

    /**
     * An uncaught Error only terminates the current Minecraft thread. Recovery OOM is different:
     * it can leave worker/query threads alive while the Supervisor waits forever. All temporary
     * tickets are released by the caller first, then the JVM is halted with a non-zero code so a
     * foreground Supervisor can retry the child in the same console.
     */
    private void terminateAfterOutOfMemory(OutOfMemoryError failure) {
        try {
            logger.fatalKey("recovery.oom_exit", RECOVERY_OOM_EXIT_CODE);
        } catch (Throwable ignored) {
            // Do not let logging allocation prevent the fail-fast process termination.
        }
        try {
            Runtime.getRuntime().halt(RECOVERY_OOM_EXIT_CODE);
        } catch (SecurityException denied) {
            // A restrictive SecurityManager must not hide the original recovery failure.
            throw failure;
        }
    }

    private void request(PendingChunk pendingChunk) {
        ServerWorld world = pendingChunk.world;
        ChunkRecoveryPlan.Entry entry = pendingChunk.entry;
        ChunkPos pos = new ChunkPos(entry.packedPosition());
        Ticket ticket = null;
        try {
            // 1.21.5 takes the LEVEL directly; the pre-1.21.5 radius conversion is not applicable.
            int ticketRadius = ChunkRecoveryPlan.ticketArgumentForLevel(entry.ticketLevel());
            ticket = new Ticket(world, pos, ticketRadius, entry.ticketLevel());
            tickets.add(ticket);
            world.getChunkManager().addTicket(
                    RECOVERY_TICKET, pos, ticketRadius);
            if (entry.forced() && !world.getForcedChunks().contains(pos.toLong())) {
                // A forced chunk is persistent world state. Keep a record only for the failure
                // path: after a successful recovery this addition is part of the restored state,
                // while aborting a failed attempt must not leave an AntiCrash-only forced chunk.
                ticket.forcedAddedByRecovery = world.setChunkForced(pos.x, pos.z, true);
            }
            // The public getChunkFutureSyncOnMainThread method pumps the main-thread executor
            // until completion when called from this thread. Advance only the ticket/holder
            // lifecycle first, then invoke the private future-producing path so this request
            // returns immediately and is observed by a later poll pass.
            world.getChunkManager().tick(() -> true, false);
            CompletableFuture<net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk>> future =
                    ((ServerChunkManagerAccessorMixin) (Object) world.getChunkManager())
                            .anticrash$getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, false);
            if (future == null) throw new IOException("FULL chunk future is unavailable");
            inFlight.put(pendingChunk, new InFlight(ticket, future));
        } catch (Exception exception) {
            if (ticket != null) ticket.failed = true;
            if (ticket != null && releaseTicket(ticket, true)) tickets.remove(ticket);
            failed++;
            if (entry.forced()) {
                logger.importantKey("chunk.forced_restore_failed", pos, entry.dimension(), exception.getMessage());
            } else {
                logger.importantKey("chunk.required_not_ready", pos, exception.getMessage());
            }
        }
    }

    /**
     * The normal ServerWorld tick is deliberately cancelled while recovery is frozen. Advance
     * only Vanilla's chunk ticket/storage lifecycle, with chunk simulation disabled, so level-31
     * recovery tickets can actually reach entity-ticking readiness before the first restored
     * block/entity tick.
     */
    private void advanceChunkLifecycle() {
        for (ServerWorld world : recoveryWorlds) {
            world.getChunkManager().tick(() -> true, false);
            // Entity sections are read asynchronously and only attached by the entity manager's
            // own tick, which normally runs at the end of ServerWorld#tick. Without this pump the
            // restored entities would join one tick after the restored blocks.
            ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                    .anticrash$getEntityManager().tick();
        }
    }

    private void refreshTicketReadiness() {
        var iterator = inFlight.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PendingChunk, InFlight> entry = iterator.next();
            PendingChunk pendingChunk = entry.getKey();
            InFlight request = entry.getValue();
            if (!request.future.isDone()) continue;
            if (request.future.isCompletedExceptionally()) {
                fail(request.ticket, pendingChunk, "FULL chunk future failed");
                iterator.remove();
                continue;
            }
            net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk> result;
            try {
                result = request.future.getNow(null);
            } catch (RuntimeException exception) {
                fail(request.ticket, pendingChunk, "FULL chunk future failed: " + exception);
                iterator.remove();
                continue;
            }
            if (result == null || !result.isPresent()) {
                fail(request.ticket, pendingChunk, "FULL chunk became unavailable");
                iterator.remove();
                continue;
            }
            if (!isReady(pendingChunk, request.ticket)) continue;
            request.ticket.ready = true;
            ready++;
            iterator.remove();
        }
    }

    private void fail(Ticket ticket, PendingChunk pendingChunk, String detail) {
        if (ticket.failed || ticket.ready) return;
        ticket.failed = true;
        failed++;
        // A failed Future is no longer part of the recovery plan. Release its temporary ticket
        // immediately instead of keeping a dead holder alive until the whole plan finishes.
        // If removal itself fails, abort() will retry it and keep the failure visible.
        if (releaseTicket(ticket, true)) tickets.remove(ticket);
        logger.importantKey("chunk.required_not_ready", ticket.pos, detail);
    }

    private boolean isReady(PendingChunk pendingChunk, Ticket ticket) {
        // Every recovery ticket level tracks entities (FULL is TRACKED, 31 is TICKING). Treat the
        // chunk as prepared only once its entity section is loaded as well.
        if (!ticket.world.isChunkLoaded(ticket.pos.toLong())) return false;
        ChunkHolder holder = ((ThreadedAnvilChunkStorageAccessorMixin)
                ticket.world.getChunkManager().chunkLoadingManager)
                .anticrash$getCurrentChunkHolders().get(ticket.pos.toLong());
        if (holder == null) return false;
        if (ticket.ticketLevel == ChunkRecoveryPlan.FULL_TICKET_LEVEL) {
            return completed(holder.getAccessibleFuture());
        }
        if (ticket.ticketLevel == ChunkRecoveryPlan.BLOCK_TICKING_TICKET_LEVEL) {
            return (holder.getLevelType() == net.minecraft.server.world.ChunkLevelType.BLOCK_TICKING
                    || holder.getLevelType() == net.minecraft.server.world.ChunkLevelType.ENTITY_TICKING)
                    && completed(holder.getTickingFuture())
                    && ticket.world.getChunkManager().isTickingFutureReady(ticket.pos.toLong());
        }
        return holder.getLevelType() == net.minecraft.server.world.ChunkLevelType.ENTITY_TICKING
                && completed(holder.getEntityTickingFuture())
                && ticket.world.shouldTickEntityAt(new net.minecraft.util.math.BlockPos(ticket.pos.getStartX(), 0, ticket.pos.getStartZ()));
    }

    private static boolean completed(CompletableFuture<? extends net.minecraft.server.world.OptionalChunk<?>> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return false;
        try {
            net.minecraft.server.world.OptionalChunk<?> result = future.getNow(null);
            return result != null && result.isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void logReady() {
        logger.normalKey("chunk.ready", ready, expected, failed, deferredLoaded, deferredEntityTicking);
    }

    public boolean isComplete() {
        return !preparing && pending.isEmpty() && inFlight.isEmpty();
    }

    public boolean isReady() {
        return isComplete() && failed == 0 && ready == expected;
    }

    public int expected() {
        return expected;
    }

    public int ready() {
        return ready;
    }

    public int failed() {
        return failed;
    }

    public int deferredLoaded() {
        return deferredLoaded;
    }

    public int deferredEntityTicking() {
        return deferredEntityTicking;
    }

    public int currentConcurrentLoads() { return inFlight.size(); }
    public int pendingFutureCount() { return inFlight.size(); }
    public boolean limitExceeded() { return limitExceeded; }
    public String blockingReason() { return blockingReason; }

    /** Releases all temporary pressure immediately when recovery can no longer continue. */
    public void abort() {
        pending.clear();
        inFlight.clear();
        recoveryWorlds.clear();
        preparing = false;
        releaseCountdown = 0;
        boolean releaseOutOfMemory = false;
        for (Ticket ticket : tickets) {
            try {
                if (!releaseTicket(ticket, true)) releaseOutOfMemory = true;
            } catch (OutOfMemoryError error) {
                // Keep attempting the remaining removals; the caller will fail fast after the
                // temporary references have been cleared.
                releaseOutOfMemory = true;
            } catch (Exception exception) {
                logger.verboseKey("chunk.ticket_release_failed", ticket.pos, exception.getMessage());
            }
        }
        tickets.clear();
        if (releaseOutOfMemory) terminateAfterOutOfMemory(MEMORY_FAILURE);
    }

    public void armRelease() {
        releaseCountdown = Math.max(1, config.recovery.temporaryTicketReleaseTicks);
    }

    /** Releases the recovery tickets before the next tick instead of after the configured delay. */
    public void releaseNow(MinecraftServer server) {
        if (tickets.isEmpty()) return;
        releaseCountdown = 1;
        tick(server);
    }

    public void tick(MinecraftServer server) {
        if (tickets.isEmpty() || releaseCountdown <= 0) return;
        if (--releaseCountdown > 0) return;
        boolean releaseOutOfMemory = false;
        for (Ticket ticket : tickets) {
            try {
                if (!releaseTicket(ticket, false)) releaseOutOfMemory = true;
            } catch (OutOfMemoryError error) {
                releaseOutOfMemory = true;
            } catch (Exception exception) {
                logger.verboseKey("chunk.ticket_release_failed", ticket.pos, exception.getMessage());
            }
        }
        tickets.clear();
        if (releaseOutOfMemory) terminateAfterOutOfMemory(MEMORY_FAILURE);
        logger.normalKey("chunk.tickets_released");
    }

    private boolean releaseTicket(Ticket ticket, boolean undoForcedState) {
        if (ticket == null || ticket.released) return true;
        try {
            ticket.world.getChunkManager().removeTicket(
                    RECOVERY_TICKET, ticket.pos, ticket.radius);
            if (undoForcedState && ticket.forcedAddedByRecovery) {
                ticket.world.setChunkForced(ticket.pos.x, ticket.pos.z, false);
            }
            ticket.released = true;
            return true;
        } catch (OutOfMemoryError error) {
            throw error;
        } catch (Exception exception) {
            logger.verboseKey("chunk.ticket_release_failed", ticket.pos, exception.getMessage());
            return false;
        }
    }

    private record PendingChunk(ServerWorld world, ChunkRecoveryPlan.Entry entry) {
    }

    private static final class Ticket {
        private final ServerWorld world;
        private final ChunkPos pos;
        private final int radius;
        private final int ticketLevel;
        private boolean forcedAddedByRecovery;
        private boolean ready;
        private boolean failed;
        private boolean released;

        private Ticket(ServerWorld world, ChunkPos pos, int radius, int ticketLevel) {
            this.world = world;
            this.pos = pos;
            this.radius = radius;
            this.ticketLevel = ticketLevel;
        }
    }

    private record InFlight(Ticket ticket,
                            CompletableFuture<net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk>> future) {
    }
}
