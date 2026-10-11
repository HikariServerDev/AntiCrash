package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChunkStateSnapshot {
    /** Version 3 records the exact observable ChunkHolder execution domain. */
    public int formatVersion = 3;
    public String dimension;
    public final List<Long> loadedChunks = new ArrayList<>();
    /** Chunks whose FULL future was complete at the checkpoint boundary. */
    public final List<Long> fullChunks = new ArrayList<>();
    /** Chunks whose block-ticking future/readiness was complete at the boundary. */
    public final List<Long> blockTickingChunks = new ArrayList<>();
    public final List<Long> entityTickingChunks = new ArrayList<>();
    public final List<Long> forcedChunks = new ArrayList<>();
    /** Moving pistons, scheduled ticks, primed TNT or falling blocks at the save boundary. */
    public final List<Long> transientStateChunks = new ArrayList<>();
    /** One semantic row per chunk known to the storage/ticket domain. */
    public final List<ChunkReadiness> domain = new ArrayList<>();

    public static ChunkStateSnapshot capture(ServerWorld world, Set<Long> transientStateChunks) {
        ChunkStateSnapshot snapshot = new ChunkStateSnapshot();
        snapshot.dimension = world.getRegistryKey().getValue().toString();
        Set<Long> loaded = new LinkedHashSet<>();
        ThreadedAnvilChunkStorage storage = world.getChunkManager().threadedAnvilChunkStorage;
        ThreadedAnvilChunkStorageAccessorMixin accessor = (ThreadedAnvilChunkStorageAccessorMixin) storage;
        for (Long position : accessor.anticrash$getLoadedChunks()) loaded.add(position);
        snapshot.loadedChunks.addAll(loaded);
        Set<Long> forced = new LinkedHashSet<>();
        for (Long position : world.getForcedChunks()) {
            forced.add(position);
            snapshot.forcedChunks.add(position);
        }
        for (ChunkHolder holder : accessor.anticrash$getCurrentChunkHolders().values()) {
            if (holder == null) continue;
            long packed = holder.getPos().toLong();
            boolean full = completed(holder.getValidFutureFor(ChunkStatus.FULL));
            boolean block = holder.getLevelType() == ChunkHolder.LevelType.TICKING
                    || holder.getLevelType() == ChunkHolder.LevelType.ENTITY_TICKING;
            block = block && completed(holder.getTickingFuture())
                    && world.getChunkManager().isTickingFutureReady(packed);
            boolean entity = holder.getLevelType() == ChunkHolder.LevelType.ENTITY_TICKING
                    && completed(holder.getEntityTickingFuture());
            // ChunkHolder also contains propagated BORDER/INACCESSIBLE entries created around a
            // ticket. They are storage bookkeeping, not part of the executable checkpoint tick
            // domain. Recording them would make the post-recovery domain differ merely because
            // AntiCrash's temporary ticket graph is still being drained. Keep every actual
            // loaded/readiness/forced chunk, and deliberately omit non-executable halo holders.
            if (!loaded.contains(packed) && !full && !block && !entity && !forced.contains(packed)) {
                continue;
            }
            if (full) snapshot.fullChunks.add(packed);
            if (block) snapshot.blockTickingChunks.add(packed);
            if (entity) snapshot.entityTickingChunks.add(packed);
            snapshot.domain.add(ChunkReadiness.of(holder, loaded.contains(packed), full, block,
                    entity, forced.contains(packed), "holder"));
        }
        // A loaded or forced position may not have a live holder row during the narrow snapshot
        // boundary. Keep it in the domain with explicit false readiness instead of silently
        // treating a missing holder as READY.
        Set<Long> known = new LinkedHashSet<>();
        for (ChunkReadiness readiness : snapshot.domain) known.add(readiness.packedPosition);
        for (Long packed : loaded) {
            if (known.add(packed)) snapshot.domain.add(ChunkReadiness.simple(
                    packed, true, false, false, false, forced.contains(packed), "loaded"));
        }
        for (Long packed : forced) {
            if (known.add(packed)) snapshot.domain.add(ChunkReadiness.simple(
                    packed, loaded.contains(packed), false, false, false, true, "forced"));
        }
        if (transientStateChunks != null) snapshot.transientStateChunks.addAll(transientStateChunks);
        snapshot.loadedChunks.sort(Long::compare);
        snapshot.fullChunks.sort(Long::compare);
        snapshot.blockTickingChunks.sort(Long::compare);
        snapshot.entityTickingChunks.sort(Long::compare);
        snapshot.forcedChunks.sort(Long::compare);
        snapshot.transientStateChunks.sort(Long::compare);
        snapshot.domain.sort(Comparator.comparingLong(row -> row.packedPosition));
        return snapshot;
    }

    public int loadedCount() { return loadedChunks.size(); }
    public int entityTickingCount() { return entityTickingChunks.size(); }
    public int fullCount() { return fullChunks == null ? 0 : fullChunks.size(); }
    public int blockTickingCount() { return blockTickingChunks == null ? 0 : blockTickingChunks.size(); }
    public int forcedCount() { return forcedChunks == null ? 0 : forcedChunks.size(); }

    public List<String> domainLines() {
        List<String> lines = new ArrayList<>();
        if (domain == null) return lines;
        for (ChunkReadiness readiness : domain) {
            if (readiness != null) lines.add(readiness.canonical());
        }
        return lines;
    }

    private static boolean completed(CompletableFuture<? extends Either<?, ?>> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return false;
        try {
            Either<?, ?> result = future.getNow(null);
            return result != null && result.left().isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static final class ChunkReadiness {
        public int formatVersion = 1;
        public long packedPosition;
        public boolean loaded;
        public boolean full;
        public boolean blockTicking;
        public boolean entityTicking;
        public boolean forced;
        /** ChunkHolder level at capture time; -1 means no holder was observable. */
        public int level = -1;
        public int completedLevel = -1;
        public String levelType;
        public String source;

        private static ChunkReadiness of(ChunkHolder holder, boolean loaded, boolean full,
                                         boolean blockTicking, boolean entityTicking,
                                         boolean forced, String source) {
            ChunkReadiness result = simple(holder.getPos().toLong(), loaded, full, blockTicking,
                    entityTicking, forced, source);
            result.level = holder.getLevel();
            result.completedLevel = holder.getCompletedLevel();
            result.levelType = holder.getLevelType() == null ? null : holder.getLevelType().name();
            return result;
        }

        private static ChunkReadiness simple(long packedPosition, boolean loaded, boolean full,
                                             boolean blockTicking, boolean entityTicking,
                                             boolean forced, String source) {
            ChunkReadiness result = new ChunkReadiness();
            result.packedPosition = packedPosition;
            result.loaded = loaded;
            result.full = full;
            result.blockTicking = blockTicking;
            result.entityTicking = entityTicking;
            result.forced = forced;
            result.source = source;
            result.levelType = "INACCESSIBLE";
            return result;
        }

        public String canonical() {
            return new ChunkPos(packedPosition).x + "," + new ChunkPos(packedPosition).z
                    + "|loaded=" + loaded + "|full=" + full + "|block=" + blockTicking
                    + "|entity=" + entityTicking + "|forced=" + forced + "|level=" + level
                    + "|completed=" + completedLevel + "|type=" + levelType + "|source=" + source;
        }

        /**
         * Returns only the flags that define whether this chunk can execute
         * world ticks. Holder level, completed level, type and source are
         * diagnostic metadata; they can legitimately change after restart
         * while the actual executable domain remains equivalent because
         * vanilla and temporary recovery tickets are reconstructed.
         */
        public String executionCanonical() {
            return new ChunkPos(packedPosition).x + "," + new ChunkPos(packedPosition).z
                    + "|loaded=" + loaded + "|full=" + full + "|block=" + blockTicking
                    + "|entity=" + entityTicking + "|forced=" + forced;
        }
    }
}
