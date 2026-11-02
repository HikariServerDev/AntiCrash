package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.PistonBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PistonBlockEntity.class)
public interface PistonBlockEntityAccessorMixin {
    @Accessor("progress")
    float anticrash$getProgress();

    @Accessor("lastProgress")
    float anticrash$getLastProgress();
}
