package com.atsukimc.anticrash.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * ChunkPos across versions. It became a record in 26.1: the x and z fields turned into accessors,
 * toLong and asLong into pack, and the packed and BlockPos constructors into unpack and containing.
 *
 * <p>{@code new ChunkPos(int, int)} and {@code ChunkPos.getX/getZ(long)} are the same everywhere
 * and need no helper.
 */
public final class VersionChunkPos {
    private VersionChunkPos() {}

    public static int x(ChunkPos pos) {
        //? if <=1.21.11 {
        /*return pos.x;
        *///?} else {
        return pos.x();
        //?}
    }

    public static int z(ChunkPos pos) {
        //? if <=1.21.11 {
        /*return pos.z;
        *///?} else {
        return pos.z();
        //?}
    }

    public static long pack(ChunkPos pos) {
        //? if <=1.21.11 {
        /*return pos.toLong();
        *///?} else {
        return pos.pack();
        //?}
    }

    public static long pack(int x, int z) {
        //? if <=1.21.11 {
        /*return ChunkPos.asLong(x, z);
        *///?} else {
        return ChunkPos.pack(x, z);
        //?}
    }

    public static ChunkPos unpack(long packed) {
        //? if <=1.21.11 {
        /*return new ChunkPos(packed);
        *///?} else {
        return ChunkPos.unpack(packed);
        //?}
    }

    /** The chunk containing a block. */
    public static ChunkPos containing(BlockPos pos) {
        //? if <=1.21.11 {
        /*return new ChunkPos(pos);
        *///?} else {
        return ChunkPos.containing(pos);
        //?}
    }
}
