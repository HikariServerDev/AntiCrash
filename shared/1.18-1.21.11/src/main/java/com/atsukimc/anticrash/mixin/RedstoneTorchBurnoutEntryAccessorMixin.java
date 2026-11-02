package com.atsukimc.anticrash.mixin;

import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RedstoneTorchBlock.BurnoutEntry.class)
public interface RedstoneTorchBurnoutEntryAccessorMixin {
    @Accessor("pos")
    BlockPos anticrash$getPos();

    @Accessor("time")
    long anticrash$getTime();
}
