package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.chunk.BlockEntityTickInvoker;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.TickingBlockEntity;
*///?}

import org.junit.jupiter.api.Test;

class BlockEntityTickerSnapshotTest {

    //? if (>=1.18 && <=1.21.11) {
    private static BlockEntityTickInvoker ticker(boolean removed)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static TickingBlockEntity ticker(boolean removed)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        return new BlockEntityTickInvoker()
        //?}
        //? if (>=26.1 && <=26.3) {
        /*return new TickingBlockEntity()
*///?}
        {
            @Override
            public void tick() {}

            @Override
            public boolean isRemoved() {
                return removed;
            }

            @Override
            public BlockPos getPos() {

                //? if (>=1.18 && <=1.21.11) {
                return BlockPos.ORIGIN;
                //?}
                //? if (>=26.1 && <=26.3) {
                /*return BlockPos.ZERO;
*///?}

            }

            //? if (>=1.18 && <=1.21.11) {
            @Override
            public String getName()
                        //?}
                        //? if (>=26.1 && <=26.3) {
                        /*@Override
                                    public String getType()
*///?}
                    {
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
