package com.atsukimc.anticrash.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$CurrentAccumulator")
public interface FluidCurrentAccessor {
    @Accessor("height") double anticrash$height();
    @Accessor("height") void anticrash$height(double value);
    @Accessor("accumulatedCurrent") net.minecraft.world.phys.Vec3 anticrash$current();
    @Accessor("accumulatedCurrent") void anticrash$current(net.minecraft.world.phys.Vec3 value);
    @Accessor("currentCount") int anticrash$count();
    @Accessor("currentCount") void anticrash$count(int value);
}
