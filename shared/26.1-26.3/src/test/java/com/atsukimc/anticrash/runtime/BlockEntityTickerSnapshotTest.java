package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import org.junit.jupiter.api.Test;

class BlockEntityTickerSnapshotTest {
    private static TickingBlockEntity ticker(boolean removed) {
        return new TickingBlockEntity() {
            @Override
            public void tick() {}

            @Override
            public boolean isRemoved() {
                return removed;
            }

            @Override
            public BlockPos getPos() {
                return BlockPos.ZERO;
            }

            @Override
            public String getType() {
                return "<null>";
            }
        };
    }

    /**
     * Two block entities removed in one tick leave two identical empty tickers in the World list
     * until the next pass drops them. They never run again and must not reach the capsule, whose
     * validator rejects the duplicate and used to fail the whole checkpoint.
     */
    @Test
    void removedTickersCarryNoOrder() {
        assertTrue(BlockEntityTickerSnapshot.removed(ticker(true)));
        assertTrue(BlockEntityTickerSnapshot.removed(null));
        assertFalse(BlockEntityTickerSnapshot.removed(ticker(false)));
    }
}
