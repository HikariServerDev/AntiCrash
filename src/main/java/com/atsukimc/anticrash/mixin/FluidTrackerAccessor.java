//? if (>=26.1 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$Tracker")
public interface FluidTrackerAccessor {

    //? if 26.3 {
    @Accessor("fluidType")
    net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid> anticrash$type();

    //?}

    @Accessor("height")
    double anticrash$height();

    @Accessor("height")
    void anticrash$height(double value);

    @Accessor("eyesInside")
    boolean anticrash$eyes();

    @Accessor("eyesInside")
    void anticrash$eyes(boolean value);

    //? if (>=26.1 && <=26.2) {
    /*@Accessor("accumulatedCurrent")
    net.minecraft.world.phys.Vec3 anticrash$current();
*///?}

    //? if (>=26.1 && <=26.2) {
    /*@Accessor("accumulatedCurrent")
    void anticrash$current(net.minecraft.world.phys.Vec3 value);
*///?}

    //? if (>=26.1 && <=26.2) {
    /*@Accessor("currentCount")
    int anticrash$count();
*///?}

    //? if (>=26.1 && <=26.2) {
    /*@Accessor("currentCount")
    void anticrash$count(int value);
*///?}

}
//?}
