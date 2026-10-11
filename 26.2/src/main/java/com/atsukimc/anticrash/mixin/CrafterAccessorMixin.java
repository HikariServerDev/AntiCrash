package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CrafterBlockEntity.class)
public interface CrafterAccessorMixin {
    @Accessor("craftingTicksRemaining") int anticrash$craftingTicksRemaining();
}
