package com.atsukimc.anticrash.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(net.minecraft.world.entity.EntityFluidInteraction.class)
public interface FluidInteractionAccessor {
    @Accessor("trackerByFluid") java.util.Map<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>, ?> anticrash$trackers();
}
