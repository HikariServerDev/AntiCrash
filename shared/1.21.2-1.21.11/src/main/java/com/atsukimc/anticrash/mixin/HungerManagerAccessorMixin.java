package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.player.HungerManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.2 removed the public exhaustion accessors, not the persisted exhaustion field. */
@Mixin(HungerManager.class)
public interface HungerManagerAccessorMixin {
    @Accessor("exhaustion")
    float anticrash$getExhaustion();

    @Accessor("exhaustion")
    void anticrash$setExhaustion(float value);
}
