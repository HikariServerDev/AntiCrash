package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.WorldAccessorMixin;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Position sequence of active moving-piston block-entity tickers in one dimension. */
public final class MovingPistonTickSnapshot {
    public String dimension;
    public int x;
    public int y;
    public int z;
    /**
     * Exact piston state at the cut (capsule v7), kept to verify the restore before the first
     * tick. The chunk data is the authority that restores it; these are never written back.
     */
    public Float progress;
    public Float lastProgress;

    public static List<MovingPistonTickSnapshot> capture(ServerWorld world,
                                                         Set<Long> protectedChunks) {
        List<MovingPistonTickSnapshot> result = new ArrayList<>();
        if (protectedChunks == null || protectedChunks.isEmpty()) return result;
        String dimension = world.getRegistryKey().getValue().toString();
        Set<BlockPos> seen = new HashSet<>();
        WorldAccessorMixin accessor = (WorldAccessorMixin) world;
        captureTickers(result, seen, world, dimension, protectedChunks,
                accessor.anticrash$getBlockEntityTickers());
        // Vanilla appends pending tickers before the next block-entity tick pass, so this
        // concatenation is the exact order the uninterrupted server would use next.
        captureTickers(result, seen, world, dimension, protectedChunks,
                accessor.anticrash$getPendingBlockEntityTickers());
        return result;
    }

    private static void captureTickers(List<MovingPistonTickSnapshot> result,
                                       Set<BlockPos> seen, ServerWorld world, String dimension,
                                       Set<Long> protectedChunks,
                                       List<BlockEntityTickInvoker> tickers) {
        for (BlockEntityTickInvoker ticker : tickers) {
            if (BlockEntityTickerSnapshot.removed(ticker)) continue;
            BlockPos position = ticker.getPos();
            if (!protectedChunks.contains(new ChunkPos(position).toLong())
                    || !(BlockEntityTickerSnapshot.loadedBlockEntity(world, position) instanceof PistonBlockEntity)
                    || !seen.add(position)) {
                continue;
            }
            MovingPistonTickSnapshot snapshot = new MovingPistonTickSnapshot();
            snapshot.dimension = dimension;
            snapshot.x = position.getX();
            snapshot.y = position.getY();
            snapshot.z = position.getZ();
            if (BlockEntityTickerSnapshot.loadedBlockEntity(world, position) instanceof PistonBlockEntity piston) {
                com.atsukimc.anticrash.mixin.PistonBlockEntityAccessorMixin accessor =
                        (com.atsukimc.anticrash.mixin.PistonBlockEntityAccessorMixin) piston;
                snapshot.progress = accessor.anticrash$getProgress();
                snapshot.lastProgress = accessor.anticrash$getLastProgress();
            }
            result.add(snapshot);
        }
    }

    public BlockPos position() {
        return new BlockPos(x, y, z);
    }
}
