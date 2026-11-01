package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.HopperBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HopperBlockEntity.class)
public interface HopperBlockEntityTimerAccessor {
    @Accessor("cooldownTime")
    int anticrash$getTransferCooldown();
}
