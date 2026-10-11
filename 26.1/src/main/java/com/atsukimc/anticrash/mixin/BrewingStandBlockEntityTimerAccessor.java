package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityTimerAccessor {
    @Accessor("brewTime")
    int anticrash$getBrewTime();

    @Accessor("ingredient")
    net.minecraft.world.item.Item anticrash$getItemBrewing();
}
