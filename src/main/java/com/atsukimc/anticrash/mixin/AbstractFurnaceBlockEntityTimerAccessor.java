package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityTimerAccessor {

    //? if (>=1.18 && <=1.21.3) {
    /*@Accessor("litTime")
    int anticrash$getBurnTime();

    @Accessor("cookingProgress")
    int anticrash$getCookTime();
    *///?} else {
    @Accessor("litTimeRemaining")
    int anticrash$getBurnTime();

    @Accessor("cookingTimer")
    int anticrash$getCookTime();
    //?}
}
