//? if >=26.1 {
package com.atsukimc.anticrash.mixin;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityFluidInteraction.class)
public interface FluidInteractionAccessor {

    //? if <=26.2 {
    /*@Accessor("trackerByFluid")
    java.util.Map<TagKey<Fluid>, ?> anticrash$trackers();
    *///?} else {
    @Accessor("fluidTrackers")
    java.util.List<?> anticrash$trackers();

    @Accessor("currentAccumulators")
    it.unimi.dsi.fastutil.objects.Reference2ObjectMap<TagKey<Fluid>, ?> anticrash$currents();
    //?}

}
//?}
