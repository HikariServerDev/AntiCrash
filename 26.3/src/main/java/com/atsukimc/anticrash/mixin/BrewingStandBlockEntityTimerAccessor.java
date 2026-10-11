package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityTimerAccessor {
    @Accessor("totalBrewTime") int anticrash$totalBrewTime();
    @Accessor("fuel") int anticrash$fuel();
    @Accessor("totalFuel") int anticrash$totalFuel();
    @Accessor("speedMultiplier") float anticrash$speed();
    @Accessor("quickCheck") net.minecraft.world.item.crafting.RecipeManager.CachedCheck anticrash$recipeCheck();
    @Accessor("brewTime")
    int anticrash$getBrewTime();

    @Accessor("ingredient")
    net.minecraft.world.item.Item anticrash$getItemBrewing();
}
