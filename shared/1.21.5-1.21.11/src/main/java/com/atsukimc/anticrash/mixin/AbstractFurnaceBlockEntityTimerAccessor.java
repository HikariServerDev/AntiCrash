package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityTimerAccessor {
    @Accessor("litTimeRemaining")
    int anticrash$getBurnTime();

    @Accessor("cookingTimeSpent")
    int anticrash$getCookTime();
}
