package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedstoneTorchBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** Read/write is restricted to replacing the new world's own burnout history during recovery. */
@Mixin(RedstoneTorchBlock.class)
public interface RedstoneTorchBlockAccessorMixin {
    @Accessor("RECENT_TOGGLES")
    static Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> anticrash$getBurnoutMap() {
        throw new AssertionError("Mixin accessor");
    }
}
