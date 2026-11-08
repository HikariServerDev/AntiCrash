//? if (>=26.1 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.world.entity.EntityFluidInteraction.class)
public interface FluidInteractionAccessor {

    //? if (>=26.1 && <=26.2) {
    /*@Accessor("trackerByFluid")
    java.util.Map<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>, ?>
            anticrash$trackers();
    *///?}
    //? if 26.3 {
    @Accessor("fluidTrackers")
    java.util.List<?> anticrash$trackers();

    @Accessor("currentAccumulators")
    it.unimi.dsi.fastutil.objects.Reference2ObjectMap<
                    net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>, ?>
            anticrash$currents();
    //?}

}
//?}
