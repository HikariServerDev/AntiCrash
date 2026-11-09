package com.atsukimc.anticrash.util;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class VersionChunkPosTest {
    @Test
    void packedCoordinatesKeepTheCheckpointWireLayout() {
        int[][] coordinates = {
            {0, 0},
            {1, -1},
            {-330, 122},
            {-1, -1},
            {Integer.MIN_VALUE, Integer.MAX_VALUE},
            {Integer.MAX_VALUE, Integer.MIN_VALUE}
        };
        for (int[] pair : coordinates) {
            int x = pair[0];
            int z = pair[1];
            // Independent wire-format oracle: X occupies the low 32 bits, Z the high 32.
            long expected = Integer.toUnsignedLong(x) | (Integer.toUnsignedLong(z) << 32);
            ChunkPos position = new ChunkPos(x, z);
            assertEquals(expected, VersionChunkPos.pack(x, z), x + "," + z);
            assertEquals(expected, VersionChunkPos.pack(position));
            ChunkPos decoded = VersionChunkPos.unpack(expected);
            assertEquals(x, VersionChunkPos.x(decoded));
            assertEquals(z, VersionChunkPos.z(decoded));
        }
    }

    @Test
    void containingUsesFloorDivisionAtNegativeBlockBoundaries() {
        int[] blocks = {-30000000, -33, -32, -17, -16, -1, 0, 15, 16, 31, 32, 30000000};
        for (int x : blocks) {
            for (int z : blocks) {
                ChunkPos chunk = VersionChunkPos.containing(new BlockPos(x, 64, z));
                assertEquals(Math.floorDiv(x, 16), VersionChunkPos.x(chunk), "block X=" + x);
                assertEquals(Math.floorDiv(z, 16), VersionChunkPos.z(chunk), "block Z=" + z);
            }
        }
    }
}
