package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.entity.BlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.entity.Entity;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.entity.item.FallingBlockEntity;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.entity.item.PrimedTnt;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.CompoundTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import net.minecraft.server.MinecraftServer;
//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.level.ChunkHolder;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ChunkHolder;
*///?}

//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.server.level.ChunkMap;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ChunkMap;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.level.ServerLevel;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.20.6) {
import net.minecraft.server.level.ChunkMap;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.core.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.item.FallingBlockEntity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.chunk.LevelChunk;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.item.PrimedTnt;
*///?}

//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.BlockEntity;
*///?}

//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
*///?}

//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.LevelChunk;
*///?}

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes Vanilla's transient chunk state eligible for the save immediately before a checkpoint.
 *
 * <p>In 1.18.2 {@code ChunkSerializer} already writes block entities and chunk-local scheduled
 * ticks. The missing part is that {@code ChunkMap} skips serialization unless
 * {@code Chunk#needsSaving()} is true. Piston progress and some scheduler mutations do not set that
 * flag reliably, so the checkpoint can otherwise contain an older moving-piston state.
 *
 * <p>This class runs only on the server thread while the checkpoint barrier owns the world. It
 * examines already accessible chunks and never requests or waits for a new chunk.
 */

/**
 * Makes Vanilla's transient chunk state eligible for the save immediately before a checkpoint.
 *
 * <p>In 1.18.2 {@code ChunkSerializer} already writes block entities and chunk-local scheduled
 * ticks. The missing part is that {@code ChunkMap} skips serialization unless
 * {@code Chunk#needsSaving()} is true. Piston progress and some scheduler mutations do not set that
 * flag reliably, so the checkpoint can otherwise contain an older moving-piston state.
 *
 * <p>This class runs only on the server thread while the checkpoint barrier owns the world. It
 * examines already accessible chunks and never requests or waits for a new chunk.
 */
public final class TransientChunkStateMarker {
    private static final String PISTON_BLOCK_ENTITY_ID = "minecraft:piston";

    private TransientChunkStateMarker() {}

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

        //? if (>=1.18 && <=1.21.11) {
        for (ServerLevel world : server.getAllLevels())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (ServerLevel world : server.getAllLevels())
*///?}
        {

            //? if (>=1.18 && <=1.20.6) {
            ChunkMap storage = world.getChunkSource().chunkMap;
            //?}
            //? if (>=1.21 && <=1.21.11) {
            /*ChunkMap storage = world.getChunkSource().chunkMap;
*///?}
            //? if (>=26.1 && <=26.3) {
            /*ChunkMap storage = world.getChunkSource().chunkMap;
*///?}

            ThreadedAnvilChunkStorageAccessorMixin accessor =
                    (ThreadedAnvilChunkStorageAccessorMixin) storage;
            for (ChunkHolder holder : accessor.anticrash$getCurrentChunkHolders().values()) {
                if (holder == null) continue;

                //? if (>=1.18 && <=1.21.11) {
                LevelChunk chunk = accessibleChunk(holder);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*LevelChunk chunk = accessibleChunk(holder);
*///?}

                if (chunk == null) continue;
                inspectedChunks++;

                int pistonsInChunk = countMovingPistons(chunk);
                // 26.2 sulfur's server ticker changes waitingCountdown without setChanged.
                // At the cut, serialize every already-loaded sulfur chunk; never load extras.

                //? if 26.2 {
                /*boolean sulfur =
                                        chunk.getBlockEntities().values().stream()
                                                .anyMatch(entity ->
                                                        entity
                                                                instanceof
                                                                net.minecraft.world.level.block.entity
                                                                        .PotentSulfurBlockEntity);
*///?}
                //? if 26.3 {
                /*boolean sulfur =
                                        chunk.getBlockEntities().values().stream()
                                                .anyMatch(entity ->
                                                        entity
                                                                        instanceof
                                                                        net.minecraft.world.level.block.entity
                                                                                .PotentSulfurBlockEntity
                                                                || entity
                                                                        instanceof
                                                                        net.minecraft.world.level.block.entity
                                                                                .BrewingStandBlockEntity);
*///?}

                // Empty schedulers are common. Avoid allocating a stream/list for them, while
                // still taking the queue snapshot only once when a queue is advertised. The
                // mutation tracer covers a concurrent add/remove between this count and the
                // read; we never infer a mixed epoch from two list observations.

                //? if (>=1.18 && <=1.21.11) {
                int blockTicks = chunk.getBlockTicks().count();
                //?}
                //? if (>=26.1 && <=26.3) {
                /*int blockTicks = chunk.getBlockTicks().count();
*///?}

                //? if (>=1.18 && <=1.21.11) {
                int fluidTicks = chunk.getFluidTicks().count();
                //?}
                //? if (>=26.1 && <=26.3) {
                /*int fluidTicks = chunk.getFluidTicks().count();
*///?}

                long advertisedScheduledTicks = (long) blockTicks + fluidTicks;

                //? if (>=1.18 && <=1.21.11) {
                List<ScheduledTickSnapshot> exactTicks =
                        advertisedScheduledTicks == 0L
                                ? List.of()
                                : ScheduledTickSnapshot.capture(
                                        //? if (>=1.18 && <=1.21.10) {
                                        world.dimension().location().toString(), chunk);
                //?}
                //? if 1.21.11 {
                /*world.dimension().identifier().toString(), chunk);
                                        *///?}
                //?}
                //? if (>=26.1 && <=26.3) {
                /*List<ScheduledTickSnapshot> exactTicks =
                                        advertisedScheduledTicks == 0L
                                                ? List.of()
                                                : ScheduledTickSnapshot.capture(
                                                        world.dimension().identifier().toString(), chunk);
*///?}

                long chunkScheduledTicks = exactTicks.size();
                // LevelTicks is driven by the block-ticking predicate, not by the
                // entity-ticking predicate. A FULL/TICKING holder can therefore own a live
                // scheduled-tick queue even when entities are deliberately not ticking there.
                // The accessible future above is the safe, non-blocking representation of that
                // queue at the checkpoint boundary; do not narrow capture to ENTITY_TICKING.
                if (chunkScheduledTicks > 0) orderedScheduledTicks.addAll(exactTicks);

                //? if (>=1.18 && <=26.1) {
                if (!needsTransientSave(pistonsInChunk > 0, blockTicks, fluidTicks))
                    //?}
                    //? if (>=26.2 && <=26.3) {
                    /*if (!needsTransientSave(pistonsInChunk > 0 || sulfur, blockTicks, fluidTicks))
*///?}
                    continue;

                //? if (>=1.18 && <=1.21.11) {
                criticalChunks.add(
                        new ChunkReference(
                                //? if (>=1.18 && <=1.21.10) {
                                world.dimension().location().toString(),
                                //?}
                                //? if 1.21.11 {
                                /*world.dimension().identifier().toString(),
                                *///?}
                                chunk.getPos().toLong()));
                //?}
                //? if (>=26.1 && <=26.3) {
                /*criticalChunks.add(
                                        new ChunkReference(
                                                world.dimension().identifier().toString(), chunk.getPos().pack()));
*///?}

                if (pistonsInChunk > 0) {
                    movingPistonChunks++;
                    movingPistons += pistonsInChunk;
                }
                if (chunkScheduledTicks > 0) {
                    scheduledTickChunks++;
                    scheduledTicks += chunkScheduledTicks;
                }

                //? if (>=1.18 && <=1.21.11) {
                if (!chunk.isUnsaved())
                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (!chunk.isUnsaved())
*///?}
                {
                    chunksMadeDirty++;

                    //? if (>=1.18.2 && <=1.21.1) {
                    chunk.setUnsaved(true);
                    //?}
                    //? if (>=1.18 && <=1.18.1) {
                    /*chunk.setUnsaved(true);
*///?}
                    //? if (>=1.21.2 && <=1.21.11) {
                    /*chunk.markUnsaved();
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*chunk.markUnsaved();
*///?}

                }
            }

            // These entities represent blocks/TNT that are between stable block states. Their
            // entity NBT is flushed by the normal save, but their chunks must also reach
            // entity-ticking readiness before restored simulation resumes.

            //? if (>=1.18 && <=1.21.11) {
            for (Entity entity : world.getAllEntities())
            //?}
            //? if (>=26.1 && <=26.3) {
            /*for (Entity entity : world.getAllEntities())
*///?}
            {
                if (!isCheckpointCriticalEntity(entity)) continue;
                transientPhysicsEntities++;

                //? if (>=1.18 && <=1.21.11) {
                criticalChunks.add(
                        new ChunkReference(
                                //? if (>=1.18 && <=1.21.10) {
                                world.dimension().location().toString(),
                                //?}
                                //? if 1.21.11 {
                                /*world.dimension().identifier().toString(),
                                *///?}
                                entity.chunkPosition().toLong()));
                //?}
                //? if (>=26.1 && <=26.3) {
                /*criticalChunks.add(
                                        new ChunkReference(
                                                world.dimension().identifier().toString(),
                                                entity.chunkPosition().pack()));
*///?}

            }
        }

        return new MarkResult(
                inspectedChunks,
                chunksMadeDirty,
                movingPistonChunks,
                movingPistons,
                scheduledTickChunks,
                scheduledTicks,
                transientPhysicsEntities,
                List.copyOf(criticalChunks),
                List.copyOf(orderedScheduledTicks));
    }

    /** Kept separate from Minecraft objects so the checkpoint policy can be regression-tested. */
    static boolean needsTransientSave(boolean hasMovingPiston, int blockTicks, int fluidTicks) {
        return hasMovingPiston || blockTicks > 0 || fluidTicks > 0;
    }

    static boolean isCheckpointCriticalEntity(Entity entity) {

        //? if (>=1.18 && <=1.21.11) {
        return entity != null
                && !entity.isRemoved()
                && (entity instanceof PrimedTnt || entity instanceof FallingBlockEntity);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*return entity != null
                        && !entity.isRemoved()
                        && (entity instanceof PrimedTnt || entity instanceof FallingBlockEntity);
*///?}

    }

    //? if (>=1.18 && <=1.21.11) {
    private static LevelChunk accessibleChunk(ChunkHolder holder)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static LevelChunk accessibleChunk(ChunkHolder holder)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        var future = holder.getFullChunkFuture();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*var future = holder.getFullChunkFuture();
*///?}

        if (!future.isDone() || future.isCompletedExceptionally()) return null;
        try {
            var ready = future.getNow(null);

            //? if (>=1.18 && <=1.20.4) {
            return ready == null ? null : ready.left().orElse(null);
            //?}
            //? if (>=1.20.5 && <=26.3) {
            /*return ready == null ? null : ready.orElse(null);
*///?}

        } catch (RuntimeException ignored) {
            // A chunk can become unavailable while its holder is being retired. Never block or
            // force-load it from this marker; the normal save path handles the holder instead.
            return null;
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static int countMovingPistons(LevelChunk chunk)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static int countMovingPistons(LevelChunk chunk)
*///?}
            {
        Map<BlockPos, BlockEntity> liveBlockEntities = chunk.getBlockEntities();
        int count = 0;
        for (BlockEntity blockEntity : liveBlockEntities.values()) {

            //? if (>=1.18 && <=1.21.11) {
            if (blockEntity instanceof PistonMovingBlockEntity)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (blockEntity instanceof PistonMovingBlockEntity)
*///?}
                count++;
        }

        // LevelChunk can retain a pending NBT entry before the block entity is materialized.
        // Include that representation too, while avoiding a duplicate for a live entry.

        //? if (>=1.18 && <=1.21.11) {
        for (BlockPos position : chunk.getBlockEntitiesPos())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (BlockPos position : chunk.getBlockEntitiesPos())
*///?}
        {
            if (liveBlockEntities.containsKey(position)) continue;

            //? if (>=1.18 && <=1.21.11) {
            CompoundTag pending = chunk.getBlockEntityNbt(position);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*CompoundTag pending = chunk.getBlockEntityNbt(position);
*///?}

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
            List<ScheduledTickSnapshot> orderedScheduledTicks) {
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

    public record ChunkReference(String dimension, long packedPosition) {}
}
