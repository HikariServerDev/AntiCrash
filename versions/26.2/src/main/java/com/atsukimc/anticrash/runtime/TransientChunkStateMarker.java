package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * Makes Vanilla's transient chunk state eligible for the save immediately before a checkpoint.
 *
 * <p>In 1.18.2 {@code ChunkSerializer} already writes block entities and chunk-local scheduled
 * ticks. The missing part is that {@code ServerChunkLoadingManager} skips serialization unless
 * {@code Chunk#needsSaving()} is true. Piston progress and some scheduler mutations do not set
 * that flag reliably, so the checkpoint can otherwise contain an older moving-piston state.</p>
 *
 * <p>This class runs only on the server thread while the checkpoint barrier owns the world. It
 * examines already accessible chunks and never requests or waits for a new chunk.</p>
 */
public final class TransientChunkStateMarker {
    private static final String PISTON_BLOCK_ENTITY_ID = "minecraft:piston";

    private TransientChunkStateMarker() {
    }

    public static MarkResult markForCheckpoint(MinecraftServer server) {
        int inspectedChunks = 0;
        int chunksMadeDirty = 0;
        int movingPistonChunks = 0;
        int movingPistons = 0;
        int scheduledTickChunks = 0;
        long scheduledTicks = 0L;
        int transientPhysicsEntities = 0;
        Set<ChunkReference> criticalChunks = new LinkedHashSet<>();
        List<ScheduledTickSnapshot> orderedScheduledTicks = new ArrayList<>();

        for (ServerLevel world : server.getAllLevels()) {
            ChunkMap storage = world.getChunkSource().chunkMap;
            ThreadedAnvilChunkStorageAccessorMixin accessor =
                    (ThreadedAnvilChunkStorageAccessorMixin) storage;
            for (ChunkHolder holder : accessor.anticrash$getCurrentChunkHolders().values()) {
                if (holder == null) continue;
                LevelChunk chunk = accessibleChunk(holder);
                if (chunk == null) continue;
                inspectedChunks++;

                int pistonsInChunk = countMovingPistons(chunk);
                // 26.2 sulfur's server ticker changes waitingCountdown without setChanged.
                // At the cut, serialize every already-loaded sulfur chunk; never load extras.
                boolean sulfur = chunk.getBlockEntities().values().stream().anyMatch(
                        entity -> entity instanceof net.minecraft.world.level.block.entity.PotentSulfurBlockEntity);
                // Empty schedulers are common. Avoid allocating a stream/list for them, while
                // still taking the queue snapshot only once when a queue is advertised. The
                // mutation tracer covers a concurrent add/remove between this count and the
                // read; we never infer a mixed epoch from two list observations.
                int blockTicks = chunk.getBlockTicks().count();
                int fluidTicks = chunk.getFluidTicks().count();
                long advertisedScheduledTicks = (long) blockTicks + fluidTicks;
                List<ScheduledTickSnapshot> exactTicks = advertisedScheduledTicks == 0L
                        ? List.of()
                        : ScheduledTickSnapshot.capture(
                        world.dimension().identifier().toString(), chunk);
                long chunkScheduledTicks = exactTicks.size();
                // WorldTickScheduler is driven by the block-ticking predicate, not by the
                // entity-ticking predicate. A FULL/TICKING holder can therefore own a live
                // scheduled-tick queue even when entities are deliberately not ticking there.
                // The accessible future above is the safe, non-blocking representation of that
                // queue at the checkpoint boundary; do not narrow capture to ENTITY_TICKING.
                if (chunkScheduledTicks > 0) orderedScheduledTicks.addAll(exactTicks);
                if (!needsTransientSave(pistonsInChunk > 0 || sulfur, blockTicks, fluidTicks)) continue;

                criticalChunks.add(new ChunkReference(
                        world.dimension().identifier().toString(), chunk.getPos().pack()));
                if (pistonsInChunk > 0) {
                    movingPistonChunks++;
                    movingPistons += pistonsInChunk;
                }
                if (chunkScheduledTicks > 0) {
                    scheduledTickChunks++;
                    scheduledTicks += chunkScheduledTicks;
                }
                if (!chunk.isUnsaved()) {
                    chunksMadeDirty++;
                    chunk.markUnsaved();
                }
            }

            // These entities represent blocks/TNT that are between stable block states. Their
            // entity NBT is flushed by the normal save, but their chunks must also reach
            // entity-ticking readiness before restored simulation resumes.
            for (Entity entity : world.getAllEntities()) {
                if (!isCheckpointCriticalEntity(entity)) continue;
                transientPhysicsEntities++;
                criticalChunks.add(new ChunkReference(
                        world.dimension().identifier().toString(),
                        entity.chunkPosition().pack()));
            }
        }

        return new MarkResult(inspectedChunks, chunksMadeDirty,
                movingPistonChunks, movingPistons, scheduledTickChunks, scheduledTicks,
                transientPhysicsEntities, List.copyOf(criticalChunks),
                List.copyOf(orderedScheduledTicks));
    }

    /** Kept separate from Minecraft objects so the checkpoint policy can be regression-tested. */
    static boolean needsTransientSave(boolean hasMovingPiston, int blockTicks, int fluidTicks) {
        return hasMovingPiston || blockTicks > 0 || fluidTicks > 0;
    }

    static boolean isCheckpointCriticalEntity(Entity entity) {
        return entity != null && !entity.isRemoved()
                && (entity instanceof PrimedTnt || entity instanceof FallingBlockEntity);
    }

    private static LevelChunk accessibleChunk(ChunkHolder holder) {
        var future = holder.getFullChunkFuture();
        if (!future.isDone() || future.isCompletedExceptionally()) return null;
        try {
            var ready = future.getNow(null);
            return ready == null ? null : ready.orElse(null);
        } catch (RuntimeException ignored) {
            // A chunk can become unavailable while its holder is being retired. Never block or
            // force-load it from this marker; the normal save path handles the holder instead.
            return null;
        }
    }

    private static int countMovingPistons(LevelChunk chunk) {
        Map<BlockPos, BlockEntity> liveBlockEntities = chunk.getBlockEntities();
        int count = 0;
        for (BlockEntity blockEntity : liveBlockEntities.values()) {
            if (blockEntity instanceof PistonMovingBlockEntity) count++;
        }

        // WorldChunk can retain a pending NBT entry before the block entity is materialized.
        // Include that representation too, while avoiding a duplicate for a live entry.
        for (BlockPos position : chunk.getBlockEntitiesPos()) {
            if (liveBlockEntities.containsKey(position)) continue;
            CompoundTag pending = chunk.getBlockEntityNbt(position);
            if (pending != null && PISTON_BLOCK_ENTITY_ID.equals(pending.getString("id"))) count++;
        }
        return count;
    }

    public record MarkResult(
            int inspectedChunks,
            int chunksMadeDirty,
            int movingPistonChunks,
            int movingPistons,
            int scheduledTickChunks,
            long scheduledTicks,
            int transientPhysicsEntities,
            List<ChunkReference> criticalChunks,
            List<ScheduledTickSnapshot> orderedScheduledTicks
    ) {
        public MarkResult {
            criticalChunks = List.copyOf(criticalChunks);
            orderedScheduledTicks = List.copyOf(orderedScheduledTicks);
        }

        public Set<Long> chunkPositions(String dimension) {
            Set<Long> result = new LinkedHashSet<>();
            for (ChunkReference chunk : criticalChunks) {
                if (chunk.dimension().equals(dimension)) result.add(chunk.packedPosition());
            }
            return result;
        }

        public int criticalChunkCount() {
            return criticalChunks.size();
        }
    }

    public record ChunkReference(String dimension, long packedPosition) {
    }
}
