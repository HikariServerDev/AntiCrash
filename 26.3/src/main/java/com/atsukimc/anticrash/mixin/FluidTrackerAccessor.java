package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$Tracker")
public interface FluidTrackerAccessor {
    @Accessor("fluidType")
    net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid> anticrash$type();

    @Accessor("height")
    double anticrash$height();

    @Accessor("height")
    void anticrash$height(double value);

    @Accessor("eyesInside")
    boolean anticrash$eyes();

    @Accessor("eyesInside")
    void anticrash$eyes(boolean value);
}
