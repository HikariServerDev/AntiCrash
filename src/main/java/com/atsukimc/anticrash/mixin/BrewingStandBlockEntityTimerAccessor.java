package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.entity.BrewingStandBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityTimerAccessor {

    //? if 26.3 {
    /*@Accessor("totalBrewTime")
        int anticrash$totalBrewTime();
*///?}

    //? if 26.3 {
    /*@Accessor("fuel")
        int anticrash$fuel();
*///?}

    //? if 26.3 {
    /*@Accessor("totalFuel")
        int anticrash$totalFuel();
*///?}

    //? if 26.3 {
    /*@Accessor("speedMultiplier")
        float anticrash$speed();
*///?}

    //? if 26.3 {
    /*@Accessor("quickCheck")
        net.minecraft.world.item.crafting.RecipeManager.CachedCheck anticrash$recipeCheck();
*///?}

    @Accessor("brewTime")
    int anticrash$getBrewTime();

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("itemBrewing")
    net.minecraft.item.Item anticrash$getItemBrewing();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("ingredient")
        net.minecraft.world.item.Item anticrash$getItemBrewing();
*///?}

}
