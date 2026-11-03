package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.WorldAccessorMixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.level.ServerLevel;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.core.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.TickingBlockEntity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.entity.TickingBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
*///?}

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
     * Exact piston state at the cut (capsule v7), kept to verify the restore before the first tick.
     * The chunk data is the authority that restores it; these are never written back.
     */
    public Float progress;

    public Float lastProgress;

    //? if (>=1.18 && <=1.21.11) {
    public static List<MovingPistonTickSnapshot> capture(
            ServerLevel world, Set<Long> protectedChunks)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static List<MovingPistonTickSnapshot> capture(
                            ServerLevel world, Set<Long> protectedChunks)
*///?}
            {
        List<MovingPistonTickSnapshot> result = new ArrayList<>();
        if (protectedChunks == null || protectedChunks.isEmpty()) return result;

        //? if (>=1.18 && <=1.21.11) {
        //? if (>=1.18 && <=1.21.10) {
        String dimension = world.dimension().location().toString();
        //?}
        //? if 1.21.11 {
        /*String dimension = world.dimension().identifier().toString();
        *///?}
        //?}
        //? if (>=26.1 && <=26.3) {
        /*String dimension = world.dimension().identifier().toString();
*///?}

        Set<BlockPos> seen = new HashSet<>();
        WorldAccessorMixin accessor = (WorldAccessorMixin) world;
        captureTickers(
                result,
                seen,
                world,
                dimension,
                protectedChunks,
                accessor.anticrash$getBlockEntityTickers());
        // Vanilla appends pending tickers before the next block-entity tick pass, so this
        // concatenation is the exact order the uninterrupted server would use next.
        captureTickers(
                result,
                seen,
                world,
                dimension,
                protectedChunks,
                accessor.anticrash$getPendingBlockEntityTickers());
        return result;
    }

    //? if (>=1.18 && <=1.21.11) {
    private static void captureTickers(
            List<MovingPistonTickSnapshot> result,
            Set<BlockPos> seen,
            ServerLevel world,
            String dimension,
            Set<Long> protectedChunks,
            List<TickingBlockEntity> tickers)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static void captureTickers(
                            List<MovingPistonTickSnapshot> result,
                            Set<BlockPos> seen,
                            ServerLevel world,
                            String dimension,
                            Set<Long> protectedChunks,
                            List<TickingBlockEntity> tickers)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        for (TickingBlockEntity ticker : tickers)
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (TickingBlockEntity ticker : tickers)
*///?}
        {
            if (BlockEntityTickerSnapshot.removed(ticker)) continue;
            BlockPos position = ticker.getPos();

            //? if (>=1.18 && <=1.21.11) {
            if (!protectedChunks.contains(new ChunkPos(position).toLong())
                    || !(BlockEntityTickerSnapshot.loadedBlockEntity(world, position)
                            instanceof PistonMovingBlockEntity)
                    || !seen.add(position))
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (!protectedChunks.contains(ChunkPos.containing(position).pack())
                                || !(BlockEntityTickerSnapshot.loadedBlockEntity(world, position)
                                        instanceof PistonMovingBlockEntity)
                                || !seen.add(position))
*///?}
            {
                continue;
            }
            MovingPistonTickSnapshot snapshot = new MovingPistonTickSnapshot();
            snapshot.dimension = dimension;
            snapshot.x = position.getX();
            snapshot.y = position.getY();
            snapshot.z = position.getZ();

            //? if (>=1.18 && <=1.21.11) {
            if (BlockEntityTickerSnapshot.loadedBlockEntity(world, position)
                    instanceof PistonMovingBlockEntity piston)
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (BlockEntityTickerSnapshot.loadedBlockEntity(world, position)
                                instanceof PistonMovingBlockEntity piston)
*///?}
            {
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
