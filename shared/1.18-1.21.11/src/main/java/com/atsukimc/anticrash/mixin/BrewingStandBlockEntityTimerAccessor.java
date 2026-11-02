package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.BrewingStandBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityTimerAccessor {
    @Accessor("brewTime")
    int anticrash$getBrewTime();

    @Accessor("itemBrewing")
    net.minecraft.item.Item anticrash$getItemBrewing();
}
