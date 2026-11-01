package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.HopperBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HopperBlockEntity.class)
public interface HopperBlockEntityTimerAccessor {
    @Accessor("transferCooldown")
    int anticrash$getTransferCooldown();
}
