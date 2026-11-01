package com.atsukimc.anticrash.mixin;

import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** Read/write is restricted to replacing the new world's own burnout history during recovery. */
@Mixin(RedstoneTorchBlock.class)
public interface RedstoneTorchBlockAccessorMixin {
    @Accessor("BURNOUT_MAP")
    static Map<BlockView, List<RedstoneTorchBlock.BurnoutEntry>> anticrash$getBurnoutMap() {
        throw new AssertionError("Mixin accessor");
    }
}
