package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RedstoneTorchBlock.Toggle.class)
public interface RedstoneTorchBurnoutEntryAccessorMixin {
    @Accessor("pos")
    BlockPos anticrash$getPos();

    @Accessor("when")
    long anticrash$getTime();
}
