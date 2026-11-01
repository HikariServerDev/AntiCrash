package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.world.entity.Entity.class)
public interface EntityFluidAccessor {
    @Accessor("fluidInteraction")
    net.minecraft.world.entity.EntityFluidInteraction anticrash$fluids();
}
