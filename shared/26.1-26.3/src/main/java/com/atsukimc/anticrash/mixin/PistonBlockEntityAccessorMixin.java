package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PistonMovingBlockEntity.class)
public interface PistonBlockEntityAccessorMixin {
    @Accessor("progress")
    float anticrash$getProgress();

    @Accessor("progressO")
    float anticrash$getLastProgress();
}
