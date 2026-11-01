package com.atsukimc.anticrash.mixin;

import net.minecraft.world.food.FoodData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.2 removed the public exhaustion accessors, not the persisted exhaustion field. */
@Mixin(FoodData.class)
public interface HungerManagerAccessorMixin {
    @Accessor("exhaustionLevel")
    float anticrash$getExhaustion();

    @Accessor("exhaustionLevel")
    void anticrash$setExhaustion(float value);
}
